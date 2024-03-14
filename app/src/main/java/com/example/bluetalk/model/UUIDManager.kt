package com.example.bluetalk.model

import android.content.Context
import java.nio.ByteBuffer
import java.util.*


object UUIDManager {
    private const val PREFS_NAME = "UUIDPrefs"
    private const val UUID_KEY = "AppUUID"

    // Method to retrieve the stored UUID, or generate a new one if necessary
    fun getStoredUUID(context: Context): UUID {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uuidString = prefs.getString(UUID_KEY, null)
        return if (uuidString == null) {
            // UUID not stored, generate a new one
            val newUUID = UUID.randomUUID()
            prefs.edit().putString(UUID_KEY, newUUID.toString()).apply()
            newUUID
        } else {
            // Return the stored UUID
            UUID.fromString(uuidString)
        }
    }

    fun uuidToBytes(uuid: UUID): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16))
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        return bb.array()
    }

    fun bytesToUUID(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes)
        val mostSigBits = bb.long
        val leastSigBits = bb.long
        return UUID(mostSigBits, leastSigBits)
    }
}
