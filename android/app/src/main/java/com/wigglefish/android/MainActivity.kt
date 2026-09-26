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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity(), SurveyHost {
    companion object {
        private const val permissionAction = "com.wigglefish.android.USB_PERMISSION"
    }

    private val session: SurveySessionViewModel by viewModels()
    private lateinit var usbManager: UsbManager
    private lateinit var serial: UsbSerialController
    private lateinit var locationManager: LocationManager
    private lateinit var wifiManager: WifiManager
    private lateinit var bluetoothManager: BluetoothManager
    private var phoneBleScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var lastLocation: Location? = null
    private var lastDecodedSatelliteCount = -1
    private val sessionLogFile by lazy { File(filesDir, "wigglefish-session.jsonl") }
    private val usbLogFile by lazy { File(filesDir, "wigglefish-usb-devices.jsonl") }
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
                session.setStatus("USB permission was denied")
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            session.setLocationText(
                "GPS LOCK  %.5f, %.5f   +/- %.0fm".format(
                    location.latitude, location.longitude, location.accuracy,
                ),
            )
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
            session.setSatelliteCount(satelliteCount)
            updateLocationText(satelliteCount)
            if (satelliteCount > 0 && satelliteCount != lastDecodedSatelliteCount) {
                lastDecodedSatelliteCount = satelliteCount
                animateDecodeLabel("GPS SATELLITES")
            }
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

        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        findViewById<BottomNavigationView>(R.id.bottomNav).setupWithNavController(navController)

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        serial = UsbSerialController(usbManager, ::handleLine) { status ->
            if (status.contains("USB connection lost", ignoreCase = true)) {
                logUsbEvent("disconnected")
            }
            session.setStatus(status)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, IntentFilter(permissionAction))
        }

        requestUsbConnection()
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

    override fun navigateTo(destinationId: Int) {
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navHost.navController.navigate(destinationId)
    }

    override fun requestUsbConnection() {
        val device = serial.findDevice()
        if (device == null) {
            session.setDeviceText("USB DEVICE  NO USB DEVICE")
            session.setStatus("ESP32-C5 / CH343 not found. Connect it with USB OTG.")
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
        session.setStatus("Waiting for USB permission...")
    }

    private fun connect(device: android.hardware.usb.UsbDevice) {
        if (!session.isUsbCollectionEnabled()) return
        if (serial.connect(device)) {
            val label = "${UsbSerialController.friendlyName(device)} VID %04X PID %04X".format(
                device.vendorId,
                device.productId,
            )
            session.setConnectedUsb(label, connected = true)
            logUsbEvent("connected")
        }
    }

    override fun togglePhoneCollection() {
        val enabled = !session.isPhoneCollectionEnabled()
        session.setPhoneCollectionEnabled(enabled)
        if (enabled) {
            startPhoneLocation()
            startPhoneWireless()
        } else {
            stopPhoneLocation()
            stopPhoneWireless()
        }
    }

    override fun toggleUsbCollection() {
        val enabled = !session.isUsbCollectionEnabled()
        session.setUsbCollectionEnabled(enabled)
        if (enabled) {
            requestUsbConnection()
        } else {
            serial.disconnect()
        }
    }

    override fun clearSession() {
        session.clearObservations()
        sessionLogFile.delete()
        usbLogFile.delete()
        decodeQueue.clear()
        decodeRunning = false
    }

    override fun shareSessionJson() {
        val records = session.snapshotRawRecords()
        if (records.isEmpty()) {
            Toast.makeText(this, "No observations to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = JSONArray(records)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish passive survey")
            putExtra(Intent.EXTRA_TEXT, payload.toString(2))
        }
        startActivity(Intent.createChooser(shareIntent, "Export survey session"))
    }

    override fun shareSessionCsv() {
        val records = session.snapshotRawRecords()
        if (records.isEmpty()) {
            Toast.makeText(this, "No observations to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = mutableListOf("MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME")
        records.forEach { record ->
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
            session.setLocationText("GPS LOCK  permission required")
            return
        }
        try {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(mainLooper))
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 2000L, 2f, locationListener, mainLooper)
                }
            }
            session.setLocationText("GPS LOCK  searching phone location...")
        } catch (_: SecurityException) {
            session.setLocationText("GPS LOCK  unavailable")
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
                session.setStatus("Phone Wi-Fi scan unavailable; ESP32-C5 scan still active")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            phoneBleScanner = bluetoothManager.adapter?.bluetoothLeScanner
            phoneBleScanner?.startScan(phoneBleCallback)
        }
    }

    private fun stopPhoneWireless() {
        try {
            unregisterReceiver(phoneWifiReceiver)
        } catch (_: IllegalArgumentException) {
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            phoneBleScanner?.stopScan(phoneBleCallback)
        }
    }

    private fun updateLocationText(satelliteCount: Int) {
        val location = lastLocation
        session.setLocationText(
            if (location == null) {
                "GPS LOCK  $satelliteCount satellites used   searching"
            } else {
                "GPS LOCK  $satelliteCount satellites used   %.5f, %.5f   +/- %.0fm".format(
                    location.latitude, location.longitude, location.accuracy,
                )
            },
        )
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
                    "ready" -> session.setStatus("Ready. Passive survey is running automatically.")
                    "scan_start" -> {
                        session.noteScanPass()
                        session.setStatus("Scanning nearby Wi-Fi signals...")
                    }
                    "scan_end" -> session.setStatus("Survey updated")
                    "scan_count" -> session.setStatus("Live survey running")
                    "error" -> session.setStatus("Device error: ${message.optString("message", "unknown")}")
                }
                when (message.optString("type")) {
                    "wifi" -> session.ingestWifi(message)?.let { animateDecodeLabel(it) }
                    "ble", "bluetooth" -> session.ingestBle(message)?.let { animateDecodeLabel(it) }
                }
            } catch (_: Exception) {
                LegacySerialParser.parse(line)?.let { handleLine(it.toString()) }
            }
        }
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
        session.setDecodeText(seed)
        for (frame in 1..frames) {
            uiHandler.postDelayed({
                val revealed = job.label.mapIndexed { index, character ->
                    if (index < job.label.length * frame / frames) character else alphabet[(index + frame) % alphabet.length]
                }.joinToString("")
                session.setDecodeText(revealed)
            }, frame * 110L)
        }
        uiHandler.postDelayed({
            session.setDecodeText("")
            decodeRunning = false
            runNextDecode()
        }, (frames + 1) * 110L)
    }

    private fun logObservation(message: JSONObject) {
        val source = message.optString("source", "UNKNOWN")
        session.noteSource(source)
        sessionLogFile.appendText("${System.currentTimeMillis()} ${message}\n")
        if (source != "PHONE") {
            val usbRecord = JSONObject(message.toString()).apply {
                put("usb_device", session.connectedUsbLabel())
                put("log_type", "usb_observation")
            }
            usbLogFile.appendText("${System.currentTimeMillis()} $usbRecord\n")
        }
    }

    private fun logUsbEvent(event: String) {
        val record = JSONObject().apply {
            put("log_type", "usb_device")
            put("event", event)
            put("device", session.connectedUsbLabel())
        }
        usbLogFile.appendText("${System.currentTimeMillis()} $record\n")
    }
}
