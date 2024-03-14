package com.example.bluetalk.bluetooth//package com.example.bluetalk.bluetooth
//
//import android.annotation.SuppressLint
//import android.app.Application
//import android.bluetooth.*
//import android.bluetooth.le.AdvertiseCallback
//import android.bluetooth.le.AdvertiseData
//import android.bluetooth.le.AdvertiseSettings
//import android.bluetooth.le.BluetoothLeAdvertiser
//import android.content.Context
//import android.os.Build
//import android.os.ParcelUuid
//import android.util.Log
//import androidx.annotation.RequiresApi
//import androidx.lifecycle.*
//import androidx.preference.PreferenceManager
//import com.example.bluetalk.database.ChatDao
//import com.example.bluetalk.database.ChatDatabase
//import com.example.bluetalk.model.*
//import com.example.bluetalk.spec.MESSAGE_UUID
//import com.example.bluetalk.spec.RREP_UUID
//import com.example.bluetalk.spec.RREQ_UUID
//import com.example.bluetalk.spec.SERVICE_UUID
//
//import com.example.bluetalk.state.ProxyState
//import com.example.bluetalk.state.UserConnectionState
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import java.util.*
//
//
//private const val TAG = "ChatServer"
//
//interface BluetoothConnectionCallback {
//    fun onConnectionSuccess(device: BluetoothDevice, uuid: String)
//    fun onConnectionFailure()
//}
//
//
//object ChatServer {
//
//    lateinit var appUUID: UUID
//
//    // hold reference to app context to run the chat server
//    private var app: Application? = null
//    private lateinit var bluetoothManager: BluetoothManager
//    private lateinit var adapter: BluetoothAdapter
//
//    private lateinit var coroutineScope: LifecycleCoroutineScope
//    // This property will be null if bluetooth is not enabled or if advertising is not
//    // possible on the device
//    private var advertiser: BluetoothLeAdvertiser? = null
//    private var advertiseCallback: AdvertiseCallback? = null
//    private var advertiseSettings: AdvertiseSettings = buildAdvertiseSettings()
//    private lateinit var advertiseData: AdvertiseData
//
//    // LiveData for reporting the messages sent to the device
//    private val _messages = MutableLiveData<Message?>()
//    val messages = _messages as LiveData<Message?>
//
//
//    //Database
//    private var database: ChatDatabase? = null
//    private var chatDao: ChatDao? = null
//
//    // LiveData for reporting connection requests
//    private val _connectionRequest = MutableLiveData<BluetoothDevice>()
//    val connectionRequest = _connectionRequest as LiveData<BluetoothDevice>
//
//    // LiveData for reporting the messages sent to the device
//    private val _requestEnableBluetooth = MutableLiveData<Boolean>()
//    val requestEnableBluetooth = _requestEnableBluetooth as LiveData<Boolean>
//
//    private var gattServer: BluetoothGattServer? = null
//    private var gattServerCallback: BluetoothGattServerCallback? = null
//
//    private var gattClient: BluetoothGatt? = null
//    private var gattClientCallback: BluetoothGattCallback? = null
//
//    // Properties for current chat device connection
//    private var currentDevice: BluetoothDevice? = null
//    private val _deviceConnection = MutableLiveData<Map<String,UserConnectionState>>()
//    val deviceConnection = _deviceConnection as LiveData<Map<String,UserConnectionState>>
//    private var gatt: BluetoothGatt? = null
//    private var messageCharacteristic: BluetoothGattCharacteristic? = null
//    private var rreqCharacteristic: BluetoothGattCharacteristic? = null
//    private var rrepCharacteristic: BluetoothGattCharacteristic? = null
//    private const val GATT_MAX_MTU_SIZE = 517
//
//
//    private const val MAX_RECONNECTION_ATTEMPTS = 5 // Maximum number of reconnection attempts
//
//
//    private var reconnectionAttempts = 0 // Current number of reconnection attempts
//
//    private val _proxyRequestsLiveData = MutableLiveData<MutableMap<String, String>>()
//    val proxyRequestsLiveData: LiveData<MutableMap<String, String>> = _proxyRequestsLiveData
//    private var _proxyState = MutableLiveData<ProxyState>()
//    var proxyState = _proxyState as LiveData<ProxyState>
//
//    var isProcessingRREQ = false
//    private val connectionCallbacks: MutableList<BluetoothConnectionCallback> = mutableListOf()
//    private var _rreqRequests = MutableLiveData<ProxyPacket?>()
//    val rreqRequests = _rreqRequests as LiveData<ProxyPacket?>
//    var _rrepRequests = MutableLiveData<ProxyPacket?>()
//    val rrepRequests = _rrepRequests as LiveData<ProxyPacket?>
//    var gattMap: MutableMap<String, BluetoothGatt> = mutableMapOf()
//    private var serviceMap = mutableMapOf<String, BluetoothGattService>()
//    var connectedDevices = mutableMapOf<String, BluetoothDevice>()
//
//    fun registerConnectionCallback(callback: BluetoothConnectionCallback) {
//        if (!connectionCallbacks.contains(callback)) {
//            connectionCallbacks.add(callback)
//        }
//    }
//
//    fun unregisterConnectionCallback(callback: BluetoothConnectionCallback) {
//        connectionCallbacks.remove(callback)
//    }
//
//    private fun notifyConnectionSuccess(device: BluetoothDevice, uuid: String) {
//        connectionCallbacks.forEach { it.onConnectionSuccess(device, uuid) }
//    }
//
//    private fun notifyConnectionFailure() {
//        connectionCallbacks.forEach { it.onConnectionFailure() }
//    }
//
//    @SuppressLint("MissingPermission")
//    fun startServer(app: Application, coroutineScope: LifecycleCoroutineScope) {
//        Log.d(TAG, "Starting Server")
//        this.app = app
//        this.coroutineScope = coroutineScope
//        bluetoothManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//        adapter= bluetoothManager.adapter
//        advertiseData = buildAdvertiseData()
//        if (!adapter.isEnabled) {
//            // prompt the user to enable bluetooth
//            _requestEnableBluetooth.value = true
//        } else {
//            _requestEnableBluetooth.value = false
//            setupGattServer(app)
//            startAdvertisement()
//        }
//        appUUID = UUIDManager.getStoredUUID(app.applicationContext)
//        database = ChatDatabase.getDatabase(app)
//        chatDao= database!!.chatDao()
//    }
//
//    @SuppressLint("MissingPermission")
//    fun stopServer() {
//        gattServer?.close()
//        gattServerCallback=null
//        stopAdvertising()
//
//    }
//
//    fun setCurrentChatConnection(device: BluetoothDevice) {
//        currentDevice = device
//
//        updateDeviceConnectionState(device, UserConnectionState.Connected(device))
//        connectToChatDevice(device)
//    }
//
//    fun updateDeviceConnectionState(device: BluetoothDevice, state: UserConnectionState) {
//        // Use a temporary map to prepare the updates
//        val updatedConnections = _deviceConnection.value?.toMutableMap() ?: mutableMapOf()
//
//        // Update the connection state for the specific device
//        updatedConnections[device.address] = state
//
//        // Post the updated map back to _deviceConnection in a thread-safe manner
//        _deviceConnection.postValue(updatedConnections)
//    }
//
//
//
//    @SuppressLint("MissingPermission")
//    private fun connectToChatDevice(device: BluetoothDevice) {
//        gattClientCallback = GattClientCallback()
//
//        if (device.bondState == BluetoothDevice.BOND_NONE) {
//            if (!device.createBond()) {
//                throw  RuntimeException("Bonding Failed");
//            }
//        }
//
//        gattClient = device.connectGatt(app, false, gattClientCallback,BluetoothDevice.TRANSPORT_LE)
//        Log.d(TAG,"connectToChatDevice: Gattclient: $gattClient")
//    }
//
//
//    @SuppressLint("MissingPermission")
//    fun sendMessage(appUUID: UUID, message: String, device: BluetoothDevice): Boolean {
//        Log.d(TAG, "Sending a message")
//        val messageBytes = "$appUUID\n$message".toByteArray(Charsets.UTF_8)
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
//            val success = gatt?.writeCharacteristic(messageCharacteristic!!,messageBytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
//            if(success == BluetoothStatusCodes.SUCCESS) {
//                Log.d(TAG, "sendMessage: Sent Code: $success")
//                val msg = Message(
//                    content = message,
//                    timestamp = System.currentTimeMillis(),
//                    messageType = MessageType.SENT,
//                    clientUuid = device.address
//                )
//                _messages.value = msg
//                insertMessageInDb(msg)
//                return true
//            }else{Log.d(TAG,"Error Sending with code: $success")}
//        }else {
//            messageCharacteristic?.let { characteristic ->
//                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
//                characteristic.value = messageBytes
//                val success = gatt?.writeCharacteristic(messageCharacteristic)
//                Log.d(TAG, "sendMessage: message send: $success")
//                if (success == true) {
//                    val msg = Message(
//                        content = message,
//                        timestamp = System.currentTimeMillis(),
//                        messageType = MessageType.SENT,
//                        clientUuid = device.address
//                    )
//                    _messages.value = msg
//                    insertMessageInDb(msg)
//                    return true
//                }else{Log.d(TAG,"Error Sending with code: $success")}
//            }.run{Log.d(TAG,"No Gatt To Send Message")}
//        }
//        return false
//    }
//
//    private fun insertMessageInDb(message: Message){
//        coroutineScope.launch(Dispatchers.IO) {
//            chatDao?.insertMessage(message)
//        }
//    }
//
//    /**
//     * Function to setup a local GATT server.
//     * This requires setting up the available services and characteristics that other devices
//     * can read and modify.
//     */
//    @SuppressLint("MissingPermission")
//    private fun setupGattServer(app: Application) {
//        gattServerCallback = GattServerCallback()
//
//        gattServer = bluetoothManager.openGattServer(
//            app,
//            gattServerCallback
//        ).apply {
//            addService(setupGattService())
//        }
//    }
//
//    /**
//     * Function to create the GATT Server with the required characteristics and descriptors
//     */
//    private fun setupGattService(): BluetoothGattService {
//        // Setup gatt service
//        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
//        // need to ensure that the property is writable and has the write permission
//        val messageCharacteristic = BluetoothGattCharacteristic(
//            MESSAGE_UUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )
//        service.addCharacteristic(messageCharacteristic)
//        val rreqCharacteristic = BluetoothGattCharacteristic(
//            RREQ_UUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )
//        service.addCharacteristic(rreqCharacteristic)
//        val rrepCharacteristic = BluetoothGattCharacteristic(
//            RREP_UUID,
//            BluetoothGattCharacteristic.PROPERTY_WRITE,
//            BluetoothGattCharacteristic.PERMISSION_WRITE
//        )
//        service.addCharacteristic(rrepCharacteristic)
//
//        val uuid = BluetoothGattCharacteristic(
//            app?.let { UUIDManager.getStoredUUID(it.applicationContext) },
//            BluetoothGattCharacteristic.PROPERTY_READ,
//            BluetoothGattCharacteristic.PERMISSION_READ
//        )
//        service.addCharacteristic(uuid)
//
//        return service
//    }
//
//    /**
//     * Start advertising this device so other BLE devices can see it and connect
//     */
//    @SuppressLint("MissingPermission")
//    private fun startAdvertisement() {
//        advertiser = adapter.bluetoothLeAdvertiser
//        Log.d(TAG, "startAdvertisement: with advertiser $advertiser")
//
//        if (advertiseCallback == null) {
//            advertiseCallback = DeviceAdvertiseCallback()
//
//            advertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
//        }
//    }
//
//    /**
//     * Stops BLE Advertising.
//     */
//    @SuppressLint("MissingPermission")
//    private fun stopAdvertising() {
//        Log.d(TAG, "Stopping Advertising with advertiser $advertiser")
//        advertiser?.stopAdvertising(advertiseCallback)
//        advertiseCallback = null
//    }
//
//    /**
//     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
//     */
//    @SuppressLint("MissingPermission")
//    private fun buildAdvertiseData(): AdvertiseData {
//        val sharedPreferences = app?.let { PreferenceManager.getDefaultSharedPreferences(it.applicationContext) }
//        val username = sharedPreferences?.getString("username", adapter.name)
//        val dataBuilder = AdvertiseData.Builder()
//            .addServiceUuid(ParcelUuid(SERVICE_UUID))
//            .addServiceData(ParcelUuid(SERVICE_UUID),
//                username?.toByteArray(Charsets.UTF_8) ?:
//                adapter.name.toByteArray(Charsets.UTF_8))
//            .setIncludeDeviceName(false)
//        return dataBuilder.build()
//    }
//
//    /**
//     * Returns an AdvertiseSettings object set to use low power (to help preserve battery life)
//     * and disable the built-in timeout since this code uses its own timeout runnable.
//     */
//    private fun buildAdvertiseSettings(): AdvertiseSettings {
//        return AdvertiseSettings.Builder()
//            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
//            .setTimeout(0)
//            .build()
//    }
//
//    /**
//     * Custom callback for the Gatt Server this device implements
//     */
//    private class GattServerCallback : BluetoothGattServerCallback() {
//        @SuppressLint("MissingPermission")
//        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
//            super.onConnectionStateChange(device, status, newState)
//            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
//            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
//            Log.d(
//                TAG,
//                "onConnectionStateChange: Server $device ${device.name} success: $isSuccess connected: $isConnected"
//            )
//            if (isSuccess && isConnected) {
//                connectedDevices[device.address] = device
//                _connectionRequest.postValue(device)
//            }else {
//                updateDeviceConnectionState(device,UserConnectionState.Disconnected)
//            }
//        }
//
//        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//        @SuppressLint("MissingPermission")
//        override fun onCharacteristicWriteRequest(
//            device: BluetoothDevice,
//            requestId: Int,
//            characteristic: BluetoothGattCharacteristic,
//            preparedWrite: Boolean,
//            responseNeeded: Boolean,
//            offset: Int,
//            value: ByteArray?
//        ) {
//            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
//            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0,null)
//            val d = adapter.getRemoteDevice(device.address)
//            when (characteristic.uuid) {
//                MESSAGE_UUID -> {
//                    print("Received Message write request")
//                    val message = value?.toString(Charsets.UTF_8)
//                    Log.d(TAG, "onCharacteristicWriteRequest: Have message: \"$message\"")
//                    message?.let { it->
//                        Log.d(TAG,"Getting $device ${device.name} and $d ${d.name}")
//                        coroutineScope.launch(Dispatchers.IO) {
//                            if (chatDao?.userExists(d.address) == false){
//                                val user= User(d.address,d.name?:"Unknown", getUUID(it))
//                                chatDao!!.insertUser(user)
//                            }
//                            val content = it.substring(it.indexOf('\n')+1)
//                            val msg= Message(
//                                content = content,
//                                timestamp = System.currentTimeMillis(),
//                                messageType = MessageType.RECEIVED,
//                                clientUuid = d.address
//                            )
//                            _messages.postValue(msg)
//                            chatDao?.insertMessage(msg)
//                        }
//                    }
//                }
//                RREQ_UUID ->{
//                    if(!isProcessingRREQ ){
//                        Log.d(TAG,"SERVER: Received RREQ Request")
//                        isProcessingRREQ = true
//                        if(value!=null){
////                            val packet = deserializePacket(value)
////                            if (packet!=null) {
////                                if (packet.src != appUUID.toString()) {
////                                    _rreqRequests.postValue(packet)
////                                }
////                            }
//                        }
//                    }else{
//                        Log.d(TAG,"SERVER: Received RREQ Request while processing another.")
//                    }
//                }
//
//                RREP_UUID ->{
//                    Log.d(TAG, "Received RREP: $device")
////                    val packet = value?.let { deserializePacket(it) }
////                    if (packet != null) {
////                        if(packet.src == appUUID.toString()){
////                            Log.d(TAG, "Path Found from A to C")
////                        }
////                    }
////                    _rrepRequests.postValue(packet)
//                }
//
//            }
//        }
//    }
//
//    private fun getUUID(message:String):String{
//        val msgParts = message.split("\n")
//        return msgParts[0]
//    }
//
//
//    private class GattClientCallback : BluetoothGattCallback() {
//        @SuppressLint("MissingPermission")
//        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
//            super.onConnectionStateChange(gatt, status, newState)
//            val isSuccess = status == BluetoothGatt.GATT_SUCCESS
//            val isConnected = newState == BluetoothProfile.STATE_CONNECTED
//            Log.d(TAG, "onConnectionStateChange: Client $gatt  success: $isSuccess connected: $isConnected")
//            if(isSuccess) {
//                if ( isConnected) {
//                    gatt.discoverServices()
//                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
//                    updateDeviceConnectionState(gatt.device, UserConnectionState.Disconnected)
//                    gatt.close()
//                }
//            }else{
//                updateDeviceConnectionState(gatt.device, UserConnectionState.Disconnected)
//                gatt.close()
//                notifyConnectionFailure()
//            }
//        }
//
//
//        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
//        @SuppressLint("MissingPermission")
//        override fun onServicesDiscovered(discoveredGatt: BluetoothGatt, status: Int) {
//            super.onServicesDiscovered(discoveredGatt, status)
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.d(TAG, "onServicesDiscovered: Have gatt $discoveredGatt")
//                gattMap[discoveredGatt.device.address] = discoveredGatt
//                val service = discoveredGatt.getService(SERVICE_UUID)
//                if (service != null) {
//                    gattMap[discoveredGatt.device.address]?.requestMtu(517)
//                    serviceMap[discoveredGatt.device.address]= service
//                    val uuid = getUUID(service)
//                    gattMap[discoveredGatt.device.address]?.let { notifyConnectionSuccess(it.device,uuid) }
//                    Log.d(TAG, "UUID Discovered: $uuid")
//                }
//            }
//        }
//
//        override fun onCharacteristicWrite(
//            gatt: BluetoothGatt?,
//            characteristic: BluetoothGattCharacteristic?,
//            status: Int
//        ) {
//            super.onCharacteristicWrite(gatt, characteristic, status)
//            Log.d(TAG, "Write successfull")
//        }
//
//        override fun onCharacteristicChanged(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic,
//            value: ByteArray
//        ) {
//            super.onCharacteristicChanged(gatt, characteristic, value)
//            Log.d(TAG, "Received RREP: ${gatt.device}")
////            val packet = deserializePacket(value)
////            if (packet != null) {
////                if(packet.src == appUUID.toString()){
////                    Log.d(TAG, "Path Found from A to C")
////                }
////            }
////            _rrepRequests.postValue(packet)
//        }
//
//        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
//            Log.d(TAG,"ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
//        }
//    }
//
//
//    private fun getUUID(service: BluetoothGattService): String{
//       val characteristics = service.characteristics
//        characteristics.forEach{
//            if(!(it.uuid == MESSAGE_UUID || it.uuid == RREQ_UUID || it.uuid == RREP_UUID) ){
//                return it.uuid.toString()
//            }
//        }
//        return ""
//    }
//
//
//    /**
//     * Custom callback after Advertising succeeds or fails to start. Broadcasts the error code
//     * in an Intent to be picked up by AdvertiserFragment and stops this Service.
//     */
//    private class DeviceAdvertiseCallback : AdvertiseCallback() {
//        override fun onStartFailure(errorCode: Int) {
//            super.onStartFailure(errorCode)
//            // Send error state to display
//            val errorMessage = "Advertise failed with error: $errorCode"
//            Log.d(TAG, errorMessage)
//            //_viewState.value = DeviceScanViewState.Error(errorMessage)
//        }
//
//        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
//            super.onStartSuccess(settingsInEffect)
//            Log.d(TAG, "Advertising successfully started")
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    fun disconnect(device: BluetoothDevice? = null){
//        gattClient=null
//        gattClientCallback=null
//        gatt?.disconnect()
//    }
//
//    private fun getPreviousDeviceUUID(packet: ProxyPacket): String? {
//        val deviceList = packet.imDevices
//        val currentIndex = deviceList.indexOf(ChatServer.appUUID.toString())
//        return if (currentIndex > 0) {
//            // Return the UUID of the previous device
//            deviceList[currentIndex - 1]
//        } else {
//            deviceList[deviceList.size-1]
//        }
//    }
//
////    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
////    @SuppressLint("MissingPermission")
////    fun sendRRep(packet: ProxyPacket) {
////        // Retrieve the connected device
////        connectionRequest.value?.let { device ->
////            // Retrieve the service and characteristic
////            val service = gattServer?.getService(SERVICE_UUID)
////            val characteristic = service?.getCharacteristic(RREP_UUID)
////            // Check if the characteristic is retrieved successfully
////            if (characteristic != null) {
////                // Serialize the packet into bytes and set it as the characteristic's value
////                val packetBytes = serializePacket(packet)
////                // Notify the connected device that this characteristic has changed
////                val success = gattServer?.notifyCharacteristicChanged(device, characteristic, false, packetBytes)
////                Log.d(TAG,"SendRRP: Send data with code: $success")
////            } else {
////                Log.e(TAG, "RREP characteristic or service not found")
////            }
////        }
////    }
//
//}
