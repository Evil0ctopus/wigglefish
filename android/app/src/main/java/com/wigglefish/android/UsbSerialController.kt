package com.wigglefish.android

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbSerialController(
    private val usbManager: UsbManager,
    private val onLine: (String) -> Unit,
    private val onState: (String) -> Unit,
) {
    companion object {
        const val vendorId = 0x1A86
        const val productId = 0x55D3
    }

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    fun findDevice(): UsbDevice? = usbManager.deviceList.values.firstOrNull {
        it.vendorId == vendorId && it.productId == productId
    }

    fun connect(device: UsbDevice): Boolean {
        disconnect()
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: run {
                onState("CH343 driver not recognized")
                return false
            }
        val selectedPort = driver.ports.firstOrNull()
            ?: run {
                onState("No serial interface found")
                return false
            }
        val selectedConnection = usbManager.openDevice(device)
            ?: run {
                onState("Android could not open the USB device")
                return false
            }
        return try {
            selectedPort.open(selectedConnection)
            selectedPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            connection = selectedConnection
            port = selectedPort
            running = true
            reader = Thread { readLoop(selectedPort) }.also { it.start() }
            onState("Connected to ${device.deviceName} at 115200 baud")
            true
        } catch (error: Exception) {
            selectedConnection.close()
            onState("Serial connection failed: ${error.message}")
            false
        }
    }

    fun disconnect() {
        running = false
        reader?.interrupt()
        reader = null
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
        connection?.close()
        connection = null
    }

    private fun readLoop(serialPort: UsbSerialPort) {
        val buffer = ByteArray(512)
        val line = StringBuilder()
        while (running && !Thread.currentThread().isInterrupted) {
            try {
                val count = serialPort.read(buffer, 1000)
                for (index in 0 until count) {
                    when (val character = buffer[index].toInt().toChar()) {
                        '\n' -> {
                            if (line.isNotEmpty()) {
                                onLine(line.toString().trim())
                                line.clear()
                            }
                        }
                        '\r' -> Unit
                        else -> if (line.length < 4096) line.append(character)
                    }
                }
            } catch (error: Exception) {
                if (running) onState("USB connection lost: ${error.message}")
                break
            }
        }
    }
}
