package com.example.bluetalk.bluetooth

import ECDHCryptoManager
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
import com.example.bluetalk.model.ProxyPacket
import com.example.bluetalk.model.UUIDManager
import com.example.bluetalk.model.User
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import no.nordicsemi.android.ble.ktx.stateAsFlow
import no.nordicsemi.android.ble.observer.ServerObserver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID


data class Path(val node1: String, val node2: String)

@SuppressLint("MissingPermission","LogNotTimber","StaticFieldLeak")
object BluetalkServer {
    private val TAG = BluetalkServer::class.java.simpleName
    private var app:Application?=null
    private var appID: UUID?=null
    private var coroutineScope:CoroutineScope?=null
    private var adapter:BluetoothAdapter?=null
    private var advertisementManager: AdvertisingManager?=null
    private var serverManger: ServerManager?=null
    //for chat messages
    private val _messages = MutableSharedFlow<Message>()
    val messages = _messages.asSharedFlow()
    //for connection state
    private var _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val processingBroadcastMsg = mutableListOf<String>()
    //Database
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    var clientConnections = mutableMapOf<String,ClientConnection>()
    var serverConnections = mutableMapOf<String, ServerConnection>()
    private var clients = mutableMapOf<String, ServerConnection>()
    var scanner:ScannerRepository?=null
    private var _foundPath = MutableLiveData<Boolean>()
    val foundPath = _foundPath as LiveData<Boolean>

    // Initialize the paths storage
// The key is a Pair<String, String> representing the SrcID and DstID, and the value is the Path
    private val paths = mutableMapOf<Pair<String, String>, Path>()

    // function to add a path upon receiving an RREQ request
    private fun addOrUpdatePathOnRREQ(srcID: String, dstID: String, node1Address: String) {
        val key = srcID to dstID
        val existingPath = paths[key]

        // If there's already a path, update it; otherwise, create a new one
        if (existingPath != null) {
            Log.d(TAG,"Same RREQ received so Ignore it")
            //paths[key] = Path(node1Address, existingPath.node2)
        } else {
            paths[key] = Path(node1Address, "")
        }
    }

    // function to update a path upon receiving an RREP response
    fun updatePathOnRREP(srcID: String, dstID: String, node2Address: String) {
        val key = srcID to dstID
        val existingPath = paths[key]

        // Only update if the path already exists
        if (existingPath != null) {
            paths[key] = Path(existingPath.node1, node2Address)
        }
       Log.d(TAG,"RREP received but did not receive RREQ. Shouldn't happen")
    }

    // Function to retrieve a path
    fun getPath(srcID: String, dstID: String): Path? {
        return paths[srcID to dstID]
    }

    private fun makeRREP(msg:String):String{
        return "2 ${getSrcUUID(msg)} ${getDestUUID(msg)} ${getMsgID(msg)} ${getName(msg)}\n"
    }


    suspend fun storeReceivedMsg(msg: String, address: String){

        insertUser(User(uuid = getSrcUUID(msg), username = getName(msg), address = address))
        val type = getMsgType(msg)
        if(type == 0){ //regular one to one message
            insertMsg(msg)
        }else if(type==1){ //path find request
            coroutineScope?.launch(Dispatchers.IO) {
                Log.w(TAG,"Received RREQ")
                addOrUpdatePathOnRREQ(getSrcUUID(msg), getDestUUID(msg),address)
                broadcastMessage(address,msg)
            }
        }else if(type==2){//path found reply
            Log.w(TAG,"Received RREP")
            updatePathOnRREP(getSrcUUID(msg), getDestUUID(msg),address)
            if(getSrcUUID(msg)== appID.toString()){
                _foundPath.postValue(true)
            }else {
                coroutineScope?.launch(Dispatchers.IO) {
                    forwardMsg(msg, address)
                }
            }
        }else{//type 3 //for forwarding msg
            Log.w(TAG,"Received Forwarding")
            if(getDestUUID(msg) == appID.toString()){
                insertMsg(msg)
            }else{
                forwardMsg(msg,address)
            }
        }

        if(getDestUUID(msg) == appID.toString()) {
            val uuid = getSrcUUID(msg)
            val content = msg.substring(msg.indexOf('\n')+1)
            val m= Message(
                id = getMsgID(msg),
                content = content,
                timestamp = System.currentTimeMillis(),
                messageType = MessageType.RECEIVED,
                clientUuid = uuid
            )
            _messages.emit(m)
            insertMessageInDb(m)
        }
        Log.d(TAG, "Message: ${msg}")
    }

    suspend fun insertUser(user:User){
        if (chatDao?.userExists(user.uuid) == true) {
            chatDao?.updateSpecificFields(user.uuid, user.username, user.address)
        } else {
            chatDao?.insertUser(user)
        }
    }

    private fun forwardMsg(msg: String, address: String) {
        val forwardingTo: String = run {
            val path = getPath(getSrcUUID(msg), getDestUUID(msg))
            when (address) {
                path?.node1 -> path.node2 // If current node is node1, forward to node2
                path?.node2 -> path.node1 // If current node is node2, should it forward to node1 or elsewhere? Adjust according to your logic.
                else -> "" // If current node is neither node1 nor node2, forwarding address is undefined
            }
        }
        if(forwardingTo==""){
            Log.d(TAG,"No path found")
            return
        }
        sendMessage(forwardingTo,msg)
    }

    private fun insertMsg(msg:String){
        val uuid = getSrcUUID(msg)
        val content = msg.substring(msg.indexOf('\n')+1)
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
    private val proccessedDst = mutableListOf<String>()
    suspend fun broadcastMessage(device:String, msg: String) {
        val srcUuid = getSrcUUID(msg)
        val dstUuid = getDestUUID(msg)
        if(getPath(srcUuid,dstUuid)?.node2?.isNotEmpty() == true){ // checking if i have path from src to dst
            Log.d(TAG,"Path Exists, No need for Broadcasting")
            forwardMsg(msg,device)
            return
        }

        val msgId = getMsgID(msg)

        // Initialize the set for this message ID if it doesn't exist
        val processedDevices = processedDevicesForMessages.getOrPut(msgId) { mutableSetOf() }

        if (!processedDevices.contains(msgId)) {

            Log.d(TAG, "Scanning")
            coroutineScope {
                val job = launch(Dispatchers.IO) {
                    scanner?.devices?.collect { device ->
                        Log.w(TAG, "Discovered: ${device.username} with ID: ${device.id}")
                        //TODO{"Remove srcUUID in final version as this is testing only}
                        if(srcUuid != appID.toString() && device.id == dstUuid){  //found device send rrep
                            processedDevices.add(device.id)
                            scanner?.stopScan()
                            updatePathOnRREP(srcUuid,dstUuid,device.device.address)
                            getPath(srcUuid,dstUuid)?.node1?.let {
                                Log.w(TAG,"Send RREP to $it")
                                sendMessage(it, makeRREP(msg))
                            }
                           this.cancel()
                        }else {
                            //TODO{"Remove first condition before && in final version, as this is for testing only}
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
                                    delay(1500)
                                    sendMessage(device.device.address, msg)
                                }
                            }
                        }
                        //delay(300) // Delay between processing devices
                    }
                }
                scanner?.searchDevices()
                delay(30000) // Keep scanning for a certain period (30 s)
                scanner?.stopScan()
                job.cancel() // Cancel the scanning job
            }
        }
    }



    fun sendAudio(device:String, bytes:ByteArray){
        if (clientConnections[device] == null) {
            val client = serverConnections[device]
            coroutineScope?.launch(Dispatchers.IO) {
                val success = client?.sendAudio(bytes)
                if (success == true) {
                    Log.d(TAG,"Audio Sent")
                } else {
                    Log.d(TAG, "Audio not Sent")
                }
            }
        } else {
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
    }

    fun startServer(app:Application, coroutineScope: CoroutineScope){
        Log.d(TAG,"Server Starting")
        this.app = app
        this.coroutineScope = coroutineScope
        appID = UUIDManager.getStoredUUID(app.applicationContext)
        adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if(!adapter!!.isEnabled){
            Toast.makeText(app.applicationContext,"Bluetooth is Disabled. Cannot Start Server.",Toast.LENGTH_LONG).show()
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
    }

    fun connectUser(device: BluetoothDevice) {
        if(clientConnections[device.address]==null ) {  //&& serverConnections[device.address]==null
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
        }//else{
//            Log.w(TAG, "Using Server")
//            coroutineScope?.launch(Dispatchers.IO) {
//                serverConnections[device.address]?.stateAsFlow()?.collect(){
//                    val updatedMap = _connectionStates.value.toMutableMap().apply {
//                        this[device.address] = it
//                    }
//                    // Post the updated map to the StateFlow
//                    _connectionStates.value = updatedMap
//                }
//            }
//        }
    }


    fun sendMessage(device:String,msg:String) {
        val srcUUID = getSrcUUID(msg)
        val dstUUID = getDestUUID(msg)
        val msgID = getMsgID(msg)
        if((clientConnections[device]?.isConnected==true) ) { //or (serverConnections[device]?.isConnected==true)
            if (clientConnections[device] == null) {
                val client = serverConnections[device]
                coroutineScope?.launch(Dispatchers.IO) {
                    val success = client?.sendMessage(msg)
                    if (success == true) {
                        val m = Message(
                            id = msgID,
                            clientUuid = dstUUID,
                            content = msg.split("\n")[1],
                            timestamp = System.currentTimeMillis(),
                            messageType = MessageType.SENT
                        )
                       if(srcUUID== appID.toString()){ insertMessageInDb(m)}
                        _messages.emit(m)
                    } else {
                        Log.d(TAG, "Message not Sent")
                    }
                }
            } else {
                val client = clientConnections[device]
                coroutineScope?.launch(Dispatchers.IO) {
                    val success = client?.sendMessage(msg)
                    if (success == true) {
                        val m = Message(
                            id = msgID,
                            clientUuid = getDestUUID(msg),
                            content = msg.split("\n")[1],
                            timestamp = System.currentTimeMillis(),
                            messageType = MessageType.SENT
                        )
                        if(srcUUID== appID.toString() && (getMsgType(msg)==0) || getMsgType(msg)==3){ insertMessageInDb(m)}
                        _messages.emit(m)
                    } else {
                        Toast.makeText(app?.applicationContext,"Could Not send the message",Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Message not Sent")
                    }
                }
            }
        }//else{
//            coroutineScope?.launch(Dispatchers.IO) {
//                broadcastMessage(device,"1 $msg")
//                paths[srcUUID to dstUUID] = Path(appID.toString(),"")
//            }
//        }
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
                                            Log.d(TAG, "Message Received: serverManager")
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
        clientConnections.values.forEach{
            it.release()
        }
        serverConnections.values.forEach{
            it.release()
        }
        clientConnections.clear()
        serverConnections.clear()
    }

    fun stopServer(){
        serverManger?.close()
        advertisementManager?.stopAdvertising()
        disconnectAll()
    }

    fun disconnectFrom(deviceAddress: String) {
        clientConnections[deviceAddress]?.release()
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

    private fun getMsgType(msg: String): Int {
        val header = msg.split("\n")
        return header[0].split(" ")[0].toInt()
    }

    private fun getName(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[4]
    }

    fun publishAudio(bytes: ByteArray) {
        Log.d(TAG,"Audio Received")
        app?.applicationContext?.let { playAudioFromByteArray(it,bytes) }
    }

    private fun playAudioFromByteArray(context: Context, audioBytes: ByteArray) {
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
                }
            }
        } catch (e: IOException) {
            // Handle exceptions
            e.printStackTrace()
        }
    }

    suspend fun postClientPublicKey(key:ByteArray, device:String){
        keyStorage[device]?.deriveSharedSecret(key)

    }

    private val keyStorage = mutableMapOf<String,ECDHCryptoManager>()
    fun exchangeKeys(device:String, uuid:String="") {
        val ecdh = ECDHCryptoManager()
        val publicKey = ecdh.getPublicKey()
        keyStorage[device] = ecdh
        sendKey(device,publicKey)
    }

    private fun sendKey(device:String, keyInBytes: ByteArray) {
        val client = clientConnections[device]
        coroutineScope?.launch(Dispatchers.IO) {
            val success = client?.sendKey(keyInBytes)
            if (success == true) {
                Log.d(TAG,"KEY Sent")
            } else {
                Log.d(TAG, "KEY not Sent")
            }
        }
    }
}