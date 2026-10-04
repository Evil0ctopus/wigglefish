package com.wigglefish.android

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID

class SmartLightBleController(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit,
) {
    data class DiscoveredLight(
        val address: String,
        val name: String,
        val rssi: Int,
        val device: BluetoothDevice,
        val lastSeen: Long,
    )

    private enum class Protocol { LOTUS, TRIONES, ELK_BLEDOM }

    private data class ControlEndpoint(
        val characteristic: BluetoothGattCharacteristic,
        val protocol: Protocol,
    )

    companion object {
        private val SERVICE_LOTUS = uuid16(0xFFD5)
        private val SERVICE_TRIONES = uuid16(0xFFE0)
        private val SERVICE_ELK = uuid16(0xFFF0)
        private val SERVICE_ELK_ALT = uuid16(0xFFD0)
        private val CHAR_LOTUS = uuid16(0xFFD9)
        private val CHAR_TRIONES = uuid16(0xFFE1)
        private val CHAR_ELK = uuid16(0xFFF3)
        private val CHAR_ELK_ALT = uuid16(0xFFE1)
        private const val DISCOVERY_TTL_MS = 30_000L
        private const val NO_RESPONSE_WRITE_DELAY_MS = 150L

        private fun uuid16(value: Int): UUID =
            UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(value))
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val discovered = linkedMapOf<String, DiscoveredLight>()
    private var gatt: BluetoothGatt? = null
    private var endpoint: ControlEndpoint? = null
    private var writeInFlight = false
    private var pendingPayload: ByteArray? = null
    private var activeWriteToken = 0L
    private var lastStatus = "No light selected"

    val statusLabel: String
        @Synchronized get() = lastStatus

    val isConnected: Boolean
        @Synchronized get() = endpoint != null

    @Synchronized
    fun observe(result: ScanResult) {
        val record = result.scanRecord
        val name = record?.deviceName.orEmpty()
        val advertisedServices = record?.serviceUuids.orEmpty().map { it.uuid }
        val isSupported = isSupportedName(name) || advertisedServices.any(::isSupportedService)
        if (!isSupported) return

        val address = result.device.address
        val previous = discovered[address]
        discovered[address] = DiscoveredLight(
            address = address,
            name = name.ifEmpty { previous?.name ?: "Compatible RGB light" },
            rssi = result.rssi,
            device = result.device,
            lastSeen = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun discoveredLights(): List<DiscoveredLight> {
        val cutoff = System.currentTimeMillis() - DISCOVERY_TTL_MS
        discovered.entries.removeAll { it.value.lastSeen < cutoff }
        return discovered.values.sortedByDescending { it.lastSeen }
    }

    @Synchronized
    fun connectTo(light: DiscoveredLight) {
        if (!hasConnectPermission()) {
            setStatus("Allow Nearby Devices permission to connect to your selected light")
            return
        }
        closeGatt()
        setStatus("Connecting to ${light.name}…")
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                light.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                light.device.connectGatt(context, false, gattCallback)
            }
            if (gatt == null) setStatus("Could not start a connection to ${light.name}")
        } catch (error: SecurityException) {
            setStatus("Bluetooth permission denied: ${error.message ?: "connection blocked"}")
        }
    }

    @Synchronized
    fun disconnect() {
        closeGatt()
        setStatus("Disconnected")
    }

    @Synchronized
    fun sendColor(red: Int, green: Int, blue: Int): Boolean {
        val control = endpoint ?: return notConnected()
        val payload = when (control.protocol) {
            Protocol.LOTUS -> RadioProtocols.createLotusLanternRgb(red, green, blue)
            Protocol.TRIONES -> RadioProtocols.createTrionesRgb(red, green, blue)
            Protocol.ELK_BLEDOM -> RadioProtocols.createElkBledomRgb(red, green, blue)
        }
        return write(control.characteristic, payload)
    }

    @Synchronized
    fun sendPower(on: Boolean): Boolean {
        val control = endpoint ?: return notConnected()
        val payload = when (control.protocol) {
            Protocol.LOTUS -> if (on) {
                byteArrayOf(0x7E, 0x04, 0x04, 0xF0.toByte(), 0x00, 0x01, 0xFF.toByte(), 0x00, 0xEF.toByte())
            } else {
                byteArrayOf(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x00, 0xEF.toByte())
            }
            Protocol.TRIONES -> byteArrayOf(
                0xCC.toByte(),
                if (on) 0x23 else 0x24,
                0x33,
            )
            Protocol.ELK_BLEDOM -> RadioProtocols.createSmartLightPower(on)
        }
        return write(control.characteristic, payload)
    }

    @Synchronized
    fun close() {
        closeGatt()
        discovered.clear()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                synchronized(this@SmartLightBleController) {
                    if (this@SmartLightBleController.gatt === gatt) {
                        endpoint = null
                        closeGatt()
                        setStatus("Light connection failed (GATT $status)")
                    }
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    synchronized(this@SmartLightBleController) {
                        if (this@SmartLightBleController.gatt !== gatt) return
                        setStatus("Connected; checking supported light controls…")
                    }
                    try {
                        if (!gatt.discoverServices()) {
                            synchronized(this@SmartLightBleController) {
                                setStatus("Connected, but service discovery could not start")
                            }
                        }
                    } catch (error: SecurityException) {
                        synchronized(this@SmartLightBleController) {
                            setStatus("Bluetooth permission denied: ${error.message ?: "service discovery blocked"}")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    synchronized(this@SmartLightBleController) {
                        if (this@SmartLightBleController.gatt === gatt) {
                            endpoint = null
                            closeGatt()
                            setStatus("Light disconnected")
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            synchronized(this@SmartLightBleController) {
                if (this@SmartLightBleController.gatt !== gatt) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    setStatus("Could not inspect the selected light's GATT services ($status)")
                    return
                }
                endpoint = findEndpoint(gatt.services)
                val control = endpoint
                if (control == null) {
                    setStatus("This light does not expose a supported Triones, Lotus, or ELK-BLEDOM control service")
                } else {
                    val lightName = discovered.values.firstOrNull { it.address == gatt.device.address }?.name
                        ?: "Selected light"
                    setStatus("$lightName connected (${control.protocol.name.replace('_', '-')})")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            synchronized(this@SmartLightBleController) {
                if (this@SmartLightBleController.gatt !== gatt) return
                writeInFlight = false
                activeWriteToken++
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    pendingPayload = null
                    setStatus("Light rejected the RGB command (GATT $status)")
                    return
                }
                val nextPayload = pendingPayload
                pendingPayload = null
                val currentEndpoint = endpoint
                if (nextPayload != null && currentEndpoint != null) {
                    write(currentEndpoint.characteristic, nextPayload)
                }
            }
        }
    }

    private fun findEndpoint(services: List<BluetoothGattService>): ControlEndpoint? {
        val definitions = listOf(
            Triple(SERVICE_LOTUS, CHAR_LOTUS, Protocol.LOTUS),
            Triple(SERVICE_TRIONES, CHAR_TRIONES, Protocol.TRIONES),
            Triple(SERVICE_ELK, CHAR_ELK, Protocol.ELK_BLEDOM),
            Triple(SERVICE_ELK_ALT, CHAR_ELK_ALT, Protocol.ELK_BLEDOM),
        )
        for ((serviceUuid, characteristicUuid, protocol) in definitions) {
            val characteristic = services
                .firstOrNull { it.uuid == serviceUuid }
                ?.getCharacteristic(characteristicUuid)
                ?: continue
            val canWrite = characteristic.properties and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (canWrite) return ControlEndpoint(characteristic, protocol)
        }
        return null
    }

    private fun write(characteristic: BluetoothGattCharacteristic, payload: ByteArray): Boolean {
        val connection = gatt
        if (connection == null || !hasConnectPermission()) {
            setStatus("Reconnect to your light before sending a command")
            return false
        }
        if (writeInFlight) {
            pendingPayload = payload
            return true
        }

        val supportsResponse = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        characteristic.writeType = if (supportsResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        writeInFlight = true
        activeWriteToken++
        val writeToken = activeWriteToken
        try {
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                connection.writeCharacteristic(
                    characteristic,
                    payload,
                    characteristic.writeType,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                connection.writeCharacteristic(characteristic)
            }
            if (!started) {
                writeInFlight = false
                setStatus("Bluetooth could not queue the light command")
                return false
            }
            setStatus("RGB command sent to selected light")
            if (!supportsResponse) {
                mainHandler.postDelayed({
                    synchronized(this) {
                        if (gatt === connection && writeInFlight && activeWriteToken == writeToken) {
                            writeInFlight = false
                            val nextPayload = pendingPayload
                            pendingPayload = null
                            val currentEndpoint = endpoint
                            if (nextPayload != null && currentEndpoint != null) {
                                write(currentEndpoint.characteristic, nextPayload)
                            }
                        }
                    }
                }, NO_RESPONSE_WRITE_DELAY_MS)
            }
            return true
        } catch (error: SecurityException) {
            writeInFlight = false
            setStatus("Bluetooth permission denied: ${error.message ?: "light command blocked"}")
            return false
        }
    }

    private fun notConnected(): Boolean {
        setStatus("Find and connect to one of your compatible lights first")
        return false
    }

    @Synchronized
    private fun closeGatt() {
        val current = gatt
        gatt = null
        endpoint = null
        writeInFlight = false
        activeWriteToken++
        pendingPayload = null
        if (current != null && hasConnectPermission()) {
            try {
                current.disconnect()
                current.close()
            } catch (error: SecurityException) {
                setStatus("Bluetooth permission denied while disconnecting: ${error.message ?: "operation blocked"}")
            }
        }
    }

    @Synchronized
    private fun setStatus(message: String) {
        lastStatus = message
        mainHandler.post { onStatusUpdate(message) }
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun isSupportedName(name: String): Boolean {
        val normalized = name.uppercase()
        return listOf("ELK-BLEDOM", "BLEDOM", "TRIONES", "LOTUS", "HAPPYLIGHT", "MAGIC BLUE", "LED STRIP")
            .any(normalized::contains)
    }

    private fun isSupportedService(uuid: UUID): Boolean =
        uuid == SERVICE_LOTUS || uuid == SERVICE_TRIONES || uuid == SERVICE_ELK || uuid == SERVICE_ELK_ALT

}
