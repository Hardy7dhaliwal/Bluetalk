package com.example.bluetalk.state


import android.bluetooth.BluetoothDevice

sealed class UserConnectionState {
    class Connected(val device: BluetoothDevice) : UserConnectionState()
    object Disconnected : UserConnectionState()
}
