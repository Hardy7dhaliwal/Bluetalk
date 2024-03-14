package com.example.bluetalk.model

import android.bluetooth.BluetoothDevice
import java.util.UUID


data class DeviceInfo(
    val device: BluetoothDevice,
    val username: String,
    val id: String
)
//
//RREQ_UUID -> {
//    ChatServer.gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
//    val path = value?.toString(Charsets.UTF_8)
//    Log.d(TAG, "onCharacteristicWriteRequest: Have path: \"$path\"")
//    path?.let {
//        val correctedPath = ChatServer.addSrc(it, d)
//        val destDevice = ChatServer.getDestFromPath(correctedPath)
//        val srcDevice = ChatServer.getSrcFromPath(correctedPath)
//        val hop = ChatServer.getHopFromPath(correctedPath)
//        if (ChatServer.proxyRequests[srcDevice] == null) {
//            // Insert the new key-value pair into the map
//            ChatServer.proxyRequests[srcDevice] = destDevice
//            if(hop.toInt() > 0) {
//                ChatServer._proxyState.postValue(ProxyState.StartRREQ(correctedPath + (d.address) + " "))
//            }else{
//                //send ERROR hop limit reached
//            }
//        } else {
//            // Ignore the request as it is a duplicate request
//            return
//        }
//    }
//}
//RREP_UUID -> {
//    ChatServer.gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
//    val path = value?.toString(Charsets.UTF_8)
//    Log.d(TAG, "onCharacteristicWriteRequest: Have path: \"$path\"")
//    path?.let {
//        var correctedPath = it
//        if(!it.contains(d.address)){
//            correctedPath += d.address
//        }
//        val destDevice = ChatServer.getDestFromPath(correctedPath)
//        if(ChatServer.proxyRequest.contains(destDevice)){
//            ChatServer.proxyPath[ChatServer.proxyRequest] =
//                ChatServer.getNodesFromPath(correctedPath)
//        }else {
//            val srcDevice = ChatServer.getSrcFromPath(correctedPath)
//            if (ChatServer.proxyRequests.isNotEmpty()) {
//                if (ChatServer.proxyRequests[srcDevice] == null) {
//                    ChatServer.proxyRequests[srcDevice] = destDevice
//                    ChatServer._proxyState.postValue(ProxyState.StartRREP(path))
//                } else {
//                    return
//                }//ignore the request as it is duplicate request
//            }
//        }
//    }
//}