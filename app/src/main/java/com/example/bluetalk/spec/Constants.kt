package com.example.bluetalk.spec

import java.util.*


val SERVICE_UUID: UUID = UUID.fromString("7db3e111-0000-1000-8000-00805f9b34fb")

/**
 * UUID for the message
 */
val MESSAGE_UUID: UUID = UUID.fromString("7db3e112-0000-1000-8000-00805f9b34fb")
val RREP_UUID: UUID = UUID.fromString("7db3e113-0000-1000-8000-00805f9b34fb")
val RREQ_UUID: UUID = UUID.fromString("7db3e114-0000-1000-8000-00805f9b34fb")

/**
 * UUID to confirm device connection
 */
val PROXY_MESSAGE_UUID: UUID = UUID.fromString("7db3e115-0000-1000-8000-00805f9b34fb")


val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

const val REQUEST_ENABLE_BT = 1