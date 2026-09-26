package com.wigglefish.android.esp

/**
 * Stub device profiles for boards Josh captured over USB OTG.
 *
 * Important: several Wireless-Tag / CH343 boards share VID/PID `1A86:55D3`.
 * Chip family must come from a ROM bootloader probe ([EspRomIdentifier]),
 * not from USB descriptors alone.
 */
data class BoardProfile(
    val id: String,
    val label: String,
    val expectedChipFamilies: List<String>,
    val usbBridge: String,
    val preferredVid: Int = 0x1A86,
    val preferredPid: Int = 0x55D3,
    val notes: String,
)

object BoardProfiles {
    val all: List<BoardProfile> = listOf(
        BoardProfile(
            id = "WT013261-S5",
            label = "WT013261-S5",
            expectedChipFamilies = listOf("ESP32-C5"),
            usbBridge = "WCH CH343",
            notes = "Primary Wigglefish C5 target. CH343 USB-UART; same VID/PID as sibling boards.",
        ),
        BoardProfile(
            id = "WT32C3-S5",
            label = "WT32C3-S5",
            expectedChipFamilies = listOf("ESP32-C3"),
            usbBridge = "WCH CH343",
            notes = "ESP32-C3 module / board. Distinguish from C5 via ROM chip_id / magic, not USB ID.",
        ),
        BoardProfile(
            id = "WT018684-S5",
            label = "WT018684-S5",
            expectedChipFamilies = listOf("ESP32", "ESP32-C3", "ESP32-C5"),
            usbBridge = "WCH CH343",
            notes = "Captured CH343 board; expected family confirmed by Identify probe (not USB alone).",
        ),
    )

    fun match(chipFamily: String?, vid: Int?, pid: Int?): BoardProfile? {
        if (chipFamily.isNullOrBlank()) return null
        val family = chipFamily.trim()
        val candidates = all.filter { profile ->
            profile.expectedChipFamilies.any { it.equals(family, ignoreCase = true) }
        }
        if (candidates.isEmpty()) return null
        if (vid != null && pid != null) {
            val usbHit = candidates.filter { it.preferredVid == vid && it.preferredPid == pid }
            if (usbHit.size == 1) return usbHit.first()
            if (usbHit.isNotEmpty()) return usbHit.first()
        }
        return candidates.firstOrNull()
    }

    /** Chip families that may receive an in-app flash for a given profile stub. */
    fun flashEligibleFamilies(profileId: String?): List<String> {
        val profile = all.firstOrNull { it.id.equals(profileId, ignoreCase = true) } ?: return emptyList()
        return profile.expectedChipFamilies
    }
}

