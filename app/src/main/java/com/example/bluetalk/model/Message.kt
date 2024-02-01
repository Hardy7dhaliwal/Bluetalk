package com.example.bluetalk.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(foreignKeys = [ForeignKey(entity = User::class,
                            parentColumns = arrayOf("userId"),
                            childColumns = arrayOf("userId"),
                            onDelete = ForeignKey.CASCADE)])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String, // the connected user
    val content: String,
    val timestamp: Long,
    val messageType : MessageType
) {

    fun getTime() : String{
        val date = Date(timestamp)
        val timeFormat = SimpleDateFormat("MMMM d, yyyy HH:mm", Locale.getDefault())
        return timeFormat.format(date)
    }
}

enum class MessageType {
    SENT,
    RECEIVED
}