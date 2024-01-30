package com.example.bluetalk.model

import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val id: UUID,
    val content: String,
    val timestamp: Date,
    val messageType : MessageType
) {

    fun getTime() : String{
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return timeFormat.format(timestamp)
    }
}

enum class MessageType {
    SENT,
    RECEIVED
}