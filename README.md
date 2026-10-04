# Wigglefish

Wigglefish is a Wi-Fi/BLE field scanner built around an ESP32-C5 and an Android USB controller. Its native Android interface uses original comic-style paintwork, readable signal cards, and five fixed navigation destinations. It can directly control a user-selected compatible BLE RGB light and exports session data for field workflows.

## Safety Scope

Passive Wi-Fi and BLE surveys are the default. Android only opens the USB serial connection after the user taps **Connect**. The app also includes optional active lab tools that transmit wireless frames or capture management/authentication traffic; these can disrupt nearby equipment. Use them only in an isolated environment with devices and networks you own or are authorized to test.

The captive portal is a clearly labeled connectivity demo. It does not ask for, accept, or store credentials. RGB light control connects only to a compatible BLE light the user explicitly selects and confirms; it does not broadcast light commands to nearby devices or the local network.

## Features

- ESP32-C5 passive Wi-Fi scanning.
- ESP32-C5 NimBLE passive BLE scanning, including names and manufacturer data.
- Android phone GPS location and GNSS satellites-used count.
- Optional phone-side Wi-Fi and BLE observations tagged as `PHONE`.
- Live Wi-Fi, BLE, and GPS counters.
- Comic radar with an animated sweep and signal blips (a signal mix, not a geographic map).
- Four saved paint palettes, springy controls, and original animated Lumi artwork.
- Recycled signal rows, collapsible tools/reports, compact-screen layouts, and reduced-motion settings.
- Wi-Fi channel activity and strongest-signal telemetry.
- Camera-like, AirTag-like, and Flipper-like heuristic categories.
- JSON session export and WiGLE/WDGWars-style CSV export.
- Wardrive Go-compatible Wi-Fi fields such as `bssid`, `ssid`, `channel`, `rssi`, and `encryption`.
- An explicitly launched captive portal demo with no credential form or storage.

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

See [the Android guide](android/README.md) for the comic UI, motion settings,
and emulator test coverage. The comic preview debug build installs separately
as `com.wigglefish.android.lumitest`; it does not replace the regular app.
Website downloads are on [the Projects page](https://evil0ctopus.github.io/projects.html#wigglefish).

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
