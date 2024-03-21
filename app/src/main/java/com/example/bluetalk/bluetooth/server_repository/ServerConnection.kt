package com.example.bluetalk.bluetooth.server_repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.asResponseFlow

@SuppressLint("LogNotTimber")
class ServerConnection(
    context: Context,
    private val scope: CoroutineScope,
    private val device: BluetoothDevice
):BleManager(context) {

    private val TAG = ServerConnection::class.java.simpleName
    private var messageCharacteristic: BluetoothGattCharacteristic?=null

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()


    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun getMinLogPriority(): Int {
        return Log.VERBOSE
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
       return true
    }

    override fun onServerReady(server: BluetoothGattServer) {
        server.getService(SERVICE_UUID)?.run{
            messageCharacteristic = getCharacteristic(MESSAGE_UUID)
        }
    }

    private fun getSrcUUID(message:String):String{
        val header = message.split("\n")
        return header[0].split(" ")[1]
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    override fun initialize() {
        requestMtu(512).enqueue()
        setWriteCallback(messageCharacteristic)
            .merge(PacketMerger())
            .asResponseFlow<MessageResponse>()
            .onEach {
                it.message?.let{msg->
                    Log.d(TAG, "Received Message: $msg")
                    _messages.emit(msg)
                    BluetalkServer.processReceivedMsg(msg, device.address)
                }
                it.audioBytes?.let {audio->
                    Log.d(TAG,"Audio Received")
                    BluetalkServer.publishAudio(audio)
                }
                it.sosMsg?.let { sosMsg->
                    Log.d(TAG, "SOS Received")
                    BluetalkServer.reportSOS(sosMsg)
                }
            }
            .launchIn(scope)
    }

    override fun onServicesInvalidated() {
        messageCharacteristic = null
    }

    override fun shouldClearCacheWhenDisconnected(): Boolean {
        return false
    }

    suspend fun connect(time:Int = 4) {
        try {
            connect(device)
                .retry(time, 300)
                .useAutoConnect(false)
                .timeout(0)
                .enqueue()
        }catch (e:Exception){
            Log.d(TAG,"Exception: $e")
        }
    }

    suspend fun sendMessage(message: String):Boolean{
        Log.d(TAG,"Message Request Received")
        val payload = Payload.newBuilder()
            .setTextMessage(message)
            .build()

        val messageBytes = payload.toByteArray()
        return try {
            sendNotification(messageCharacteristic, messageBytes)
                .split(PacketSplitter())
                .before{
                    Log.d(TAG,"Connected: $isConnected and Ready: $isReady")
                }
                .enqueue()
            true
        }catch (e:Exception){
            print("Notification: $e")
            false
        }
    }

    suspend fun sendAudio(bytes: ByteArray):Boolean{
        Log.d(TAG,"Audio Request Received")
        if(!isConnected) return false

        val payload = Payload.newBuilder()
            .setAudioData(ByteString.copyFrom(bytes))
            .build()

        val messageBytes = payload.toByteArray()

        return try {
            sendNotification(messageCharacteristic, messageBytes)
                .split(PacketSplitter())
                .enqueue()
            true
        }catch (e:Exception){
            print("Notification: $e")
            false
        }
    }

    fun release(){
        cancelQueue()
        disconnect().enqueue()
    }
}