package com.example.bluetalk.model

import java.text.SimpleDateFormat
import java.util.*

data class Conversation(
    val id: Long,
    val address: String,
    val uuid: String,
    val username: String,
    val content: String,
    val timestamp: Long,
    // Add other fields from the Message and User entities as needed
){
    fun getTime() : String{
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return timeFormat.format(date)
    }
}
