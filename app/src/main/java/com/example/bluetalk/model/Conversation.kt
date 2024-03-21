package com.example.bluetalk.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
Conversation model uses User and Message to show Each user's last message and timestamp on ConversationListFragment
 */
data class Conversation(
    val id: Long,
    val address: String, //mac address of the user
    val uuid: String,  //uuid of the user
    val username: String,
    val content: String, // last message content
    val timestamp: Long, //last message timestamp
    // Add other fields from the Message and User entities as needed
){
    fun getTime() : String{
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return timeFormat.format(date)
    }
}
