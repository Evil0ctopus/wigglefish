package com.wigglefish.android

import org.json.JSONObject

object LegacySerialParser {
    private val macPattern = Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val rssiPattern = Regex("(?i)(?:rssi|signal)\\s*[:=]?\\s*(-?\\d{1,3})")
    private val channelPattern = Regex("(?i)(?:channel|chan|ch)\\s*[:=]?\\s*(\\d{1,3})")
    private val ssidPattern = Regex("(?i)ssid\\s*[:=]\\s*([^,|;]+)")
    private val namePattern = Regex("(?i)(?:name|device)\\s*[:=]\\s*([^,|;]+)")

    fun parse(line: String): JSONObject? {
        parseCsvRow(line)?.let { return it }
        val address = macPattern.find(line)?.value ?: return null
        val rssi = rssiPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val channel = channelPattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val ssid = ssidPattern.find(line)?.groupValues?.getOrNull(1)?.trim()
        val name = namePattern.find(line)?.groupValues?.getOrNull(1)?.trim()
        val isBle = line.contains("ble", true) || line.contains("bluetooth", true) || line.contains("beacon", true)
        return JSONObject().apply {
            put("type", if (isBle) "bluetooth" else "wifi")
            put("source", "LEGACY_SERIAL")
            if (isBle) {
                put("address", address)
                put("mac", address)
                put("name", name ?: "")
            } else {
                put("bssid", address)
                put("ssid", ssid ?: "")
                put("channel", channel ?: 0)
                put("security", "LEGACY_SERIAL")
            }
            put("rssi", rssi)
        }
    }

    private fun parseCsvRow(line: String): JSONObject? {
        if (!line.contains(',')) return null
        val fields = line.split(',').map { it.trim().trim('"') }
        if (fields.size < 6 || !macPattern.matches(fields[0])) return null
        val channel = fields.getOrNull(4)?.toIntOrNull() ?: return null
        val rssi = fields.getOrNull(5)?.toIntOrNull() ?: return null
        return JSONObject().apply {
            put("type", "wifi")
            put("source", "HALEHOUND_CSV")
            put("bssid", fields[0])
            put("ssid", fields[1])
            put("security", fields[2])
            put("encryption", fields[2])
            put("channel", channel)
            put("rssi", rssi)
        }
    }
}