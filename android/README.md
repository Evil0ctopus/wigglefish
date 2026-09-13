# Wigglefish Android controller

This native Android app connects to the ESP32-C5 through USB-C OTG and the CH343 USB-serial bridge.

## Behavior

- Prefers the CH343 by VID/PID `1A86:55D3`, then accepts any USB-serial device supported by usb-serial-for-android.
- Requests Android USB permission.
- Registers for the CH343 USB attach event so connecting the board can launch the controller.
- Opens the serial link at 115200 baud.
- Reads the firmware's newline-delimited passive-survey JSON stream.
- Displays Wi-Fi and BLE observations with separate live counters.
- Accepts the firmware's `type: "wifi"` and Wardrive Go-compatible `type: "bluetooth"` records.

The survey is passive and intended for networks you are authorized to observe. The app does not join networks or send disruptive wireless frames.

## Build

Open this `android` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on the Galaxy S26 Ultra. Connect the ESP32-C5 through a USB-C OTG data adapter and accept the USB permission prompt.

The board still needs compatible Wigglefish firmware. A bare ESP32, factory firmware, or unrelated firmware may connect as serial but will not produce Wi-Fi/BLE observations. Flash the project firmware once per board, then the app can reuse any supported USB serial bridge.
