package com.example.bluetalk.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class User(
    @PrimaryKey val uuid: String,
    var username: String,
    var address: String,
    var key: String=""
)
