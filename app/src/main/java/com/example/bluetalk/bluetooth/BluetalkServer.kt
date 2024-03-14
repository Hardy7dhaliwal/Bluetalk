package com.example.bluetalk.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.util.UUID

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
    private var _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Initializing)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val processingBroadcastMsg = mutableListOf<String>()
    //Database
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    var clientConnections = mutableMapOf<String,ClientConnection>()
    var serverConnections = mutableMapOf<String, ServerConnection>()
    private var clients = mutableMapOf<String, ServerConnection>()
    var scanner:ScannerRepository?=null

    private var _rreqRequest = MutableLiveData<ProxyPacket>()
    var rreqRequest = _rreqRequest as LiveData<ProxyPacket>
    private var proccessedProxyMsgs = mutableMapOf<String,String>()
    private var path  = mutableMapOf<String, ProxyPacket>()
    private val _pathFound = MutableLiveData<Boolean>()
    val pathFound = _pathFound as LiveData<Boolean>


    private fun getName(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[3]
    }
    suspend fun storeReceivedMsg(msg:String){
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


        }else{
            coroutineScope?.launch(Dispatchers.IO) {
                broadcastMessage(msg)
            }
        }

        Log.d(TAG, "Message: ${msg}")
        //insertMessageInDb(message)
    }


    private fun insertMessageInDb(message: Message) {
        coroutineScope?.launch(Dispatchers.IO) {
            chatDao?.insertMessage(message)
        }
    }

//      suspend fun broadcastMessage(msg:String){
//
//         if(!processingBroadcastMsg.contains(getMsgID(msg))) {
//             processingBroadcastMsg.add(getMsgID(msg))
//             val proccessedProxyDevices = mutableListOf<String>()
//             val srcUUid = getSrcUUID(msg)
//             val dstUUID = getDestUUID(msg)
//             Log.d(TAG, "Scanning")
//             coroutineScope?.launch(Dispatchers.IO) {
//                 val job = launch(Dispatchers.IO) {
//                     scanner?.devices?.collect { device ->
//                         Log.d(TAG, "Discovered: ${device.username} with ID :${device.id}")
//                         Log.d(TAG, "Processing: ${device.device.address}  ${device.username}")
//                         if (!(srcUUid == appID.toString() && device.id == dstUUID)) {
//                             if (srcUUid != device.id) { //ignore the src device
//                                 proccessedProxyDevices.add(device.id)
//                                 if (clientConnections[device.device.address]?.isConnected != true && serverConnections[device.device.address]?.isConnected!=true) {
//                                     val isConnected = connect_(device.device)
//                                     Log.w(TAG,"Is Connected Now. SHould send reply")
//                                     if (isConnected) {
//                                        // Log.w(TAG,"Is Connected Now. SHould send reply")
//                                         delay(1000)
//                                         sendMessage(device.device.address, msg)
//                                         if (device.id == dstUUID) {
//                                             scanner?.stopScan()
//                                         }
//                                     }
//                                 } else {
//                                     sendMessage(device.device.address, msg)
//                                 }
//                             }
//                         }
//                         delay(1000)
//                     }
//                 }
//                 scanner?.searchDevices()
//                 delay((15000))
//                 scanner?.stopScan()
//                 job.cancel()
//             }
//         }
//    }

    // Define a map to track processed devices for each message
    private val processedDevicesForMessages = mutableMapOf<String, MutableSet<String>>()
    private val proccessedDst = mutableListOf<String>()
    suspend fun broadcastMessage(msg: String) {
        val msgId = getMsgID(msg)

        // Initialize the set for this message ID if it doesn't exist
        val processedDevices = processedDevicesForMessages.getOrPut(msgId) { mutableSetOf() }

        if (!processedDevices.contains(msgId)) {
            val srcUuid = getSrcUUID(msg)
            val dstUuid = getDestUUID(msg)
            Log.d(TAG, "Scanning")
            processedDevices.add(dstUuid)
            coroutineScope {
                val job = launch(Dispatchers.IO) {
                    scanner?.devices?.collect { device ->
                        Log.w(TAG, "Discovered: ${device.username} with ID: ${device.id}")
                        if (!(srcUuid == appID.toString() && device.id == dstUuid) && srcUuid != device.id && !processedDevices.contains(device.id)) {
                            processedDevices.add(device.id) // Mark as processed for this message
                            connectUser(device.device)
                            while(clientConnections[device.device.address]?.isReady != true && serverConnections[device.device.address]?.isReady != true ){
                                //Log.w(TAG,"State: ${clientConnections[device.device.address]?.state}")
                            }
                            Log.d(TAG,"Connected with proxy ${device.username}")
                            delay(1500)
                            sendMessage(device.device.address, msg)
                            if (device.id == dstUuid) {
                                scanner?.stopScan()
                            }
                        }
                        delay(300) // Delay between processing devices
                    }
                }
                scanner?.searchDevices()
                delay(15000) // Keep scanning for a certain period
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
//        coroutineScope.launch {
//            val job = launch(Dispatchers.IO) {
//                scanner?.devices?.collect { device ->
//                    Log.w(TAG, "Discovered: ${device.username} with ID: ${device.id}")
//                    connectUser(device.device)
//                    delay(1000) // Delay between processing devices
//                }
//            }
//            scanner?.searchDevices()
//            delay(50000) // Keep scanning for a certain period
//            scanner?.stopScan()
//            job.cancel() // Cancel the scanning job
//        }
    }

    fun connectUser(device: BluetoothDevice) {
        if(clientConnections[device.address]==null && serverConnections[device.address]==null) {
            Log.w(TAG, "Using Client")
            val clientConnection =
                coroutineScope?.let {
                    app?.let { it1 -> ClientConnection(it1.applicationContext, it, device, chatDao!!) }
                }
            if (clientConnection != null) {
                coroutineScope?.launch(Dispatchers.IO) {
                    clientConnection.connect()
                    clientConnections[device.address] = clientConnection
                }
            }
            coroutineScope?.launch(Dispatchers.IO) {
                clientConnection?.stateAsFlow()?.collect { state ->
                    _connectionState.emit(state) // Emit state updates to the shared flow
                }
            }
        }else{
            Log.w(TAG, "Using Server")
            coroutineScope?.launch(Dispatchers.IO) {
                serverConnections[device.address]?.stateAsFlow()?.collect(){
                    _connectionState.emit(it)
                }
            }
        }
    }



    fun sendMessage(device:String,msg:String) {
        val srcUUID = getSrcUUID(msg)
        val dstUUID = getDestUUID(msg)
        val msgID = getMsgID(msg)
        if((clientConnections[device]?.isConnected==true) or (serverConnections[device]?.isConnected==true)) {
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
                        if(srcUUID== appID.toString()){ insertMessageInDb(m)}
                        _messages.emit(m)
                    } else {
                        Log.d(TAG, "Message not Sent")
                    }
                }
            }
        }else{
            coroutineScope?.launch(Dispatchers.IO) {
                broadcastMessage(msg)
            }
        }
    }

    private fun getMsgID(msg: String): String {
        val header = msg.split("\n")
        return header[0].split(" ")[2]
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
                                device,
                                chatDao!!
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
        clientConnections.values.forEach{
            it.release()
        }
        serverConnections.values.forEach{
            it.release()
        }
    }

    fun disconnectFrom(deviceAddress: String) {
        clientConnections[deviceAddress]?.release()

    }


    private fun getSrcUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[0]
    }

    private fun getDestUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[1]
    }

    fun publishAudio(bytes: ByteArray) {

    }

}