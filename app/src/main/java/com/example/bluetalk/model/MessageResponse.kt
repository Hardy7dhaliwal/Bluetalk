package com.example.bluetalk.model

import android.bluetooth.BluetoothDevice
import com.example.bluetalk.Packet.Payload
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.response.ReadResponse

class MessageResponse:ReadResponse() {
    var message:String?=null
    var audioBytes:ByteArray?=null
    var key: ByteArray?=null
    override fun onDataReceived(device: BluetoothDevice, data: Data) {
        val bytes = data.value!!
        val payload = Payload.parseFrom(bytes)
        if(payload.hasTextMessage()){message = payload.textMessage}
        if(payload.hasAudioData()) {audioBytes = payload.audioData.toByteArray()}
        if(payload.hasKey()) {key = payload.key.toByteArray()}
    }
}