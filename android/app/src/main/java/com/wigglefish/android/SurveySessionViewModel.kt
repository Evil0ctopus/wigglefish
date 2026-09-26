package com.wigglefish.android

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.json.JSONObject

data class SurveyUiState(
    val status: String = "REAL SIGNALS // PASSIVE WARDRIVE // ESP32-C5",
    val wifiCount: Int = 0,
    val bleCount: Int = 0,
    val gpsSatCount: Int = 0,
    val gpsFixCount: Int = 0,
    val hasGpsFix: Boolean = false,
    val strongest: String = "SIGNAL LOCK  --   strongest observation: waiting",
    val location: String = "GPS LOCK  phone location optional",
    val source: String = "ESP32_SERIAL 0   PHONE 0   LEGACY_SERIAL 0",
    val device: String = "USB DEVICE  NO USB DEVICE",
    val session: String = "SESSION  00:00  Wi-Fi 0  BLE 0  GPS pts 0  hits 0  quality idle",
    val coverageHint: String = "COVERAGE  waiting for GPS + observations",
    val spectrum: String = "No channel activity yet",
    val viewLabel: String = "VIEW  ALL   /   0 live signals",
    val category: String = "CAMERAS  00     FLIPPER-LIKE  00     AIRTAG-LIKE  00\nOTHER WIFI  00     OTHER BLE  00     GPS SAT  00",
    val results: String = "Waiting for passive observations...",
    val decode: String = "",
    val signalCount: Int = 0,
    val phoneCollectionOn: Boolean = true,
    val usbCollectionOn: Boolean = true,
    val connectButtonLabel: String = "CONNECT USB",
    val selectedView: String = "ALL",
    val loadedSessionLabel: String = "",
    val observationsWithGps: Int = 0,
    val distanceMeters: Double = 0.0,
)

data class IdentifyUiState(
    val status: String = "Plug board via USB-C OTG (data cable). Then Connect → Identify → Flash.",
    val busy: Boolean = false,
    val chipFamily: String? = null,
    val profileId: String? = null,
    val resultText: String = (
        "Waiting for Identify…\n\n" +
            "Chip family comes from ROM probe (SYNC + GET_SECURITY_INFO / magic).\n" +
            "USB VID/PID alone cannot tell WT013261-S5 vs WT32C3-S5 vs WT018684-S5."
    ),
)

data class FlashUiState(
    val status: String = "",
    val busy: Boolean = false,
    val percent: Int = 0,
    val stageLabel: String = "IDLE",
    val message: String = "Idle",
    val logText: String = "",
)

/**
 * Activity-scoped fused wardrive session: Wi-Fi + BLE + GPS on one timeline.
 * Shared by Home / Connect / Survey / Exports fragments.
 */
class SurveySessionViewModel : ViewModel() {
    private val observations = linkedMapOf<String, ObservationRecord>()
    private val wifiChannels = linkedMapOf<Int, Int>()
    private val gpsFixes = mutableListOf<GpsFix>()
    private val sourceCounts = linkedMapOf<String, Int>()

    private var sessionStartedAt = System.currentTimeMillis()
    private var sessionEndedAt: Long? = null
    private var loadedSessionLabel: String = ""
    private var frozenSummary: SessionSummary? = null
    private var scanPasses = 0
    private var lastObservationAt = 0L
    private var selectedView = "ALL"
    private var satelliteCount = 0
    private var phoneCollectionEnabled = true
    private var usbCollectionEnabled = true
    private var connectedUsbLabel = ""
    private var statusText = "REAL SIGNALS // PASSIVE WARDRIVE // ESP32-C5"
    private var locationText = "GPS LOCK  phone location optional"
    private var deviceText = "USB DEVICE  NO USB DEVICE"
    private var connectButtonLabel = "CONNECT USB"
    private var decodeText = ""
    private var currentGps: GpsFix? = null

    private val _ui = MutableLiveData(SurveyUiState())
    val ui: LiveData<SurveyUiState> = _ui

    private val _identify = MutableLiveData(IdentifyUiState())
    val identify: LiveData<IdentifyUiState> = _identify

    private val _flash = MutableLiveData(FlashUiState())
    val flash: LiveData<FlashUiState> = _flash

    fun snapshotObservations(): List<ObservationRecord> = observations.values.toList()

    fun snapshotRawRecords(): List<JSONObject> = observations.values.map { it.toJson() }

    fun snapshotGpsFixes(): List<GpsFix> = gpsFixes.toList()

    fun recordCount(): Int = observations.size

    fun currentGpsFix(): GpsFix? = currentGps

    fun sessionSummary(): SessionSummary {
        frozenSummary?.let { return it }
        val end = sessionEndedAt ?: System.currentTimeMillis()
        val duration = (end - sessionStartedAt).coerceAtLeast(0L)
        val uniqueWifi = observations.values.count { it.type == "wifi" }
        val uniqueBle = observations.values.count { it.type == "ble" }
        val hits = observations.values.sumOf { it.hitCount }
        val gpsCount = gpsFixes.size
        val hasFix = currentGps != null || gpsCount > 0
        val withGps = observations.values.count { it.lastGps != null }
        val withoutGps = observations.size - withGps
        val minutes = (duration / 60000.0).coerceAtLeast(0.05)
        val dist = trackDistanceMeters()
        val quality = qualityLabel(duration, uniqueWifi, uniqueBle, gpsCount, hasFix)
        return SessionSummary(
            durationMs = duration,
            uniqueWifi = uniqueWifi,
            uniqueBle = uniqueBle,
            gpsPointCount = gpsCount,
            observationHits = hits,
            qualityLabel = quality,
            hasGpsFix = hasFix,
            observationsWithGps = withGps,
            observationsWithoutGps = withoutGps,
            distanceMeters = dist,
            gpsFixRatePerMin = gpsCount / minutes,
            channelHistogram = wifiChannels.toMap(),
        )
    }

    fun trackDistanceMeters(): Double {
        if (gpsFixes.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until gpsFixes.size) {
            sum += distanceRoughMeters(gpsFixes[i - 1], gpsFixes[i])
        }
        return sum
    }

    fun mapMarkers(): List<ObservationRecord> =
        observations.values.filter { it.lastGps != null }

    fun replaceSession(
        records: List<ObservationRecord>,
        summary: SessionSummary,
        fixes: List<GpsFix>,
        label: String,
    ) {
        observations.clear()
        records.forEach { observations[it.key] = it }
        wifiChannels.clear()
        summary.channelHistogram.forEach { (ch, hits) -> wifiChannels[ch] = hits }
        if (wifiChannels.isEmpty()) {
            records.filter { it.type == "wifi" && it.channel > 0 }.forEach { r ->
                wifiChannels[r.channel] = (wifiChannels[r.channel] ?: 0) + r.hitCount
            }
        }
        gpsFixes.clear()
        gpsFixes.addAll(fixes)
        currentGps = fixes.lastOrNull()
        sessionStartedAt = System.currentTimeMillis() - summary.durationMs
        sessionEndedAt = System.currentTimeMillis()
        frozenSummary = summary
        loadedSessionLabel = label
        decodeText = "Loaded archived session: $label"
        publish()
    }

    fun beginNewLiveSession() {
        frozenSummary = null
        sessionEndedAt = null
        loadedSessionLabel = ""
        sessionStartedAt = System.currentTimeMillis()
        publish()
    }

    fun markSessionEnded() {
        if (sessionEndedAt == null) {
            sessionEndedAt = System.currentTimeMillis()
            frozenSummary = null
        }
        publish()
    }

    fun isPhoneCollectionEnabled(): Boolean = phoneCollectionEnabled

    fun isUsbCollectionEnabled(): Boolean = usbCollectionEnabled

    fun connectedUsbLabel(): String = connectedUsbLabel

    fun setStatus(status: String) {
        statusText = status
        if (status.contains("USB connection lost", true)) {
            deviceText = "USB DEVICE  DISCONNECTED"
            connectedUsbLabel = ""
            connectButtonLabel = "CONNECT USB"
        }
        publish()
    }

    fun setLocationText(text: String) {
        locationText = text
        publish()
    }

    fun setSatelliteCount(count: Int) {
        satelliteCount = count
        publish()
    }

    fun updateGpsFix(fix: GpsFix) {
        if (frozenSummary != null) {
            frozenSummary = null
            sessionEndedAt = null
            loadedSessionLabel = ""
        }
        currentGps = fix
        // Deduplicate near-identical consecutive fixes (time + position).
        val last = gpsFixes.lastOrNull()
        val shouldAppend = last == null ||
            fix.timestampMs - last.timestampMs >= 2000L ||
            distanceRoughMeters(last, fix) >= 2.0
        if (shouldAppend) {
            gpsFixes += fix
            // Cap timeline memory for long drives.
            if (gpsFixes.size > 5000) {
                gpsFixes.removeAt(0)
            }
        }
        publish()
    }

    fun setDeviceText(text: String) {
        deviceText = text
        publish()
    }

    fun setConnectedUsb(label: String, connected: Boolean) {
        connectedUsbLabel = label
        deviceText = if (connected) "USB DEVICE  $label" else textOrDisconnected(label)
        connectButtonLabel = if (connected) "CONNECTED" else "CONNECT USB"
        publish()
    }

    private fun textOrDisconnected(label: String): String =
        if (label.isEmpty()) "USB DEVICE  NO USB DEVICE" else "USB DEVICE  $label"

    fun setPhoneCollectionEnabled(enabled: Boolean) {
        phoneCollectionEnabled = enabled
        publish()
    }

    fun setUsbCollectionEnabled(enabled: Boolean) {
        usbCollectionEnabled = enabled
        if (!enabled) {
            deviceText = "USB DEVICE  COLLECTION OFF"
            connectButtonLabel = "CONNECT USB"
        }
        publish()
    }

    fun setSelectedView(view: String) {
        selectedView = view
        publish()
    }

    fun setDecodeText(text: String) {
        decodeText = text
        publish()
    }

    fun noteScanPass() {
        scanPasses += 1
        publish()
    }

    fun noteSource(source: String) {
        sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
    }

    fun ingestWifi(message: JSONObject): String? {
        if (frozenSummary != null) {
            frozenSummary = null
            sessionEndedAt = null
            loadedSessionLabel = ""
        }
        val ssid = message.optString("ssid").ifEmpty { "<hidden>" }
        val bssid = message.optString("bssid").ifEmpty { message.optString("mac") }
        val channel = message.optInt("channel")
        val key = bssid.ifEmpty { "wifi:$ssid:$channel" }
        val security = message.optString("security").ifEmpty {
            message.optString("encryption").ifEmpty { message.optString("capabilities") }
        }
        val frequency = message.optInt("frequency", 0)
        val rssi = message.optInt("rssi")
        val source = message.optString("source", "")
        val now = System.currentTimeMillis()
        val gps = currentGps
        if (channel > 0) {
            wifiChannels[channel] = (wifiChannels[channel] ?: 0) + 1
        }
        lastObservationAt = now
        val existing = observations[key]
        val isNew = existing == null
        if (existing == null) {
            observations[key] = ObservationRecord.newWifi(
                key = key,
                mac = bssid,
                ssid = ssid,
                channel = channel,
                frequencyMhz = frequency,
                security = security,
                rssi = rssi,
                source = source,
                nowMs = now,
                gps = gps,
            )
        } else {
            existing.update(
                rssi = rssi,
                ssidOrName = ssid,
                channel = channel,
                frequencyMhz = frequency,
                security = security,
                source = source,
                nowMs = now,
                gps = gps,
            )
        }
        publish()
        return if (isNew && ssid != "<hidden>") ssid else null
    }

    fun ingestBle(message: JSONObject): String? {
        if (frozenSummary != null) {
            frozenSummary = null
            sessionEndedAt = null
            loadedSessionLabel = ""
        }
        val address = message.optString("address").ifEmpty { message.optString("mac") }
        val name = message.optString("name").ifEmpty { address }
        val key = address.ifEmpty { "ble:$name" }
        val rssi = message.optInt("rssi")
        val source = message.optString("source", "")
        val now = System.currentTimeMillis()
        val gps = currentGps
        lastObservationAt = now
        val existing = observations[key]
        val isNew = existing == null
        if (existing == null) {
            observations[key] = ObservationRecord.newBle(
                key = key,
                mac = address,
                name = name,
                rssi = rssi,
                source = source,
                nowMs = now,
                gps = gps,
            )
        } else {
            existing.update(
                rssi = rssi,
                ssidOrName = name,
                channel = 0,
                frequencyMhz = 0,
                security = "",
                source = source,
                nowMs = now,
                gps = gps,
            )
        }
        publish()
        return if (isNew) name.ifEmpty { address } else null
    }

    fun setIdentifyBusy(busy: Boolean, status: String? = null) {
        val current = _identify.value ?: IdentifyUiState()
        _identify.value = current.copy(
            busy = busy,
            status = status ?: current.status,
        )
    }

    fun setIdentifyResult(
        status: String,
        resultText: String,
        chipFamily: String? = null,
        profileId: String? = null,
    ) {
        _identify.value = IdentifyUiState(
            status = status,
            busy = false,
            chipFamily = chipFamily,
            profileId = profileId,
            resultText = resultText,
        )
    }

    fun setFlashBusy(busy: Boolean, status: String? = null, message: String? = null) {
        val current = _flash.value ?: FlashUiState()
        _flash.value = current.copy(
            busy = busy,
            status = status ?: current.status,
            message = message ?: current.message,
            stageLabel = if (busy) current.stageLabel.ifBlank { "…" } else current.stageLabel,
        )
    }

    fun setFlashProgress(
        stageLabel: String,
        percent: Int,
        message: String,
        status: String? = null,
    ) {
        val current = _flash.value ?: FlashUiState()
        _flash.value = current.copy(
            busy = true,
            stageLabel = stageLabel,
            percent = percent.coerceIn(0, 100),
            message = message,
            status = status ?: current.status,
        )
    }

    fun setFlashResult(status: String, message: String, logText: String, success: Boolean) {
        _flash.value = FlashUiState(
            status = status,
            busy = false,
            percent = if (success) 100 else (_flash.value?.percent ?: 0),
            stageLabel = if (success) "DONE" else "FAILED",
            message = message,
            logText = logText,
        )
    }

    fun clearObservations() {
        observations.clear()
        wifiChannels.clear()
        gpsFixes.clear()
        sourceCounts.clear()
        scanPasses = 0
        lastObservationAt = 0L
        decodeText = ""
        currentGps = null
        sessionStartedAt = System.currentTimeMillis()
        sessionEndedAt = null
        frozenSummary = null
        loadedSessionLabel = ""
        publish()
    }

    private fun publish() {
        val summary = sessionSummary()
        val spectrum = if (wifiChannels.isEmpty()) {
            "No channel activity yet"
        } else {
            wifiChannels.entries.sortedBy { it.key }.joinToString("\n") { (channel, hits) ->
                val bar = "#".repeat(hits.coerceAtMost(12))
                "CH %-3d %-12s %d hits".format(channel, bar, hits)
            }
        }
        val records = when (selectedView) {
            "WIFI" -> observations.values.filter { it.type == "wifi" }
            "BLE" -> observations.values.filter { it.type == "ble" }
            else -> observations.values.toList()
        }.sortedByDescending { it.lastRssi }
        val coverage = coverageHint(summary)
        _ui.value = SurveyUiState(
            status = statusText,
            wifiCount = summary.uniqueWifi,
            bleCount = summary.uniqueBle,
            gpsSatCount = satelliteCount,
            gpsFixCount = summary.gpsPointCount,
            hasGpsFix = summary.hasGpsFix,
            strongest = strongestSignalSummary(),
            location = locationText,
            source = sourceCounts.entries.joinToString("   ") { "${it.key} ${it.value}" }
                .ifEmpty { "ESP32_SERIAL 0   PHONE 0   LEGACY_SERIAL 0" },
            device = deviceText,
            session = summary.formatLine() + if (scanPasses > 0) "  passes=$scanPasses" else "",
            coverageHint = coverage,
            spectrum = spectrum,
            viewLabel = "VIEW  $selectedView   /   ${observations.size} unique  (${summary.observationHits} hits)",
            category = categorySummary(),
            results = if (records.isEmpty()) {
                "Waiting for passive observations..."
            } else {
                records.joinToString("\n") { it.displayLine() }
            },
            decode = decodeText,
            signalCount = observations.size,
            phoneCollectionOn = phoneCollectionEnabled,
            usbCollectionOn = usbCollectionEnabled,
            connectButtonLabel = connectButtonLabel,
            selectedView = selectedView,
            loadedSessionLabel = loadedSessionLabel,
            observationsWithGps = summary.observationsWithGps,
            distanceMeters = summary.distanceMeters,
        )
    }

    private fun strongestSignalSummary(): String {
        val strongest = observations.values.maxByOrNull { it.lastRssi }
            ?: return "SIGNAL LOCK  --   strongest observation: waiting"
        return "SIGNAL LOCK  ${strongest.type.uppercase(Locale.US)}   ${strongest.lastRssi} dBm   ${strongest.ssidOrName}"
    }

    private fun categorySummary(): String {
        var cameras = 0
        var flipper = 0
        var airtags = 0
        var otherWifi = 0
        var otherBle = 0
        observations.values.forEach { record ->
            val text = "${record.ssidOrName} ${record.vendor} ${record.mac}".lowercase(Locale.US)
            when {
                text.contains("airtag") || text.contains("find my") -> airtags++
                text.contains("flipper") || text.contains("badusb") -> flipper++
                text.contains("camera") || text.contains("cam") || text.contains("hikvision") || text.contains("ring") -> cameras++
                record.type == "wifi" -> otherWifi++
                else -> otherBle++
            }
        }
        return "CAMERAS  %02d     FLIPPER-LIKE  %02d     AIRTAG-LIKE  %02d\nOTHER WIFI  %02d     OTHER BLE  %02d     GPS SAT  %02d".format(
            cameras, flipper, airtags, otherWifi, otherBle, satelliteCount,
        )
    }

    private fun qualityLabel(
        durationMs: Long,
        uniqueWifi: Int,
        uniqueBle: Int,
        gpsCount: Int,
        hasFix: Boolean,
    ): String {
        if (uniqueWifi + uniqueBle == 0) return "idle"
        val minutes = (durationMs / 60000.0).coerceAtLeast(0.1)
        val uniquesPerMin = (uniqueWifi + uniqueBle) / minutes
        val gpsRate = gpsCount / minutes
        return when {
            hasFix && uniquesPerMin >= 8 && gpsRate >= 4 -> "dense"
            hasFix && uniquesPerMin >= 3 && gpsRate >= 1 -> "good"
            hasFix || uniquesPerMin >= 1 -> "fair"
            else -> "sparse"
        }
    }

    private fun coverageHint(summary: SessionSummary): String = summary.formatCoverageDetail()

    private fun distanceRoughMeters(a: GpsFix, b: GpsFix): Double {
        val latMid = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val metersPerLat = 111_320.0
        val metersPerLon = 111_320.0 * Math.cos(latMid)
        val dy = dLat * metersPerLat
        val dx = dLon * metersPerLon
        return Math.sqrt(dx * dx + dy * dy)
    }
}
