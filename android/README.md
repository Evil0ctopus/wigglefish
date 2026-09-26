# Wigglefish Android controller

This native Android app connects to the ESP32-C5 through USB-C OTG and the CH343 USB-serial bridge.

## Navigation map

The UI is a bottom-nav shell (AndroidX Navigation + Fragments). Five destinations:

1. **Home** — status summary, strongest signal, Wi-Fi / BLE / GPS counters, and quick jumps to the other pages.
2. **Connect / Device** — USB connect, phone + USB collection toggles, permission/device/GPS/source status.
3. **Flash / Firmware** — **Identify chip** over USB OTG (PR1). Full in-app flash write is not shipped yet (PR2). Desktop ESP Web Tools / firmware release assets still work for flashing.
4. **Live Field** — spectrum, ALL/Wi-Fi/BLE filters, matrix decode stream, radar, categorized devices, live observation list.
5. **Exports / Sessions** — JSON + CSV share, clear session, session counters / source tallies.

Shared observational state lives in an activity-scoped `SurveySessionViewModel`. USB serial, phone Wi-Fi/BLE/GPS, and file logging stay owned by `MainActivity`, which implements `SurveyHost` for fragment actions.

## Flash identify flow (PR1)

1. Plug the board with a **data-capable** USB-C OTG adapter (charge-only cables fail silently).
2. Open **Flash / Firmware**.
3. Tap **Connect / use USB device** (reuses the existing USB permission + `UsbSerialController` stack). Preferred bridge is CH343 `1A86:55D3`; other usb-serial devices still appear.
4. Tap **Identify chip**. The app:
   - Opens a binary USB session (survey line-reader paused if it was running)
   - Runs a classic DTR/RTS reset into ROM download mode (CH343: DTR re-applied after RTS)
   - Sends ROM `SYNC`, then prefers `GET_SECURITY_INFO` chip_id (C3=`5`, C5=`23`, …)
   - Falls back to `READ_REG` magic at `0x40001000` for classic ESP32 / S2-style detection
   - Best-effort MAC read from eFuse for C3 / C5 / ESP32
5. Results show chip family, MAC (if available), USB VID/PID/serial/product, and a **profile hint** against stubs for `WT013261-S5`, `WT32C3-S5`, and `WT018684-S5`.

**Same VID/PID is expected** across those Wireless-Tag / CH343 boards — Identify must use the chip probe, not USB IDs alone. Profile stubs live in `BoardProfiles.kt` and `assets/board_profiles.json`.

### What this is / is not

- **Is:** pragmatic Kotlin ROM identify good enough to report ESP32 vs ESP32-C3 vs ESP32-C5 on a plugged CH343 board.
- **Is not:** Python `esptool`, full stub upload, or firmware write. NDK `esp-serial-flasher` integration is deferred; PR2 should add real flash using the same USB transport.
- If auto-reset fails on a particular bridge, hold **BOOT**, tap reset/EN, then Identify again.

### Power / OTG notes

- Phone must supply OTG host power; some phones need a powered hub for hungry boards.
- After Identify the chip may remain in download mode — use **Connect** on the Connect page (or replug) before expecting survey JSON again.

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
- Keeps a separate USB-device JSONL audit log for connected-board events and passive board observations.

The survey is passive and intended for networks you are authorized to observe. The app does not join networks or send disruptive wireless frames. Identify / future flash tooling does not add offensive RF features.

The compatibility parser is intentionally metadata-only. It does not interpret attack commands, credentials, handshakes, portals, or private payloads. HaleHound deployments that only save CSV to a microSD card still need the CSV file transferred to the phone; the app cannot read another board's SD card through a serial connection automatically.

## Build

Open this `android` directory in Android Studio, allow Gradle to sync, then run the `app` configuration on your Android phone. Connect the ESP32-C5 through a USB-C OTG data adapter and accept the USB permission prompt.

From a machine with the Android SDK and a Gradle wrapper (or Gradle 8.7+):

```bash
cd android
./gradlew assembleDebug
```

CYD boards can connect through a USB-C OTG adapter when the board exposes its USB-UART bridge. The board still needs compatible passive scanner firmware, such as HaleHound CSV output or firmware that emits Wigglefish JSON. A bare ESP32, factory firmware, or unrelated firmware may connect as serial but will not produce Wi-Fi/BLE observations.

## Launcher icon

Adaptive + density mipmaps under `android/app/src/main/res`:
- Foreground: pink fish silhouette + cyan RF arcs (brand colors `#FF4FD8` / `#8FF7FF`)
- Background: deep night `#050611`
- Preview: [docs/ic_launcher_preview.png](docs/ic_launcher_preview.png)

