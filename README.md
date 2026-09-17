# Wigglefish

Wigglefish is a passive Wi-Fi/BLE field scanner built around an ESP32-C5 and an Android USB controller. It presents live signal telemetry in a neon field-console UI and exports session data for wardriving workflows.

## Safety Scope

Wigglefish is metadata-only and passive:

- It does not join Wi-Fi networks.
- It does not deauthenticate devices.
- It does not capture credentials, handshakes, or private payloads.
- It does not inject wireless frames.
- It only reports visible Wi-Fi and BLE advertisement metadata.

Use it only where you are authorized to observe wireless signals.

## Features

- ESP32-C5 passive Wi-Fi scanning.
- ESP32-C5 NimBLE passive BLE scanning, including names and manufacturer data.
- Android phone GPS location and GNSS satellites-used count.
- Optional phone-side Wi-Fi and BLE observations tagged as `PHONE`.
- Live Wi-Fi, BLE, and GPS counters.
- Radar HUD with animated sweep, signal blips, scanlines, and glitch effects.
- Matrix background and transient one-character signal reveal animation.
- Wi-Fi channel activity and strongest-signal telemetry.
- Camera-like, AirTag-like, and Flipper-like heuristic categories.
- JSON session export and WiGLE/WDGWars-style CSV export.
- Wardrive Go-compatible Wi-Fi fields such as `bssid`, `ssid`, `channel`, `rssi`, and `encryption`.

## Repository Layout

```text
android/       Native Android USB controller and field UI
firmware/      ESP-IDF ESP32-C5 passive scanner
src/           Python serial inspection and metadata models
tests/         Python regression tests
web/           Browser Web Serial launcher and dashboard
```

## ESP32-C5 Firmware

The firmware targets ESP32-C5 ECO2 hardware and uses ESP-IDF 5.5.5. It communicates at 115200 baud through a CH343 USB-serial bridge.

```powershell
call C:\Users\jlors\esp\esp-idf-5.5.5\export.bat
cd firmware
idf.py set-target esp32c5
idf.py build
idf.py -p COM11 flash monitor
```

Confirm the correct port before flashing. The firmware uses a custom 2 MB factory app partition and 4 MB flash configuration.

The stream is newline-delimited JSON. Examples:

```json
{"type":"wifi","ssid":"Example","bssid":"AA:BB:CC:DD:EE:FF","channel":11,"rssi":-52,"security":"WPA2","encryption":"WPA2"}
{"type":"bluetooth","address":"11:22:33:44:55:66","mac":"11:22:33:44:55:66","name":"Beacon","rssi":-61}
```

## Android App

Build with the bundled Android SDK and Gradle installation:

```powershell
$env:ANDROID_HOME = "$PWD\tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
cd android
..\tools\gradle-8.7\bin\gradle.bat assembleDebug
```

Install the generated APK:

```powershell
..\tools\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

Connect the ESP32-C5 through a USB-C OTG adapter and allow USB, location, Bluetooth, and nearby Wi-Fi permissions. The phone and ESP32-C5 can scan concurrently.

## Python CLI

```powershell
python -m pip install -e .
wigglefish ports
wigglefish scan --wifi --ble --wardrivego
```

## Web Launcher

The `web` directory is a static browser dashboard for compatible ESP32 serial scanners. It uses Web Serial at 115200 baud and shows live Wi-Fi/BLE/GPS records, a radar view, transient signal decode animation, and CSV export.

Open it from a secure origin such as GitHub Pages or localhost in Chrome/Edge, then choose **CONNECT SERIAL** and select the ESP32 serial port. The board must already be running firmware that emits the Wigglefish newline-delimited JSON protocol.

## Tests

```powershell
python -m pytest -q
```

## Notes

Device categories in the Android UI are heuristics based on visible names, addresses, and metadata. They are labels for investigation, not guaranteed device identification. GPS data is optional and comes from Android's location APIs; the ESP32-C5 does not provide GPS by itself.

## ☕ Support My Work

If you find this firmware or my open-source security tools helpful, consider supporting future development and late-night coding sessions!

[![PayPal](https://img.shields.io/badge/PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://paypal.me/Evil0ctopus)
