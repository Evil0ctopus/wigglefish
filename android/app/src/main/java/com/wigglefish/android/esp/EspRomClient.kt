package com.wigglefish.android.esp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared ESP ROM serial protocol (SLIP + command framing).
 *
 * Used by [EspRomIdentifier] and [EspRomFlasher]. Not a Python esptool port —
 * enough ROM loader surface for identify + flash-write without stub upload.
 */
interface EspSerialTransport {
    fun setDtr(value: Boolean)
    fun setRts(value: Boolean)
    fun write(data: ByteArray)
    fun readAvailable(maxBytes: Int, timeoutMs: Int): ByteArray
    fun purgeInput()
    /** Optional baud change for faster flash; default no-op. */
    fun setBaudRate(baud: Int) {}
}

open class EspRomClient(
    protected val transport: EspSerialTransport,
) {
    companion object {
        const val CMD_FLASH_BEGIN = 0x02
        const val CMD_FLASH_DATA = 0x03
        const val CMD_FLASH_END = 0x04
        const val CMD_SYNC = 0x08
        const val CMD_READ_REG = 0x0A
        const val CMD_SPI_SET_PARAMS = 0x0B
        const val CMD_SPI_ATTACH = 0x0D
        const val CMD_CHANGE_BAUDRATE = 0x0F
        const val CMD_SPI_FLASH_MD5 = 0x13
        const val CMD_GET_SECURITY_INFO = 0x14

        const val CHECKSUM_MAGIC = 0xEF
        const val FLASH_WRITE_SIZE = 0x400
        const val CHIP_DETECT_MAGIC_REG = 0x40001000

        // IMAGE_CHIP_ID values from esptool targets (GET_SECURITY_INFO).
        val CHIP_ID_NAMES = mapOf(
            0 to "ESP32",
            2 to "ESP32-S2",
            5 to "ESP32-C3",
            9 to "ESP32-S3",
            12 to "ESP32-C2",
            13 to "ESP32-C6",
            16 to "ESP32-H2",
            18 to "ESP32-P4",
            20 to "ESP32-C61",
            23 to "ESP32-C5",
        )

        val MAGIC_NAMES = mapOf(
            0x00F01D83L to "ESP32",
            0x000007C6L to "ESP32-S2",
            0x09F01D83L to "ESP32",
            0x6921506FL to "ESP32-C3",
            0x1B31506FL to "ESP32-C3",
            0x0EB004EFL to "ESP32-C6",
            0x2CE0806FL to "ESP32-C6",
            0xD7B73E80L to "ESP32-H2",
            0x5FD1406FL to "ESP32-C5",
        )

        const val MAC_EFUSE_C3 = 0x60008844
        const val MAC_EFUSE_C5 = 0x600B4844
        const val MAC_EFUSE_ESP32 = 0x3FF5A000
    }

    /**
     * Classic auto-reset into download mode.
     * CH343/CH340: re-apply DTR after RTS changes (usbser-style quirk).
     */
    fun enterBootloader(log: StringBuilder? = null) {
        log?.appendLine("reset → download (classic DTR/RTS)")
        transport.purgeInput()
        setLines(dtr = false, rts = false)
        sleep(50)
        setLines(dtr = false, rts = true)
        sleep(100)
        setLines(dtr = true, rts = false)
        sleep(50)
        setLines(dtr = false, rts = false)
        sleep(100)
        transport.purgeInput()
    }

    /**
     * Classic hard reset back to run mode (IO0 high, pulse EN).
     */
    fun resetToRun(log: StringBuilder? = null) {
        log?.appendLine("reset → run mode (classic DTR/RTS)")
        setLines(dtr = false, rts = false)
        sleep(50)
        // EN low
        setLines(dtr = false, rts = true)
        sleep(100)
        // EN high, IO0 high → app
        setLines(dtr = false, rts = false)
        sleep(150)
        transport.purgeInput()
    }

    protected fun setLines(dtr: Boolean, rts: Boolean) {
        transport.setRts(rts)
        transport.setDtr(dtr)
        transport.setDtr(dtr)
    }

    fun sync(log: StringBuilder? = null): Boolean {
        val payload = ByteArray(36)
        payload[0] = 0x07
        payload[1] = 0x07
        payload[2] = 0x12
        payload[3] = 0x20
        for (i in 4 until 36) payload[i] = 0x55

        repeat(8) { attempt ->
            transport.purgeInput()
            try {
                val (value, _) = command(CMD_SYNC, payload, timeoutMs = 120)
                repeat(7) {
                    try {
                        command(null, timeoutMs = 80)
                    } catch (_: Exception) {
                    }
                }
                log?.appendLine("sync ok (attempt ${attempt + 1}, val=$value)")
                return true
            } catch (_: Exception) {
                sleep(40)
            }
        }
        log?.appendLine("sync exhausted")
        return false
    }

    /**
     * @param checkStatus when true, fail if ROM status byte != 0
     * @return Pair(value field from header, body without trailing status bytes when checked)
     */
    protected fun command(
        op: Int?,
        data: ByteArray = ByteArray(0),
        checksum: Int = 0,
        timeoutMs: Int = 300,
        checkStatus: Boolean = false,
    ): Pair<Int, ByteArray> {
        if (op != null) {
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.put(0x00)
            header.put(op.toByte())
            header.putShort(data.size.toShort())
            header.putInt(checksum)
            slipWrite(header.array() + data)
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val packet = slipRead(timeoutMs = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(20))
                ?: continue
            if (packet.size < 8) continue
            val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
            val direction = buf.get().toInt() and 0xFF
            val opRet = buf.get().toInt() and 0xFF
            val len = buf.short.toInt() and 0xFFFF
            val value = buf.int
            if (direction != 0x01) continue
            if (op != null && opRet != op) continue
            val body = if (packet.size > 8) packet.copyOfRange(8, packet.size) else ByteArray(0)
            if (len > 0 && body.size < len) continue
            if (checkStatus) {
                // ROM (non-stub) typically returns 4 status bytes; first is status flag.
                if (body.isEmpty()) {
                    throw IllegalStateException("empty status for op=0x${op?.toString(16)}")
                }
                val status = body[0].toInt() and 0xFF
                if (status != 0) {
                    val err = if (body.size > 1) body[1].toInt() and 0xFF else -1
                    throw IllegalStateException("ROM status=$status err=0x${err.toString(16)} op=0x${op?.toString(16)}")
                }
                // Strip 2 or 4 trailing status bytes for callers that want payload.
                val payloadLen = when {
                    body.size >= 4 -> body.size - 4
                    body.size >= 2 -> body.size - 2
                    else -> 0
                }
                val payload = if (payloadLen > 0) body.copyOfRange(0, payloadLen) else ByteArray(0)
                return value to payload
            }
            return value to body
        }
        throw IllegalStateException("timeout waiting for ROM response")
    }

    protected fun checkedCommand(
        op: Int,
        data: ByteArray = ByteArray(0),
        checksum: Int = 0,
        timeoutMs: Int = 3000,
    ): Pair<Int, ByteArray> = command(op, data, checksum, timeoutMs, checkStatus = true)

    protected fun checksum(data: ByteArray): Int {
        var state = CHECKSUM_MAGIC
        for (b in data) state = state xor (b.toInt() and 0xFF)
        return state
    }

    protected fun u32(vararg values: Int): ByteArray {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putInt(it) }
        return buf.array()
    }

    protected fun slipWrite(packet: ByteArray) {
        val out = ByteArrayOutputStream(packet.size + 16)
        out.write(0xC0)
        for (b in packet) {
            val v = b.toInt() and 0xFF
            when (v) {
                0xC0 -> {
                    out.write(0xDB)
                    out.write(0xDC)
                }
                0xDB -> {
                    out.write(0xDB)
                    out.write(0xDD)
                }
                else -> out.write(v)
            }
        }
        out.write(0xC0)
        transport.write(out.toByteArray())
    }

    protected fun slipRead(timeoutMs: Int): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var started = false
        var escape = false
        val packet = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.readAvailable(512, 40)
            if (chunk.isEmpty()) continue
            for (b in chunk) {
                val v = b.toInt() and 0xFF
                if (!started) {
                    if (v == 0xC0) {
                        started = true
                        packet.reset()
                        escape = false
                    }
                    continue
                }
                if (escape) {
                    escape = false
                    when (v) {
                        0xDC -> packet.write(0xC0)
                        0xDD -> packet.write(0xDB)
                        else -> {
                            started = false
                            packet.reset()
                        }
                    }
                    continue
                }
                when (v) {
                    0xDB -> escape = true
                    0xC0 -> {
                        if (packet.size() > 0) return packet.toByteArray()
                        packet.reset()
                    }
                    else -> packet.write(v)
                }
            }
        }
        return null
    }

    protected fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    protected fun readReg(address: Int, log: StringBuilder? = null): Int? {
        return try {
            val (value, _) = checkedCommand(CMD_READ_REG, u32(address), timeoutMs = 400)
            value
        } catch (error: Exception) {
            // Some chips reply without clean status framing on READ_REG — fall back.
            try {
                val addr = u32(address)
                val (value, _) = command(CMD_READ_REG, addr, timeoutMs = 400)
                value
            } catch (inner: Exception) {
                log?.appendLine("READ_REG 0x${address.toString(16)}: ${inner.message ?: error.message}")
                null
            }
        }
    }
}
