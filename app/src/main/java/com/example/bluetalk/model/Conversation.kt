package com.example.bluetalk.model

data class Conversation(
    val id: String,
    val participant: User, // List of users participating in the conversation
    val lastMessage: Message // The last message sent in the conversation
    // Add additional fields as necessary
)
