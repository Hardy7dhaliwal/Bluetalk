package com.example.bluetalk.bluetooth.server_repository

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import com.example.bluetalk.spec.MESSAGE_UUID
import com.example.bluetalk.spec.PROXY_MESSAGE_UUID
import com.example.bluetalk.spec.RREP_UUID
import com.example.bluetalk.spec.RREQ_UUID
import com.example.bluetalk.spec.SERVICE_UUID
import no.nordicsemi.android.ble.BleServerManager

class ServerManager(
    context: Context,
): BleServerManager(context) {
    private val TAG = ServerManager::class.java.simpleName


    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    override fun getMinLogPriority(): Int {
        return Log.VERBOSE
    }//


    override fun initializeServer(): List<BluetoothGattService> {
        return listOf(
            service(
                SERVICE_UUID,
                characteristic(
                    MESSAGE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                    cccd(),
                    description("A sample client server interaction.", false)
                )
//                characteristic(
//                    RREQ_UUID,
//                    BluetoothGattCharacteristic.PROPERTY_WRITE,
//                    BluetoothGattCharacteristic.PERMISSION_WRITE
//                ),
//                characteristic(
//                    PROXY_MESSAGE_UUID,
//                    BluetoothGattCharacteristic.PROPERTY_WRITE,
//                    BluetoothGattCharacteristic.PERMISSION_WRITE
//                ),
//                characteristic(
//                    RREP_UUID,
//                     BluetoothGattCharacteristic.PROPERTY_WRITE ,
//                    BluetoothGattCharacteristic.PERMISSION_WRITE,
//                )
            )
        )
    }

}