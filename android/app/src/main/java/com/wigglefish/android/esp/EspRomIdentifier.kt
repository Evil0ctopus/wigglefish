package com.wigglefish.android.esp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ESP ROM bootloader identify (PR1, kept for PR2 flash eligibility).
 *
 * Intentionally **not** a full flasher and **not** a Python esptool port.
 * Implements enough of the Espressif serial protocol to:
 *  1. Classic DTR/RTS reset into download mode (CH343-aware)
 *  2. SYNC (0x08)
 *  3. Prefer GET_SECURITY_INFO (0x14) chip_id (C3=5, C5=23, …)
 *  4. Fall back to READ_REG magic at 0x40001000 (classic ESP32 / S2)
 *  5. Best-effort MAC read from eFuse for C3 / C5 / ESP32
 *
 * Flash write lives in [EspRomFlasher] (same transport / SLIP stack).
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

class EspRomIdentifier(
    transport: EspSerialTransport,
) : EspRomClient(transport) {

    fun identify(
        usbVid: Int? = null,
        usbPid: Int? = null,
        usbSerial: String? = null,
        usbProduct: String? = null,
        bridgeLabel: String? = null,
        alreadySynced: Boolean = false,
    ): EspIdentifyResult {
        val log = StringBuilder()
        try {
            if (!alreadySynced) {
                enterBootloader(log)
                if (!sync(log)) {
                    return fail(
                        "ROM sync failed — hold BOOT / check OTG data cable / power.",
                        log, usbVid, usbPid, usbSerial, usbProduct, bridgeLabel,
                    )
                }
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
                magic = readReg(CHIP_DETECT_MAGIC_REG, log)?.toLong()?.and(0xFFFFFFFFL)
            }

            if (chipFamily == null) {
                return fail(
                    "Synced but could not resolve chip family.",
                    log, usbVid, usbPid, usbSerial, usbProduct, bridgeLabel,
                )
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

    private fun getSecurityInfo(log: StringBuilder): Int? {
        return try {
            val (_, data) = command(CMD_GET_SECURITY_INFO, ByteArray(0), timeoutMs = 400)
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
}
