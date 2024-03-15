package com.example.bluetalk.spec


import android.annotation.SuppressLint
import android.util.Log
import no.nordicsemi.android.ble.data.DataMerger
import no.nordicsemi.android.ble.data.DataStream
import java.nio.ByteBuffer

class PacketMerger : DataMerger {
    private var expectedSize = 0
    private var totalReceivedSize = 0 // Track the total size of received packets

    /**
     * A method that merges the last packet into the output message. All bytes from the lastPacket
     * are simply copied to the output stream until null is returned.
     *
     * @param output     the stream for the output message, initially empty.
     * @param lastPacket the data received in the last read/notify/indicate operation.
     * @param index      an index of the packet, 0-based.
     * @return True,    if the message is complete, false if more data are expected.
     */
    @SuppressLint("LogNotTimber")
    override fun merge(output: DataStream, lastPacket: ByteArray?, index: Int): Boolean {
        if (lastPacket == null) return false

        // Extract message size from the first packet
        if (index == 0) {
            val buffer = ByteBuffer.wrap(lastPacket)
            expectedSize = buffer.short.toInt() // Get the expected size
            totalReceivedSize = lastPacket.size - 2 // Initialize total received size excluding the header
            // Write the first packet's data (excluding the header) to the output
            output.write(lastPacket, 2, lastPacket.size - 2)
        } else {
            totalReceivedSize += lastPacket.size
            output.write(lastPacket)
        }

        // Check if the total received size matches the expected size
        return totalReceivedSize >= expectedSize
    }

    // Remember to reset totalReceivedSize and expectedSize when starting a new message
}