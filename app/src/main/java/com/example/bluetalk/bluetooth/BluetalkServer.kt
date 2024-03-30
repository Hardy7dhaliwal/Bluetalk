package com.example.bluetalk.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.example.bluetalk.bluetooth.client_repository.ClientConnection
import com.example.bluetalk.bluetooth.client_repository.ScannerRepository
import com.example.bluetalk.bluetooth.server_repository.AdvertisingManager
import com.example.bluetalk.bluetooth.server_repository.ServerConnection
import com.example.bluetalk.bluetooth.server_repository.ServerManager
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.MessageType
import com.example.bluetalk.model.UUIDManager
import com.example.bluetalk.model.User
import com.example.bluetalk.security.CryptoManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.ktx.state
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import no.nordicsemi.android.ble.ktx.stateAsFlow
import no.nordicsemi.android.ble.observer.ServerObserver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeoutException


data class Path(val node1: String, val node2: String)


@SuppressLint("MissingPermission","LogNotTimber","StaticFieldLeak")
object BluetalkServer {
    private var isServerStarted = false
    private val TAG = BluetalkServer::class.java.simpleName
    private var app:Application?=null
    private var appID: UUID?=null
    private var coroutineScope:CoroutineScope?=null
    private var adapter:BluetoothAdapter?=null
    private var advertisementManager: AdvertisingManager?=null
    private var serverManger: ServerManager?=null
    //for chat messages
    private val _messages = MutableSharedFlow<Message>()

    //for connection state
    private var _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    //Database
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    var clientConnections = mutableMapOf<String,ClientConnection>()
    var serverConnections = mutableMapOf<String, ServerConnection>()
    var scanner:ScannerRepository?=null
    private var _foundPath = MutableLiveData<Boolean>()
    val foundPath = _foundPath as LiveData<Boolean>
    private val _sosRequest  = MutableLiveData<String>()
    val sosRequest = _sosRequest as LiveData<String>
    var keyErrorDB = mutableListOf<String>()
    private var job:Job?=null
    // Initialize the paths storage
// The key is a Pair<String, String> representing the SrcID and DstID, and the value is the Path
    private val paths = mutableMapOf<Pair<String, String>, Path>()

    // function to add a path upon receiving an RREQ request
    private fun addOrUpdatePathOnRREQ(srcID: String, dstID: String, node1Address: String) {
        val key = srcID to dstID
        val existingPath = paths[key]

        // If there's already a path, update it; otherwise, create a new one
        if (existingPath != null && existingPath.node2.isNotEmpty()) {
            Log.d(TAG,"Same RREQ received so Ignore it")
            //paths[key] = Path(node1Address, existingPath.node2)
        } else {
            paths[key] = Path(node1Address, "")
        }
    }

    // function to update a path upon receiving an RREP response
    private fun updatePathOnRREP(srcID: String, dstID: String, node2Address: String) {
        val key = srcID to dstID
        val existingPath = paths[key]

        // Only update if the path already exists
        if (existingPath != null && existingPath.node1.isNotEmpty()) {
            paths[key] = Path(existingPath.node1, node2Address)
        }else{
            paths[key] = Path("",node2Address)
        }
       Log.d(TAG,"RREP received.")
    }

    // Function to retrieve a path
    private fun getPath(srcID: String, dstID: String): Path? {
        return paths[srcID to dstID]
    }

    private fun makeRREP(msg:String):String{
        return "2 ${getSrcUUID(msg)} ${getDestUUID(msg)} ${getMsgID(msg)} ${getName(msg)} 0 0\n"
    }


    suspend fun processReceivedMsg(msg: String, address: String){
        val srcID = getSrcUUID(msg)
        val dstID = getDestUUID(msg)
        val type = getMsgType(msg)

        if(type == 0){ //regular one to one message
            insertUser(User(uuid = srcID, username = getName(msg), address=address))
            if(isKeyExchangeMessage(msg)){
                reportClientPublicKey(address,msg,srcID)
            }else {
                Log.w(TAG, "Message Received from ${getName(msg)}: ${msg.split("\n")[1]}")
                insertReceivedMsg(msg)
            }
        }else if(type==1){ //path find request
            coroutineScope?.launch(Dispatchers.IO) {
                Log.w(TAG,"Received RREQ")
                addOrUpdatePathOnRREQ(srcID, dstID,address)
                broadcastMessage(address,msg)
            }
        }else if(type==2){//path found reply
            Log.w(TAG,"Received RREP")
            updatePathOnRREP(srcID, dstID,address)
            if(srcID== appID.toString()){
                _foundPath.postValue(true)
            }else {
                coroutineScope?.launch(Dispatchers.IO) {
                    forwardMsg(msg, address)
                }
            }
            job?.cancel()
        }else if (type==3){//for forwarding msg
            Log.w(TAG,"Received Forwarding")
            updatePathOnRREP(appID.toString(),srcID,address )
            if(dstID == appID.toString()){
                if(isKeyExchangeMessage(msg)){
                    reportClientPublicKey(address,msg,srcID,type)
                }else{
                    Log.w(TAG, "Message Received from ${getName(msg)}: ${msg.split("\n")[1]}")
                    insertReceivedMsg(msg)
                }
            }else{
                Log.w(TAG, "Forwarding Message from ${getName(msg)}: ${msg.split("\n")[1]}")
                forwardMsg(msg,address)
            }
        }

    }

    private suspend fun insertUser(user:User){
        Log.w(TAG,"${user.uuid} ${user.username} ${user.address}")
        if (chatDao?.userExists(user.uuid) == true) {
            chatDao?.updateSpecificFields(user.uuid, user.username, user.address)
        } else {
            chatDao?.insertUser(user)
        }
    }

    fun forwardMsg(msg: String, address: String) {
        val srcUUID = getSrcUUID(msg)
        val destUUID = getDestUUID(msg)

        // Check for direct path or reverse path
        val path = paths[srcUUID to destUUID] ?: paths[destUUID to srcUUID]

        if (appID.toString() == srcUUID) {
            Log.w(TAG, "Forwarding Message to ${path?.node2}")
            path?.node2?.let { sendMessage(it, msg) }
        } else {
            val forwardingTo: String = when (address) {
                path?.node1 -> path.node2 // If current node is node1, forward to node2
                path?.node2 -> path.node1 // If current node is node2, forward to node1
                else -> "" // If current node is neither node1 nor node2, forwarding address is undefined
            }

            if (forwardingTo.isBlank()) {
                Log.d(TAG, "No path found for forwarding")
                return
            }
            sendMessage(forwardingTo, msg)
        }
    }


    private fun insertReceivedMsg(msg:String){
        val uuid = getSrcUUID(msg)
        var content = msg.substring(msg.indexOf('\n')+1)
        if(isEncrypted(msg)){
            content = keyStorage[getSrcUUID(msg)]!!.decryptDataWithAES(content)
        }else if (isKeyError(msg)){
            if(!keyErrorDB.contains(uuid)) {
                keyErrorDB.add(uuid)
            }
            keyStorage[uuid]=null
            coroutineScope?.launch(Dispatchers.Main){
                Toast.makeText(app?.applicationContext,"Key Exchange Failed.", Toast.LENGTH_LONG).show()
            }

        }
        val m= Message(
            id = getMsgID(msg),
            content = content,
            timestamp = System.currentTimeMillis(),
            messageType = MessageType.RECEIVED,
            clientUuid = uuid
        )
        insertMessageInDb(m)
    }

    private fun insertMessageInDb(message: Message) {
        coroutineScope?.launch(Dispatchers.IO) {
            chatDao?.insertMessage(message)
        }
    }

    // Define a map to track processed devices for each message
    private val processedDevicesForMessages = mutableMapOf<String, MutableSet<String>>()

    suspend fun broadcastMessage(device:String, msg: String) {
        job?.cancel() // if there is any other job then cancel that job
        val srcUuid = getSrcUUID(msg)
        val dstUuid = getDestUUID(msg)
        if(getPath(srcUuid,dstUuid)?.node2?.isNotEmpty() == true){ // checking if i have path from src to dst
            Log.d(TAG,"Path Exists, No need for Broadcasting")
            _foundPath.postValue(true)
            return
        }
        val msgId = getMsgID(msg)
        // Initialize the set for this message ID if it doesn't exist
        val processedDevices = processedDevicesForMessages.getOrPut(msgId) { mutableSetOf() }
        if (!processedDevices.contains(msgId)) { //If message with msgID is not proccessed then continue
            Log.d(TAG, "Scanning")
            coroutineScope {
                 job = launch(Dispatchers.IO) {
                     try {
                         withTimeout(20000L) {
                             scanner?.devices?.collect { device ->
                                 Log.w(TAG, "Discovered: ${device.username} with ID: ${device.id}")
                                 //TODO{"Remove srcUUID in final version as this is testing only}
                                 if (srcUuid != appID.toString() && device.id == dstUuid) {  //found device send rrep
                                     processedDevices.add(device.id)
                                     scanner?.stopScan()
                                     connectUser(device.device) // make connection with requested user
                                     launch(Dispatchers.IO) {
                                         while (clientConnections[device.device.address]?.isReady != true) {// && serverConnections[device.device.address]?.isReady != true
                                             Log.w(
                                                 TAG,
                                                 "${device.username} State: ${clientConnections[device.device.address]?.state}"
                                             )
                                             delay(200)
                                         }
                                         updatePathOnRREP(
                                             srcUuid,
                                             dstUuid,
                                             device.device.address
                                         ) // saves the path for this dstID user
                                         getPath(srcUuid, dstUuid)?.node1?.let {
                                             Log.w(TAG, "Send RREP to $it")
                                             sendMessage(it, makeRREP(msg))
                                         }
                                     }
                                     this.cancel()
                                 } else {
                                     //TODO{"Remove first condition before "&&" in final version, as this is for testing only}
                                     if (!(srcUuid == appID.toString() && device.id == dstUuid) && srcUuid != device.id && !processedDevices.contains(
                                             device.id
                                         )
                                     ) {
                                         processedDevices.add(device.id) // Mark as processed for this message
                                         connectUser(device.device)
                                         launch(Dispatchers.IO) {
                                             while (clientConnections[device.device.address]?.isReady != true) {// && serverConnections[device.device.address]?.isReady != true
                                                 //Log.w(TAG,"State: ${clientConnections[device.device.address]?.state}")
                                                 delay(100)
                                             }
                                             Log.d(TAG, "Connected with proxy ${device.username}")
                                             delay(1500) // it takes around 2 seconds to make BLE connection
                                             sendMessage(device.device.address, msg) // send RREQ
                                         }
                                     }
                                 }
                             }
                         }
                     }catch (e:TimeoutException){
                         Log.d(TAG,"Timeout Occured.")
                     }finally {
                         scanner?.stopScan()
                     }
                }
                scanner?.searchDevices()
                delay(20000) // Keep scanning for a certain period (20 s)
                scanner?.stopScan()
                job!!.cancel() // Cancel the scanning job
            }
        }
    }


    fun sendAudio(device:String, bytes:ByteArray){

        val client = clientConnections[device]
        coroutineScope?.launch(Dispatchers.IO) {
            val success = client?.sendAudio(bytes)
            if (success == true) {
                Log.d(TAG,"Audio Sent")
            } else {
                Log.d(TAG, "Audio not Sent")
            }
        }

    }

    fun startServer(app:Application, coroutineScope: CoroutineScope){
        Log.d(TAG,"Server Starting")
        this.app = app
        this.coroutineScope = coroutineScope
        appID = UUIDManager.getStoredUUID(app.applicationContext)
        adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if(!adapter!!.isEnabled){
            Toast.makeText(app.applicationContext,"Bluetooth is Disabled. Cannot Start Server.",Toast.LENGTH_LONG).show()
            isServerStarted=false
            return
        }
        val sharedPreferences = app.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }
        val username = sharedPreferences?.getString("username","Null")
        adapter?.let {adapter->
            advertisementManager = username?.let { name->
                AdvertisingManager(appID!!,name, adapter ) }
        }
        database = ChatDatabase.getDatabase(app)
        chatDao = database!!.chatDao()
        serverManger = ServerManager(app.applicationContext)
        startServerManager()
        scanner = ScannerRepository(app.applicationContext, adapter!!,coroutineScope)
        isServerStarted=true
    }

    fun connectUser(device: BluetoothDevice) {
        if(isServerStarted) {
            if (clientConnections[device.address] == null || clientConnections[device.address]?.isConnected == false) {
                Log.w(TAG, "Using Client")
                val clientConnection =
                    coroutineScope?.let {
                        app?.let { it1 -> ClientConnection(it1.applicationContext, it, device) }
                    }
                if (clientConnection != null) {
                    coroutineScope?.launch(Dispatchers.IO) {
                        clientConnection.connect()
                        clientConnections[device.address] = clientConnection
                    }
                }
                coroutineScope?.launch(Dispatchers.IO) {
                    clientConnection?.stateAsFlow()?.collect { state ->
                        val updatedMap = _connectionStates.value.toMutableMap().apply {
                            this[device.address] = state
                        }
                        // Post the updated map to the StateFlow
                        _connectionStates.value = updatedMap
                    }
                }
            }
        }
    }


    fun sendMessage(device:String,msg:String) {
        val srcUUID = getSrcUUID(msg)
        val dstUUID = getDestUUID(msg)
        val msgID = getMsgID(msg)
        val content = msg.split("\n")[1]
        if((clientConnections[device]?.isConnected==true) ) {
             val client = clientConnections[device]
            coroutineScope?.launch(Dispatchers.IO) {
                val success = client?.sendMessage(msg)
                if (success == true) {
                    val m = Message(
                        id = msgID,
                        clientUuid = getDestUUID(msg),
                        content = content,
                        timestamp = System.currentTimeMillis(),
                        messageType = MessageType.SENT
                    )
                    if(srcUUID== appID.toString() && (getMsgType(msg)==0 || getMsgType(msg)==3) && !isKeyExchangeMessage(msg)){
                        Log.w(TAG,"Received: $msg")
                        if(isEncrypted(msg)){
                            m.content= keyStorage[dstUUID]!!.decryptDataWithAES(content)
                            insertMessageInDb(m)
                        }else{
                            insertMessageInDb(m)
                        }

                    }
                    _messages.emit(m)
                } else {
                    Toast.makeText(app?.applicationContext,"Could Not send the message",Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Message not Sent")
                }
            }
        }
    }



    private fun startServerManager(){

        coroutineScope?.launch(Dispatchers.IO) {
            try {
                advertisementManager?.startAdvertising()
                Log.d(TAG,"Advertisement started successfully")
            } catch (exception: Exception) {
                throw Exception("Could not start server.", exception)
            }
        }

        serverManger!!.setServerObserver(object: ServerObserver{
            override fun onServerReady() {
                Log.w(TAG, "Server is ready.")
            }

            override fun onDeviceConnectedToServer(device: BluetoothDevice) {
                Log.d(TAG, "Connected $device")
                app?.let {
                    coroutineScope?.let { scope ->
                        scope.launch(Dispatchers.IO) {
                            serverConnections[device.address] = ServerConnection(
                                it.applicationContext,
                                scope,
                                device
                            )
                                .apply {
                                    messages
                                        .onEach {
                                            //Log.d(TAG, "Message Received: serverManager")
                                        }
                                }
                                .apply {
                                    useServer(serverManger!!)
                                    Log.d(TAG, "Called userServer")
                                    val exceptionHandler =
                                        CoroutineExceptionHandler { _, throwable ->
                                            Log.e("ServerViewModel", "Error", throwable)
                                        }
                                    scope.launch(Dispatchers.IO + exceptionHandler) {
                                        connect()
                                        connectUser(device)
                                    }
                                }
                        }
                    }
                }
            }

            override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
                Log.d(TAG,"Device disconnected on onDeviceDisconnected")
                serverConnections[device.address]?.release()
                serverConnections.remove(device.address)
            }
        })
        serverManger!!.open()
    }

    fun disconnectAll(){
        paths.clear()
        clientConnections.values.forEach{
            it.release()
        }
        serverConnections.values.forEach{
            it.release()
        }
        clientConnections.clear()
        serverConnections.clear()
        job?.cancel()
    }

    fun stopServer(){
        serverManger?.close()
        advertisementManager?.stopAdvertising()
        disconnectAll()
    }

    fun disconnectFrom(deviceAddress: String) {
        clientConnections[deviceAddress]?.release()
    }

    private fun getMsgType(msg: String): Int {
        val header = msg.split("\n")
        return header[0].split(" ")[0].toInt()
    }

    private fun getSrcUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[1]
    }

    private fun getDestUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[2]
    }

    private fun getMsgID(msg: String): String {
        val header = msg.split("\n")
        return header[0].split(" ")[3]
    }

    private fun getName(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[4]
    }

    private fun isEncrypted(message:String): Boolean {
        val header = message.split("\n")
        return header[0].split(" ")[5].toInt()==1
    }
    private fun isKeyError(message:String): Boolean {
        val header = message.split("\n")
        return header[0].split(" ")[5].toInt()==2
    }

    private fun isKeyExchangeMessage(message: String):Boolean{
        val header = message.split("\n")
        return header[0].split(" ")[6].toInt() == 1
    }




    fun publishAudio(bytes: ByteArray) {
        Log.d(TAG,"Audio Received")
        app?.applicationContext?.let { playAudioFromByteArray(it,bytes) }
    }

    private val _audioObserver = MutableLiveData<Boolean>()
    val audioObserver = _audioObserver as LiveData<Boolean>

    private fun playAudioFromByteArray(context: Context, audioBytes: ByteArray) {
        _audioObserver.postValue(true)
        // Create a temporary file to hold the audio data
        val tempFile = File.createTempFile("audio", ".3gp", context.cacheDir).apply {
            deleteOnExit()
        }

        try {
            // Write the byte array to the file
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioBytes)
            }

            // Now play the audio from the file
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                start()

                // Release the MediaPlayer once playback is complete
                setOnCompletionListener {
                    it.release()
                    _audioObserver.postValue(false)
                }
            }
        } catch (e: IOException) {
            // Handle exceptions
            e.printStackTrace()
        }
    }

    private fun reportClientPublicKey(device:String, receivedMsg:String, dstID: String, connectionType: Int=0){
        if(keyStorage[dstID]==null){
            exchangeKeys(device, dstID,connectionType)
        }
        Log.w(TAG,"Key Received From ${getName(receivedMsg)}:\n\t${receivedMsg.split("\n")[1]}")
        keyStorage[dstID]?.deriveSharedSecret(Base64.getDecoder().decode(receivedMsg.split("\n")[1]))
    }

    val keyStorage = mutableMapOf<String, CryptoManager?>()

    fun exchangeKeys(device:String, dstUuid:String, connectionType:Int=0) {
        if(keyStorage[dstUuid]==null || keyStorage[dstUuid]?.isInitialized() == false) {
            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(app!!.applicationContext)
            val username = sharedPreferences.getString("username", "Not set")
            val header = "$connectionType $appID $dstUuid ${UUID.randomUUID()} $username 0 1"
            val ecdh = CryptoManager()
            val publicKey = ecdh.getPublicKey()
            keyStorage[dstUuid] = ecdh
            Log.w(TAG, "Key Sent From $username:\n\t${Base64.getEncoder().encodeToString(publicKey)}")
            if (connectionType == 0) {
                sendMessage(device, "$header\n${Base64.getEncoder().encodeToString(publicKey)}")
            } else {
                forwardMsg("$header\n${Base64.getEncoder().encodeToString(publicKey)}", device)
            }
        }
    }

    private val receivedSosRequests = mutableListOf<String>()
    fun reportSOS(sosMsg: String) {
        val sosUuid = getSrcUUID(sosMsg)
        if(!receivedSosRequests.contains(sosUuid)){
            _sosRequest.postValue(sosMsg)
            receivedSosRequests.add(sosUuid)
        }
    }

    suspend fun broadcastSOS(msg:String) {
        receivedSosRequests.add(getSrcUUID(msg))
        job?.cancel()
        // Initialize the set for this message ID if it doesn't exist
        val processedDevices = mutableListOf<String>()
        Log.d(TAG, "Scanning")
        coroutineScope {
            job = launch(Dispatchers.IO) {
                try {
                    withTimeout(20000L) {
                        scanner?.devices?.collect { device ->
                            Log.w(
                                TAG,
                                "Discovered: ${device.username} with ID: ${device.id} : ${
                                    processedDevices.contains(device.id)
                                }"
                            )
                            if (!processedDevices.contains(device.id)) {
                                processedDevices.add(device.id) // Mark as processed for this message
                                connectUser(device.device)
                                launch(Dispatchers.IO) {
                                    while (clientConnections[device.device.address]?.isReady != true) {
                                        //Log.w(TAG,"State: ${clientConnections[device.device.address]?.state}")
                                        delay(100)
                                    }
                                    Log.d(TAG, "Connected with proxy ${device.username}")
                                    delay(1500)
                                    clientConnections[device.device.address]?.sendSOS(msg)
                                }
                            }
                        }
                    }
                }catch (e: TimeoutException){
                    Log.d(TAG, "Timeout occurred")
                }finally {
                    scanner?.stopScan()
                }
            }
            scanner?.searchDevices()
            delay(20000) // Keep scanning for a certain period (20 s)
            scanner?.stopScan()
            job!!.cancel() // Cancel the scanning job
            //disconnectAll() //after sos sending is finished then disconnect from all to save BLE stack memory
        }
    }
}