package com.wigglefish.android

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Passive wardrive export formatters: JSON, CSV, Wardrive Go CSV, WiGLE 1.6 CSV, GeoJSON.
 * Metadata-only — no handshake / PCAP / attack payloads.
 */
object WardriveExporter {
    const val FORMAT_VERSION = "0.4.0"

    fun csvEscape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    fun buildJson(records: List<ObservationRecord>, summary: SessionSummary, gpsFixes: List<GpsFix>): String {
        val root = JSONObject().apply {
            put("format", "wigglefish-wardrive-json")
            put("version", FORMAT_VERSION)
            put("passiveOnly", true)
            put("summary", summaryToJson(summary))
            put("observations", JSONArray().apply {
                records.forEach { put(it.toJson()) }
            })
            put("gpsTimeline", JSONArray().apply {
                gpsFixes.forEach { put(it.toJson()) }
            })
        }
        return root.toString(2)
    }

    fun summaryToJson(summary: SessionSummary): JSONObject = JSONObject().apply {
        put("durationMs", summary.durationMs)
        put("uniqueWifi", summary.uniqueWifi)
        put("uniqueBle", summary.uniqueBle)
        put("gpsPointCount", summary.gpsPointCount)
        put("observationHits", summary.observationHits)
        put("qualityLabel", summary.qualityLabel)
        put("hasGpsFix", summary.hasGpsFix)
        put("observationsWithGps", summary.observationsWithGps)
        put("observationsWithoutGps", summary.observationsWithoutGps)
        put("distanceMeters", summary.distanceMeters)
        put("gpsFixRatePerMin", summary.gpsFixRatePerMin)
        put(
            "channelHistogram",
            JSONObject().apply {
                summary.channelHistogram.forEach { (ch, hits) -> put(ch.toString(), hits) }
            },
        )
    }

    fun summaryFromJson(obj: JSONObject?): SessionSummary {
        if (obj == null) {
            return SessionSummary(0, 0, 0, 0, 0, "idle", false)
        }
        val histObj = obj.optJSONObject("channelHistogram")
        val hist = mutableMapOf<Int, Int>()
        if (histObj != null) {
            histObj.keys().forEach { key ->
                key.toIntOrNull()?.let { hist[it] = histObj.optInt(key, 0) }
            }
        }
        return SessionSummary(
            durationMs = obj.optLong("durationMs", 0L),
            uniqueWifi = obj.optInt("uniqueWifi", 0),
            uniqueBle = obj.optInt("uniqueBle", 0),
            gpsPointCount = obj.optInt("gpsPointCount", 0),
            observationHits = obj.optInt("observationHits", 0),
            qualityLabel = obj.optString("qualityLabel", "idle"),
            hasGpsFix = obj.optBoolean("hasGpsFix", false),
            observationsWithGps = obj.optInt("observationsWithGps", 0),
            observationsWithoutGps = obj.optInt("observationsWithoutGps", 0),
            distanceMeters = obj.optDouble("distanceMeters", 0.0),
            gpsFixRatePerMin = obj.optDouble("gpsFixRatePerMin", 0.0),
            channelHistogram = hist,
        )
    }

    /** Legacy / generic CSV (kept for compatibility). */
    fun buildGenericCsv(records: List<ObservationRecord>): String {
        val rows = mutableListOf("MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME,VENDOR,HITCOUNT,PEAK_RSSI,AVG_RSSI,LAT,LON,ACCURACY,FIRST_SEEN,LAST_SEEN")
        records.forEach { r ->
            val ssid = if (r.type == "wifi") r.ssidOrName else ""
            val name = if (r.type == "ble") r.ssidOrName else ""
            val gps = r.lastGps
            rows += listOf(
                r.mac,
                ssid,
                r.security,
                r.channel.toString(),
                r.lastRssi.toString(),
                r.type,
                name,
                r.vendor,
                r.hitCount.toString(),
                r.peakRssi.toString(),
                r.avgRssi.toString(),
                gps?.latitude?.toString() ?: "",
                gps?.longitude?.toString() ?: "",
                gps?.accuracyM?.toString() ?: "",
                WardriveTime.formatUtc(r.firstSeenMs),
                WardriveTime.formatUtc(r.lastSeenMs),
            ).joinToString(",") { csvEscape(it) }
        }
        return rows.joinToString("\n")
    }

    /**
     * Wardrive Go–compatible CSV fields (MAC/SSID/AUTH/CHANNEL/RSSI/TYPE/NAME)
     * plus GPS + timing for fused sessions.
     */
    fun buildWardriveGoCsv(records: List<ObservationRecord>): String {
        val rows = mutableListOf(
            "MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME,LAT,LON,ALT,ACCURACY,FIRSTSEEN,LASTSEEN,HITCOUNT,PEAKRSSI,VENDOR,SOURCE",
        )
        records.forEach { r ->
            val ssid = if (r.type == "wifi") r.ssidOrName else ""
            val name = if (r.type == "ble") r.ssidOrName else ""
            val gps = r.lastGps
            rows += listOf(
                r.mac,
                ssid,
                r.security,
                r.channel.toString(),
                r.lastRssi.toString(),
                if (r.type == "ble") "bluetooth" else "wifi",
                name,
                gps?.latitude?.toString() ?: "",
                gps?.longitude?.toString() ?: "",
                gps?.altitudeM?.toInt()?.toString() ?: "",
                gps?.accuracyM?.toString() ?: "",
                WardriveTime.formatUtc(r.firstSeenMs),
                WardriveTime.formatUtc(r.lastSeenMs),
                r.hitCount.toString(),
                r.peakRssi.toString(),
                r.vendor,
                r.source,
            ).joinToString(",") { csvEscape(it) }
        }
        return rows.joinToString("\n")
    }

    data class WigleExportResult(val csv: String, val exportedRows: Int, val skippedNoGps: Int)

    /**
     * WiGLE Wifi 1.6-compatible CSV (WIFI + BLE rows).
     * Rows without a GPS fix are omitted (no 0.0/0.0 placeholders).
     */
    fun buildWigleCsv(records: List<ObservationRecord>): WigleExportResult {
        val pre = listOf(
            "WigleWifi-1.6",
            "appRelease=$FORMAT_VERSION",
            "model=${Build.MODEL}",
            "release=${Build.VERSION.RELEASE}",
            "device=${Build.DEVICE}",
            "display=${Build.DISPLAY}",
            "board=${Build.BOARD}",
            "brand=${Build.BRAND}",
        ).joinToString(",")
        val header = "MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type"
        val rows = mutableListOf(pre, header)
        var exported = 0
        var skipped = 0
        records.forEach { r ->
            val gps = r.lastGps
            if (gps == null || !isValidGps(gps.latitude, gps.longitude)) {
                skipped++
                return@forEach
            }
            val freq = when {
                r.frequencyMhz > 0 -> r.frequencyMhz
                r.type == "wifi" -> WardriveTime.channelToFrequencyMhz(r.channel)
                else -> 0
            }
            val auth = when {
                r.type == "wifi" && r.security.isNotEmpty() -> {
                    val s = r.security
                    if (s.startsWith("[")) s else "[$s]"
                }
                r.type == "ble" -> "Misc [LE]"
                else -> ""
            }
            val type = if (r.type == "ble") "BLE" else "WIFI"
            val ssid = r.ssidOrName
            rows += listOf(
                r.mac,
                ssid,
                auth,
                WardriveTime.formatUtc(r.firstSeenMs),
                if (r.type == "ble") "0" else r.channel.toString(),
                if (freq > 0) freq.toString() else "",
                r.peakRssi.toString(),
                gps.latitude.toString(),
                gps.longitude.toString(),
                gps.altitudeM?.toInt()?.toString() ?: "0",
                gps.accuracyM.toString(),
                "",
                "",
                type,
            ).joinToString(",") { csvEscape(it) }
            exported++
        }
        return WigleExportResult(rows.joinToString("\n"), exported, skipped)
    }

    data class GeoJsonExportResult(
        val geoJson: String,
        val observationFeatures: Int,
        val trackPoints: Int,
        val withoutCoords: Int,
    )

    /**
     * GeoJSON FeatureCollection:
     * - Point features for observations (lastGps, else nearest GPS-in-time)
     * - LineString for the session GPS track
     * - Meta documents count of observations that still lack any position
     */
    fun buildGeoJson(records: List<ObservationRecord>, gpsFixes: List<GpsFix>): GeoJsonExportResult {
        val features = JSONArray()
        var withCoords = 0
        var withoutCoords = 0
        records.forEach { r ->
            val resolved = resolvePosition(r, gpsFixes)
            if (resolved == null) {
                withoutCoords++
                return@forEach
            }
            val (gps, source) = resolved
            withCoords++
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", JSONArray().apply {
                                put(gps.longitude)
                                put(gps.latitude)
                                gps.altitudeM?.let { put(it) }
                            })
                        },
                    )
                    put(
                        "properties",
                        JSONObject().apply {
                            put("kind", "observation")
                            put("type", r.type)
                            put("mac", r.mac)
                            put("name", r.ssidOrName)
                            put("channel", r.channel)
                            put("security", r.security)
                            put("vendor", r.vendor)
                            put("rssi", r.lastRssi)
                            put("peakRssi", r.peakRssi)
                            put("avgRssi", r.avgRssi)
                            put("hitCount", r.hitCount)
                            put("firstSeen", WardriveTime.formatUtc(r.firstSeenMs))
                            put("lastSeen", WardriveTime.formatUtc(r.lastSeenMs))
                            put("accuracyM", gps.accuracyM.toDouble())
                            put("source", r.source)
                            put("positionSource", source)
                        },
                    )
                },
            )
        }

        if (gpsFixes.size >= 2) {
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "LineString")
                            put(
                                "coordinates",
                                JSONArray().apply {
                                    gpsFixes.forEach { g ->
                                        put(JSONArray().apply {
                                            put(g.longitude)
                                            put(g.latitude)
                                            g.altitudeM?.let { put(it) }
                                        })
                                    }
                                },
                            )
                        },
                    )
                    put(
                        "properties",
                        JSONObject().apply {
                            put("kind", "session_track")
                            put("pointCount", gpsFixes.size)
                        },
                    )
                },
            )
        }

        val geo = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
            put(
                "properties",
                JSONObject().apply {
                    put("generator", "wigglefish")
                    put("version", FORMAT_VERSION)
                    put("passiveOnly", true)
                    put("observationFeatures", withCoords)
                    put("observationsWithoutCoords", withoutCoords)
                    put("trackPoints", gpsFixes.size)
                    put(
                        "note",
                        "Observations without lastGps use nearest GPS-in-time when available; " +
                            "remaining withoutCoords are omitted from features (counted here).",
                    )
                },
            )
        }
        return GeoJsonExportResult(geo.toString(2), withCoords, gpsFixes.size, withoutCoords)
    }

    fun isValidGps(lat: Double, lon: Double): Boolean {
        if (lat == 0.0 && lon == 0.0) return false
        if (lat !in -90.0..90.0) return false
        if (lon !in -180.0..180.0) return false
        return true
    }

    /** Prefer lastGps; else nearest GPS fix by absolute time delta (best-effort). */
    fun resolvePosition(record: ObservationRecord, gpsFixes: List<GpsFix>): Pair<GpsFix, String>? {
        val last = record.lastGps
        if (last != null && isValidGps(last.latitude, last.longitude)) {
            return last to "lastGps"
        }
        if (gpsFixes.isEmpty()) return null
        val target = if (record.lastSeenMs > 0L) record.lastSeenMs else record.firstSeenMs
        var best: GpsFix? = null
        var bestDelta = Long.MAX_VALUE
        for (g in gpsFixes) {
            if (!isValidGps(g.latitude, g.longitude)) continue
            val d = abs(g.timestampMs - target)
            if (d < bestDelta) {
                bestDelta = d
                best = g
            }
        }
        return best?.let { it to "nearestGpsInTime" }
    }
}
