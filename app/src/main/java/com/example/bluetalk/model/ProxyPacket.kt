package com.example.bluetalk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class ProxyPacket(
    val src: String,
    var dst: String,
    val imDevices: MutableList<String>
)
// Serialize
fun serializePacket(packet: ProxyPacket): ByteArray? {
    return try {
        val jsonString = Json.encodeToString(ProxyPacket.serializer(), packet)
        jsonString.toByteArray(Charsets.UTF_8)
    } catch (e: SerializationException) {
        println("Error serializing ProxyPacket: ${e.message}")
        null // or handle the error as appropriate for your application
    }
}

// Deserialize
fun deserializePacket(data: ByteArray): ProxyPacket? {
    val jsonString = data.toString(Charsets.UTF_8)
    return try {
        Json.decodeFromString(ProxyPacket.serializer(), jsonString)
    } catch (e: SerializationException) {
        println("Error deserializing ProxyPacket: ${e.message}")
        null // or handle the error as appropriate for your application
    }
}