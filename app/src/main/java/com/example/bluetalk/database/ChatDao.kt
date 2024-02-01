package com.example.bluetalk.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.User

@Dao
interface ChatDao {
    @Insert
    fun insertUser(user: User)

    @Insert
    fun insertMessage(message: Message)

    @Query("SELECT * FROM message WHERE userId = :userId ORDER BY timestamp DESC")
    fun getMessagesForUser(userId: String): LiveData<List<Message>>

    @Query("SELECT * FROM user")
    fun getAllUsers(): LiveData<List<User>>
}
