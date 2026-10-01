package com.wigglefish.android

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time 802.11 packet dissection, EAPOL 4-way handshake & PMKID extractor,
 * Deauth storm detector, and Evil Twin detector.
 */
class PacketAnalyzer(
    private val onHandshakeCaptured: (HandshakeRecord) -> Unit,
    private val onDeauthAlert: (DeauthAlert) -> Unit,
    private val onEvilTwinAlert: (EvilTwinAlert) -> Unit,
    private val onProbeObserved: (ClientProbe) -> Unit,
    private val onSkimmerDetected: ((BleSkimmerAlert) -> Unit)? = null,
    private val onStationDiscovered: ((StationClient) -> Unit)? = null
) {

    data class HandshakeRecord(
        val ssid: String,
        val bssid: String,
        val clientMac: String,
        val type: String, // "PMKID", "WPA2_4WAY", "WPA2_HALF"
        val pmkidHex: String = "",
        val hashcat22000: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    data class DeauthAlert(
        val apMac: String,
        val clientMac: String,
        val count: Int,
        val reasonCode: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class EvilTwinAlert(
        val ssid: String,
        val primaryBssid: String,
        val rogueBssid: String,
        val detail: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ClientProbe(
        val clientMac: String,
        val queriedSsid: String,
        val rssi: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class StationClient(
        val clientMac: String,
        val apBssid: String,
        var packetCount: Int = 1,
        var lastRssi: Int = -60,
        var lastSeen: Long = System.currentTimeMillis()
    )

    data class WpsPmfAudit(
        val bssid: String,
        val ssid: String,
        val wpsEnabled: Boolean = false,
        val wpsLocked: Boolean = false,
        val pmfRequired: Boolean = false,
        val pmfCapable: Boolean = false
    )

    data class BleSkimmerAlert(
        val address: String,
        val name: String,
        val type: String, // "SKIMMER_HC05", "AIRTAG_TRACKER", "SMARTTAG", "TILE"
        val detail: String,
        val rssi: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Internal state tracking
    private val deauthSlidingWindow = mutableListOf<Long>()
    private val knownSsidProfiles = mutableMapOf<String, String>() // SSID -> Primary BSSID
    private val pendingM1Frames = mutableMapOf<String, ByteArray>() // "BSSID:CLIENT" -> M1 frame
    private val capturedHandshakes = mutableListOf<HandshakeRecord>()
    private val probedClientMap = mutableMapOf<String, MutableSet<String>>() // ClientMAC -> Set<SSID>
    private val connectedStationsMap = mutableMapOf<String, StationClient>() // "STA:AP" -> StationClient
    private val wpsPmfAuditMap = mutableMapOf<String, WpsPmfAudit>() // BSSID -> WpsPmfAudit
    private val detectedSkimmers = mutableListOf<BleSkimmerAlert>()

    fun getCapturedHandshakes(): List<HandshakeRecord> = synchronized(capturedHandshakes) {
        capturedHandshakes.toList()
    }

    fun getProbedClients(): Map<String, Set<String>> = synchronized(probedClientMap) {
        probedClientMap.mapValues { it.value.toSet() }
    }

    fun getConnectedStations(): List<StationClient> = synchronized(connectedStationsMap) {
        connectedStationsMap.values.sortedByDescending { it.lastSeen }.toList()
    }

    fun getWpsPmfAudits(): Map<String, WpsPmfAudit> = synchronized(wpsPmfAuditMap) {
        wpsPmfAuditMap.toMap()
    }

    fun getDetectedSkimmers(): List<BleSkimmerAlert> = synchronized(detectedSkimmers) {
        detectedSkimmers.toList()
    }

    /**
     * Checks BLE advertisements for rogue tracker tags and financial skimmer modules (HC-05/06/CC2541).
     */
    fun analyzeBleAdvertisement(address: String, name: String, mfrBytes: ByteArray?, rssi: Int) {
        val cleanAddr = address.uppercase()
        val upperName = name.uppercase()

        // 1. ATM / Gas Pump Bluetooth Skimmer Signature Check
        val isSkimmerOUI = cleanAddr.startsWith("00:14:03") || cleanAddr.startsWith("20:13:08") ||
                cleanAddr.startsWith("00:18:E4") || cleanAddr.startsWith("00:13:EF")
        val isSkimmerName = upperName.contains("HC-05") || upperName.contains("HC-06") ||
                upperName.contains("BT_MODULE") || upperName.contains("PUMP") || upperName.contains("DISPENSER")

        if (isSkimmerOUI || isSkimmerName) {
            val alert = BleSkimmerAlert(
                address = address,
                name = name.ifEmpty { "Unknown Skimmer Module" },
                type = "SKIMMER_HC05",
                detail = "Known hardware signature for unencrypted ATM/Pump skimmer module",
                rssi = rssi
            )
            synchronized(detectedSkimmers) {
                if (detectedSkimmers.none { it.address.equals(address, ignoreCase = true) }) {
                    detectedSkimmers.add(alert)
                    onSkimmerDetected?.invoke(alert)
                }
            }
            return
        }

        // 2. Apple AirTag / Find My Tracker Check
        if (mfrBytes != null && mfrBytes.size >= 4) {
            // Apple ID 0x004C (76, 0)
            if (mfrBytes[0] == 0x4C.toByte() && mfrBytes[1] == 0x00.toByte()) {
                val subType = mfrBytes[2].toInt() and 0xFF
                if (subType == 0x12 || subType == 0x07) { // 0x12 = FindMy / AirTag beacon
                    val alert = BleSkimmerAlert(
                        address = address,
                        name = "Apple AirTag / Find My",
                        type = "AIRTAG_TRACKER",
                        detail = "Proximity tracker beacon detected broadcasting nearby",
                        rssi = rssi
                    )
                    synchronized(detectedSkimmers) {
                        if (detectedSkimmers.none { it.address.equals(address, ignoreCase = true) }) {
                            detectedSkimmers.add(alert)
                            onSkimmerDetected?.invoke(alert)
                        }
                    }
                }
            }
        }
    }

    /**
     * Dissects a raw 802.11 IEEE frame.
     */
    fun processFrame(frame: ByteArray, channel: Int, rssi: Int) {
        if (frame.size < 24) return

        val frameControl = ((frame[1].toInt() and 0xFF) shl 8) or (frame[0].toInt() and 0xFF)
        val type = (frameControl shr 2) and 0x03
        val subtype = (frameControl shr 4) and 0x0F

        val addr1 = RadioProtocols.formatMac(frame, 4)  // Destination / Receiver
        val addr2 = RadioProtocols.formatMac(frame, 10) // Source / Transmitter
        val addr3 = RadioProtocols.formatMac(frame, 16) // BSSID / Filter

        when (type) {
            0 -> handleManagementFrame(subtype, frame, addr1, addr2, addr3, channel, rssi)
            2 -> handleDataFrame(subtype, frame, addr1, addr2, addr3, channel, rssi)
        }
    }

    private fun handleManagementFrame(
        subtype: Int,
        frame: ByteArray,
        addr1: String,
        addr2: String,
        addr3: String,
        channel: Int,
        rssi: Int
    ) {
        when (subtype) {
            4 -> { // Probe Request
                val ssid = extractSsidFromIe(frame, 24)
                if (ssid.isNotEmpty() && ssid != "<hidden>") {
                    synchronized(probedClientMap) {
                        probedClientMap.getOrPut(addr2) { mutableSetOf() }.add(ssid)
                    }
                    onProbeObserved(ClientProbe(addr2, ssid, rssi))
                }
            }
            8, 5 -> { // Beacon (8) or Probe Response (5)
                val ssid = extractSsidFromIe(frame, 36)
                val wpsLocked = isWpsLocked(frame, 36)
                val (pmfReq, pmfCap) = getPmfCapabilities(frame, 36)

                if (addr3.isNotEmpty() && addr3 != "00:00:00:00:00:00") {
                    synchronized(wpsPmfAuditMap) {
                        wpsPmfAuditMap[addr3] = WpsPmfAudit(
                            bssid = addr3,
                            ssid = ssid,
                            wpsEnabled = wpsLocked != null,
                            wpsLocked = wpsLocked ?: false,
                            pmfRequired = pmfReq,
                            pmfCapable = pmfCap
                        )
                    }
                }

                if (ssid.isNotEmpty() && ssid != "<hidden>") {
                    synchronized(knownSsidProfiles) {
                        val primary = knownSsidProfiles[ssid]
                        if (primary == null) {
                            knownSsidProfiles[ssid] = addr3
                        } else if (primary != addr3) {
                            // Check for rogue AP / evil twin
                            onEvilTwinAlert(
                                EvilTwinAlert(
                                    ssid = ssid,
                                    primaryBssid = primary,
                                    rogueBssid = addr3,
                                    detail = "SSID duplicate detected on alternate BSSID $addr3 (Original: $primary)"
                                )
                            )
                        }
                    }
                }
            }
            10, 12 -> { // Disassociation (10) or Deauthentication (12)
                val reasonCode = if (frame.size >= 26) {
                    ((frame[25].toInt() and 0xFF) shl 8) or (frame[24].toInt() and 0xFF)
                } else 0

                val now = System.currentTimeMillis()
                synchronized(deauthSlidingWindow) {
                    deauthSlidingWindow.add(now)
                    deauthSlidingWindow.removeAll { now - it > 3000 } // Keep last 3 seconds
                    if (deauthSlidingWindow.size >= 4) {
                        onDeauthAlert(
                            DeauthAlert(
                                apMac = addr2,
                                clientMac = addr1,
                                count = deauthSlidingWindow.size,
                                reasonCode = reasonCode
                            )
                        )
                    }
                }
            }
        }
    }

    private fun handleDataFrame(
        subtype: Int,
        frame: ByteArray,
        addr1: String,
        addr2: String,
        addr3: String,
        channel: Int,
        rssi: Int
    ) {
        // Track connected client stations from ToDS / FromDS flags
        val frameControl1 = frame[1].toInt() and 0xFF
        val toDs = (frameControl1 and 0x01) != 0
        val fromDs = (frameControl1 and 0x02) != 0

        var clientSta: String? = null
        var apBssid: String? = null

        if (toDs && !fromDs) {
            apBssid = addr1
            clientSta = addr2
        } else if (!toDs && fromDs) {
            clientSta = addr1
            apBssid = addr2
        }

        if (clientSta != null && apBssid != null &&
            !clientSta.startsWith("FF:FF", ignoreCase = true) &&
            !clientSta.startsWith("01:00:5E", ignoreCase = true) &&
            !clientSta.startsWith("33:33", ignoreCase = true)
        ) {
            val key = "$clientSta:$apBssid"
            synchronized(connectedStationsMap) {
                val existing = connectedStationsMap[key]
                if (existing != null) {
                    existing.packetCount++
                    existing.lastRssi = rssi
                    existing.lastSeen = System.currentTimeMillis()
                } else {
                    val sta = StationClient(clientSta, apBssid, 1, rssi)
                    connectedStationsMap[key] = sta
                    onStationDiscovered?.invoke(sta)
                }
            }
        }

        // Look for 802.1X / EAPOL (EtherType 0x888E) inside LLC/SNAP header or QoS data
        val eapolOffset = findEapolOffset(frame) ?: return
        val eapolLen = frame.size - eapolOffset
        if (eapolLen < 95) return // Minimum EAPOL Key frame length

        val eapolType = frame[eapolOffset + 1].toInt() and 0xFF
        if (eapolType != 3) return // 3 = EAPOL-Key

        val keyDescType = frame[eapolOffset + 4].toInt() and 0xFF
        if (keyDescType != 2 && keyDescType != 254) return // 2 = RSN (WPA2), 254 = WPA1

        val keyInfo = ((frame[eapolOffset + 5].toInt() and 0xFF) shl 8) or (frame[eapolOffset + 6].toInt() and 0xFF)
        val keyMic = (keyInfo and 0x0100) != 0 || (keyInfo and 0x0040) != 0
        val keyAck = (keyInfo and 0x0080) != 0 || (keyInfo and 0x0008) != 0

        val apMac = addr3
        val clientMac = if (addr1 == apMac) addr2 else addr1
        val pairKey = "$apMac:$clientMac"

        // 1. Check for PMKID in EAPOL frame KDE (Key Data Encapsulation)
        val pmkid = extractPmkidFromEapol(frame, eapolOffset)
        if (pmkid != null) {
            val pmkidHex = RadioProtocols.bytesToHex(pmkid)
            val cleanApMac = apMac.replace(":", "").lowercase()
            val cleanClientMac = clientMac.replace(":", "").lowercase()
            val hashcat = "WPA*01*$pmkidHex*$cleanApMac*$cleanClientMac***"
            val record = HandshakeRecord(
                ssid = "<captured>",
                bssid = apMac,
                clientMac = clientMac,
                type = "PMKID",
                pmkidHex = pmkidHex,
                hashcat22000 = hashcat
            )
            synchronized(capturedHandshakes) {
                if (capturedHandshakes.none { it.bssid == apMac && it.pmkidHex == pmkidHex }) {
                    capturedHandshakes.add(record)
                    onHandshakeCaptured(record)
                }
            }
            return
        }

        // 2. Classify 4-Way Handshake Message
        if (keyAck && !keyMic) {
            // Message 1 (AP -> Client)
            pendingM1Frames[pairKey] = frame.copyOf()
        } else if (!keyAck && keyMic) {
            // Message 2 (Client -> AP)
            val m1 = pendingM1Frames[pairKey]
            val recordType = if (m1 != null) "WPA2_4WAY" else "WPA2_HALF"
            val cleanApMac = apMac.replace(":", "").lowercase()
            val cleanClientMac = clientMac.replace(":", "").lowercase()
            val eapolHex = RadioProtocols.bytesToHex(frame.copyOfRange(eapolOffset, frame.size))
            val hashcat = "WPA*02*${cleanApMac}*${cleanClientMac}***$eapolHex"

            val record = HandshakeRecord(
                ssid = "<captured>",
                bssid = apMac,
                clientMac = clientMac,
                type = recordType,
                hashcat22000 = hashcat
            )
            synchronized(capturedHandshakes) {
                if (capturedHandshakes.none { it.bssid == apMac && it.clientMac == clientMac }) {
                    capturedHandshakes.add(record)
                    onHandshakeCaptured(record)
                }
            }
        }
    }

    private fun findEapolOffset(frame: ByteArray): Int? {
        // Standard 802.11 Data header is 24 bytes, QoS Data is 26 bytes
        for (offset in 24..38) {
            if (offset + 8 <= frame.size) {
                // Check LLC/SNAP header (AA AA 03 00 00 00 88 8E)
                if (frame[offset] == 0xAA.toByte() &&
                    frame[offset + 1] == 0xAA.toByte() &&
                    frame[offset + 2] == 0x03.toByte() &&
                    frame[offset + 6] == 0x88.toByte() &&
                    frame[offset + 7] == 0x8E.toByte()
                ) {
                    return offset + 8
                }
            }
        }
        return null
    }

    private fun extractPmkidFromEapol(frame: ByteArray, eapolOffset: Int): ByteArray? {
        val keyDataLenOffset = eapolOffset + 97
        if (keyDataLenOffset + 2 > frame.size) return null
        val keyDataLen = ((frame[keyDataLenOffset].toInt() and 0xFF) shl 8) or (frame[keyDataLenOffset + 1].toInt() and 0xFF)
        val keyDataStart = keyDataLenOffset + 2
        if (keyDataStart + keyDataLen > frame.size) return null

        var idx = keyDataStart
        while (idx + 6 <= keyDataStart + keyDataLen) {
            val tag = frame[idx].toInt() and 0xFF
            val len = frame[idx + 1].toInt() and 0xFF
            if (tag == 0xDD && len >= 20) { // Vendor Specific KDE
                // Check OUI 00-0F-AC and Type 4 (PMKID)
                if (frame[idx + 2] == 0x00.toByte() &&
                    frame[idx + 3] == 0x0F.toByte() &&
                    frame[idx + 4] == 0xAC.toByte() &&
                    frame[idx + 5] == 0x04.toByte()
                ) {
                    if (idx + 6 + 16 <= frame.size) {
                        return frame.copyOfRange(idx + 6, idx + 6 + 16)
                    }
                }
            }
            idx += 2 + len
        }
        return null
    }

    private fun extractSsidFromIe(frame: ByteArray, startOffset: Int): String {
        var offset = startOffset
        while (offset + 2 <= frame.size) {
            val tag = frame[offset].toInt() and 0xFF
            val len = frame[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > frame.size) break
            if (tag == 0) { // Tag 0 = SSID
                if (len == 0) return "<hidden>"
                return String(frame, offset + 2, len, Charsets.UTF_8)
            }
            offset += 2 + len
        }
        return ""
    }

    /**
     * Parses Tag 221 (Vendor Specific with WPS OUI 00-50-F2:04) to detect WPS locked state.
     * Returns true if locked, false if unlocked, null if WPS not present.
     */
    private fun isWpsLocked(frame: ByteArray, startOffset: Int): Boolean? {
        var offset = startOffset
        while (offset + 2 <= frame.size) {
            val tag = frame[offset].toInt() and 0xFF
            val len = frame[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > frame.size) break
            if (tag == 221 && len >= 4) {
                // Microsoft / Wi-Fi Alliance WPS OUI 00-50-F2 Type 4
                if (frame[offset + 2] == 0x00.toByte() &&
                    frame[offset + 3] == 0x50.toByte() &&
                    frame[offset + 4] == 0xF2.toByte() &&
                    frame[offset + 5] == 0x04.toByte()
                ) {
                    // Search inner WPS attributes
                    var inner = offset + 6
                    val end = offset + 2 + len
                    var locked = false
                    while (inner + 4 <= end) {
                        val attrType = ((frame[inner].toInt() and 0xFF) shl 8) or (frame[inner + 1].toInt() and 0xFF)
                        val attrLen = ((frame[inner + 2].toInt() and 0xFF) shl 8) or (frame[inner + 3].toInt() and 0xFF)
                        if (inner + 4 + attrLen > end) break
                        if (attrType == 0x1057 && attrLen == 1) { // 0x1057 = AP Setup Locked attribute
                            locked = frame[inner + 4].toInt() != 0
                        }
                        inner += 4 + attrLen
                    }
                    return locked
                }
            }
            offset += 2 + len
        }
        return null
    }

    /**
     * Parses Tag 48 (RSN Information Element) to audit 802.11w PMF protection.
     * Returns Pair(pmfRequired, pmfCapable).
     */
    private fun getPmfCapabilities(frame: ByteArray, startOffset: Int): Pair<Boolean, Boolean> {
        var offset = startOffset
        while (offset + 2 <= frame.size) {
            val tag = frame[offset].toInt() and 0xFF
            val len = frame[offset + 1].toInt() and 0xFF
            if (offset + 2 + len > frame.size) break
            if (tag == 48 && len >= 8) { // Tag 48 = RSN IE
                var idx = offset + 8 // Skip Version (2) + Group Cipher (4) + Pairwise Count (2)
                if (idx + 2 <= offset + 2 + len) {
                    val pairCount = ((frame[idx - 2].toInt() and 0xFF) or ((frame[idx - 1].toInt() and 0xFF) shl 8))
                    idx += pairCount * 4
                    if (idx + 2 <= offset + 2 + len) {
                        val akmCount = ((frame[idx].toInt() and 0xFF) or ((frame[idx + 1].toInt() and 0xFF) shl 8))
                        idx += 2 + (akmCount * 4)
                        if (idx + 2 <= offset + 2 + len) {
                            val rsnCaps = (frame[idx].toInt() and 0xFF) or ((frame[idx + 1].toInt() and 0xFF) shl 8)
                            val pmfRequired = (rsnCaps and 0x0040) != 0 // Bit 6 = Management Frame Protection Required
                            val pmfCapable = (rsnCaps and 0x0080) != 0  // Bit 7 = Management Frame Protection Capable
                            return Pair(pmfRequired, pmfCapable)
                        }
                    }
                }
            }
            offset += 2 + len
        }
        return Pair(false, false)
    }
}
