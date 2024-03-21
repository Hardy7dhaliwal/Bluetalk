package com.example.bluetalk.model

import android.bluetooth.BluetoothDevice


data class DeviceInfo(
    val device: BluetoothDevice,
    val username: String,
    val id: String
)