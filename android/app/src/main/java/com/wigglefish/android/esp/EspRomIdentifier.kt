package com.wigglefish.android.esp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal ESP ROM bootloader identify (PR1).
 *
 * Intentionally **not** a full flasher and **not** a Python esptool port.
 * Implements enough of the Espressif serial protocol to:
 *  1. Classic DTR/RTS reset into download mode (CH343-aware)
 *  2. SYNC (0x08)
 *  3. Prefer GET_SECURITY_INFO (0x14) chip_id (C3=5, C5=23, …)
 *  4. Fall back to READ_REG magic at 0x40001000 (classic ESP32 / S2)
 *  5. Best-effort MAC read from eFuse for C3 / C5
 *
 * Full flash + stub upload belongs in a later PR (esp-serial-flasher / NDK
 * or expanded Kotlin). Documented in android/README.md.
 */
data class EspIdentifyResult(
    val success: Boolean,
    val chipFamily: String?,
    val chipId: Int? = null,
    val magic: Long? = null,
    val mac: String? = null,
    val usbVid: Int? = null,
    val usbPid: Int? = null,
    val usbSerial: String? = null,
    val usbProduct: String? = null,
    val bridgeLabel: String? = null,
    val matchedProfileId: String? = null,
    val matchedProfileLabel: String? = null,
    val detail: String,
)

interface EspSerialTransport {
    fun setDtr(value: Boolean)
    fun setRts(value: Boolean)
    fun write(data: ByteArray)
    fun readAvailable(maxBytes: Int, timeoutMs: Int): ByteArray
    fun purgeInput()
}

class EspRomIdentifier(
    private val transport: EspSerialTransport,
) {
    companion object {
        private const val CMD_SYNC = 0x08
        private const val CMD_READ_REG = 0x0A
        private const val CMD_GET_SECURITY_INFO = 0x14
        private const val CHIP_DETECT_MAGIC_REG = 0x40001000
        private const val CHECKSUM_MAGIC = 0xEF

        // IMAGE_CHIP_ID values from esptool targets (GET_SECURITY_INFO).
        private val CHIP_ID_NAMES = mapOf(
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

        // CHIP_DETECT_MAGIC_REG_ADDR fallbacks (classic Xtensa / older path).
        private val MAGIC_NAMES = mapOf(
            0x00F01D83L to "ESP32",
            0x000007C6L to "ESP32-S2",
            0x09F01D83L to "ESP32", // some ECO variants seen in the wild
            0x6921506FL to "ESP32-C3",
            0x1B31506FL to "ESP32-C3",
            0x0EB004EFL to "ESP32-C6",
            0x2CE0806FL to "ESP32-C6",
            0xD7B73E80L to "ESP32-H2",
            0x5FD1406FL to "ESP32-C5",
        )

        private const val MAC_EFUSE_C3 = 0x60008844
        private const val MAC_EFUSE_C5 = 0x600B4844
        private const val MAC_EFUSE_ESP32 = 0x3FF5A000
    }

    fun identify(
        usbVid: Int? = null,
        usbPid: Int? = null,
        usbSerial: String? = null,
        usbProduct: String? = null,
        bridgeLabel: String? = null,
    ): EspIdentifyResult {
        val log = StringBuilder()
        try {
            enterBootloader(log)
            if (!sync(log)) {
                return fail("ROM sync failed — hold BOOT / check OTG data cable / power.", log, usbVid, usbPid, usbSerial, usbProduct, bridgeLabel)
            }

            var chipId: Int? = null
            var chipFamily: String? = null
            var magic: Long? = null

            val security = getSecurityInfo(log)
            if (security != null) {
                chipId = security
                chipFamily = CHIP_ID_NAMES[security] ?: "Unknown(chip_id=$security)"
                log.appendLine("security chip_id=$security → $chipFamily")
            }

            if (chipFamily == null) {
                magic = readReg(CHIP_DETECT_MAGIC_REG, log)?.toLong()?.and(0xFFFFFFFFL)
                if (magic != null) {
                    chipFamily = MAGIC_NAMES[magic] ?: "Unknown(magic=0x${magic.toString(16)})"
                    log.appendLine("magic 0x${magic.toString(16)} → $chipFamily")
                }
            } else {
                // Still capture magic for diagnostics when possible.
                magic = readReg(CHIP_DETECT_MAGIC_REG, log)?.toLong()?.and(0xFFFFFFFFL)
            }

            if (chipFamily == null) {
                return fail("Synced but could not resolve chip family.", log, usbVid, usbPid, usbSerial, usbProduct, bridgeLabel)
            }

            val mac = readMacBestEffort(chipFamily, log)
            val profile = BoardProfiles.match(chipFamily, usbVid, usbPid)

            return EspIdentifyResult(
                success = true,
                chipFamily = chipFamily,
                chipId = chipId,
                magic = magic,
                mac = mac,
                usbVid = usbVid,
                usbPid = usbPid,
                usbSerial = usbSerial,
                usbProduct = usbProduct,
                bridgeLabel = bridgeLabel,
                matchedProfileId = profile?.id,
                matchedProfileLabel = profile?.label,
                detail = log.toString().trim(),
            )
        } catch (error: Exception) {
            log.appendLine("error: ${error.message}")
            return fail(error.message ?: "Identify failed", log, usbVid, usbPid, usbSerial, usbProduct, bridgeLabel)
        }
    }

    private fun fail(
        message: String,
        log: StringBuilder,
        usbVid: Int?,
        usbPid: Int?,
        usbSerial: String?,
        usbProduct: String?,
        bridgeLabel: String?,
    ): EspIdentifyResult {
        log.appendLine(message)
        return EspIdentifyResult(
            success = false,
            chipFamily = null,
            usbVid = usbVid,
            usbPid = usbPid,
            usbSerial = usbSerial,
            usbProduct = usbProduct,
            bridgeLabel = bridgeLabel,
            detail = log.toString().trim(),
        )
    }

    /**
     * Classic auto-reset into download mode.
     * CH343/CH340: re-apply DTR after RTS changes (usbser-style quirk; harmless on Android).
     */
    private fun enterBootloader(log: StringBuilder) {
        log.appendLine("reset → download (classic DTR/RTS)")
        transport.purgeInput()
        setLines(dtr = false, rts = false)
        sleep(50)
        // EN low (reset), IO0 high
        setLines(dtr = false, rts = true)
        sleep(100)
        // EN high, IO0 low → bootloader
        setLines(dtr = true, rts = false)
        sleep(50)
        // IO0 high again
        setLines(dtr = false, rts = false)
        sleep(100)
        transport.purgeInput()
    }

    private fun setLines(dtr: Boolean, rts: Boolean) {
        transport.setRts(rts)
        // CH343 / Windows usbser quirk: poke DTR after RTS so the change sticks.
        transport.setDtr(dtr)
        transport.setDtr(dtr)
    }

    private fun sync(log: StringBuilder): Boolean {
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
                // Drain a few extra SYNC replies the ROM often queues.
                repeat(7) {
                    try {
                        command(null, timeoutMs = 80)
                    } catch (_: Exception) {
                    }
                }
                log.appendLine("sync ok (attempt ${attempt + 1}, val=$value)")
                return true
            } catch (_: Exception) {
                sleep(40)
            }
        }
        log.appendLine("sync exhausted")
        return false
    }

    private fun getSecurityInfo(log: StringBuilder): Int? {
        return try {
            val (_, data) = command(CMD_GET_SECURITY_INFO, ByteArray(0), timeoutMs = 400)
            // 20-byte info + 2/4 status bytes (ESP32-S3 and later), or 12-byte (ESP32-S2).
            when {
                data.size >= 24 -> {
                    val status = data[20].toInt() and 0xFF
                    if (status != 0) throw IllegalStateException("security status=$status")
                    ByteBuffer.wrap(data, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                }
                data.size >= 22 -> {
                    val status = data[20].toInt() and 0xFF
                    if (status != 0) throw IllegalStateException("security status=$status")
                    ByteBuffer.wrap(data, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                }
                data.size >= 14 -> {
                    // ESP32-S2: 12 info bytes + status, no chip_id field.
                    log.appendLine("security_info len=${data.size} (no chip_id / S2-like)")
                    null
                }
                else -> {
                    log.appendLine("security_info too short: ${data.size}")
                    null
                }
            }
        } catch (error: Exception) {
            log.appendLine("GET_SECURITY_INFO: ${error.message}")
            null
        }
    }

    private fun readReg(address: Int, log: StringBuilder): Int? {
        return try {
            val addr = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(address).array()
            val (value, _) = command(CMD_READ_REG, addr, timeoutMs = 400)
            value
        } catch (error: Exception) {
            log.appendLine("READ_REG 0x${address.toString(16)}: ${error.message}")
            null
        }
    }

    private fun readMacBestEffort(chipFamily: String, log: StringBuilder): String? {
        val base = when {
            chipFamily.contains("C5", ignoreCase = true) -> MAC_EFUSE_C5
            chipFamily.contains("C3", ignoreCase = true) -> MAC_EFUSE_C3
            chipFamily.equals("ESP32", ignoreCase = true) -> MAC_EFUSE_ESP32
            else -> return null
        }
        val mac0 = readReg(base, log) ?: return null
        val mac1 = readReg(base + 4, log) ?: return null
        return if (chipFamily.equals("ESP32", ignoreCase = true)) {
            // ESP32: mac0 low 32 + mac1 low 16, big-endian display order differs slightly.
            val bytes = byteArrayOf(
                ((mac1 ushr 8) and 0xFF).toByte(),
                (mac1 and 0xFF).toByte(),
                ((mac0 ushr 24) and 0xFF).toByte(),
                ((mac0 ushr 16) and 0xFF).toByte(),
                ((mac0 ushr 8) and 0xFF).toByte(),
                (mac0 and 0xFF).toByte(),
            )
            formatMac(bytes).also { log.appendLine("mac $it") }
        } else {
            // C3/C5 (and most RISC-V): mac0 = bytes 0..3 little, mac1 low 16 = bytes 4..5
            val bytes = byteArrayOf(
                (mac0 and 0xFF).toByte(),
                ((mac0 ushr 8) and 0xFF).toByte(),
                ((mac0 ushr 16) and 0xFF).toByte(),
                ((mac0 ushr 24) and 0xFF).toByte(),
                (mac1 and 0xFF).toByte(),
                ((mac1 ushr 8) and 0xFF).toByte(),
            )
            formatMac(bytes).also { log.appendLine("mac $it") }
        }
    }

    private fun formatMac(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02X".format(it) }

    /**
     * @param op null = read a response without sending (post-SYNC drain)
     * @return Pair(value field from header, payload without trailing status bytes)
     */
    private fun command(op: Int?, data: ByteArray = ByteArray(0), timeoutMs: Int = 300): Pair<Int, ByteArray> {
        if (op != null) {
            val chk = 0 // identify cmds; FLASH_DATA etc. would checksum payload
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.put(0x00) // request
            header.put(op.toByte())
            header.putShort(data.size.toShort())
            header.putInt(chk)
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
            // Body includes trailing ROM status bytes (2 or 4). Callers trim as needed.
            if (len > 0 && body.size < len) continue
            return value to body
        }
        throw IllegalStateException("timeout waiting for ROM response")
    }

    private fun checksum(data: ByteArray): Int {
        var state = CHECKSUM_MAGIC
        for (b in data) state = state xor (b.toInt() and 0xFF)
        return state
    }

    private fun slipWrite(packet: ByteArray) {
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

    private fun slipRead(timeoutMs: Int): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var started = false
        var escape = false
        val packet = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.readAvailable(256, 40)
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
                            // Invalid escape — resync
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
                        // consecutive C0 — keep waiting for next frame
                        packet.reset()
                    }
                    else -> packet.write(v)
                }
            }
        }
        return null
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
