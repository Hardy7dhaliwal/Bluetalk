package com.example.bluetalk.bluetooth.client_repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.example.bluetalk.Packet.Payload
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.model.MessageResponse
import com.example.bluetalk.spec.MESSAGE_UUID
import com.example.bluetalk.spec.PacketMerger
import com.example.bluetalk.spec.PacketSplitter
import com.example.bluetalk.spec.SERVICE_UUID
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.asResponseFlow
import no.nordicsemi.android.ble.ktx.suspend

@SuppressLint("LogNotTimber")
class ClientConnection(
    context: Context,
    private val scope: CoroutineScope,
    private val device: BluetoothDevice
):BleManager(context) {


    private val TAG = ClientConnection::class.java.simpleName
    private var messageCharacteristic: BluetoothGattCharacteristic?=null

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


    override fun onServicesInvalidated() {
        messageCharacteristic = null
    }

    private fun getSrcUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[1]
    }

    private fun getName(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[4]
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
                    BluetalkServer.processReceivedMsg(msg, device.address)
                }
                it.audioBytes?.let {bytes->
                    Log.d(TAG,"Audio Received")
                    BluetalkServer.publishAudio(bytes)
                }
            }
            .launchIn(scope)
        //enableNotifications(messageCharacteristic).enqueue()
    }





    suspend fun connect(){
        try {
            connect(device)
                .retry(4, 500)
                .useAutoConnect(false)
                .timeout(0)
                .suspend()
        }catch (e:RequestFailedException){
            Log.w(TAG,"RequestFailedException: $e")
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

    fun release(){
        cancelQueue()
        disconnect().enqueue()
    }

    fun sendSOS(message: String):Boolean{
        Log.d(TAG,"SOS Request Received")
        if(!isConnected) return false
        val payload = Payload.newBuilder()
            .setSos(message)
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

}