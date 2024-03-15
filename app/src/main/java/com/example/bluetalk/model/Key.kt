package com.example.bluetalk.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
data class Key(
    @PrimaryKey(autoGenerate = true) val id:Long,
    val clientUuid:String,
    val key: String
)