package com.wigglefish.android

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.wigglefish.android.esp.EspSerialTransport
import java.io.IOException

class UsbSerialController(
    private val usbManager: UsbManager,
    private val onLine: (String) -> Unit,
    private val onState: (String) -> Unit,
) : EspSerialTransport {
    companion object {
        const val vendorId = 0x1A86
        const val productId = 0x55D3

        fun friendlyName(device: UsbDevice): String {
            return when (device.vendorId to device.productId) {
                0x303A to 0x1001 -> "Espressif ESP32 native USB/JTAG"
                0x1A86 to 0x7523 -> "WCH CH340 USB-serial (CYD likely)"
                0x1A86 to 0x55D3 -> "WCH CH343 USB-serial"
                0x1A86 to 0x55D4 -> "WCH CH340 USB-serial"
                0x10C4 to 0xEA60 -> "Silicon Labs CP210x USB-serial"
                0x0403 to 0x6001 -> "FTDI USB-serial"
                0x0483 to 0x5740 -> "Flipper Zero USB serial"
                else -> device.productName ?: device.manufacturerName ?: "Unknown USB serial device"
            }
        }
    }

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var attachedDevice: UsbDevice? = null
    private var reader: Thread? = null
    @Volatile private var running = false
    @Volatile private var binaryMode = false

    fun findDevice(): UsbDevice? {
        val devices = usbManager.deviceList.values.toList()
        val preferred = devices.filter { it.vendorId == vendorId && it.productId == productId }
        return (preferred + devices.filterNot { preferred.contains(it) }).firstOrNull { device ->
            UsbSerialProber.getDefaultProber().probeDevice(device)?.ports?.isNotEmpty() == true
        }
    }

    fun currentDevice(): UsbDevice? = attachedDevice

    fun isOpen(): Boolean = port != null

    /**
     * Open the serial port for survey JSON streaming (line reader on).
     */
    fun connect(device: UsbDevice): Boolean = open(device, startLineReader = true)

    /**
     * Open without the newline survey reader — used for ROM identify / future flash.
     */
    fun connectBinary(device: UsbDevice): Boolean = open(device, startLineReader = false)

    private fun open(device: UsbDevice, startLineReader: Boolean): Boolean {
        disconnect()
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: run {
                onState("USB serial driver not recognized")
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
            // Idle control lines high (inactive) before any reset sequence.
            try {
                selectedPort.setDTR(false)
                selectedPort.setRTS(false)
            } catch (_: Exception) {
            }
            connection = selectedConnection
            port = selectedPort
            attachedDevice = device
            binaryMode = !startLineReader
            if (startLineReader) {
                running = true
                reader = Thread { readLoop(selectedPort) }.also { it.start() }
                onState("Connected to ${device.deviceName} at 115200 baud")
            } else {
                running = false
                onState("USB binary session open (${friendlyName(device)})")
            }
            true
        } catch (error: Exception) {
            selectedConnection.close()
            onState("Serial connection failed: ${error.message}")
            false
        }
    }

    fun pauseLineReader() {
        running = false
        reader?.interrupt()
        reader = null
        binaryMode = true
    }

    fun resumeLineReader() {
        val serialPort = port ?: return
        if (reader?.isAlive == true) return
        binaryMode = false
        running = true
        reader = Thread { readLoop(serialPort) }.also { it.start() }
    }

    fun disconnect() {
        running = false
        binaryMode = false
        reader?.interrupt()
        reader = null
        try {
            port?.close()
        } catch (_: Exception) {
        }
        port = null
        attachedDevice = null
        connection?.close()
        connection = null
    }

    override fun setDtr(value: Boolean) {
        val serialPort = port ?: throw IOException("USB port not open")
        serialPort.setDTR(value)
    }

    override fun setRts(value: Boolean) {
        val serialPort = port ?: throw IOException("USB port not open")
        serialPort.setRTS(value)
    }

    override fun write(data: ByteArray) {
        val serialPort = port ?: throw IOException("USB port not open")
        serialPort.write(data, 1000)
    }

    override fun readAvailable(maxBytes: Int, timeoutMs: Int): ByteArray {
        val serialPort = port ?: throw IOException("USB port not open")
        val buffer = ByteArray(maxBytes.coerceAtLeast(1))
        val count = try {
            serialPort.read(buffer, timeoutMs.coerceAtLeast(1))
        } catch (_: Exception) {
            0
        }
        return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }

    override fun setBaudRate(baud: Int) {
        val serialPort = port ?: throw IOException("USB port not open")
        serialPort.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    }

    override fun purgeInput() {
        val serialPort = port ?: return
        try {
            serialPort.purgeHwBuffers(false, true)
        } catch (_: Exception) {
        }
        // Drain whatever is already queued.
        repeat(8) {
            val leftover = readAvailable(512, 20)
            if (leftover.isEmpty()) return
        }
    }

    private fun readLoop(serialPort: UsbSerialPort) {
        val buffer = ByteArray(512)
        val line = StringBuilder()
        while (running && !Thread.currentThread().isInterrupted && !binaryMode) {
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
                if (running && !binaryMode) onState("USB connection lost: ${error.message}")
                break
            }
        }
    }
}
