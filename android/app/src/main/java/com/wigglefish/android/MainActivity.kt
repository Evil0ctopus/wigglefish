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
import android.graphics.Color
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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val permissionAction = "com.wigglefish.android.USB_PERMISSION"
    }

    // Header & Badges
    private lateinit var appTitleText: TextView
    private lateinit var themeButton: Button
    private lateinit var connectButton: Button
    private lateinit var deviceBadge: TextView
    private lateinit var gpsBadge: TextView
    private lateinit var threatAlertText: TextView
    private lateinit var appStatusText: TextView
    private lateinit var uxAirspaceChipText: TextView
    private lateinit var uxOpsChipText: TextView
    private lateinit var uxTrafficChipText: TextView

    // Tab buttons & Sections
    private lateinit var tabScannerBtn: Button
    private lateinit var tabRadarBtn: Button
    private lateinit var tabToolsBtn: Button
    private lateinit var tabAnalyticsBtn: Button
    private lateinit var tabExportsBtn: Button

    private lateinit var sectionScanner: View
    private lateinit var sectionRadar: View
    private lateinit var sectionTools: View
    private lateinit var sectionAnalytics: View
    private lateinit var sectionExports: View

    // Scanner section views
    private lateinit var countWifiText: TextView
    private lateinit var countBleText: TextView
    private lateinit var countThreatText: TextView
    private lateinit var filterAllBtn: Button
    private lateinit var filterWifiBtn: Button
    private lateinit var filterBleBtn: Button
    private lateinit var filterVulnBtn: Button
    private lateinit var resultsText: TextView

    // Radar & Hunter section views
    private lateinit var radarView: SignalRadarView
    private lateinit var decodeText: TextView
    private lateinit var geigerTargetLabel: TextView
    private lateinit var rssiHistoryText: TextView
    private lateinit var airspaceStatusText: TextView
    private lateinit var airspaceTargetText: TextView
    private lateinit var airspacePatternText: TextView
    private lateinit var airspaceTrackText: TextView
    private lateinit var airspaceVisualTrackText: TextView
    private lateinit var airspaceTimelineText: TextView
    private lateinit var airspaceNewTrackBtn: Button
    private lateinit var airspaceLogSightingBtn: Button
    private lateinit var airspaceExportBtn: Button
    private lateinit var geigerSelectBtn: Button
    private lateinit var geigerAudioBtn: Button
    private lateinit var geigerHapticBtn: Button
    private lateinit var spectrumText: TextView

    // Cyber Tools section views
    private lateinit var beaconStatusText: TextView
    private lateinit var bleSpamStatusText: TextView
    private lateinit var portalStatusText: TextView
    private lateinit var probeFloodStatusText: TextView
    private lateinit var beaconDetailText: TextView
    private lateinit var bleDetailText: TextView
    private lateinit var huntDetailText: TextView
    private lateinit var huntStageText: TextView
    private lateinit var huntChecklistText: TextView
    private lateinit var lightDetailText: TextView
    private lateinit var portalDetailText: TextView
    private lateinit var probeDetailText: TextView
    private lateinit var ledDetailText: TextView
    private lateinit var toolLiveOpsText: TextView
    private lateinit var toolTrafficText: TextView
    private lateinit var toolLastEventText: TextView
    private lateinit var toolEventFeedText: TextView
    private lateinit var toolArmBtn: Button
    private lateinit var toolPcapToggleBtn: Button
    private lateinit var toolStopAllBtn: Button
    private lateinit var toolPortalViewCredsBtn: Button
    private lateinit var toolViewStationsBtn: Button
    private lateinit var toolViewSkimmersBtn: Button
    private lateinit var toolViewWpsPmfBtn: Button
    private lateinit var intelPostureText: TextView
    private lateinit var intelCountsText: TextView
    private lateinit var intelTopFindingsText: TextView
    private lateinit var intelStationsBtn: Button
    private lateinit var intelSkimmersBtn: Button
    private lateinit var intelWpsPmfBtn: Button
    private lateinit var intelHashesBtn: Button
    private lateinit var analyticsContentText: TextView

    // WigglePwn Cyber Pet views & engine
    private lateinit var petDockContainer: View
    private lateinit var wigglePetView: CopilotOctopusAnimationView
    private lateinit var petNameTitleText: TextView
    private lateinit var petPwnedBadgeText: TextView
    private lateinit var petSpeechText: TextView
    private lateinit var petExpText: TextView
    private lateinit var petMoodTagText: TextView
    private lateinit var petCompanion: WigglePetCompanion

    // Managers & Controllers
    private lateinit var usbManager: UsbManager
    private lateinit var serial: UsbSerialController
    private lateinit var locationManager: LocationManager
    private lateinit var wifiManager: WifiManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var packetAnalyzer: PacketAnalyzer
    private lateinit var toolsController: WirelessToolsController
    private var currentTheme = AppTheme.KOHOLINT_TOYBOX

    private var phoneBleScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var satelliteCount = 0
    private val wifiRecords = linkedMapOf<String, String>()
    private val bleRecords = linkedMapOf<String, String>()
    private val wifiChannels = linkedMapOf<Int, Int>()
    private val rawRecords = linkedMapOf<String, JSONObject>()
    private var scanPasses = 0
    private var lastObservationAt = 0L
    private var selectedFilter = "ALL"
    private var lastLocation: Location? = null
    private val trackPoints = mutableListOf<Location>()
    private val rssiHistory = linkedMapOf<String, MutableList<Int>>()
    private val airspaceSignalTracker = AirspaceSignalTracker()
    private var lastAirspaceSampleAt = 0L
    private var lastDecodedSatelliteCount = -1
    private val sourceCounts = linkedMapOf<String, Int>()
    private val sessionLogFile by lazy { File(filesDir, "wigglefish-session.jsonl") }
    private val usbLogFile by lazy { File(filesDir, "wigglefish-usb-devices.jsonl") }
    private val airspaceTrackFile by lazy { File(filesDir, "wigglefish-airspace-track.jsonl") }
    private val airspaceSightingsFile by lazy { File(filesDir, "wigglefish-airspace-sightings.jsonl") }
    private var activeVisualTrackId: String? = null
    private var connectedUsbLabel = ""
    private val uiHandler = Handler(Looper.getMainLooper())
    private val decodeQueue = ArrayDeque<DecodeJob>()
    private val toolEventFeed = ArrayDeque<String>()
    private var decodeRunning = false
    private var lastTrafficSnapshot = ToolTrafficSnapshot()
    private var lastTrafficPulseAt = 0L
    private var selectedTabIndex = 0
    private var toolsArmed = false
    private var lastLedAlert = "READY"
    private var huntStartedAt = 0L
    private var huntTargetSsid = ""
    private var huntTargetBssid = ""
    private var huntInitialKeyCount = 0
    private val toolStatusTicker = object : Runnable {
        override fun run() {
            if (::toolsController.isInitialized) updateToolStatusLabels()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private data class DecodeJob(val label: String)
    private data class ToolTrafficSnapshot(
        val beaconFramesSent: Long = 0L,
        val bleAdvertisementsSent: Long = 0L,
        val deauthFramesSent: Long = 0L,
        val probeFramesSent: Long = 0L,
        val rawWifiFramesObserved: Long = 0L,
        val smartLightCommandsSent: Long = 0L,
    )

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
                deviceBadge.text = "USB: PERMISSION DENIED"
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            trackPoints.add(Location(location))
            gpsBadge.text = "GPS: %.4f, %.4f ±%.0fm".format(
                location.latitude, location.longitude, location.accuracy
            )
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satelliteCount = (0 until status.satelliteCount).count { status.usedInFix(it) }
            updateLocationText()
            if (satelliteCount > 0 && satelliteCount != lastDecodedSatelliteCount) {
                lastDecodedSatelliteCount = satelliteCount
                animateDecodeLabel("GPS SAT: $satelliteCount")
            }
            scheduleRenderNetworks()
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
                    put("security", securityFromCapabilities(result.capabilities))
                }
                handleLine(message.toString())
            }
        }
    }

    private fun securityFromCapabilities(capabilities: String): String = when {
        capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        else -> "OPEN"
    }

    private val phoneBleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED) {
                    result.device.name ?: ""
                } else {
                    ""
                }
            } else {
                result.device.name ?: ""
            }
            val message = JSONObject().apply {
                put("type", "bluetooth")
                put("source", "PHONE")
                put("address", result.device.address)
                put("name", deviceName)
                put("rssi", result.rssi)
            }
            handleLine(message.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentTheme = AppTheme.getSavedTheme(this)

        initViews()
        initControllers()
        restoreAirspaceState()
        initListeners()
        applyTheme(currentTheme)

        requestConnection()
        startPhoneLocation()
        startPhoneWireless()
        startToolStatusTicker()
    }

    private fun initViews() {
        appTitleText = findViewById(R.id.appTitleText)
        themeButton = findViewById(R.id.themeButton)
        connectButton = findViewById(R.id.connectButton)
        deviceBadge = findViewById(R.id.deviceBadge)
        gpsBadge = findViewById(R.id.gpsBadge)
        threatAlertText = findViewById(R.id.threatAlertText)
        appStatusText = findViewById(R.id.appStatusText)
        uxAirspaceChipText = findViewById(R.id.uxAirspaceChipText)
        uxOpsChipText = findViewById(R.id.uxOpsChipText)
        uxTrafficChipText = findViewById(R.id.uxTrafficChipText)

        tabScannerBtn = findViewById(R.id.tabScannerBtn)
        tabRadarBtn = findViewById(R.id.tabRadarBtn)
        tabToolsBtn = findViewById(R.id.tabToolsBtn)
        tabAnalyticsBtn = findViewById(R.id.tabAnalyticsBtn)
        tabExportsBtn = findViewById(R.id.tabExportsBtn)

        sectionScanner = findViewById(R.id.sectionScanner)
        sectionRadar = findViewById(R.id.sectionRadar)
        sectionTools = findViewById(R.id.sectionTools)
        sectionAnalytics = findViewById(R.id.sectionAnalytics)
        sectionExports = findViewById(R.id.sectionExports)

        countWifiText = findViewById(R.id.countWifiText)
        countBleText = findViewById(R.id.countBleText)
        countThreatText = findViewById(R.id.countThreatText)
        filterAllBtn = findViewById(R.id.filterAllBtn)
        filterWifiBtn = findViewById(R.id.filterWifiBtn)
        filterBleBtn = findViewById(R.id.filterBleBtn)
        filterVulnBtn = findViewById(R.id.filterVulnBtn)
        resultsText = findViewById(R.id.resultsText)

        radarView = findViewById(R.id.radarView)
        decodeText = findViewById(R.id.decodeText)
        geigerTargetLabel = findViewById(R.id.geigerTargetLabel)
        rssiHistoryText = findViewById(R.id.rssiHistoryText)
        airspaceStatusText = findViewById(R.id.airspaceStatusText)
        airspaceTargetText = findViewById(R.id.airspaceTargetText)
        airspacePatternText = findViewById(R.id.airspacePatternText)
        airspaceTrackText = findViewById(R.id.airspaceTrackText)
        airspaceVisualTrackText = findViewById(R.id.airspaceVisualTrackText)
        airspaceTimelineText = findViewById(R.id.airspaceTimelineText)
        airspaceNewTrackBtn = findViewById(R.id.airspaceNewTrackBtn)
        airspaceLogSightingBtn = findViewById(R.id.airspaceLogSightingBtn)
        airspaceExportBtn = findViewById(R.id.airspaceExportBtn)
        geigerSelectBtn = findViewById(R.id.geigerSelectBtn)
        geigerAudioBtn = findViewById(R.id.geigerAudioBtn)
        geigerHapticBtn = findViewById(R.id.geigerHapticBtn)
        spectrumText = findViewById(R.id.spectrumText)

        beaconStatusText = findViewById(R.id.beaconStatusText)
        bleSpamStatusText = findViewById(R.id.bleSpamStatusText)
        portalStatusText = findViewById(R.id.portalStatusText)
        probeFloodStatusText = findViewById(R.id.probeFloodStatusText)
        beaconDetailText = findViewById(R.id.beaconDetailText)
        bleDetailText = findViewById(R.id.bleDetailText)
        huntDetailText = findViewById(R.id.huntDetailText)
        huntStageText = findViewById(R.id.huntStageText)
        huntChecklistText = findViewById(R.id.huntChecklistText)
        lightDetailText = findViewById(R.id.lightDetailText)
        portalDetailText = findViewById(R.id.portalDetailText)
        probeDetailText = findViewById(R.id.probeDetailText)
        ledDetailText = findViewById(R.id.ledDetailText)
        toolLiveOpsText = findViewById(R.id.toolLiveOpsText)
        toolTrafficText = findViewById(R.id.toolTrafficText)
        toolLastEventText = findViewById(R.id.toolLastEventText)
        toolEventFeedText = findViewById(R.id.toolEventFeedText)
        toolArmBtn = findViewById(R.id.toolArmBtn)
        toolPcapToggleBtn = findViewById(R.id.toolPcapToggleBtn)
        toolStopAllBtn = findViewById(R.id.toolStopAllBtn)
        toolPortalViewCredsBtn = findViewById(R.id.toolPortalViewCredsBtn)
        toolViewStationsBtn = findViewById(R.id.toolViewStationsBtn)
        toolViewSkimmersBtn = findViewById(R.id.toolViewSkimmersBtn)
        toolViewWpsPmfBtn = findViewById(R.id.toolViewWpsPmfBtn)
        intelPostureText = findViewById(R.id.intelPostureText)
        intelCountsText = findViewById(R.id.intelCountsText)
        intelTopFindingsText = findViewById(R.id.intelTopFindingsText)
        intelStationsBtn = findViewById(R.id.intelStationsBtn)
        intelSkimmersBtn = findViewById(R.id.intelSkimmersBtn)
        intelWpsPmfBtn = findViewById(R.id.intelWpsPmfBtn)
        intelHashesBtn = findViewById(R.id.intelHashesBtn)
        analyticsContentText = findViewById(R.id.analyticsContentText)

        // WigglePwn Cyber Pet Dock Views
        petDockContainer = findViewById(R.id.petDockContainer)
        wigglePetView = findViewById(R.id.wigglePetView)
        petNameTitleText = findViewById(R.id.petNameTitleText)
        petPwnedBadgeText = findViewById(R.id.petPwnedBadgeText)
        petSpeechText = findViewById(R.id.petSpeechText)
        petExpText = findViewById(R.id.petExpText)
        petMoodTagText = findViewById(R.id.petMoodTagText)

        val headerContainer = findViewById<View>(R.id.headerContainer)
        val rootContainer = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val topPadding = (statusBarInsets.top.coerceAtLeast((24 * resources.displayMetrics.density).toInt()))
            headerContainer.setPadding(
                headerContainer.paddingLeft,
                topPadding,
                headerContainer.paddingRight,
                headerContainer.paddingBottom
            )
            rootContainer.setPadding(0, 0, 0, navBarInsets.bottom)
            insets
        }
        clearButtonBackgroundTints(rootContainer)
    }

    private fun clearButtonBackgroundTints(view: View) {
        if (view is Button) view.backgroundTintList = null
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) clearButtonBackgroundTints(view.getChildAt(index))
        }
    }

    private fun initControllers() {
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        serial = UsbSerialController(usbManager, ::handleLine, ::setStatus)

        petCompanion = WigglePetCompanion(this) { petState ->
            renderPet(petState)
        }

        packetAnalyzer = PacketAnalyzer(
            onHandshakeCaptured = { record ->
                runOnUiThread {
                    Toast.makeText(this, "🔑 KEY CAPTURED: ${record.bssid} [${record.type}]", Toast.LENGTH_LONG).show()
                    threatAlertText.visibility = View.VISIBLE
                    threatAlertText.text = "🔑 CAPTURED: ${record.ssid} (${record.bssid}) [${record.type}]"
                    animateDecodeLabel("KEY: ${record.bssid}")
                    if (record.bssid.equals(huntTargetBssid, ignoreCase = true)) {
                        huntStageText.text = "STAGE: CAPTURED ${record.type} | Export ready"
                        appendToolEvent("Handshake captured: ${record.type} ${record.bssid}")
                    }
                    petCompanion.onHandshakeCaptured(record.bssid)
                    // Hardware RGB alert (Gold)
                    serial.send("{\"cmd\":\"led_alert\",\"color\":\"gold\"}")
                }
            },
            onDeauthAlert = { alert ->
                runOnUiThread {
                    threatAlertText.visibility = View.VISIBLE
                    threatAlertText.text = "⚠️ DEAUTH STORM: ${alert.count} frames targeting ${alert.apMac}"
                    petCompanion.onThreatOrSkimmerAlert("Deauth Storm")
                    // Hardware RGB alert (Red)
                    serial.send("{\"cmd\":\"led_alert\",\"color\":\"red\"}")
                }
            },
            onEvilTwinAlert = { alert ->
                runOnUiThread {
                    threatAlertText.visibility = View.VISIBLE
                    threatAlertText.text = "⚠️ EVIL TWIN DETECTED: ${alert.ssid} (${alert.rogueBssid})"
                    petCompanion.onThreatOrSkimmerAlert("Evil Twin")
                    serial.send("{\"cmd\":\"led_alert\",\"color\":\"red\"}")
                }
            },
            onProbeObserved = { probe ->
                runOnUiThread {
                    if (probe.queriedSsid.isNotEmpty() && probe.queriedSsid != "<hidden>") {
                        animateDecodeLabel("PROBE: ${probe.queriedSsid}")
                    }
                }
            },
            onSkimmerDetected = { skimmer ->
                runOnUiThread {
                    threatAlertText.visibility = View.VISIBLE
                    threatAlertText.text = "🚨 SKIMMER / TRACKER: ${skimmer.name} (${skimmer.address})"
                    toolViewSkimmersBtn.text = "🕵️ SKIMMERS (${packetAnalyzer.getDetectedSkimmers().size})"
                    petCompanion.onThreatOrSkimmerAlert(skimmer.name)
                    serial.send("{\"cmd\":\"led_alert\",\"color\":\"red\"}")
                    Toast.makeText(this, "🚨 ROGUE DEVICE DETECTED: ${skimmer.name}", Toast.LENGTH_LONG).show()
                }
            },
            onStationDiscovered = { sta ->
                runOnUiThread {
                    toolViewStationsBtn.text = "📱 CLIENTS (${packetAnalyzer.getConnectedStations().size})"
                }
            }
        )

        toolsController = WirelessToolsController(this, serial, packetAnalyzer) { msg ->
            setStatus(msg)
            updateToolStatusLabels()
        }

        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun restoreAirspaceState() {
        val preferences = getSharedPreferences("airspace", MODE_PRIVATE)
        activeVisualTrackId = preferences.getString("active_visual_track_id", null)
        val target = preferences.getString("active_rf_target", null)
        if (!target.isNullOrBlank()) {
            toolsController.setGeigerTarget(target)
            if (airspaceTrackFile.exists()) {
                airspaceTrackFile.readLines().mapNotNull { line ->
                    runCatching { JSONObject(line) }.getOrNull()
                }.filter { it.optString("target").equals(target, ignoreCase = true) }
                    .takeLast(120)
                    .forEach { sample ->
                        val timestamp = sample.optLong("timestamp")
                        airspaceSignalTracker.record(sample.optInt("rssi_dbm"), timestamp)
                        lastAirspaceSampleAt = maxOf(lastAirspaceSampleAt, timestamp)
                    }
            }
        }
        renderAirspaceSightings()
        updateAirspaceTrackDisplay(target)
    }

    private fun initListeners() {
        // Top Header
        themeButton.setOnClickListener { showThemePickerDialog() }
        connectButton.setOnClickListener { requestConnection() }

        // Interactive Pet Poke Listener
        val onPetPoke = View.OnClickListener {
            wigglePetView.triggerPokeBounce()
            petCompanion.onPetInteracted()
            try { toolsController.processGeigerPing(-40) } catch (_: Exception) {}
        }
        petDockContainer.setOnClickListener(onPetPoke)
        wigglePetView.setOnClickListener(onPetPoke)

        // Tap on Pet Title to view/select Evolution Stages
        petNameTitleText.setOnClickListener { showEvolutionStagesDialog() }
        petDockContainer.setOnLongClickListener {
            showEvolutionStagesDialog()
            true
        }

        // Tabs
        tabScannerBtn.setOnClickListener { switchTab(0) }
        tabRadarBtn.setOnClickListener { switchTab(1) }
        tabToolsBtn.setOnClickListener { switchTab(2) }
        tabAnalyticsBtn.setOnClickListener { switchTab(3) }
        tabExportsBtn.setOnClickListener { switchTab(4) }

        // Filters in Scanner
        filterAllBtn.setOnClickListener { setFilter("ALL") }
        filterWifiBtn.setOnClickListener { setFilter("WIFI") }
        filterBleBtn.setOnClickListener { setFilter("BLE") }
        filterVulnBtn.setOnClickListener { setFilter("VULN") }

        // Geiger Hunter
        geigerSelectBtn.setOnClickListener { showGeigerTargetDialog() }
        airspaceNewTrackBtn.setOnClickListener { startNewVisualTrack() }
        airspaceLogSightingBtn.setOnClickListener { showAirspaceSightingDialog() }
        airspaceExportBtn.setOnClickListener { shareAirspaceLog() }
        geigerAudioBtn.setOnClickListener {
            toolsController.geigerAudioEnabled = !toolsController.geigerAudioEnabled
            geigerAudioBtn.text = if (toolsController.geigerAudioEnabled) "🔊 AUDIO [ON]" else "🔇 AUDIO [OFF]"
        }
        geigerHapticBtn.setOnClickListener {
            toolsController.geigerHapticEnabled = !toolsController.geigerHapticEnabled
            geigerHapticBtn.text = if (toolsController.geigerHapticEnabled) "📳 HAPTIC [ON]" else "📴 HAPTIC [OFF]"
        }

        // Cyber Tools Tab Actions
        toolArmBtn.setOnClickListener {
            toolsArmed = !toolsArmed
            appendToolEvent(if (toolsArmed) "Tools armed for active transmissions" else "Tools disarmed")
            updateToolStatusLabels()
        }
        toolPcapToggleBtn.setOnClickListener {
            val state = toolsController.getRuntimeState()
            if (state.isPcapActive) {
                toolsController.stopPcapCapture()
            } else {
                toolsController.startPcapCapture("wigglefish-tools-${System.currentTimeMillis()}.pcap")
            }
            updateToolStatusLabels()
        }
        toolStopAllBtn.setOnClickListener {
            toolsController.stopAllTools()
            toolsArmed = false
            huntStartedAt = 0L
            huntTargetSsid = ""
            huntTargetBssid = ""
            huntInitialKeyCount = packetAnalyzer.getCapturedHandshakes().size
            appendToolEvent("Emergency stop: all running tools halted")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBeaconRandomBtn).setOnClickListener {
            if (!requireToolsArmed("Beacon Flooder")) return@setOnClickListener
            toolsController.startBeaconSpam(WirelessToolsController.BeaconMode.RANDOM)
            petCompanion.onAttackStarted("Beacon Flooder")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBeaconRickrollBtn).setOnClickListener {
            if (!requireToolsArmed("Rickroll Flooder")) return@setOnClickListener
            toolsController.startBeaconSpam(WirelessToolsController.BeaconMode.RICKROLL)
            petCompanion.onAttackStarted("Rickroll Flooder")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBeaconCloneBtn).setOnClickListener {
            if (!requireToolsArmed("AP Clone Flooder")) return@setOnClickListener
            val cloned = rawRecords.values
                .filter { it.optString("type") == "wifi" }
                .mapNotNull { it.optString("ssid").takeIf { s -> s.isNotEmpty() && s != "<hidden>" } }
                .distinct()
            toolsController.startBeaconSpam(WirelessToolsController.BeaconMode.CLONED, cloned)
            petCompanion.onAttackStarted("AP Clone Flooder")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBeaconStopBtn).setOnClickListener {
            toolsController.stopBeaconSpam()
            updateToolStatusLabels()
        }

        findViewById<Button>(R.id.toolBleAppleBtn).setOnClickListener {
            if (!requireToolsArmed("Apple BLE Spam")) return@setOnClickListener
            toolsController.startBleSpam(WirelessToolsController.BleSpamMode.APPLE)
            petCompanion.onAttackStarted("Apple BLE Spam")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBleAndroidBtn).setOnClickListener {
            if (!requireToolsArmed("Android FastPair Spam")) return@setOnClickListener
            toolsController.startBleSpam(WirelessToolsController.BleSpamMode.ANDROID)
            petCompanion.onAttackStarted("Android FastPair Spam")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBleAllBtn).setOnClickListener {
            if (!requireToolsArmed("All-in-One BLE Spam")) return@setOnClickListener
            toolsController.startBleSpam(WirelessToolsController.BleSpamMode.ALL_IN_ONE)
            petCompanion.onAttackStarted("All-in-One BLE Spam")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolBleStopBtn).setOnClickListener {
            toolsController.stopBleSpam()
            updateToolStatusLabels()
        }

        findViewById<Button>(R.id.toolHuntLaunchBtn).setOnClickListener {
            if (!requireToolsArmed("Handshake Hunter")) return@setOnClickListener
            val wifiAps = rawRecords.values.filter { it.optString("type") == "wifi" }
            showHandshakeHuntDialog(wifiAps)
        }
        findViewById<Button>(R.id.toolDeauthPulseBtn).setOnClickListener {
            if (!requireToolsArmed("Deauth Pulse")) return@setOnClickListener
            val wifiAps = rawRecords.values.filter { it.optString("type") == "wifi" }
            showDeauthTargetDialog(wifiAps)
        }
        findViewById<Button>(R.id.toolKarmaProbesBtn).setOnClickListener {
            showClientProbesDialog()
        }
        findViewById<Button>(R.id.toolHuntStopBtn).setOnClickListener {
            toolsController.stopHandshakeHunt()
            huntStartedAt = 0L
            appendToolEvent("Handshake hunter stopped")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolHuntExportBtn).setOnClickListener { shareHashcat22000() }

        // Smart RGB Light Hijacker Buttons
        findViewById<Button>(R.id.toolLightOnBtn).setOnClickListener {
            toolsController.sendSmartLightPower(true)
            petCompanion.onAttackStarted("Smart Light ON")
        }
        findViewById<Button>(R.id.toolLightOffBtn).setOnClickListener {
            toolsController.sendSmartLightPower(false)
            petCompanion.onAttackStarted("Smart Light OFF")
        }
        findViewById<Button>(R.id.toolLightRedBtn).setOnClickListener { toolsController.sendSmartLightColor(255, 0, 0) }
        findViewById<Button>(R.id.toolLightGreenBtn).setOnClickListener { toolsController.sendSmartLightColor(0, 255, 0) }
        findViewById<Button>(R.id.toolLightBlueBtn).setOnClickListener { toolsController.sendSmartLightColor(0, 0, 255) }
        findViewById<Button>(R.id.toolLightGoldBtn).setOnClickListener { toolsController.sendSmartLightColor(255, 215, 0) }
        findViewById<Button>(R.id.toolLightRainbowBtn).setOnClickListener {
            if (!requireToolsArmed("RGB Rainbow Override")) return@setOnClickListener
            toolsController.startSmartLightRainbow()
            petCompanion.onAttackStarted("RGB Rainbow Override")
        }
        findViewById<Button>(R.id.toolLightStrobeBtn).setOnClickListener {
            if (!requireToolsArmed("RGB Strobe Hijack")) return@setOnClickListener
            toolsController.startSmartLightStrobe()
            petCompanion.onAttackStarted("RGB Strobe Hijack")
        }

        // Evil Captive Portal Buttons
        findViewById<Button>(R.id.toolPortalLaunchBtn).setOnClickListener {
            if (!requireToolsArmed("Evil Captive Portal")) return@setOnClickListener
            showEvilPortalLaunchDialog()
        }
        findViewById<Button>(R.id.toolPortalStopBtn).setOnClickListener {
            toolsController.stopEvilPortal()
            updateToolStatusLabels()
        }
        toolPortalViewCredsBtn.setOnClickListener { showHarvestedCredentialsDialog() }

        // Probe Flooder & Recon Buttons
        findViewById<Button>(R.id.toolProbeFloodStartBtn).setOnClickListener {
            if (!requireToolsArmed("Probe Flooder")) return@setOnClickListener
            val ssids = rawRecords.values.filter { it.optString("type") == "wifi" }.mapNotNull { it.optString("ssid").takeIf { s -> s.isNotEmpty() && s != "<hidden>" } }
            toolsController.startProbeFlood(ssids)
            petCompanion.onAttackStarted("Probe Flooder")
            updateToolStatusLabels()
        }
        findViewById<Button>(R.id.toolProbeFloodStopBtn).setOnClickListener {
            toolsController.stopProbeFlood()
            updateToolStatusLabels()
        }

        toolViewStationsBtn.setOnClickListener { showConnectedStationsDialog() }
        toolViewSkimmersBtn.setOnClickListener { showBleSkimmersDialog() }
        toolViewWpsPmfBtn.setOnClickListener { showWpsPmfDialog() }
        intelStationsBtn.setOnClickListener { showConnectedStationsDialog() }
        intelSkimmersBtn.setOnClickListener { showBleSkimmersDialog() }
        intelWpsPmfBtn.setOnClickListener { showWpsPmfDialog() }
        intelHashesBtn.setOnClickListener { shareHashcat22000() }

        // WS2812 LED Tests
        findViewById<Button>(R.id.ledAlertRedBtn).setOnClickListener { sendLedAlert("red") }
        findViewById<Button>(R.id.ledAlertGoldBtn).setOnClickListener { sendLedAlert("gold") }
        findViewById<Button>(R.id.ledAlertCyanBtn).setOnClickListener { sendLedAlert("cyan") }
        findViewById<Button>(R.id.ledAlertMagentaBtn).setOnClickListener { sendLedAlert("magenta") }

        // Export Tab Actions
        findViewById<Button>(R.id.exportPcapBtn).setOnClickListener { sharePcap() }
        findViewById<Button>(R.id.exportHashcatBtn).setOnClickListener { shareHashcat22000() }
        findViewById<Button>(R.id.exportCsvBtn).setOnClickListener { shareCsv() }
        findViewById<Button>(R.id.exportGpxBtn).setOnClickListener { shareGpx() }
        findViewById<Button>(R.id.exportJsonBtn).setOnClickListener { shareSession() }
        findViewById<Button>(R.id.clearSessionBtn).setOnClickListener { clearSessionData() }
    }

    private fun requireToolsArmed(toolName: String): Boolean {
        if (toolsArmed) return true
        val message = "Arm tools before starting $toolName"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        appendToolEvent(message)
        updateToolStatusLabels()
        return false
    }

    private fun sendLedAlert(color: String) {
        serial.send("{\"cmd\":\"led_alert\",\"color\":\"$color\"}")
        lastLedAlert = color.uppercase(Locale.US)
        appendToolEvent("ESP32 LED alert: $lastLedAlert")
        updateToolStatusLabels()
    }

    private fun renderPet(state: WigglePetCompanion.PetState) {
        runOnUiThread {
            wigglePetView.setMood(state.mood)
            wigglePetView.setEvolutionStage(state.evolutionStage)
            petNameTitleText.text = "🐙 ${state.name} LVL ${state.level} (${state.levelTitle})"
            petPwnedBadgeText.text = "🔑 PWND: ${state.pwnedCount}"
            petSpeechText.text = state.speechText
            petExpText.text = "EXP: ${state.currentExp}/${state.maxExp}"
            petMoodTagText.text = "${state.mood.tag} 💬 TAP TO POKE"
        }
    }

    private fun showEvolutionStagesDialog() {
        val stages = WigglePetCompanion.EvolutionStage.values()
        val options = stages.map { stage ->
            val status = if (petCompanion.state.level >= stage.minLevel) "✓ UNLOCKED" else "🔒 (Requires Lvl ${stage.minLevel})"
            "${stage.title} — $status"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🌟 Wiggler Evolution Tree")
            .setItems(options) { _, which ->
                val chosenStage = stages[which]
                petCompanion.setLevel(chosenStage.minLevel)
                Toast.makeText(this, "Transformed into ${chosenStage.title}!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun switchTab(tabIndex: Int) {
        selectedTabIndex = tabIndex
        sectionScanner.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        sectionRadar.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        sectionTools.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        sectionAnalytics.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
        sectionExports.visibility = if (tabIndex == 4) View.VISIBLE else View.GONE

        val tabBtns = listOf(tabScannerBtn, tabRadarBtn, tabToolsBtn, tabAnalyticsBtn, tabExportsBtn)
        tabBtns.forEachIndexed { i, btn ->
            if (i == tabIndex) {
                btn.setBackgroundResource(R.drawable.btn_3d_cyan)
                btn.setTextColor(Color.BLACK)
            } else {
                btn.setBackgroundResource(R.drawable.btn_3d_dark)
                btn.setTextColor(Color.WHITE)
            }
        }
        if (tabIndex == 3) updateAnalyticsContent()
        updateGlobalUxDashboard()
    }

    private fun setFilter(filter: String) {
        selectedFilter = filter
        val filterBtns = listOf(
            "ALL" to filterAllBtn,
            "WIFI" to filterWifiBtn,
            "BLE" to filterBleBtn,
            "VULN" to filterVulnBtn
        )
        filterBtns.forEach { (name, btn) ->
            if (name == filter) {
                btn.setBackgroundResource(R.drawable.btn_3d_cyan)
                btn.setTextColor(Color.BLACK)
            } else {
                btn.setBackgroundResource(R.drawable.btn_3d_dark)
                btn.setTextColor(Color.WHITE)
            }
        }
        renderNetworks()
    }

    private fun startToolStatusTicker() {
        uiHandler.removeCallbacks(toolStatusTicker)
        uiHandler.post(toolStatusTicker)
    }

    private fun showThemePickerDialog() {
        val themes = AppTheme.values()
        val names = themes.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("🎨 Select Console Palette")
            .setItems(names) { _, which ->
                val selected = themes[which]
                AppTheme.saveTheme(this, selected)
                applyTheme(selected)
                Toast.makeText(this, "Theme set to ${selected.title}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun applyTheme(theme: AppTheme) {
        currentTheme = theme
        findViewById<View>(R.id.rootContainer).setBackgroundColor(theme.background)
        findViewById<View>(R.id.headerContainer).setBackgroundColor(theme.surface)
        appTitleText.setTextColor(theme.primaryAccent)
        countWifiText.setTextColor(theme.primaryAccent)
        countBleText.setTextColor(theme.secondaryAccent)
        countThreatText.setTextColor(theme.tertiaryAccent)
        petNameTitleText.setTextColor(theme.primaryAccent)
        petPwnedBadgeText.setTextColor(theme.tertiaryAccent)
        renderNetworks()
    }

    private fun updateToolStatusLabels() {
        val state = toolsController.getRuntimeState()
        val runningTools = listOf(
            state.beaconMode != WirelessToolsController.BeaconMode.OFF,
            state.bleSpamMode != WirelessToolsController.BleSpamMode.OFF,
            state.activeHuntBssid != null,
            state.isEvilPortalActive,
            state.isProbeFloodActive,
            state.isSmartLightAnimationActive,
            state.geigerTarget != null,
            state.isPcapActive,
        ).count { it }
        val runningLabel = if (runningTools == 1) "1 RUNNING" else "$runningTools RUNNING"
        val huntLabel = state.activeHuntBssid?.let { " | HUNT ch ${state.activeHuntChannel}: $it" }.orEmpty()
        val geigerLabel = state.geigerTarget?.let { " | GEIGER: $it" }.orEmpty()
        val pcapLabel = if (state.isPcapActive) " | PCAP ON" else ""
        val armLabel = if (toolsArmed) "ARMED" else "SAFE"
        val currentTraffic = ToolTrafficSnapshot(
            beaconFramesSent = state.beaconFramesSent,
            bleAdvertisementsSent = state.bleAdvertisementsSent,
            deauthFramesSent = state.deauthFramesSent,
            probeFramesSent = state.probeFramesSent,
            rawWifiFramesObserved = state.rawWifiFramesObserved,
            smartLightCommandsSent = state.smartLightCommandsSent,
        )
        val beaconDelta = currentTraffic.beaconFramesSent - lastTrafficSnapshot.beaconFramesSent
        val bleDelta = currentTraffic.bleAdvertisementsSent - lastTrafficSnapshot.bleAdvertisementsSent
        val deauthDelta = currentTraffic.deauthFramesSent - lastTrafficSnapshot.deauthFramesSent
        val probeDelta = currentTraffic.probeFramesSent - lastTrafficSnapshot.probeFramesSent
        val rawDelta = currentTraffic.rawWifiFramesObserved - lastTrafficSnapshot.rawWifiFramesObserved
        val lightDelta = currentTraffic.smartLightCommandsSent - lastTrafficSnapshot.smartLightCommandsSent
        val trafficDelta = beaconDelta + bleDelta + deauthDelta + probeDelta + rawDelta + lightDelta
        val now = System.currentTimeMillis()
        if (trafficDelta > 0 && now - lastTrafficPulseAt >= 1500L) {
            lastTrafficPulseAt = now
            appendToolEvent("Traffic +$trafficDelta: beacon +$beaconDelta, BLE +$bleDelta, deauth +$deauthDelta, probes +$probeDelta, lights +$lightDelta, RX +$rawDelta")
        }
        lastTrafficSnapshot = currentTraffic

        beaconStatusText.text = "📡 BEACON FLOODER: [${if (state.beaconMode == WirelessToolsController.BeaconMode.OFF) "OFF" else "RUNNING ${state.beaconMode.name}"}]"
        bleSpamStatusText.text = "📶 BLE ECOSYSTEM SPAMMER: [${if (state.bleSpamMode == WirelessToolsController.BleSpamMode.OFF) "OFF" else "RUNNING ${state.bleSpamMode.name}"}]"
        portalStatusText.text = "🕸️ EVIL CAPTIVE PORTAL: [${if (state.isEvilPortalActive) "RUNNING ${state.evilPortalSsid}" else "OFF"}]"
        probeFloodStatusText.text = "🌊 PROBE FLOODER: [${if (state.isProbeFloodActive) "RUNNING" else "OFF"}]$huntLabel"
        beaconDetailText.text = "Mode ${state.beaconMode.name} | TX ${state.beaconFramesSent} frames | ${if (toolsArmed) "Armed" else "Arm required"}"
        bleDetailText.text = "Mode ${state.bleSpamMode.name} | TX ${state.bleAdvertisementsSent} adverts | ${if (toolsArmed) "Armed" else "Arm required"}"
        val handshakeCount = packetAnalyzer.getCapturedHandshakes().size
        val newHuntKeys = (handshakeCount - huntInitialKeyCount).coerceAtLeast(0)
        val huntElapsed = if (huntStartedAt > 0L) formatElapsed(huntStartedAt) else "idle"
        val targetRecord = state.activeHuntBssid?.let { targetBssid ->
            rawRecords.values.firstOrNull { it.optString("bssid").equals(targetBssid, ignoreCase = true) }
        }
        val targetRssi = targetRecord?.optInt("rssi")
        val targetScore = targetRecord?.optInt("security_score")
        val targetSecurity = targetRecord?.optString("security", "?") ?: "?"
        huntDetailText.text = "Target ${state.activeHuntBssid ?: "NONE"} | Ch ${state.activeHuntChannel} | Deauth TX ${state.deauthFramesSent} | Keys $handshakeCount"
        huntStageText.text = when {
            state.activeHuntBssid == null -> "STAGE: IDLE | Pick a WPA/WPA2 AP to begin"
            newHuntKeys > 0 -> "STAGE: CAPTURED | $newHuntKeys new key record(s) | Export ready"
            else -> "STAGE: LISTENING | $huntElapsed | waiting for EAPOL/PMKID"
        }
        huntChecklistText.text = buildString {
            append("Target: ${huntTargetSsid.ifEmpty { state.activeHuntBssid ?: "none" }}\n")
            append("Security: $targetSecurity")
            if (targetScore != null) append(" | Score $targetScore/100")
            if (targetRssi != null) append(" | RSSI $targetRssi dBm")
            append("\nPCAP: ${if (state.isPcapActive) "recording" else "off"} | Keys this hunt: $newHuntKeys")
        }
        lightDetailText.text = "Mode ${state.smartLightMode.name} | Commands ${state.smartLightCommandsSent} | Animation ${if (state.isSmartLightAnimationActive) "ON" else "OFF"}"
        portalDetailText.text = "SSID ${if (state.isEvilPortalActive) state.evilPortalSsid else "none"} | Credentials ${state.harvestedCredentialCount} | ${if (toolsArmed) "Armed" else "Arm required"}"
        probeDetailText.text = "Flood ${if (state.isProbeFloodActive) "ON" else "OFF"} | Probe TX ${state.probeFramesSent} | Clients ${packetAnalyzer.getConnectedStations().size} | Skimmers ${packetAnalyzer.getDetectedSkimmers().size}"
        ledDetailText.text = "LED $lastLedAlert | Auto: gold=keys red=threats | Manual test ready"

        toolLiveOpsText.text = "LIVE OPS: $armLabel | $runningLabel$geigerLabel$pcapLabel"
        toolTrafficText.text = "NOW +$trafficDelta/s | beacon +$beaconDelta BLE +$bleDelta deauth +$deauthDelta probes +$probeDelta lights +$lightDelta RX +$rawDelta\nTOTAL beacon ${state.beaconFramesSent} | BLE ${state.bleAdvertisementsSent} | deauth ${state.deauthFramesSent} | probes ${state.probeFramesSent} | lights ${state.smartLightCommandsSent} | RX raw ${state.rawWifiFramesObserved}"
        toolLastEventText.text = "Last event: ${state.lastEvent} (${formatElapsed(state.lastEventAt)})"
        appStatusText.text = "OPS: $runningLabel | RX ${state.rawWifiFramesObserved} | ${state.lastEvent}"
        updateGlobalUxDashboard(state, runningTools, trafficDelta)

        toolArmBtn.text = if (toolsArmed) "🟢 ARMED" else "🛡️ ARM"
        toolArmBtn.setBackgroundResource(if (toolsArmed) R.drawable.btn_3d_cyan else R.drawable.btn_3d_amber)
        toolArmBtn.setTextColor(Color.BLACK)
        toolPcapToggleBtn.text = if (state.isPcapActive) "💾 PCAP ON" else "💾 PCAP"
        toolPcapToggleBtn.setBackgroundResource(if (state.isPcapActive) R.drawable.btn_3d_cyan else R.drawable.btn_3d_dark)
        toolPcapToggleBtn.setTextColor(if (state.isPcapActive) Color.BLACK else Color.WHITE)
        toolStopAllBtn.text = if (runningTools > 0) "⛔ STOP $runningTools" else "⛔ STOP ALL"

        findViewById<Button>(R.id.toolBeaconStopBtn).text = if (state.beaconMode == WirelessToolsController.BeaconMode.OFF) "⛔ FLOOD OFF" else "⛔ STOP ${state.beaconMode.name}"
        findViewById<Button>(R.id.toolBleStopBtn).text = if (state.bleSpamMode == WirelessToolsController.BleSpamMode.OFF) "⛔ SPAM OFF" else "⛔ STOP ${state.bleSpamMode.name}"
        findViewById<Button>(R.id.toolPortalStopBtn).text = if (state.isEvilPortalActive) "⛔ STOP PORTAL" else "⛔ PORTAL OFF"
        findViewById<Button>(R.id.toolProbeFloodStopBtn).text = if (state.isProbeFloodActive) "⛔ STOP FLOOD" else "⛔ FLOOD OFF"

        styleToolButton(R.id.toolBeaconRandomBtn, state.beaconMode == WirelessToolsController.BeaconMode.RANDOM)
        styleToolButton(R.id.toolBeaconRickrollBtn, state.beaconMode == WirelessToolsController.BeaconMode.RICKROLL)
        styleToolButton(R.id.toolBeaconCloneBtn, state.beaconMode == WirelessToolsController.BeaconMode.CLONED)
        styleStopButton(R.id.toolBeaconStopBtn, state.beaconMode != WirelessToolsController.BeaconMode.OFF)
        styleToolButton(R.id.toolBleAppleBtn, state.bleSpamMode == WirelessToolsController.BleSpamMode.APPLE)
        styleToolButton(R.id.toolBleAndroidBtn, state.bleSpamMode == WirelessToolsController.BleSpamMode.ANDROID)
        styleToolButton(R.id.toolBleAllBtn, state.bleSpamMode == WirelessToolsController.BleSpamMode.ALL_IN_ONE)
        styleStopButton(R.id.toolBleStopBtn, state.bleSpamMode != WirelessToolsController.BleSpamMode.OFF)
        styleToolButton(R.id.toolHuntLaunchBtn, state.activeHuntBssid != null)
        styleStopButton(R.id.toolHuntStopBtn, state.activeHuntBssid != null)
        styleToolButton(R.id.toolHuntExportBtn, handshakeCount > 0)
        styleToolButton(R.id.toolLightRainbowBtn, state.smartLightMode == WirelessToolsController.SmartLightMode.RAINBOW)
        styleToolButton(R.id.toolLightStrobeBtn, state.smartLightMode == WirelessToolsController.SmartLightMode.STROBE)
        styleToolButton(R.id.toolPortalLaunchBtn, state.isEvilPortalActive)
        styleStopButton(R.id.toolPortalStopBtn, state.isEvilPortalActive)
        styleToolButton(R.id.toolProbeFloodStartBtn, state.isProbeFloodActive)
        styleStopButton(R.id.toolProbeFloodStopBtn, state.isProbeFloodActive)

        toolPortalViewCredsBtn.text = "🔑 HARVESTED CREDENTIALS (${state.harvestedCredentialCount})"
        toolViewStationsBtn.text = "📱 CLIENTS (${packetAnalyzer.getConnectedStations().size})"
        toolViewSkimmersBtn.text = "🕵️ SKIMMERS (${packetAnalyzer.getDetectedSkimmers().size})"
    }

    private fun updateGlobalUxDashboard(
        state: WirelessToolsController.ToolRuntimeState? = if (::toolsController.isInitialized) toolsController.getRuntimeState() else null,
        runningTools: Int = state?.let { currentRunningToolCount(it) } ?: 0,
        trafficDelta: Long = 0L,
    ) {
        if (!::uxAirspaceChipText.isInitialized) return
        val signalCount = wifiRecords.size + bleRecords.size
        val threatCount = rawRecords.values.count {
            val risk = it.optString("risk_level")
            risk == "CRITICAL" || risk == "HIGH"
        }
        val trafficLabel = when {
            trafficDelta > 0L -> "+$trafficDelta/s"
            lastObservationAt > 0L -> formatElapsed(lastObservationAt)
            else -> "IDLE"
        }
        uxAirspaceChipText.text = "AIR\n$signalCount SIG / $threatCount RISK"
        uxOpsChipText.text = if (runningTools > 0) "TOOLS\n$runningTools ON" else "TOOLS\nREADY"
        uxTrafficChipText.text = "TRAFFIC\n$trafficLabel"
        tabScannerBtn.text = "📡 SCAN ${wifiRecords.size + bleRecords.size}"
        tabRadarBtn.text = if (state?.geigerTarget != null) "🛰️ LOCK" else "🛰️ AIRSPACE"
        tabToolsBtn.text = if (runningTools > 0) "⚡ $runningTools ON" else "⚡ TOOLS"
        tabAnalyticsBtn.text = if (threatCount > 0) "📊 $threatCount RISK" else "📊 INTEL"
        tabExportsBtn.text = if (state?.isPcapActive == true) "💾 REC" else "💾 SAVE"
        val tabBtns = listOf(tabScannerBtn, tabRadarBtn, tabToolsBtn, tabAnalyticsBtn, tabExportsBtn)
        tabBtns.forEachIndexed { i, btn ->
            if (i == selectedTabIndex) {
                btn.setBackgroundResource(R.drawable.btn_3d_cyan)
                btn.setTextColor(Color.BLACK)
            }
        }
    }

    private fun currentRunningToolCount(state: WirelessToolsController.ToolRuntimeState): Int = listOf(
        state.beaconMode != WirelessToolsController.BeaconMode.OFF,
        state.bleSpamMode != WirelessToolsController.BleSpamMode.OFF,
        state.activeHuntBssid != null,
        state.isEvilPortalActive,
        state.isProbeFloodActive,
        state.isSmartLightAnimationActive,
        state.geigerTarget != null,
        state.isPcapActive,
    ).count { it }

    private fun styleToolButton(buttonId: Int, active: Boolean) {
        val button = findViewById<Button>(buttonId)
        button.setBackgroundResource(if (active) R.drawable.btn_3d_amber else R.drawable.btn_3d_dark)
        button.setTextColor(if (active) Color.BLACK else Color.WHITE)
    }

    private fun styleStopButton(buttonId: Int, active: Boolean) {
        val button = findViewById<Button>(buttonId)
        button.setBackgroundResource(if (active) R.drawable.btn_3d_pink else R.drawable.btn_3d_dark)
        button.setTextColor(if (active) Color.BLACK else Color.WHITE)
    }

    private fun appendToolEvent(message: String) {
        if (!::toolEventFeedText.isInitialized) return
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$stamp] $message"
        if (toolEventFeed.firstOrNull() == line) return
        toolEventFeed.addFirst(line)
        while (toolEventFeed.size > 8) toolEventFeed.removeLast()
        toolEventFeedText.text = toolEventFeed.joinToString("\n")
    }

    private fun formatElapsed(timestamp: Long): String {
        val elapsedSeconds = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)
        return when {
            elapsedSeconds < 2L -> "now"
            elapsedSeconds < 60L -> "${elapsedSeconds}s ago"
            else -> "${elapsedSeconds / 60L}m ago"
        }
    }

    private fun clearSessionData() {
        wifiRecords.clear()
        bleRecords.clear()
        wifiChannels.clear()
        rawRecords.clear()
        sourceCounts.clear()
        sessionLogFile.delete()
        usbLogFile.delete()
        trackPoints.clear()
        rssiHistory.clear()
        airspaceSignalTracker.clear()
        lastAirspaceSampleAt = 0L
        airspaceTrackFile.delete()
        airspaceSightingsFile.delete()
        activeVisualTrackId = null
        getSharedPreferences("airspace", MODE_PRIVATE).edit().remove("active_visual_track_id").apply()
        renderAirspaceSightings()
        decodeQueue.clear()
        decodeRunning = false
        scanPasses = 0
        lastObservationAt = 0L
        decodeText.text = ""
        threatAlertText.visibility = View.GONE
        lockAirspaceSignalTarget(null)
        geigerTargetLabel.text = "TARGET: NONE"
        renderNetworks()
        Toast.makeText(this, "Session records cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(toolStatusTicker)
        toolsController.cleanup()
        serial.disconnect()
        stopPhoneLocation()
        stopPhoneWireless()
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    private fun requestConnection() {
        val device = serial.findDevice()
        if (device == null) {
            deviceBadge.text = "USB: NO DEVICE DETECTED"
            setStatus("ESP32-C5 / CH343 not found. Connect with USB OTG.")
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
        deviceBadge.text = "USB: WAITING PERMISSION..."
    }

    private fun connect(device: android.hardware.usb.UsbDevice) {
        if (serial.connect(device)) {
            connectButton.text = "CONNECTED"
            connectedUsbLabel = "${UsbSerialController.friendlyName(device)} VID %04X PID %04X".format(device.vendorId, device.productId)
            deviceBadge.text = "USB: CONNECTED (${device.deviceName})"
            logUsbEvent("connected")
            // Send ping and set dual-band
            serial.send("{\"cmd\":\"status\"}")
        }
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
            gpsBadge.text = "GPS: PERMISSION NEEDED"
            return
        }
        try {
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(mainLooper))
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 2000L, 2f, locationListener, mainLooper)
                }
            }
            gpsBadge.text = "GPS: SEARCHING SATELLITES"
        } catch (_: SecurityException) {
            gpsBadge.text = "GPS: UNAVAILABLE"
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
                setStatus("Phone scan unavailable; ESP32-C5 active")
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
        gpsBadge.text = if (location == null) {
            "GPS: $satelliteCount SATS (SEARCHING)"
        } else {
            "GPS: $satelliteCount SATS (%.4f, %.4f ±%.0fm)".format(
                location.latitude, location.longitude, location.accuracy
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
                    "ready" -> setStatus("Ready. ESP32-C5 Dual-Band Transceiver active.")
                    "scan_start" -> {
                        scanPasses += 1
                        setStatus("Scanning 2.4G & 5G airspace...")
                    }
                    "scan_end" -> setStatus("Airspace survey updated")
                    "error" -> setStatus("Device error: ${message.optString("message", "unknown")}")
                }
                when (message.optString("type")) {
                    "wifi" -> {
                        val ssid = message.optString("ssid").ifEmpty { "<hidden>" }
                        val bssid = message.optString("bssid")
                        val key = bssid.ifEmpty { "$ssid:${message.optInt("channel")}" }
                        val channel = message.optInt("channel")
                        val security = message.optString("security", "OPEN")
                        wifiChannels[channel] = (wifiChannels[channel] ?: 0) + 1

                        if (!message.has("vendor")) {
                            val vendor = NetworkIntelligence.lookupVendor(bssid)
                            val securityScore = NetworkIntelligence.scoreSecurity(ssid, security, channel)
                            message.put("vendor", vendor)
                            message.put("device_type", NetworkIntelligence.getDeviceType(vendor))
                            message.put("security_score", securityScore.score)
                            message.put("risk_level", securityScore.riskLevel.name)
                            message.put("vulnerabilities", JSONArray(securityScore.vulnerabilities))
                            message.put("recommendations", JSONArray(securityScore.recommendations))
                        }
                        rawRecords[key] = JSONObject(message.toString())
                        rssiHistory.getOrPut(key) { mutableListOf() }.apply { add(message.optInt("rssi")); if (size > 30) removeAt(0) }
                        lastObservationAt = System.currentTimeMillis()
                        val vendorLabel = message.optString("vendor", "Unknown")
                        val riskLabel = message.optString("risk_level", "UNKNOWN")
                        val band = if (channel >= 36) "5G" else "2.4G"
                        val riskEmoji = when (riskLabel) {
                            "CRITICAL" -> "💥"
                            "HIGH" -> "⚠️"
                            "MEDIUM" -> "🛡️"
                            "LOW" -> "✨"
                            else -> "🔹"
                        }
                        val formatted = "📶 [%s] %4d dBm  ch %-2d  %-18s  🛡️ %-7s  🏰 %-9s  💎 %s/100 %s".format(
                            band, message.optInt("rssi"), channel, ssid,
                            security, vendorLabel, message.optInt("security_score"), riskEmoji
                        )
                        val isNew = !wifiRecords.containsKey(key)
                        wifiRecords[key] = formatted
                        if (isNew) {
                            if (ssid != "<hidden>") animateDecodeLabel(ssid)
                            petCompanion.onWifiDiscovered()
                            appendToolEvent("New Wi-Fi: $ssid ch $channel ${message.optInt("rssi")} dBm")
                        }
                        if (toolsController.geigerTarget?.equals(bssid, ignoreCase = true) == true) {
                            toolsController.processGeigerPing(message.optInt("rssi"))
                            recordLockedSignal(message.optInt("rssi"), bssid)
                            rssiHistoryText.text = "🎯 RSSI: ${message.optInt("rssi")} dBm [LOCK ACTIVE]"
                            updateRadarTelemetry(vulnerableCountHint = null)
                        }
                        scheduleRenderNetworks()
                    }
                    "ble", "bluetooth" -> {
                        val address = message.optString("address").ifEmpty { message.optString("mac") }
                        val name = message.optString("name").ifEmpty { address }
                        val isNew = !bleRecords.containsKey(address)
                        if (!message.has("vendor")) {
                            val vendor = NetworkIntelligence.lookupVendor(address)
                            message.put("vendor", vendor)
                            message.put("device_type", NetworkIntelligence.getDeviceType(vendor))
                        }
                        rawRecords[address] = JSONObject(message.toString())
                        rssiHistory.getOrPut(address) { mutableListOf() }.apply { add(message.optInt("rssi")); if (size > 30) removeAt(0) }
                        lastObservationAt = System.currentTimeMillis()
                        val vendorLabel = message.optString("vendor", "Unknown")
                        bleRecords[address] = "🔹 [BLE] %4d dBm          %-18s  🏰 %-9s  📍 %s".format(
                            message.optInt("rssi"),
                            name,
                            vendorLabel,
                            address
                        )

                        // Check for ATM/Pump skimmers and AirTags
                        var mfrBytes: ByteArray? = null
                        val mfrArray = message.optJSONArray("manufacturer_data")
                        if (mfrArray != null) {
                            mfrBytes = ByteArray(mfrArray.length()) { mfrArray.getInt(it).toByte() }
                        }
                        packetAnalyzer.analyzeBleAdvertisement(address, name, mfrBytes, message.optInt("rssi"))

                        if (isNew) {
                            animateDecodeLabel(name.ifEmpty { address })
                            petCompanion.onBleDiscovered()
                            appendToolEvent("New BLE: ${name.ifEmpty { address }} ${message.optInt("rssi")} dBm")
                        }
                        if (toolsController.geigerTarget?.equals(address, ignoreCase = true) == true) {
                            toolsController.processGeigerPing(message.optInt("rssi"))
                            recordLockedSignal(message.optInt("rssi"), address)
                            rssiHistoryText.text = "🎯 RSSI: ${message.optInt("rssi")} dBm [LOCK ACTIVE]"
                            updateRadarTelemetry(vulnerableCountHint = null)
                        }
                        scheduleRenderNetworks()
                    }
                    "credential_harvested" -> {
                        val ssid = message.optString("ssid")
                        val template = message.optString("template")
                        val user = message.optString("username")
                        val pass = message.optString("password")
                        val cred = WirelessToolsController.HarvestedCredential(ssid, template, user, pass)
                        toolsController.addHarvestedCredential(cred)
                        threatAlertText.visibility = View.VISIBLE
                        threatAlertText.text = "🔑 CREDENTIAL HARVESTED: [$user : $pass] on '$ssid'"
                        toolPortalViewCredsBtn.text = "🔑 HARVESTED CREDENTIALS (${toolsController.harvestedCredentials.size})"
                        updateToolStatusLabels()
                        animateDecodeLabel("CRED: $user")
                        Toast.makeText(this, "🔑 PHISHING CREDENTIAL CAPTURED: $user", Toast.LENGTH_LONG).show()
                    }
                    "raw_wifi" -> {
                        val ch = message.optInt("ch", 1)
                        val rssi = message.optInt("rssi", -50)
                        val data = message.optString("data")
                        if (data.isNotEmpty()) {
                            toolsController.handleRawWifiFrame(ch, rssi, data)
                            updateToolStatusLabels()
                        }
                    }
                }
            } catch (_: Exception) {
                LegacySerialParser.parse(line)?.let { handleLine(it.toString()) }
            }
        }
    }

    private var renderScheduled = false

    private fun scheduleRenderNetworks() {
        if (renderScheduled) return
        renderScheduled = true
        uiHandler.postDelayed({
            renderScheduled = false
            renderNetworks()
        }, 350)
    }

    private fun renderNetworks() {
        countWifiText.text = "${wifiRecords.size}\nWi-Fi APs"
        countBleText.text = "${bleRecords.size}\nBLE Beacons"
        val vulnerableCount = rawRecords.values.count {
            val risk = it.optString("risk_level")
            risk == "CRITICAL" || risk == "HIGH"
        }
        countThreatText.text = "$vulnerableCount\nVulnerable"
        updateRadarTelemetry(vulnerableCount)

        // 2.4G & 5G dual-band spectrum rendering
        if (wifiChannels.isEmpty()) {
            spectrumText.text = "No channel activity yet"
        } else {
            val sb = StringBuilder()
            val twoGhz = wifiChannels.entries.filter { it.key in 1..14 }.sortedBy { it.key }
            val fiveGhz = wifiChannels.entries.filter { it.key >= 36 }.sortedBy { it.key }

            if (twoGhz.isNotEmpty()) {
                sb.append("--- 2.4 GHz BAND ---\n")
                twoGhz.forEach { (ch, count) ->
                    sb.append("CH %-3d %-12s %d APs\n".format(ch, "█".repeat(count.coerceAtMost(10)), count))
                }
            }
            if (fiveGhz.isNotEmpty()) {
                sb.append("\n--- 5 GHz DUAL-BAND ---\n")
                fiveGhz.forEach { (ch, count) ->
                    sb.append("CH %-3d %-12s %d APs\n".format(ch, "█".repeat(count.coerceAtMost(10)), count))
                }
            }
            spectrumText.text = sb.toString()
        }

        val entries = when (selectedFilter) {
            "WIFI" -> wifiRecords.entries.toList()
            "BLE" -> bleRecords.entries.toList()
            "VULN" -> wifiRecords.entries.filter {
                val risk = rawRecords[it.key]?.optString("risk_level")
                risk == "CRITICAL" || risk == "HIGH"
            }
            else -> wifiRecords.entries.toList() + bleRecords.entries.toList()
        }.sortedBy { it.value }

        renderResultsList(entries)
    }

    private fun updateRadarTelemetry(vulnerableCountHint: Int? = null) {
        if (!::radarView.isInitialized) return
        val target = toolsController.geigerTarget
        val targetRecord = target?.let { lockedMac ->
            rawRecords.values.firstOrNull { record ->
                record.optString("bssid").equals(lockedMac, ignoreCase = true) ||
                    record.optString("mac").equals(lockedMac, ignoreCase = true) ||
                    record.optString("address").equals(lockedMac, ignoreCase = true)
            }
        }
        val targetName = targetRecord?.optString("ssid")
            ?.ifEmpty { targetRecord.optString("name") }
            ?.ifEmpty { target }
        val targetRssi = targetRecord?.optInt("rssi")
        val riskCount = vulnerableCountHint ?: rawRecords.values.count {
            val risk = it.optString("risk_level")
            risk == "CRITICAL" || risk == "HIGH"
        }
        updateAirspaceTrackDisplay(targetName)
        radarView.setTelemetry(
            wifiCount = wifiRecords.size,
            bleCount = bleRecords.size,
            riskCount = riskCount,
            targetLabel = targetName,
            targetRssi = targetRssi,
            channelHeat = wifiChannels,
        )
    }

    private fun recordLockedSignal(rssiDbm: Int, address: String) {
        val observedAt = System.currentTimeMillis()
        airspaceSignalTracker.record(rssiDbm, observedAt)
        lastAirspaceSampleAt = observedAt
        val record = JSONObject().apply {
            put("type", "airspace_signal")
            put("timestamp", observedAt)
            put("target", address)
            put("rssi_dbm", rssiDbm)
            lastLocation?.let {
                put("observer_latitude", it.latitude)
                put("observer_longitude", it.longitude)
                put("observer_accuracy_m", it.accuracy)
            }
        }
        airspaceTrackFile.appendText("$record\n")
    }

    private fun updateAirspaceTrackDisplay(targetName: String?) {
        if (!::airspaceStatusText.isInitialized) return
        val target = toolsController.geigerTarget
        if (target.isNullOrBlank()) {
            airspaceStatusText.text = "STATUS: NO RF TARGET LOCKED"
            airspaceTargetText.text = "TARGET: NONE"
            airspacePatternText.text = "FLIGHT PATH: NOT AVAILABLE FROM RF STRENGTH"
            airspaceTrackText.text = "RF TRACK: select a signal target to begin"
            return
        }

        val now = System.currentTimeMillis()
        val snapshot = airspaceSignalTracker.snapshot(now)
        val stale = lastAirspaceSampleAt == 0L || now - lastAirspaceSampleAt > 30_000L
        airspaceStatusText.text = when {
            stale && snapshot.sampleCount == 0 -> "STATUS: LOCKED / NO RECENT SIGNAL"
            snapshot.trend == SignalTrend.INSUFFICIENT -> "STATUS: LOCKED / BUILDING SIGNAL BASELINE"
            else -> "STATUS: LOCKED / ${snapshot.trend.label}"
        }
        airspaceTargetText.text = "RF TARGET: ${targetName ?: target} | $target"
        airspacePatternText.text = "FLIGHT PATH: UNKNOWN (RF TREND DOES NOT SHOW DIRECTION)"
        val elapsedSeconds = snapshot.elapsedMillis / 1000L
        val changeText = snapshot.medianChangeDb?.let { " | median change ${if (it > 0) "+" else ""}${it} dB" }.orEmpty()
        val recent = airspaceSignalTracker.recentSamples().joinToString("\n") { sample ->
            "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(sample.observedAtMillis))}  ${sample.rssiDbm} dBm"
        }
        val historyText = if (recent.isEmpty()) "No RF observations yet" else recent
        airspaceTrackText.text = "RF TRACK: ${snapshot.sampleCount} samples / ${elapsedSeconds}s$changeText\n$historyText"
    }

    private fun startNewVisualTrack() {
        activeVisualTrackId = "VIS-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}"
        getSharedPreferences("airspace", MODE_PRIVATE).edit()
            .putString("active_visual_track_id", activeVisualTrackId)
            .apply()
        renderAirspaceSightings()
        Toast.makeText(this, "New visual track started", Toast.LENGTH_SHORT).show()
    }

    private fun showAirspaceSightingDialog() {
        if (activeVisualTrackId == null) startNewVisualTrack()

        fun spinner(options: List<String>): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, options).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        fun addField(form: LinearLayout, label: String, control: View) {
            form.addView(TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(0, 8, 0, 0)
            })
            form.addView(control)
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 0)
        }
        val category = spinner(listOf("Unknown airborne light", "Likely drone / RC craft", "Aircraft / helicopter", "Balloon / lantern", "Bird / flock"))
        val confidence = spinner(listOf("Low", "Medium", "High"))
        val direction = spinner(listOf("Unknown", "Overhead", "N", "NE", "E", "SE", "S", "SW", "W", "NW"))
        val movement = spinner(listOf("Unknown", "Appears closer", "Appears farther", "Holding position", "Crossing left to right", "Crossing right to left", "Possible repeated pass"))
        val label = EditText(this).apply { hint = "e.g. white light westbound" }
        val lightPattern = EditText(this).apply { hint = "e.g. steady white, alternating red/green" }
        val notes = EditText(this).apply {
            hint = "What you observed"
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        addField(form, "Object label", label)
        addField(form, "Object category", category)
        addField(form, "Confidence", confidence)
        addField(form, "Direction", direction)
        addField(form, "Observed movement", movement)
        addField(form, "Light pattern", lightPattern)
        addField(form, "Notes", notes)

        AlertDialog.Builder(this)
            .setTitle("Log Airspace Sighting")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save sighting") { _, _ ->
                val observedAt = System.currentTimeMillis()
                val sighting = JSONObject().apply {
                    put("type", "visual_sighting")
                    put("track_id", activeVisualTrackId)
                    put("timestamp", observedAt)
                    put("label", label.text.toString().trim())
                    put("category", category.selectedItem.toString())
                    put("confidence", confidence.selectedItem.toString())
                    put("direction", direction.selectedItem.toString())
                    put("movement_observation", movement.selectedItem.toString())
                    put("light_pattern", lightPattern.text.toString().trim())
                    put("notes", notes.text.toString().trim())
                    lastLocation?.let {
                        put("observer_latitude", it.latitude)
                        put("observer_longitude", it.longitude)
                        put("observer_accuracy_m", it.accuracy)
                    }
                }
                airspaceSightingsFile.appendText("$sighting\n")
                renderAirspaceSightings()
                Toast.makeText(this, "Sighting saved to this device", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderAirspaceSightings() {
        if (!::airspaceTimelineText.isInitialized) return
        activeVisualTrackId = activeVisualTrackId ?: getSharedPreferences("airspace", MODE_PRIVATE)
            .getString("active_visual_track_id", null)
        val trackId = activeVisualTrackId
        if (trackId == null) {
            airspaceVisualTrackText.text = "VISUAL TRACK: NONE"
            airspaceTimelineText.text = "No visual sightings logged"
            return
        }
        val entries = if (airspaceSightingsFile.exists()) {
            airspaceSightingsFile.readLines().mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }.filter { it.optString("track_id") == trackId }.takeLast(6)
        } else emptyList()
        val patternSummary = when {
            entries.any { it.optString("movement_observation") == "Possible repeated pass" } -> "POSSIBLE REPEAT REPORTED"
            entries.size < 2 -> "NEED 2+ SIGHTINGS"
            else -> "MULTIPLE SIGHTINGS"
        }
        airspaceVisualTrackText.text = "VISUAL TRACK: $trackId | ${entries.size} sightings | $patternSummary"
        airspaceTimelineText.text = if (entries.isEmpty()) "No sightings on this track yet" else entries.asReversed().joinToString("\n") { entry ->
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.optLong("timestamp")))
            val label = entry.optString("label").ifEmpty { entry.optString("category") }
            "$time  $label / ${entry.optString("direction")} / ${entry.optString("movement_observation")}"
        }
    }

    private fun shareAirspaceLog() {
        val files = listOf(airspaceSightingsFile, airspaceTrackFile).filter { it.exists() && it.length() > 0L }
        if (files.isEmpty()) {
            Toast.makeText(this, "No Airspace Monitor observations to export", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = files.flatMap { file -> file.readLines().filter { it.isNotBlank() } }.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish Airspace Monitor log")
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        startActivity(Intent.createChooser(intent, "Export Airspace Monitor log"))
    }

    private fun renderResultsList(entries: List<Map.Entry<String, String>>) {
        if (entries.isEmpty()) {
            resultsText.text = "No observations matching filter [$selectedFilter]..."
            return
        }
        val builder = android.text.SpannableStringBuilder()
        entries.forEachIndexed { index, entry ->
            val start = builder.length
            builder.append(entry.value)
            val riskLevel = rawRecords[entry.key]?.optString("risk_level")
            val color = riskColor(riskLevel)
            builder.setSpan(
                android.text.style.ForegroundColorSpan(color),
                start, builder.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            builder.setSpan(
                object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) = showNetworkDetailDialog(entry.key)
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.color = color
                        ds.isUnderlineText = false
                    }
                },
                start, builder.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (index != entries.lastIndex) builder.append("\n")
        }
        resultsText.text = builder
        resultsText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        resultsText.highlightColor = Color.TRANSPARENT
    }

    private fun riskColor(riskLevel: String?): Int = when (riskLevel) {
        "CRITICAL" -> Color.parseColor("#FF1744")
        "HIGH" -> Color.parseColor("#FF9100")
        "MEDIUM" -> Color.parseColor("#FFD600")
        "LOW" -> Color.parseColor("#00E676")
        else -> currentTheme.primaryAccent
    }

    private fun showNetworkDetailDialog(key: String) {
        val record = rawRecords[key] ?: return
        val type = record.optString("type")
        val bssid = record.optString("bssid").ifEmpty { record.optString("mac") }.ifEmpty { record.optString("address") }
        val ssid = record.optString("ssid").ifEmpty { record.optString("name") }
        val channel = record.optInt("channel", 1)

        val message = buildString {
            append("Address: $bssid\n")
            if (channel > 0) append("Channel: $channel (${if (channel >= 36) "5GHz" else "2.4GHz"})\n")
            append("Vendor: ${record.optString("vendor", "Unknown")}\n")
            append("Device type: ${record.optString("device_type", "Unknown")}\n")
            if (record.has("security_score")) {
                append("Security score: ${record.optInt("security_score")}/100\n")
                append("Risk level: ${record.optString("risk_level", "UNKNOWN")}\n")
            }
            val vulnerabilities = record.optJSONArray("vulnerabilities")
            if (vulnerabilities != null && vulnerabilities.length() > 0) {
                append("\nVulnerabilities:\n")
                for (i in 0 until vulnerabilities.length()) append("- ${vulnerabilities.getString(i)}\n")
            }
            val recommendations = record.optJSONArray("recommendations")
            if (recommendations != null && recommendations.length() > 0) {
                append("\nRecommendations:\n")
                for (i in 0 until recommendations.length()) append("- ${recommendations.getString(i)}\n")
            }
        }
        val title = ssid.ifEmpty { key }
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNeutralButton("🎯 Lock Geiger") { _, _ ->
                lockAirspaceSignalTarget(bssid, record)
                geigerTargetLabel.text = "TARGET: $title ($bssid)"
                Toast.makeText(this, "Geiger Hunter locked on $bssid", Toast.LENGTH_SHORT).show()
            }

        if (type == "wifi") {
            builder.setNegativeButton("⚡ Deauth Pulse") { _, _ ->
                toolsController.sendDeauthBurst(bssid, channel = channel, count = 15)
                Toast.makeText(this, "Deauth pulse sent to $bssid", Toast.LENGTH_SHORT).show()
            }
        }

        builder.show()
    }

    private fun showGeigerTargetDialog() {
        val allEntries = rawRecords.values.toList()
        if (allEntries.isEmpty()) {
            Toast.makeText(this, "No signals discovered to target yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf("❌ UNLOCK / CLEAR TARGET")
        allEntries.forEach { record ->
            val type = record.optString("type").uppercase(Locale.US)
            val mac = record.optString("bssid").ifEmpty { record.optString("mac") }.ifEmpty { record.optString("address") }
            val name = record.optString("ssid").ifEmpty { record.optString("name").ifEmpty { mac } }
            val rssi = record.optInt("rssi")
            labels.add("[$type] $name ($mac)  $rssi dBm")
        }

        AlertDialog.Builder(this)
            .setTitle("🎯 Select Geiger Target")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    lockAirspaceSignalTarget(null)
                    geigerTargetLabel.text = "TARGET: NONE"
                    Toast.makeText(this, "Geiger target cleared", Toast.LENGTH_SHORT).show()
                } else {
                    val targetRecord = allEntries[which - 1]
                    val mac = targetRecord.optString("bssid").ifEmpty { targetRecord.optString("mac") }.ifEmpty { targetRecord.optString("address") }
                    val name = targetRecord.optString("ssid").ifEmpty { targetRecord.optString("name").ifEmpty { mac } }
                    lockAirspaceSignalTarget(mac, targetRecord)
                    geigerTargetLabel.text = "TARGET: $name ($mac)"
                    Toast.makeText(this, "Geiger Hunter locked on $mac", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun lockAirspaceSignalTarget(address: String?, record: JSONObject? = null) {
        toolsController.setGeigerTarget(address)
        getSharedPreferences("airspace", MODE_PRIVATE).edit().apply {
            if (address.isNullOrBlank()) remove("active_rf_target") else putString("active_rf_target", address)
        }.apply()
        airspaceSignalTracker.clear()
        lastAirspaceSampleAt = 0L
        if (!address.isNullOrBlank() && record != null) {
            recordLockedSignal(record.optInt("rssi"), address)
        }
        updateAirspaceTrackDisplay(record?.optString("ssid")?.ifEmpty { record.optString("name") } ?: address)
    }

    private fun showDeauthTargetDialog(wifiAps: List<JSONObject>) {
        if (wifiAps.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi APs discovered yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = wifiAps.map { ap ->
            val security = ap.optString("security", "?")
            val score = ap.optInt("security_score", -1)
            val scoreText = if (score >= 0) " score $score/100" else ""
            "${ap.optString("ssid").ifEmpty { "<hidden>" }} (${ap.optString("bssid")}) ch ${ap.optInt("channel")} $security$scoreText ${ap.optInt("rssi")} dBm"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("⚡ Target Deauth Pulse")
            .setItems(labels) { _, which ->
                val selected = wifiAps[which]
                val bssid = selected.optString("bssid")
                val channel = selected.optInt("channel", 1)
                toolsController.sendDeauthBurst(bssid, channel = channel, count = 20)
                Toast.makeText(this, "Deauthing $bssid on ch $channel...", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showHandshakeHuntDialog(wifiAps: List<JSONObject>) {
        if (wifiAps.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi APs discovered yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = wifiAps.map { ap ->
            "${ap.optString("ssid")} (${ap.optString("bssid")}) ch ${ap.optInt("channel")}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("🔑 Select AP for Handshake Capture")
            .setItems(labels) { _, which ->
                val selected = wifiAps[which]
                val bssid = selected.optString("bssid")
                val ssid = selected.optString("ssid")
                val channel = selected.optInt("channel", 1)
                huntStartedAt = System.currentTimeMillis()
                huntTargetSsid = ssid.ifEmpty { "<hidden>" }
                huntTargetBssid = bssid
                huntInitialKeyCount = packetAnalyzer.getCapturedHandshakes().size
                huntStageText.text = "STAGE: ARMING | tuning ch $channel and starting PCAP"
                huntChecklistText.text = "Target: $huntTargetSsid\nSecurity: ${selected.optString("security", "?")} | RSSI ${selected.optInt("rssi")} dBm\nPCAP: starting | Keys this hunt: 0"
                toolsController.startHandshakeHunt(bssid, ssid, channel)
                appendToolEvent("Handshake hunter target: $huntTargetSsid $bssid ch $channel")
                Toast.makeText(this, "Hunting handshake for $ssid on ch $channel", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showClientProbesDialog() {
        val probes = packetAnalyzer.getProbedClients()
        if (probes.isEmpty()) {
            Toast.makeText(this, "No client probe requests observed yet", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        probes.forEach { (clientMac, ssids) ->
            sb.append("CLIENT: $clientMac\n")
            ssids.forEach { ssid -> sb.append("  ↳ Probed SSID: \"$ssid\"\n") }
            sb.append("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("📋 Sniffed Client Probes (Karma Recon)")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showEvilPortalLaunchDialog() {
        val templates = arrayOf(
            "⚠️ Router Firmware Update (WPA Password Prompt)",
            "📶 Free Public Wi-Fi Portal (Login Prompt)",
            "🔐 Google Sign-in Verification"
        )
        val templateKeys = arrayOf("router", "wifi", "google")

        val input = android.widget.EditText(this).apply {
            hint = "Broadcast SSID (e.g. Free-Public-WiFi)"
            setText("Free-WiFi")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        AlertDialog.Builder(this)
            .setTitle("🕸️ Launch Evil Captive Portal")
            .setView(input)
            .setItems(templates) { _, which ->
                val chosenSsid = input.text.toString().ifEmpty { "Free-WiFi" }
                val chosenTemplate = templateKeys[which]
                toolsController.startEvilPortal(chosenSsid, chosenTemplate)
                updateToolStatusLabels()
                Toast.makeText(this, "Evil Portal running on SSID: $chosenSsid", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHarvestedCredentialsDialog() {
        val creds = toolsController.harvestedCredentials
        if (creds.isEmpty()) {
            Toast.makeText(this, "No credentials harvested yet", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        creds.forEachIndexed { i, c ->
            sb.append("#${i + 1} [SSID: ${c.ssid}] [${c.template.uppercase()}]\n")
            if (c.username.isNotEmpty()) sb.append("  Username: ${c.username}\n")
            sb.append("  Password: ${c.password}\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("🔑 Harvested Phishing Credentials (${creds.size})")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .setNeutralButton("Share / Copy") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Wigglefish Harvested Credentials")
                    putExtra(Intent.EXTRA_TEXT, sb.toString())
                }
                startActivity(Intent.createChooser(intent, "Export Credentials"))
            }
            .show()
    }

    private fun showConnectedStationsDialog() {
        val stations = packetAnalyzer.getConnectedStations()
        if (stations.isEmpty()) {
            Toast.makeText(this, "No active client station packets observed yet", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        stations.take(25).forEach { sta ->
            val vendor = NetworkIntelligence.lookupVendor(sta.clientMac)
            sb.append("📱 STA: ${sta.clientMac} (${vendor})\n")
            sb.append("   ↳ Connected to AP: ${sta.apBssid} | Pkts: ${sta.packetCount} | RSSI: ${sta.lastRssi} dBm\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("📱 Active Client Stations (${stations.size})")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showBleSkimmersDialog() {
        val skimmers = packetAnalyzer.getDetectedSkimmers()
        if (skimmers.isEmpty()) {
            Toast.makeText(this, "No rogue skimmer modules or AirTags detected nearby", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        skimmers.forEach { s ->
            sb.append("🚨 [${s.type}] ${s.name}\n")
            sb.append("   MAC: ${s.address} | RSSI: ${s.rssi} dBm\n")
            sb.append("   Detail: ${s.detail}\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("🕵️ Rogue Skimmers & Tracker Tags (${skimmers.size})")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showWpsPmfDialog() {
        val audits = packetAnalyzer.getWpsPmfAudits()
        if (audits.isEmpty()) {
            Toast.makeText(this, "No WPS or PMF beacons decoded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        audits.values.take(25).forEach { a ->
            val wpsText = when {
                !a.wpsEnabled -> "WPS: Disabled"
                a.wpsLocked -> "WPS: 🔒 LOCKED"
                else -> "WPS: ⚠️ VULNERABLE (UNLOCKED)"
            }
            val pmfText = when {
                a.pmfRequired -> "PMF: 🛡️ Required (802.11w Protected)"
                a.pmfCapable -> "PMF: Optional (Capable)"
                else -> "PMF: ⚠️ None (Vulnerable to Deauth)"
            }
            sb.append("📶 ${a.ssid} (${a.bssid})\n")
            sb.append("   $wpsText\n")
            sb.append("   $pmfText\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("🛡️ WPS & 802.11w PMF Security Audit (${audits.size})")
            .setMessage(sb.toString())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateAnalyticsContent() {
        val wifiEntries = rawRecords.values.filter { it.optString("type") == "wifi" }
        val bleCount = bleRecords.size
        val stationCount = packetAnalyzer.getConnectedStations().size
        val skimmerCount = packetAnalyzer.getDetectedSkimmers().size
        val handshakeCount = packetAnalyzer.getCapturedHandshakes().size
        val wpsPmfCount = packetAnalyzer.getWpsPmfAudits().size
        if (wifiEntries.isEmpty()) {
            intelPostureText.text = "POSTURE: WAITING FOR AIRSPACE DATA"
            intelCountsText.text = "0 WIFI\n$bleCount BLE\n0 RISK"
            intelTopFindingsText.text = "No Wi-Fi observations yet. BLE, client, and risk intel will populate as packets arrive."
            analyticsContentText.text = "Start scanning or connect the ESP32-C5 to build an intelligence report.\n\nCLIENTS: $stationCount\nSKIMMERS: $skimmerCount\nHANDSHAKES: $handshakeCount\nWPS/PMF AUDITS: $wpsPmfCount"
            updateIntelActionButtons(stationCount, skimmerCount, wpsPmfCount, handshakeCount)
            return
        }
        val encryption = wifiEntries.groupingBy { it.optString("security", "OPEN") }.eachCount()
        val riskCounts = wifiEntries.groupingBy { it.optString("risk_level", "UNKNOWN") }.eachCount()
        val vendorCounts = wifiEntries.groupingBy { it.optString("vendor", "Unknown") }.eachCount()
            .entries.sortedByDescending { it.value }.take(5)
        val avgScore = wifiEntries.mapNotNull { if (it.has("security_score")) it.optInt("security_score") else null }
            .let { if (it.isEmpty()) 0.0 else it.average() }
        val highRiskCount = riskCounts.getOrDefault("CRITICAL", 0) + riskCounts.getOrDefault("HIGH", 0)
        val openCount = encryption.getOrDefault("OPEN", 0)
        val weakCount = encryption.getOrDefault("WEP", 0) + openCount
        val twoGhzCount = wifiEntries.count { it.optInt("channel") in 1..14 }
        val fiveGhzCount = wifiEntries.count { it.optInt("channel") >= 36 }
        val posture = when {
            highRiskCount > 0 -> "HIGH RISK"
            weakCount > 0 -> "WATCHLIST"
            avgScore >= 80.0 -> "CLEAN"
            else -> "MIXED"
        }
        val postureColor = when (posture) {
            "HIGH RISK" -> Color.parseColor("#FF3366")
            "WATCHLIST" -> Color.parseColor("#FFE600")
            "CLEAN" -> Color.parseColor("#00F5D4")
            else -> Color.parseColor("#B3E5FC")
        }
        intelPostureText.text = "POSTURE: $posture | SCORE %.0f/100 | $highRiskCount HIGH RISK".format(avgScore)
        intelPostureText.setTextColor(postureColor)
        intelCountsText.text = "${wifiEntries.size} WIFI\n$bleCount BLE\n$highRiskCount RISK"
        intelTopFindingsText.text = buildString {
            append("Bands: 2.4G $twoGhzCount / 5G $fiveGhzCount\n")
            append("Weak/Open: $weakCount | Clients: $stationCount\n")
            append("Skimmers: $skimmerCount | Handshakes: $handshakeCount")
        }
        updateIntelActionButtons(stationCount, skimmerCount, wpsPmfCount, handshakeCount)

        val message = buildString {
            append("-- EXECUTIVE SUMMARY --\n")
            append("POSTURE: $posture\n")
            append("TOTAL WI-FI NETWORKS: ${wifiEntries.size}\n")
            append("BLE DEVICES: $bleCount\n")
            append("CHANNELS IN USE: ${wifiChannels.size} (2.4G: $twoGhzCount / 5G: $fiveGhzCount)\n")
            append("AVERAGE SECURITY SCORE: %.0f/100\n".format(avgScore))
            append("OPEN/WEP WATCHLIST: $weakCount\n")
            append("CLIENT STATIONS: $stationCount\n")
            append("SKIMMER/TRACKER ALERTS: $skimmerCount\n")
            append("CAPTURED HANDSHAKES: $handshakeCount\n")
            append("WPS/PMF AUDITS: $wpsPmfCount\n\n")
            append("-- ENCRYPTION BREAKDOWN --\n")
            encryption.entries.sortedByDescending { it.value }.forEach { append("${it.key}: ${it.value}\n") }
            append("\n-- RISK DISTRIBUTION --\n")
            riskCounts.entries.sortedByDescending { it.value }.forEach { append("${it.key}: ${it.value}\n") }
            append("\n-- TOP DETECTED VENDORS --\n")
            vendorCounts.forEach { append("${it.key}: ${it.value}\n") }
            append("\n-- PRIORITY WATCHLIST --\n")
            val watchlist = wifiEntries
                .filter { it.optString("risk_level") == "CRITICAL" || it.optString("risk_level") == "HIGH" || it.optString("security") in listOf("OPEN", "WEP") }
                .sortedBy { it.optInt("security_score", 100) }
                .take(8)
            if (watchlist.isEmpty()) {
                append("No high-risk or open/WEP networks identified yet.\n")
            } else {
                watchlist.forEach { record ->
                    append("${record.optString("risk_level", "UNKNOWN")} | ${record.optString("security", "?")} | ch ${record.optInt("channel")} | ${record.optString("ssid", "<hidden>")} | ${record.optString("bssid")}\n")
                }
            }
        }
        analyticsContentText.text = message
    }

    private fun updateIntelActionButtons(stationCount: Int, skimmerCount: Int, wpsPmfCount: Int, handshakeCount: Int) {
        if (!::intelStationsBtn.isInitialized) return
        intelStationsBtn.text = "STA $stationCount"
        intelSkimmersBtn.text = "SKIM $skimmerCount"
        intelWpsPmfBtn.text = "WPS $wpsPmfCount"
        intelHashesBtn.text = "KEY $handshakeCount"
    }

    private fun sharePcap() {
        val writer = toolsController.getPcapWriter()
        val pcapFile = writer?.getFile() ?: File(filesDir, "wigglefish-session.pcap")
        if (!pcapFile.exists() || pcapFile.length() <= 24L) {
            toolsController.startPcapCapture("wigglefish-live-${System.currentTimeMillis()}.pcap")
            Toast.makeText(this, "PCAP recording started. Packets will stream to file.", Toast.LENGTH_SHORT).show()
            return
        }
        writer?.flush()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.tcpdump.pcap"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish 802.11 PCAP Capture")
            putExtra(Intent.EXTRA_TEXT, "Captured ${writer?.getPacketCount() ?: 0} 802.11 frames from ESP32 dumb radio.")
        }
        startActivity(Intent.createChooser(intent, "Export PCAP Capture"))
    }

    private fun shareHashcat22000() {
        val handshakes = packetAnalyzer.getCapturedHandshakes()
        if (handshakes.isEmpty()) {
            Toast.makeText(this, "No WPA/WPA2 handshakes or PMKIDs captured yet", Toast.LENGTH_SHORT).show()
            return
        }
        val content = handshakes.joinToString("\n") { it.hashcat22000 }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish Captured Handshakes (Hashcat 22000)")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(intent, "Export Hashcat 22000 Hashes"))
    }

    private fun shareSession() {
        if (rawRecords.isEmpty()) {
            Toast.makeText(this, "No observations to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = JSONArray(rawRecords.values.map { JSONObject(it.toString()) })
        if (airspaceTrackFile.exists()) {
            airspaceTrackFile.forEachLine { line ->
                if (line.isNotBlank()) payload.put(JSONObject(line))
            }
        }
        if (airspaceSightingsFile.exists()) {
            airspaceSightingsFile.forEachLine { line ->
                if (line.isNotBlank()) payload.put(JSONObject(line))
            }
        }
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

    private fun shareGpx() {
        if (trackPoints.isEmpty()) {
            Toast.makeText(this, "No GPS track recorded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val points = trackPoints.joinToString("\n") { point ->
            "<trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\"><time>${format.format(Date(point.time))}</time></trkpt>"
        }
        val gpx = "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"Wigglefish\"><trk><name>Wigglefish passive survey</name><trkseg>$points</trkseg></trk></gpx>"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_SUBJECT, "Wigglefish GPS track")
            putExtra(Intent.EXTRA_TEXT, gpx)
        }
        startActivity(Intent.createChooser(intent, "Export GPX track"))
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
            }, frame * 100L)
        }
        uiHandler.postDelayed({
            decodeText.text = ""
            decodeRunning = false
            runNextDecode()
        }, (frames + 1) * 100L)
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

    private fun setStatus(status: String) {
        runOnUiThread {
            if (::appStatusText.isInitialized) {
                appStatusText.text = "OPS: ${status}"
            }
            if (::toolLastEventText.isInitialized) {
                toolLastEventText.text = "Last event: $status"
            }
            appendToolEvent(status)
            if (status.contains("USB connection lost", true)) {
                logUsbEvent("disconnected")
                deviceBadge.text = "USB: DISCONNECTED"
                connectedUsbLabel = ""
            }
        }
    }
}
