package com.example.bluetalk.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.User

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM message WHERE clientUuid = :uuid ORDER BY timestamp ASC")
    fun getMessagesForUser(uuid: String): LiveData<List<Message>>

    @Query("SELECT * FROM user")
    fun getAllUsers(): LiveData<List<User>>

    @Query("SELECT * FROM User WHERE address = :address")
    suspend fun getUserByAddress(address: String): User?

    @Query("SELECT * FROM User WHERE uuid = :uuid")
    suspend fun getUserByUUID(uuid: String): User?

    @Query("SELECT EXISTS(SELECT * FROM User WHERE uuid = :uuid)")
    suspend fun userExists(uuid: String): Boolean

    @Query("UPDATE User SET username = :name WHERE uuid = :uuid")
    suspend fun updateUserName(uuid: String, name: String)

    @Query("UPDATE User SET address = :address WHERE uuid = :uuid")
    suspend fun updateAddress(uuid: String, address: String)

    @Query(
        """
        SELECT message.id AS id, message.clientUuid AS uuid,User.uuid AS uuid, User.address AS address, message.content AS content, message.timestamp AS timestamp, User.username as username
        FROM message
        INNER JOIN User ON message.clientUuid = User.uuid
        WHERE message.id IN (
            SELECT MAX(message.id) FROM message GROUP BY message.clientUuid
        )
        ORDER BY message.timestamp DESC
    """
    )
    fun getConversations(): LiveData<List<Conversation>>

}
