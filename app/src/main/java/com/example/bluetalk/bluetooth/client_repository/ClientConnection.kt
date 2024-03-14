package com.example.bluetalk.bluetooth.client_repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.example.bluetalk.Packet.Payload
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.model.MessageResponse
import com.example.bluetalk.model.ProxyPacket
import com.example.bluetalk.model.User
import com.example.bluetalk.model.serializePacket
import com.example.bluetalk.spec.MESSAGE_UUID
import com.example.bluetalk.spec.PacketMerger
import com.example.bluetalk.spec.PacketSplitter
import com.example.bluetalk.spec.SERVICE_UUID
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.asResponseFlow
import no.nordicsemi.android.ble.ktx.suspend

@SuppressLint("LogNotTimber")
class ClientConnection(
    context: Context,
    private val scope: CoroutineScope,
    private val device: BluetoothDevice,
    private val chatDao: ChatDao
):BleManager(context) {


    private val TAG = ClientConnection::class.java.simpleName
    private var messageCharacteristic: BluetoothGattCharacteristic?=null
    private var rreqCharacteristic: BluetoothGattCharacteristic?=null
    private var rrepCharacteristic:BluetoothGattCharacteristic?=null
    private var proxyCharacteristic:BluetoothGattCharacteristic?=null
    private val _rrep_packet = MutableSharedFlow<ProxyPacket>()
    val rrep_packet = _rrep_packet.asSharedFlow()

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun getMinLogPriority(): Int {
        return Log.VERBOSE
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        gatt.getService(SERVICE_UUID)?.run {
            messageCharacteristic = getCharacteristic(MESSAGE_UUID)

        }
        return messageCharacteristic!=null
    }


//    override fun onServicesInvalidated() {
//        messageCharacteristic = null
////        rreqCharacteristic = null
////        rrepCharacteristic = null
////        proxyCharacteristic = null
//    }

    private fun getSrcUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[0]
    }

    private fun getName(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[3]
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun initialize() {
        createBondInsecure().enqueue()
        requestMtu(512).enqueue()
        setNotificationCallback(messageCharacteristic)
            // Merges packets until the entire text is present in the stream [PacketMerger.merge].
            .merge(PacketMerger())
            .asResponseFlow<MessageResponse>()
            .onEach {
                it.message?.let { msg ->
                    Log.d(TAG, "Received Message: $msg")
                    chatDao.insertUser(User(uuid = getSrcUUID(msg), username = getName(msg), address = device.address))
                    BluetalkServer.storeReceivedMsg(msg)
                }
                it.audioBytes?.let {bytes->
                    Log.d(TAG,"Audio Received")
                    BluetalkServer.publishAudio(bytes)
                }
            }
            .launchIn(scope)
        enableNotifications(messageCharacteristic).enqueue()
    }



    suspend fun connect(){
        try {
            connect(device)
                .retry(4, 500)
                .useAutoConnect(false)
                .timeout(0)
                .suspend()
        }catch (e:Exception){
            Log.d(TAG,"Exception: $e")
        }
    }

    suspend fun sendMessage(message: String):Boolean{
        Log.d(TAG,"Message Request Received")
        if(!isConnected) return false

        val payload = Payload.newBuilder()
            .setTextMessage(message)
            .build()
        val messageBytes = payload.toByteArray()
        return try {
            writeCharacteristic(
                messageCharacteristic,
                messageBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).split(PacketSplitter())
                .before{Log.d(TAG,"device: Connected: $isConnected and Ready:$isReady")}
                .enqueue()
            true
        }catch (e:Exception){
            print("WriteCLient, Exception: $e")
            false
        }
    }

    fun sendAudio(bytes: ByteArray):Boolean{
        Log.d(TAG,"Audio Request Received")
        if(!isConnected) return false

        val payload = Payload.newBuilder()
            .setAudioData(ByteString.copyFrom(bytes))
            .build()

        val messageBytes = payload.toByteArray()
        return try {
            writeCharacteristic(
                messageCharacteristic,
                messageBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).split(PacketSplitter())
                .before{Log.d(TAG,"device: Connected: $isConnected and Ready:$isReady")}
                .enqueue()
            true
        }catch (e:Exception){
            print("WriteCLient Audio, Exception: $e")
            false
        }
    }

    suspend fun sendProxyMessage(message: String):Boolean{
        Log.d(TAG,"Proxy Message Request Received")
        if(!isConnected) return false
        val messageBytes = message.toByteArray()
        return try {
            writeCharacteristic(
                proxyCharacteristic,
                messageBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).split(PacketSplitter())
                .enqueue()
            true
        }catch (e:Exception){
            print("Write-CLient, Exception: $e")
            false
        }
    }

    suspend fun sendRREQ(packet: ProxyPacket):Boolean{
        if(!isConnected) return false
        val proxyBytes = serializePacket(packet)
        return try {
            writeCharacteristic(
                rreqCharacteristic,
                proxyBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).split(PacketSplitter())
                .enqueue()
            true
        }catch (e:Exception){
            false
        }
    }

    fun release(){
        cancelQueue()
        disconnect().enqueue()
    }

    suspend fun sendRREP(proxyPacket: ProxyPacket): Boolean {
        if(!isConnected) return false
        val proxyBytes = serializePacket(proxyPacket)
        return try {
            writeCharacteristic(
                rrepCharacteristic,
                proxyBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).split(PacketSplitter())
                .enqueue()
            true
        }catch (e:Exception){
            false
        }
    }
}