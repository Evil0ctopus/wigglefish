# Wigglefish Android controller

This native Android app connects to the ESP32-C5 through USB-C OTG and the CH343 USB-serial bridge.

## Behavior

- Prefers the CH343 by VID/PID `1A86:55D3`, recognizes common CYD CH340 `1A86:7523`, CP210x, FTDI, and native ESP USB IDs, then accepts any USB-serial device supported by usb-serial-for-android.
- Requests Android USB permission.
- Registers for the CH343 USB attach event so connecting the board can launch the controller.
- Opens the serial link at 115200 baud.
- Reads the firmware's newline-delimited passive-survey JSON stream.
- Displays Wi-Fi and BLE observations with separate live counters.
- Accepts the firmware's `type: "wifi"` and Wardrive Go-compatible `type: "bluetooth"` records.
- Normalizes common legacy text scan lines containing SSID/BSSID/MAC/RSSI fields from other passive firmware families.
- Parses WiGLE-style CSV rows used by HaleHound-style ESP32 CYD builds when those rows are streamed or imported.

The survey is passive and intended for networks you are authorized to observe. The app does not join networks or send disruptive wireless frames.

The compatibility parser is intentionally metadata-only. It does not interpret attack commands, credentials, handshakes, portals, or private payloads. HaleHound deployments that only save CSV to a microSD card still need the CSV file transferred to the phone; the app cannot read another board's SD card through a serial connection automatically.

## Build

Open this `android` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on the Galaxy S26 Ultra. Connect the ESP32-C5 through a USB-C OTG data adapter and accept the USB permission prompt.

CYD boards can connect through a USB-C OTG adapter when the board exposes its USB-UART bridge. The board still needs compatible passive scanner firmware, such as HaleHound CSV output or firmware that emits Wigglefish JSON. A bare ESP32, factory firmware, or unrelated firmware may connect as serial but will not produce Wi-Fi/BLE observations.
