package com.wigglefish.android

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.json.JSONObject

data class SurveyUiState(
    val status: String = "REAL SIGNALS // PASSIVE RECON // ESP32-C5",
    val wifiCount: Int = 0,
    val bleCount: Int = 0,
    val gpsSatCount: Int = 0,
    val strongest: String = "SIGNAL LOCK  --   strongest observation: waiting",
    val location: String = "GPS LOCK  phone location optional",
    val source: String = "ESP32_SERIAL 0   PHONE 0   LEGACY_SERIAL 0",
    val device: String = "USB DEVICE  NO USB DEVICE",
    val session: String = "SESSION  00 passes   0 records   waiting",
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
 * Activity-scoped session state shared by Home / Connect / Live Field / Exports fragments.
 */
class SurveySessionViewModel : ViewModel() {
    private val wifiRecords = linkedMapOf<String, String>()
    private val bleRecords = linkedMapOf<String, String>()
    private val wifiChannels = linkedMapOf<Int, Int>()
    private val rawRecords = linkedMapOf<String, JSONObject>()
    private val sourceCounts = linkedMapOf<String, Int>()

    private var scanPasses = 0
    private var lastObservationAt = 0L
    private var selectedView = "ALL"
    private var satelliteCount = 0
    private var phoneCollectionEnabled = true
    private var usbCollectionEnabled = true
    private var connectedUsbLabel = ""
    private var statusText = "REAL SIGNALS // PASSIVE RECON // ESP32-C5"
    private var locationText = "GPS LOCK  phone location optional"
    private var deviceText = "USB DEVICE  NO USB DEVICE"
    private var connectButtonLabel = "CONNECT USB"
    private var decodeText = ""

    private val _ui = MutableLiveData(SurveyUiState())
    val ui: LiveData<SurveyUiState> = _ui

    private val _identify = MutableLiveData(IdentifyUiState())
    val identify: LiveData<IdentifyUiState> = _identify

    private val _flash = MutableLiveData(FlashUiState())
    val flash: LiveData<FlashUiState> = _flash

    fun snapshotRawRecords(): List<JSONObject> = rawRecords.values.map { JSONObject(it.toString()) }

    fun recordCount(): Int = rawRecords.size

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
        val ssid = message.optString("ssid").ifEmpty { "<hidden>" }
        val bssid = message.optString("bssid")
        val key = bssid.ifEmpty { "$ssid:${message.optInt("channel")}" }
        val channel = message.optInt("channel")
        wifiChannels[channel] = (wifiChannels[channel] ?: 0) + 1
        rawRecords[key] = JSONObject(message.toString())
        lastObservationAt = System.currentTimeMillis()
        val formatted = "WIFI   %4d dBm   ch %-2d   %-24s  %s".format(
            message.optInt("rssi"),
            message.optInt("channel"),
            ssid,
            message.optString("security", "OPEN"),
        )
        val isNew = !wifiRecords.containsKey(key)
        wifiRecords[key] = formatted
        publish()
        return if (isNew && ssid != "<hidden>") ssid else null
    }

    fun ingestBle(message: JSONObject): String? {
        val address = message.optString("address").ifEmpty { message.optString("mac") }
        val name = message.optString("name").ifEmpty { address }
        val isNew = !bleRecords.containsKey(address)
        rawRecords[address] = JSONObject(message.toString())
        lastObservationAt = System.currentTimeMillis()
        bleRecords[address] = "BLE    %4d dBm   %-24s  %s".format(
            message.optInt("rssi"),
            name,
            address,
        )
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
        wifiRecords.clear()
        bleRecords.clear()
        wifiChannels.clear()
        rawRecords.clear()
        sourceCounts.clear()
        scanPasses = 0
        lastObservationAt = 0L
        decodeText = ""
        publish()
    }

    private fun publish() {
        val lastSeen = if (lastObservationAt == 0L) "waiting" else "live"
        val spectrum = if (wifiChannels.isEmpty()) {
            "No channel activity yet"
        } else {
            wifiChannels.entries.sortedBy { it.key }.joinToString("\n") { (channel, hits) ->
                val bar = "#".repeat(hits.coerceAtMost(12))
                "CH %-3d %-12s %d AP".format(channel, bar, hits)
            }
        }
        val records = when (selectedView) {
            "WIFI" -> wifiRecords.values
            "BLE" -> bleRecords.values
            else -> wifiRecords.values + bleRecords.values
        }.sorted()
        _ui.value = SurveyUiState(
            status = statusText,
            wifiCount = wifiRecords.size,
            bleCount = bleRecords.size,
            gpsSatCount = satelliteCount,
            strongest = strongestSignalSummary(),
            location = locationText,
            source = sourceCounts.entries.joinToString("   ") { "${it.key} ${it.value}" }
                .ifEmpty { "ESP32_SERIAL 0   PHONE 0   LEGACY_SERIAL 0" },
            device = deviceText,
            session = "SESSION  %02d passes   %d records   %s".format(scanPasses, rawRecords.size, lastSeen),
            spectrum = spectrum,
            viewLabel = "VIEW  $selectedView   /   ${rawRecords.size} live signals",
            category = categorySummary(),
            results = if (records.isEmpty()) {
                "Waiting for passive observations..."
            } else {
                records.joinToString("\n")
            },
            decode = decodeText,
            signalCount = rawRecords.size,
            phoneCollectionOn = phoneCollectionEnabled,
            usbCollectionOn = usbCollectionEnabled,
            connectButtonLabel = connectButtonLabel,
            selectedView = selectedView,
        )
    }

    private fun strongestSignalSummary(): String {
        val strongest = rawRecords.values.maxByOrNull { it.optInt("rssi", -127) }
            ?: return "SIGNAL LOCK  --   strongest observation: waiting"
        val type = strongest.optString("type").uppercase(Locale.US)
        val name = strongest.optString("ssid").ifEmpty {
            strongest.optString("name").ifEmpty { strongest.optString("mac") }
        }
        return "SIGNAL LOCK  $type   ${strongest.optInt("rssi")} dBm   $name"
    }

    private fun categorySummary(): String {
        var cameras = 0
        var flipper = 0
        var airtags = 0
        var otherWifi = 0
        var otherBle = 0
        rawRecords.values.forEach { record ->
            val text = record.toString().lowercase(Locale.US)
            val type = record.optString("type")
            when {
                text.contains("airtag") || text.contains("find my") || text.contains("0x004c") -> airtags++
                text.contains("flipper") || text.contains("badusb") -> flipper++
                text.contains("camera") || text.contains("cam") || text.contains("hikvision") || text.contains("ring") -> cameras++
                type == "wifi" -> otherWifi++
                else -> otherBle++
            }
        }
        return "CAMERAS  %02d     FLIPPER-LIKE  %02d     AIRTAG-LIKE  %02d\nOTHER WIFI  %02d     OTHER BLE  %02d     GPS SAT  %02d".format(
            cameras, flipper, airtags, otherWifi, otherBle, satelliteCount,
        )
    }
}
