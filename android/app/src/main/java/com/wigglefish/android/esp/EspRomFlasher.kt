package com.wigglefish.android.esp

import java.security.MessageDigest

data class FlashPart(
    val label: String,
    val offset: Int,
    val data: ByteArray,
)

enum class FlashStage {
    SYNC,
    ATTACH,
    ERASE,
    WRITE,
    VERIFY,
    RESET,
    DONE,
    FAILED,
}

data class FlashProgress(
    val stage: FlashStage,
    val percent: Int,
    val message: String,
    val partLabel: String? = null,
    val partIndex: Int = 0,
    val partCount: Int = 0,
)

data class FlashResult(
    val success: Boolean,
    val message: String,
    val detail: String,
    val md5Ok: Boolean? = null,
)

/**
 * ROM-loader flash writer (no stub, no Python esptool).
 *
 * Sequence for ESP32-C5 / C3-family ROM (SUPPORTS_ENCRYPTED_FLASH):
 *  SYNC → SPI_ATTACH → SPI_SET_PARAMS → [optional baud bump] →
 *  per part: FLASH_BEGIN (erase) → FLASH_DATA blocks → SPI_FLASH_MD5 →
 *  FLASH_END (stay) → hard reset to run.
 */
class EspRomFlasher(
    transport: EspSerialTransport,
) : EspRomClient(transport) {

    fun flash(
        parts: List<FlashPart>,
        flashSizeBytes: Int = 4 * 1024 * 1024,
        supportsEncryptedFlashWord: Boolean = true,
        preferHighBaud: Boolean = true,
        onProgress: (FlashProgress) -> Unit = {},
    ): FlashResult {
        val log = StringBuilder()
        if (parts.isEmpty()) {
            return FlashResult(false, "No flash parts", "empty parts list")
        }

        fun progress(stage: FlashStage, percent: Int, message: String, partLabel: String? = null, partIndex: Int = 0) {
            onProgress(
                FlashProgress(
                    stage = stage,
                    percent = percent.coerceIn(0, 100),
                    message = message,
                    partLabel = partLabel,
                    partIndex = partIndex,
                    partCount = parts.size,
                ),
            )
        }

        try {
            progress(FlashStage.SYNC, 1, "Entering download mode…")
            enterBootloader(log)
            if (!sync(log)) {
                progress(FlashStage.FAILED, 0, "ROM sync failed")
                return FlashResult(false, "ROM sync failed", log.toString())
            }

            progress(FlashStage.ATTACH, 5, "Attaching SPI flash…")
            spiAttach(log)
            spiSetParams(flashSizeBytes, log)

            var highBaud = false
            if (preferHighBaud) {
                highBaud = tryChangeBaud(460800, log)
            }

            val totalBytes = parts.sumOf { it.data.size }.coerceAtLeast(1)
            var writtenBytes = 0
            var allMd5Ok = true

            parts.forEachIndexed { index, part ->
                val partPctBase = 10 + (80.0 * writtenBytes / totalBytes).toInt()
                progress(
                    FlashStage.ERASE,
                    partPctBase,
                    "Erasing ${part.label} @ 0x${part.offset.toString(16)} (${part.data.size} bytes)…",
                    part.label,
                    index,
                )
                flashBegin(part.data.size, part.offset, supportsEncryptedFlashWord, log)

                val blocks = (part.data.size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
                for (seq in 0 until blocks) {
                    val start = seq * FLASH_WRITE_SIZE
                    val end = minOf(start + FLASH_WRITE_SIZE, part.data.size)
                    val chunk = ByteArray(FLASH_WRITE_SIZE) { i ->
                        val src = start + i
                        if (src < end) part.data[src] else 0xFF.toByte()
                    }
                    // Erase can take a while; first block after begin may need longer timeout.
                    val timeout = if (seq == 0) 60_000 else 8_000
                    flashDataBlock(chunk, seq, timeout, log)

                    val done = writtenBytes + end
                    val pct = 10 + (80.0 * done / totalBytes).toInt()
                    progress(
                        FlashStage.WRITE,
                        pct,
                        "Writing ${part.label}: block ${seq + 1}/$blocks",
                        part.label,
                        index,
                    )
                }
                writtenBytes += part.data.size

                progress(
                    FlashStage.VERIFY,
                    10 + (80.0 * writtenBytes / totalBytes).toInt(),
                    "Verifying MD5 for ${part.label}…",
                    part.label,
                    index,
                )
                val md5Ok = verifyMd5(part.offset, part.data, log)
                if (!md5Ok) allMd5Ok = false
            }

            progress(FlashStage.RESET, 95, "Leaving flash mode / resetting to run…")
            try {
                // Stay in loader then hard-reset (more reliable than FLASH_END reboot on some bridges).
                flashEnd(reboot = false, log)
            } catch (error: Exception) {
                log.appendLine("FLASH_END: ${error.message}")
            }

            if (highBaud) {
                try {
                    transport.setBaudRate(115200)
                    log.appendLine("host baud restored 115200")
                } catch (error: Exception) {
                    log.appendLine("baud restore: ${error.message}")
                }
            }

            resetToRun(log)
            progress(FlashStage.DONE, 100, "Flash complete")

            val msg = if (allMd5Ok) {
                "Flashed ${parts.size} part(s); MD5 OK. Board reset to run — open Live Field / Connect."
            } else {
                "Flashed ${parts.size} part(s); MD5 verify incomplete/mismatch — check log. Try Connect / Live Field after reset."
            }
            return FlashResult(true, msg, log.toString().trim(), md5Ok = allMd5Ok)
        } catch (error: Exception) {
            log.appendLine("error: ${error.message}")
            progress(FlashStage.FAILED, 0, error.message ?: "Flash failed")
            try {
                transport.setBaudRate(115200)
            } catch (_: Exception) {
            }
            return FlashResult(false, error.message ?: "Flash failed", log.toString().trim(), md5Ok = null)
        }
    }

    private fun spiAttach(log: StringBuilder) {
        // ROM: u32(hspi=0) + u32(is_legacy=0)
        checkedCommand(CMD_SPI_ATTACH, u32(0, 0), timeoutMs = 2000)
        log.appendLine("SPI_ATTACH ok")
    }

    private fun spiSetParams(flashSizeBytes: Int, log: StringBuilder) {
        val payload = u32(
            0, // fl_id
            flashSizeBytes,
            64 * 1024, // block
            4 * 1024, // sector
            256, // page
            0xFFFF, // status mask
        )
        checkedCommand(CMD_SPI_SET_PARAMS, payload, timeoutMs = 2000)
        log.appendLine("SPI_SET_PARAMS size=$flashSizeBytes")
    }

    private fun tryChangeBaud(baud: Int, log: StringBuilder): Boolean {
        return try {
            // ROM: new baud, old=0
            command(CMD_CHANGE_BAUDRATE, u32(baud, 0), timeoutMs = 1000)
            sleep(50)
            transport.setBaudRate(baud)
            transport.purgeInput()
            // Re-sync at new rate (best-effort)
            if (!sync(log)) {
                log.appendLine("baud $baud sync failed — reverting 115200")
                transport.setBaudRate(115200)
                transport.purgeInput()
                enterBootloader(log)
                if (!sync(log)) throw IllegalStateException("re-sync after baud revert failed")
                spiAttach(log)
                return false
            }
            log.appendLine("baud → $baud")
            true
        } catch (error: Exception) {
            log.appendLine("CHANGE_BAUDRATE: ${error.message}")
            try {
                transport.setBaudRate(115200)
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun flashBegin(
        size: Int,
        offset: Int,
        supportsEncryptedFlashWord: Boolean,
        log: StringBuilder,
    ) {
        val numBlocks = (size + FLASH_WRITE_SIZE - 1) / FLASH_WRITE_SIZE
        val eraseSize = numBlocks * FLASH_WRITE_SIZE
        val base = u32(eraseSize, numBlocks, FLASH_WRITE_SIZE, offset)
        val payload = if (supportsEncryptedFlashWord) base + u32(0) else base
        // ROM erases up front — allow ~40s/MB.
        val timeout = (40_000L * eraseSize / (1024 * 1024)).toInt().coerceIn(15_000, 120_000)
        checkedCommand(CMD_FLASH_BEGIN, payload, timeoutMs = timeout)
        log.appendLine("FLASH_BEGIN offset=0x${offset.toString(16)} erase=$eraseSize blocks=$numBlocks")
    }

    private fun flashDataBlock(data: ByteArray, seq: Int, timeoutMs: Int, log: StringBuilder) {
        val header = u32(data.size, seq, 0, 0)
        try {
            checkedCommand(CMD_FLASH_DATA, header + data, checksum(data), timeoutMs = timeoutMs)
        } catch (first: Exception) {
            // One retry (USB OTG glitches).
            sleep(20)
            try {
                checkedCommand(CMD_FLASH_DATA, header + data, checksum(data), timeoutMs = timeoutMs)
            } catch (second: Exception) {
                log.appendLine("FLASH_DATA seq=$seq fail: ${second.message}")
                throw second
            }
        }
    }

    private fun flashEnd(reboot: Boolean, log: StringBuilder) {
        // reboot=true → word 0; reboot=false → word 1 (stay / run-user semantics per esptool)
        val word = if (reboot) 0 else 1
        try {
            checkedCommand(CMD_FLASH_END, u32(word), timeoutMs = 3000)
            log.appendLine("FLASH_END reboot=$reboot")
        } catch (error: Exception) {
            // Some ROM paths don't always ACK END cleanly after MD5; treat as soft failure.
            log.appendLine("FLASH_END soft-fail: ${error.message}")
        }
    }

    private fun verifyMd5(offset: Int, data: ByteArray, log: StringBuilder): Boolean {
        return try {
            val (_, body) = checkedCommand(
                CMD_SPI_FLASH_MD5,
                u32(offset, data.size, 0, 0),
                timeoutMs = 30_000,
            )
            val expected = MessageDigest.getInstance("MD5").digest(data)
            val expectedHex = expected.joinToString("") { "%02x".format(it) }

            val actualHex = when {
                // ROM: 32 ASCII hex chars
                body.size >= 32 && body.take(32).all { b ->
                    val c = b.toInt().toChar()
                    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
                } -> body.decodeToString(0, 32).lowercase()
                // Stub-style: 16 raw bytes
                body.size >= 16 -> body.copyOfRange(0, 16).joinToString("") { "%02x".format(it) }
                else -> {
                    log.appendLine("MD5 @0x${offset.toString(16)}: unexpected body len=${body.size}")
                    return false
                }
            }
            val ok = actualHex.equals(expectedHex, ignoreCase = true)
            log.appendLine(
                if (ok) "MD5 OK ${data.size}B @0x${offset.toString(16)} $actualHex"
                else "MD5 MISMATCH @0x${offset.toString(16)} got=$actualHex want=$expectedHex",
            )
            ok
        } catch (error: Exception) {
            log.appendLine("SPI_FLASH_MD5 @0x${offset.toString(16)}: ${error.message}")
            false
        }
    }
}
