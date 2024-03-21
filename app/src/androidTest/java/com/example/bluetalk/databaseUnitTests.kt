package com.example.bluetalk

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.MessageType
import com.example.bluetalk.model.User
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: ChatDatabase
    private lateinit var chatDao: ChatDao


    /**
     * Gets the value of a LiveData safely.
     */
    private fun <T> getOrAwaitValue(liveData: LiveData<T>, time: Long = 4, timeUnit: TimeUnit = TimeUnit.SECONDS): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(value: T) {
                data = value
                latch.countDown()
                liveData.removeObserver(this)
            }
        }
        liveData.observeForever(observer)
        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(time, timeUnit)) {
            liveData.removeObserver(observer)
            throw TimeoutException("LiveData value was never set.")
        }
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, ChatDatabase::class.java).build()
        chatDao = db.chatDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun userExists() = runBlocking {
        val user = User(uuid="123456",username="Harry",address="00:00:00:00")
        chatDao.insertUser(user)
        assertTrue(chatDao.userExists("123456"))
    }

    @Test
    fun insertAndGetUserByUUID() = runBlocking {
        val user = User(uuid="123456",username="Harry",address="00:00:00:00")
        chatDao.insertUser(user)
        val u = chatDao.getUserByUUID("123456")
        assertTrue(u == user)
    }
    @Test
    fun insertAndGetUserByAddress() = runBlocking {
        val user = User(uuid="123456",username="Harry",address="00:00:00:00")
        chatDao.insertUser(user)
        val u = chatDao.getUserByAddress("00:00:00:00")
        assertTrue(u == user)
    }

    @Test
    fun getAllUsers()= runBlocking {
        val user = User(uuid = "1", username = "User1", address = "Address-1")
        chatDao.insertUser(user)

        val user2 = User(uuid = "2", username = "User1", address = "Address-2")
        chatDao.insertUser(user2)
        // Since LiveData observation must happen on the main thread, and to ensure it captures the inserted messages, use runOnMainSync for observation.
        val users = mutableListOf<User>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            chatDao.getAllUsers().observeForever { observedUsers ->
                if (observedUsers.isNotEmpty()) {
                    users.addAll(observedUsers)
                    latch.countDown()
                }
            }
        }

        // Wait for LiveData to emit, but ensure this does not run on the main thread to prevent deadlock.
        latch.await(2, TimeUnit.SECONDS)
        // Now we can assert based on the observed messages
        assertThat(users.size, `is`(2))
        assertThat(users[0].uuid, `is`("1"))
        assertThat(users[1].uuid, `is`("2"))
    }

    @Test
    fun duplicateUsers()= runBlocking {
        val user = User(uuid = "testUuid", username = "Test User", address = "Test Address")
        val user2 = User(uuid = "testUuid", username = "Test User", address = "Test Address")
        chatDao.insertUser(user)
        chatDao.insertUser(user2)
        val users = mutableListOf<User>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            chatDao.getAllUsers().observeForever { observedUsers ->
                if (observedUsers.isNotEmpty()) {
                    users.addAll(observedUsers)
                    latch.countDown()
                }
            }
        }

        // Wait for LiveData to emit, but ensure this does not run on the main thread to prevent deadlock.
        latch.await(2, TimeUnit.SECONDS)
        // Now we can assert based on the observed messages
        assertThat(users.size, `is`(1))
    }

    @Test
    fun insertAndGetMessagesForUser() = runBlocking {
        val user = User(uuid = "testUuid", username = "Test User", address = "Test Address")
        chatDao.insertUser(user)

        val message1 = Message(id = "1", clientUuid = user.uuid, content = "Hello", timestamp = System.currentTimeMillis(), messageType = MessageType.RECEIVED)
        val message2 = Message(id="2",clientUuid = user.uuid, content = "World", timestamp = System.currentTimeMillis() + 1, messageType = MessageType.RECEIVED)

        // Insert messages
        chatDao.insertMessage(message1)
        chatDao.insertMessage(message2)

        // Since LiveData observation must happen on the main thread, and to ensure it captures the inserted messages, use runOnMainSync for observation.
        val messages = mutableListOf<Message>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            chatDao.getMessagesForUser(user.uuid).observeForever { observedMessages ->
                if (observedMessages.isNotEmpty()) {
                    messages.addAll(observedMessages)
                    latch.countDown()
                }
            }
        }

        // Wait for LiveData to emit, but ensure this does not run on the main thread to prevent deadlock.
        latch.await(2, TimeUnit.SECONDS)
        // Now we can assert based on the observed messages
        assertThat(messages.size, `is`(2))
        assertThat(messages[0].content, `is`("Hello"))
        assertThat(messages[1].content, `is`("World"))
    }

    @Test
    fun updateUsername() = runBlocking {
        val user = User(uuid = "testUuid", username = "Old Name", address = "Test Address")
        chatDao.insertUser(user)

        chatDao.updateUserName(user.uuid, "New Name")
        val loaded = chatDao.getUserByUUID(user.uuid)

        assertThat(loaded?.username, `is`("New Name"))
    }

    @Test
    fun updateUserAddress() = runBlocking {
        val user = User(uuid = "testUuid", username = "Old Name", address = "Test Address")
        chatDao.insertUser(user)

        chatDao.updateAddress(user.uuid, "New Address")
        val loaded = chatDao.getUserByUUID(user.uuid)

        assertThat(loaded?.address, `is`("New Address"))
    }

    @Test
    fun updateUserFields() = runBlocking {
        val user = User(uuid = "testUuid", username = "Old Name", address = "Test Address")
        chatDao.insertUser(user)

        chatDao.updateSpecificFields(user.uuid, "New Name","New Address")
        val loaded = chatDao.getUserByUUID(user.uuid)

        assertThat(loaded?.address, `is`("New Address"))
        assertThat(loaded?.username, `is`("New Name"))
    }

    @Test
    fun getConversations_returnsLatestMessages() = runBlocking {
        // Step 1: Insert users
        val user1 = User(uuid = "uuid1", username = "User 1", address = "Address 1")
        val user2 = User(uuid = "uuid2", username = "User 2", address = "Address 2")
        chatDao.insertUser(user1)
        chatDao.insertUser(user2)

        // Step 2: Insert messages for each user
        // User 1 messages
        chatDao.insertMessage(Message(id="1",clientUuid = user1.uuid, content = "Hello", timestamp = System.currentTimeMillis() - 1000, messageType = MessageType.SENT))
        chatDao.insertMessage(Message(id="2",clientUuid = user1.uuid, content = "How are you?", timestamp = System.currentTimeMillis(), messageType = MessageType.SENT))
        // User 2 messages
        chatDao.insertMessage(Message(id="3",clientUuid = user2.uuid, content = "Hi", timestamp = System.currentTimeMillis() - 2000, messageType = MessageType.SENT))
        chatDao.insertMessage(Message(id="4",clientUuid = user2.uuid, content = "Goodbye", timestamp = System.currentTimeMillis() - 1000, messageType = MessageType.SENT))

        // Step 3: Observe the LiveData and assert
        val conversations = mutableListOf<Conversation>()
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            chatDao.getConversations().observeForever { observedconversations ->
                if (observedconversations.isNotEmpty()) {
                    conversations.addAll(observedconversations)
                    latch.countDown()
                }
            }
        }
        // Wait for LiveData to emit, but ensure this does not run on the main thread to prevent deadlock.
        latch.await(2, TimeUnit.SECONDS)
        // Assertions
        assertEquals(2, conversations.size) // We expect the latest message per user
        // Assert the expected latest message content for each user
        assertTrue(conversations.any { it.content == "How are you?" && it.uuid == user1.uuid })
        assertTrue(conversations.any { it.content == "Goodbye" && it.uuid == user2.uuid })
        // Verify order by timestamp descending
        assertTrue(conversations[0].timestamp >= conversations[1].timestamp)
    }

}