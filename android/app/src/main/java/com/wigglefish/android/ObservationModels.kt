package com.wigglefish.android

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** GPS fix attached to observations / session timeline. */
data class GpsFix(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val provider: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestampMs", timestampMs)
        put("lat", latitude)
        put("lon", longitude)
        put("accuracyM", accuracyM.toDouble())
        if (altitudeM != null) put("altitudeM", altitudeM)
        if (provider.isNotEmpty()) put("provider", provider)
    }

    companion object {
        fun fromJson(obj: JSONObject?): GpsFix? {
            if (obj == null || !obj.has("lat") || !obj.has("lon")) return null
            return GpsFix(
                timestampMs = obj.optLong("timestampMs", 0L),
                latitude = obj.optDouble("lat"),
                longitude = obj.optDouble("lon"),
                accuracyM = obj.optDouble("accuracyM", 0.0).toFloat(),
                altitudeM = if (obj.has("altitudeM")) obj.optDouble("altitudeM") else null,
                provider = obj.optString("provider", ""),
            )
        }
    }
}

/** Deduped Wi-Fi / BLE observation with running stats + last GPS. */
data class ObservationRecord(
    val key: String,
    val type: String, // wifi | ble
    val mac: String,
    var ssidOrName: String,
    var channel: Int = 0,
    var frequencyMhz: Int = 0,
    var security: String = "",
    var vendor: String = "",
    var source: String = "",
    val firstSeenMs: Long,
    var lastSeenMs: Long,
    var hitCount: Int = 1,
    var peakRssi: Int,
    var rssiSum: Long,
    var lastRssi: Int,
    var lastGps: GpsFix? = null,
) {
    val avgRssi: Int
        get() = if (hitCount <= 0) lastRssi else (rssiSum / hitCount).toInt()

    fun update(
        rssi: Int,
        ssidOrName: String,
        channel: Int,
        frequencyMhz: Int,
        security: String,
        source: String,
        nowMs: Long,
        gps: GpsFix?,
    ) {
        if (ssidOrName.isNotEmpty() && ssidOrName != "<hidden>") {
            this.ssidOrName = ssidOrName
        }
        if (channel > 0) this.channel = channel
        if (frequencyMhz > 0) this.frequencyMhz = frequencyMhz
        if (security.isNotEmpty()) this.security = security
        if (source.isNotEmpty()) this.source = source
        lastSeenMs = nowMs
        hitCount += 1
        lastRssi = rssi
        rssiSum += rssi.toLong()
        if (rssi > peakRssi) peakRssi = rssi
        if (gps != null) lastGps = gps
        if (vendor.isEmpty() && mac.isNotEmpty()) {
            vendor = OuiLookup.vendorFor(mac)
        }
    }

    fun displayLine(): String {
        val vendorBit = if (vendor.isNotEmpty()) "  $vendor" else ""
        return if (type == "wifi") {
            "WIFI  %4d dBm  ch %-3d  %-20s  %-10s%s  n=%d peak=%d".format(
                lastRssi, channel, ssidOrName.take(20), security.take(10), vendorBit, hitCount, peakRssi,
            )
        } else {
            "BLE   %4d dBm  %-20s  %s%s  n=%d peak=%d".format(
                lastRssi, ssidOrName.take(20), mac, vendorBit, hitCount, peakRssi,
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("key", key)
        put("mac", mac)
        if (type == "wifi") {
            put("bssid", mac)
            put("ssid", ssidOrName)
            put("channel", channel)
            if (frequencyMhz > 0) put("frequency", frequencyMhz)
            put("security", security)
            put("encryption", security)
        } else {
            put("address", mac)
            put("name", ssidOrName)
        }
        put("rssi", lastRssi)
        put("peakRssi", peakRssi)
        put("avgRssi", avgRssi)
        put("hitCount", hitCount)
        put("firstSeenMs", firstSeenMs)
        put("lastSeenMs", lastSeenMs)
        if (vendor.isNotEmpty()) put("vendor", vendor)
        if (source.isNotEmpty()) put("source", source)
        lastGps?.let { put("gps", it.toJson()) }
    }

    companion object {
        fun newWifi(
            key: String,
            mac: String,
            ssid: String,
            channel: Int,
            frequencyMhz: Int,
            security: String,
            rssi: Int,
            source: String,
            nowMs: Long,
            gps: GpsFix?,
        ): ObservationRecord = ObservationRecord(
            key = key,
            type = "wifi",
            mac = mac,
            ssidOrName = ssid,
            channel = channel,
            frequencyMhz = frequencyMhz,
            security = security,
            vendor = OuiLookup.vendorFor(mac),
            source = source,
            firstSeenMs = nowMs,
            lastSeenMs = nowMs,
            hitCount = 1,
            peakRssi = rssi,
            rssiSum = rssi.toLong(),
            lastRssi = rssi,
            lastGps = gps,
        )

        fun newBle(
            key: String,
            mac: String,
            name: String,
            rssi: Int,
            source: String,
            nowMs: Long,
            gps: GpsFix?,
        ): ObservationRecord = ObservationRecord(
            key = key,
            type = "ble",
            mac = mac,
            ssidOrName = name,
            vendor = OuiLookup.vendorFor(mac),
            source = source,
            firstSeenMs = nowMs,
            lastSeenMs = nowMs,
            hitCount = 1,
            peakRssi = rssi,
            rssiSum = rssi.toLong(),
            lastRssi = rssi,
            lastGps = gps,
        )

        fun fromJson(obj: JSONObject): ObservationRecord {
            val type = obj.optString("type", "wifi")
            val mac = obj.optString("mac").ifEmpty {
                obj.optString("bssid").ifEmpty { obj.optString("address") }
            }
            val name = if (type == "wifi") {
                obj.optString("ssid").ifEmpty { obj.optString("name") }
            } else {
                obj.optString("name").ifEmpty { obj.optString("ssid") }
            }
            val hit = obj.optInt("hitCount", 1).coerceAtLeast(1)
            val lastRssi = obj.optInt("rssi", obj.optInt("lastRssi", 0))
            val peak = obj.optInt("peakRssi", lastRssi)
            val avg = obj.optInt("avgRssi", lastRssi)
            val rssiSum = avg.toLong() * hit
            val key = obj.optString("key").ifEmpty { "$type:$mac" }
            return ObservationRecord(
                key = key,
                type = type,
                mac = mac,
                ssidOrName = name,
                channel = obj.optInt("channel", 0),
                frequencyMhz = obj.optInt("frequency", obj.optInt("frequencyMhz", 0)),
                security = obj.optString("security").ifEmpty { obj.optString("encryption") },
                vendor = obj.optString("vendor").ifEmpty { OuiLookup.vendorFor(mac) },
                source = obj.optString("source", ""),
                firstSeenMs = obj.optLong("firstSeenMs", 0L),
                lastSeenMs = obj.optLong("lastSeenMs", 0L),
                hitCount = hit,
                peakRssi = peak,
                rssiSum = rssiSum,
                lastRssi = lastRssi,
                lastGps = GpsFix.fromJson(obj.optJSONObject("gps")),
            )
        }
    }
}

data class SessionSummary(
    val durationMs: Long,
    val uniqueWifi: Int,
    val uniqueBle: Int,
    val gpsPointCount: Int,
    val observationHits: Int,
    val qualityLabel: String,
    val hasGpsFix: Boolean,
    val observationsWithGps: Int = 0,
    val observationsWithoutGps: Int = 0,
    val distanceMeters: Double = 0.0,
    val gpsFixRatePerMin: Double = 0.0,
    val channelHistogram: Map<Int, Int> = emptyMap(),
) {
    fun formatDuration(): String {
        val durSec = (durationMs / 1000L).coerceAtLeast(0L)
        val hh = durSec / 3600
        val mm = (durSec % 3600) / 60
        val ss = durSec % 60
        return if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)
    }

    fun formatLine(): String {
        val dist = when {
            distanceMeters <= 0.0 -> ""
            distanceMeters < 1000.0 -> "  ~%.0fm".format(distanceMeters)
            else -> "  ~%.2fkm".format(distanceMeters / 1000.0)
        }
        return "SESSION  %s  Wi-Fi %d  BLE %d  GPS pts %d  hits %d  quality %s%s".format(
            formatDuration(), uniqueWifi, uniqueBle, gpsPointCount, observationHits, qualityLabel, dist,
        )
    }

    fun formatCoverageDetail(): String {
        val gpsBit = if (hasGpsFix || gpsPointCount > 0) {
            "GPS %d pts (%.1f/min)  geo'd %d / bare %d".format(
                gpsPointCount, gpsFixRatePerMin, observationsWithGps, observationsWithoutGps,
            )
        } else {
            "no GPS fix — WiGLE/GeoJSON need coordinates"
        }
        val topCh = channelHistogram.entries.sortedByDescending { it.value }.take(5)
            .joinToString(" ") { "ch${it.key}:${it.value}" }
            .ifEmpty { "no channel hits" }
        val dist = when {
            distanceMeters <= 0.0 -> "track n/a"
            distanceMeters < 1000.0 -> "track ~%.0fm".format(distanceMeters)
            else -> "track ~%.2fkm".format(distanceMeters / 1000.0)
        }
        return "COVERAGE  %s  |  %s  |  %s  |  %s".format(
            qualityLabel.uppercase(), gpsBit, dist, topCh,
        )
    }
}

object WardriveTime {
    private val utcFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatUtc(ms: Long): String = synchronized(utcFmt) { utcFmt.format(Date(ms)) }

    fun channelToFrequencyMhz(channel: Int): Int = when {
        channel in 1..13 -> 2407 + channel * 5
        channel == 14 -> 2484
        channel in 36..165 -> 5000 + channel * 5
        else -> 0
    }
}
