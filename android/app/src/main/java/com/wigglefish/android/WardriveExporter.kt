package com.wigglefish.android

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Passive wardrive export formatters: JSON, CSV, Wardrive Go CSV, WiGLE 1.6 CSV, GeoJSON.
 * Metadata-only — no handshake / PCAP / attack payloads.
 */
object WardriveExporter {
    fun csvEscape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    fun buildJson(records: List<ObservationRecord>, summary: SessionSummary, gpsFixes: List<GpsFix>): String {
        val root = JSONObject().apply {
            put("format", "wigglefish-wardrive-json")
            put("version", "0.3.0")
            put("passiveOnly", true)
            put("summary", JSONObject().apply {
                put("durationMs", summary.durationMs)
                put("uniqueWifi", summary.uniqueWifi)
                put("uniqueBle", summary.uniqueBle)
                put("gpsPointCount", summary.gpsPointCount)
                put("observationHits", summary.observationHits)
                put("qualityLabel", summary.qualityLabel)
                put("hasGpsFix", summary.hasGpsFix)
            })
            put("observations", JSONArray().apply {
                records.forEach { put(it.toJson()) }
            })
            put("gpsTimeline", JSONArray().apply {
                gpsFixes.forEach { put(it.toJson()) }
            })
        }
        return root.toString(2)
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

    /** WiGLE Wifi 1.6-compatible CSV (WIFI + BLE rows). */
    fun buildWigleCsv(records: List<ObservationRecord>): String {
        val pre = listOf(
            "WigleWifi-1.6",
            "appRelease=0.3.0",
            "model=${Build.MODEL}",
            "release=${Build.VERSION.RELEASE}",
            "device=${Build.DEVICE}",
            "display=${Build.DISPLAY}",
            "board=${Build.BOARD}",
            "brand=${Build.BRAND}",
        ).joinToString(",")
        val header = "MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type"
        val rows = mutableListOf(pre, header)
        records.forEach { r ->
            val gps = r.lastGps
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
            val ssid = if (r.type == "wifi") r.ssidOrName else r.ssidOrName
            rows += listOf(
                r.mac,
                ssid,
                auth,
                WardriveTime.formatUtc(r.firstSeenMs),
                if (r.type == "ble") "0" else r.channel.toString(),
                if (freq > 0) freq.toString() else "",
                r.peakRssi.toString(),
                gps?.latitude?.toString() ?: "0.0",
                gps?.longitude?.toString() ?: "0.0",
                gps?.altitudeM?.toInt()?.toString() ?: "0",
                gps?.accuracyM?.toString() ?: "0.0",
                "",
                "",
                type,
            ).joinToString(",") { csvEscape(it) }
        }
        return rows.joinToString("\n")
    }

    fun buildGeoJson(records: List<ObservationRecord>, gpsFixes: List<GpsFix>): String {
        val features = JSONArray()
        records.forEach { r ->
            val gps = r.lastGps ?: return@forEach
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
                        },
                    )
                },
            )
        }
        gpsFixes.forEach { g ->
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", JSONArray().apply {
                                put(g.longitude)
                                put(g.latitude)
                                g.altitudeM?.let { put(it) }
                            })
                        },
                    )
                    put(
                        "properties",
                        JSONObject().apply {
                            put("kind", "gps_fix")
                            put("timestamp", WardriveTime.formatUtc(g.timestampMs))
                            put("accuracyM", g.accuracyM.toDouble())
                            put("provider", g.provider)
                        },
                    )
                },
            )
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
            put(
                "properties",
                JSONObject().apply {
                    put("generator", "wigglefish")
                    put("version", "0.3.0")
                    put("passiveOnly", true)
                },
            )
        }.toString(2)
    }
}
