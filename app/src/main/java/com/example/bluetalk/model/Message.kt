package com.example.bluetalk.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = arrayOf("uuid"),
            childColumns = arrayOf("clientUuid"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clientUuid"])
    ]
)
data class Message(
    @PrimaryKey val id: String, // UUID as a string
    val clientUuid: String, // Links to User's UUID
    var content: String, // The message content
    val timestamp: Long, // When the message was sent or received
    val messageType: MessageType // Enum for the type of message
)
{

    fun getTime(): String {
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("MMMM d, yyyy HH:mm", Locale.getDefault())
        return timeFormat.format(date)
    }
}
enum class MessageType {
    SENT,
    RECEIVED
}