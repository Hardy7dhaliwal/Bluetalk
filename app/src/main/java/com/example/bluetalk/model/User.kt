package com.example.bluetalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class User(
    @PrimaryKey val uuid: String,
    var username: String,
    var address: String
)
