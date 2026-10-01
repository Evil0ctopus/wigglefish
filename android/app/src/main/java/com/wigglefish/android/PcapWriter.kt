package com.wigglefish.android

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Libpcap (.pcap) file writer for 802.11 IEEE frames.
 * Fully compatible with Wireshark, Aircrack-ng, and Hashcat/hcxtools.
 */
class PcapWriter(private val outputFile: File) {

    companion object {
        private const val PCAP_MAGIC = 0xa1b2c3d4.toInt()
        private const val VERSION_MAJOR: Short = 2
        private const val VERSION_MINOR: Short = 4
        private const val SNAPLEN = 65535
        private const val LINKTYPE_IEEE802_11 = 105 // Raw 802.11 frames

        fun createSessionPcap(context: Context, filename: String = "wigglefish-capture-${System.currentTimeMillis()}.pcap"): PcapWriter {
            val file = File(context.filesDir, filename)
            return PcapWriter(file)
        }
    }

    private var outputStream: OutputStream? = null
    private var packetCount = 0

    init {
        open()
    }

    @Synchronized
    private fun open() {
        if (outputStream != null) return
        val isNew = !outputFile.exists() || outputFile.length() == 0L
        outputStream = FileOutputStream(outputFile, true)
        if (isNew) {
            writeGlobalHeader()
        }
    }

    private fun writeGlobalHeader() {
        val buffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(PCAP_MAGIC)
        buffer.putShort(VERSION_MAJOR)
        buffer.putShort(VERSION_MINOR)
        buffer.putInt(0) // thiszone
        buffer.putInt(0) // sigfigs
        buffer.putInt(SNAPLEN)
        buffer.putInt(LINKTYPE_IEEE802_11)
        outputStream?.write(buffer.array())
        outputStream?.flush()
    }

    @Synchronized
    fun writePacket(packetBytes: ByteArray, timestampMs: Long = System.currentTimeMillis()) {
        val stream = outputStream ?: return
        val len = minOf(packetBytes.size, SNAPLEN)
        val sec = (timestampMs / 1000L).toInt()
        val usec = ((timestampMs % 1000L) * 1000L).toInt()

        val header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(sec)
        header.putInt(usec)
        header.putInt(len) // incl_len
        header.putInt(packetBytes.size) // orig_len

        stream.write(header.array())
        stream.write(packetBytes, 0, len)
        packetCount++
        if (packetCount % 5 == 0) {
            stream.flush()
        }
    }

    @Synchronized
    fun flush() {
        outputStream?.flush()
    }

    @Synchronized
    fun close() {
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null
    }

    fun getFile(): File = outputFile
    fun getPacketCount(): Int = packetCount
}
