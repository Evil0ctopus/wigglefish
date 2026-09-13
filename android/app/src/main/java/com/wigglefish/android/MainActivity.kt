package com.wigglefish.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.hardware.usb.UsbManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GnssStatus
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val permissionAction = "com.wigglefish.android.USB_PERMISSION"
    }

    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var bleCountText: TextView
    private lateinit var gpsCountText: TextView
    private lateinit var spectrumText: TextView
    private lateinit var resultsText: TextView
    private lateinit var sessionText: TextView
    private lateinit var viewText: TextView
    private lateinit var categoryText: TextView
    private lateinit var radarView: SignalRadarView
    private lateinit var decodeText: TextView
    private lateinit var strongestText: TextView
    private lateinit var locationText: TextView
    private lateinit var sourceText: TextView
    private lateinit var deviceText: TextView
    private lateinit var phoneCollectionButton: Button
    private lateinit var usbCollectionButton: Button
    private lateinit var connectButton: Button
    private lateinit var usbManager: UsbManager
    private lateinit var serial: UsbSerialController
    private lateinit var locationManager: LocationManager
    private lateinit var wifiManager: WifiManager
    private lateinit var bluetoothManager: BluetoothManager
    private var phoneBleScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var satelliteCount = 0
    private val wifiRecords = linkedMapOf<String, String>()
    private val bleRecords = linkedMapOf<String, String>()
    private val wifiChannels = linkedMapOf<Int, Int>()
    private val rawRecords = linkedMapOf<String, JSONObject>()
    private var scanPasses = 0
    private var lastObservationAt = 0L
    private var selectedView = "ALL"
    private var lastLocation: Location? = null
    private var lastDecodedSatelliteCount = -1
    private var phoneCollectionEnabled = true
    private var usbCollectionEnabled = true
    private val sourceCounts = linkedMapOf<String, Int>()
    private val sessionLogFile by lazy { File(filesDir, "wigglefish-session.jsonl") }
    private val usbLogFile by lazy { File(filesDir, "wigglefish-usb-devices.jsonl") }
    private var connectedUsbLabel = ""
    private val uiHandler = Handler(Looper.getMainLooper())
    private val decodeQueue = ArrayDeque<DecodeJob>()
    private var decodeRunning = false

    private data class DecodeJob(val label: String)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != permissionAction) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                connect(device)
            } else {
                setStatus("USB permission was denied")
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            locationText.text = "GPS LOCK  %.5f, %.5f   +/- %.0fm".format(
                location.latitude, location.longitude, location.accuracy,
            )
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
            updateLocationText()
            if (satelliteCount > 0 && satelliteCount != lastDecodedSatelliteCount) {
                lastDecodedSatelliteCount = satelliteCount
                animateDecodeLabel("GPS SATELLITES")
            }
            renderNetworks()
        }
    }

    private val phoneWifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            for (result in wifiManager.scanResults) {
                val message = JSONObject().apply {
                    put("type", "wifi")
                    put("source", "PHONE")
                    put("ssid", result.SSID)
                    put("bssid", result.BSSID)
                    put("channel", frequencyToChannel(result.frequency))
                    put("rssi", result.level)
                    put("security", "PHONE_SCAN")
                }
                handleLine(message.toString())
            }
        }
    }

    private val phoneBleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val message = JSONObject().apply {
                put("type", "bluetooth")
                put("source", "PHONE")
                put("address", result.device.address)
                put("name", result.device.name ?: "")
                put("rssi", result.rssi)
            }
            handleLine(message.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        countText = findViewById(R.id.countText)
        bleCountText = findViewById(R.id.bleCountText)
        gpsCountText = findViewById(R.id.gpsCountText)
        spectrumText = findViewById(R.id.spectrumText)
        resultsText = findViewById(R.id.resultsText)
        sessionText = findViewById(R.id.sessionText)
        viewText = findViewById(R.id.viewText)
        categoryText = findViewById(R.id.categoryText)
        radarView = findViewById(R.id.radarView)
        decodeText = findViewById(R.id.decodeText)
        strongestText = findViewById(R.id.strongestText)
        locationText = findViewById(R.id.locationText)
        sourceText = findViewById(R.id.sourceText)
        deviceText = findViewById(R.id.deviceText)
        phoneCollectionButton = findViewById(R.id.phoneCollectionButton)
        usbCollectionButton = findViewById(R.id.usbCollectionButton)
        connectButton = findViewById(R.id.connectButton)
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        serial = UsbSerialController(usbManager, ::handleLine, ::setStatus)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, IntentFilter(permissionAction))
        }
        connectButton.setOnClickListener { requestConnection() }
        phoneCollectionButton.setOnClickListener { togglePhoneCollection() }
        usbCollectionButton.setOnClickListener { toggleUsbCollection() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { shareSession() }
        findViewById<Button>(R.id.csvButton).setOnClickListener { shareCsv() }
        findViewById<Button>(R.id.allButton).setOnClickListener { selectedView = "ALL"; renderNetworks() }
        findViewById<Button>(R.id.wifiButton).setOnClickListener { selectedView = "WIFI"; renderNetworks() }
        findViewById<Button>(R.id.bleButton).setOnClickListener { selectedView = "BLE"; renderNetworks() }
        findViewById<Button>(R.id.clearButton).setOnClickListener {
            wifiRecords.clear()
            bleRecords.clear()
            wifiChannels.clear()
            rawRecords.clear()
            sourceCounts.clear()
            sessionLogFile.delete()
            usbLogFile.delete()
            decodeQueue.clear()
            decodeRunning = false
            scanPasses = 0
            lastObservationAt = 0L
            decodeText.text = ""
            renderNetworks()
        }
        requestConnection()
        startPhoneLocation()
        startPhoneWireless()
    }

    override fun onDestroy() {
        serial.disconnect()
        stopPhoneLocation()
        stopPhoneWireless()
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    private fun requestConnection() {
        val device = serial.findDevice()
        if (device == null) {
            deviceText.text = "USB DEVICE  NO USB DEVICE"
            setStatus("ESP32-C5 / CH343 not found. Connect it with USB OTG.")
            return
        }
        if (usbManager.hasPermission(device)) {
            connect(device)
            return
        }
        val permission = PendingIntent.getBroadcast(
            this,
            0,
            Intent(permissionAction).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, permission)
        setStatus("Waiting for USB permission...")
    }

    private fun connect(device: android.hardware.usb.UsbDevice) {
        if (!usbCollectionEnabled) return
        if (serial.connect(device)) {
            connectButton.text = "CONNECTED"
            connectedUsbLabel = "${UsbSerialController.friendlyName(device)} VID %04X PID %04X".format(device.vendorId, device.productId)
            deviceText.text = "USB DEVICE  $connectedUsbLabel"
            logUsbEvent("connected")
        }
    }

    private fun togglePhoneCollection() {
        phoneCollectionEnabled = !phoneCollectionEnabled
        if (phoneCollectionEnabled) {
            startPhoneLocation()
            startPhoneWireless()
        } else {
            stopPhoneLocation()
            stopPhoneWireless()
            phoneCollectionButton.text = "PHONE COLLECTION OFF"
        }
        updateCollectionButtons()
    }

    private fun toggleUsbCollection() {
        usbCollectionEnabled = !usbCollectionEnabled
        if (usbCollectionEnabled) {
            requestConnection()
        } else {
            serial.disconnect()
            deviceText.text = "USB DEVICE  COLLECTION OFF"
            usbCollectionButton.text = "USB COLLECTION OFF"
        }
        updateCollectionButtons()
    }

    private fun updateCollectionButtons() {
        phoneCollectionButton.text = if (phoneCollectionEnabled) "PHONE COLLECTION ON" else "PHONE COLLECTION OFF"
        usbCollectionButton.text = if (usbCollectionEnabled) "USB COLLECTION ON" else "USB COLLECTION OFF"
    }

    private fun startPhoneLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.NEARBY_WIFI_DEVICES,
                ),
                42,
            )
            locationText.text = "GPS LOCK  permission required"
            return
        }
        try {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(mainLooper))
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 2000L, 2f, locationListener, mainLooper)
                }
            }
            locationText.text = "GPS LOCK  searching phone location..."
        } catch (_: SecurityException) {
            locationText.text = "GPS LOCK  unavailable"
        }
    }

    private fun stopPhoneLocation() {
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            startPhoneLocation()
            startPhoneWireless()
        }
    }

    private fun startPhoneWireless() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            registerReceiver(phoneWifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            try {
                wifiManager.startScan()
            } catch (_: SecurityException) {
                setStatus("Phone Wi-Fi scan unavailable; ESP32-C5 scan still active")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            phoneBleScanner = bluetoothManager.adapter?.bluetoothLeScanner
            phoneBleScanner?.startScan(phoneBleCallback)
        }
    }

    private fun stopPhoneWireless() {
        try { unregisterReceiver(phoneWifiReceiver) } catch (_: IllegalArgumentException) { }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            phoneBleScanner?.stopScan(phoneBleCallback)
        }
    }

    private fun updateLocationText() {
        val location = lastLocation
        locationText.text = if (location == null) {
            "GPS LOCK  $satelliteCount satellites used   searching"
        } else {
            "GPS LOCK  $satelliteCount satellites used   %.5f, %.5f   +/- %.0fm".format(
                location.latitude, location.longitude, location.accuracy,
            )
        }
    }

    private fun frequencyToChannel(frequency: Int): Int = when {
        frequency in 2412..2484 -> (frequency - 2407) / 5
        frequency in 5000..5900 -> (frequency - 5000) / 5
        else -> 0
    }

    private fun handleLine(line: String) {
        runOnUiThread {
            try {
                val message = JSONObject(line)
                if (message.has("type") && !message.has("source")) message.put("source", "ESP32_SERIAL")
                if (message.has("type")) logObservation(message)
                when (message.optString("event")) {
                    "ready" -> setStatus("Ready. Passive survey is running automatically.")
                    "scan_start" -> {
                        scanPasses += 1
                        setStatus("Scanning nearby Wi-Fi signals...")
                    }
                    "scan_end" -> setStatus("Survey updated")
                    "scan_count" -> setStatus("Live survey running")
                    "error" -> setStatus("Device error: ${message.optString("message", "unknown")}")
                }
                when (message.optString("type")) {
                    "wifi" -> {
                        val ssid = message.optString("ssid").ifEmpty { "<hidden>" }
                        val bssid = message.optString("bssid")
                        val key = bssid.ifEmpty { "$ssid:${message.optInt("channel")}" }
                        val channel = message.optInt("channel")
                        wifiChannels[channel] = (wifiChannels[channel] ?: 0) + 1
                        rawRecords[key] = JSONObject(message.toString())
                        lastObservationAt = System.currentTimeMillis()
                        val formatted = "WIFI   %4d dBm   ch %-2d   %-24s  %s".format(
                            message.optInt("rssi"), message.optInt("channel"), ssid,
                            message.optString("security", "OPEN"),
                        )
                        val isNew = !wifiRecords.containsKey(key)
                        wifiRecords[key] = formatted
                        if (isNew && ssid != "<hidden>") animateDecodeLabel(ssid)
                        renderNetworks()
                    }
                    "ble", "bluetooth" -> {
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
                        if (isNew) animateDecodeLabel(name.ifEmpty { address })
                        renderNetworks()
                    }
                }
            } catch (_: Exception) {
                LegacySerialParser.parse(line)?.let { handleLine(it.toString()) }
            }
        }
    }

    private fun renderNetworks() {
        countText.text = "${wifiRecords.size}\nWi-Fi"
        bleCountText.text = "${bleRecords.size}\nBLE"
        gpsCountText.text = "$satelliteCount\nGPS SAT"
        categoryText.text = categorySummary()
        radarView.setSignalCount(rawRecords.size)
        strongestText.text = strongestSignalSummary()
        sourceText.text = sourceCounts.entries.joinToString("   ") { "${it.key} ${it.value}" }
        val lastSeen = if (lastObservationAt == 0L) "waiting" else "live"
        sessionText.text = "SESSION  %02d passes   %d records   %s".format(scanPasses, rawRecords.size, lastSeen)
        spectrumText.text = if (wifiChannels.isEmpty()) {
            "No channel activity yet"
        } else {
            wifiChannels.entries.sortedBy { it.key }.joinToString("\n") { (channel, hits) ->
                val bar = "#".repeat(hits.coerceAtMost(12))
                "CH %-3d %-12s %d AP".format(channel, bar, hits)
            }
        }
        viewText.text = "VIEW  $selectedView   /   ${rawRecords.size} live signals"
        val records = when (selectedView) {
            "WIFI" -> wifiRecords.values
            "BLE" -> bleRecords.values
            else -> wifiRecords.values + bleRecords.values
        }.sorted()
        resultsText.text = if (records.isEmpty()) {
            "Waiting for passive observations..."
        } else {
            records.joinToString("\n")
        }
    }

    private fun shareSession() {
        if (rawRecords.isEmpty()) {
            Toast.makeText(this, "No observations to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = JSONArray(rawRecords.values.map { JSONObject(it.toString()) })
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish passive survey")
            putExtra(Intent.EXTRA_TEXT, payload.toString(2))
        }
        startActivity(Intent.createChooser(shareIntent, "Export survey session"))
    }

    private fun shareCsv() {
        if (rawRecords.isEmpty()) {
            Toast.makeText(this, "No observations to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = mutableListOf("MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME")
        rawRecords.values.forEach { record ->
            val type = record.optString("type")
            val mac = record.optString("bssid").ifEmpty { record.optString("mac") }
            val ssid = record.optString("ssid")
            val auth = record.optString("encryption", record.optString("security"))
            val channel = record.optInt("channel", 0).toString()
            val rssi = record.optInt("rssi", 0).toString()
            val name = record.optString("name")
            rows += listOf(mac, ssid, auth, channel, rssi, type, name)
                .joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish wardrive CSV")
            putExtra(Intent.EXTRA_TEXT, rows.joinToString("\n"))
        }
        startActivity(Intent.createChooser(shareIntent, "Export wardrive CSV"))
    }

    private fun animateDecodeLabel(label: String) {
        decodeQueue.addLast(DecodeJob(label))
        runNextDecode()
    }

    private fun runNextDecode() {
        if (decodeRunning || decodeQueue.isEmpty()) return
        decodeRunning = true
        val job = decodeQueue.removeFirst()
        val alphabet = "01ZX7#@$%&"
        val frames = 12
        val seed = job.label.map { alphabet[it.code % alphabet.length] }.joinToString("")
        decodeText.text = seed
        for (frame in 1..frames) {
            uiHandler.postDelayed({
                val revealed = job.label.mapIndexed { index, character ->
                    if (index < job.label.length * frame / frames) character else alphabet[(index + frame) % alphabet.length]
                }.joinToString("")
                decodeText.text = revealed
            }, frame * 110L)
        }
        uiHandler.postDelayed({
            decodeText.text = ""
            decodeRunning = false
            runNextDecode()
        }, (frames + 1) * 110L)
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

    private fun logObservation(message: JSONObject) {
        val source = message.optString("source", "UNKNOWN")
        sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
        sessionLogFile.appendText("${System.currentTimeMillis()} ${message}\n")
        if (source != "PHONE") {
            val usbRecord = JSONObject(message.toString()).apply {
                put("usb_device", connectedUsbLabel)
                put("log_type", "usb_observation")
            }
            usbLogFile.appendText("${System.currentTimeMillis()} $usbRecord\n")
        }
    }

    private fun logUsbEvent(event: String) {
        val record = JSONObject().apply {
            put("log_type", "usb_device")
            put("event", event)
            put("device", connectedUsbLabel)
        }
        usbLogFile.appendText("${System.currentTimeMillis()} $record\n")
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

    private fun setStatus(status: String) {
        runOnUiThread {
            statusText.text = status
            if (status.contains("USB connection lost", true)) {
                logUsbEvent("disconnected")
                deviceText.text = "USB DEVICE  DISCONNECTED"
                connectedUsbLabel = ""
            }
        }
    }
}
