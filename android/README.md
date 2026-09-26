# Wigglefish Android controller

This native Android app connects to the ESP32-C5 through USB-C OTG and the CH343 USB-serial bridge.

## Navigation map

The UI is a bottom-nav shell (AndroidX Navigation + Fragments). Five destinations:

1. **Home** — status summary, strongest signal, Wi-Fi / BLE / GPS counters, and quick jumps to the other pages.
2. **Connect / Device** — USB connect, phone + USB collection toggles, permission/device/GPS/source status.
3. **Flash / Firmware** — **Identify chip** + **in-app flash write** over USB OTG (Kotlin ROM protocol, not Python esptool).
4. **Live Field** — spectrum, ALL/Wi-Fi/BLE filters, matrix decode stream, radar, categorized devices, live observation list.
5. **Exports / Sessions** — JSON + CSV share, clear session, session counters / source tallies.

Shared observational state lives in an activity-scoped `SurveySessionViewModel`. USB serial, phone Wi-Fi/BLE/GPS, and file logging stay owned by `MainActivity`, which implements `SurveyHost` for fragment actions.

## Flash flow (Identify + write)

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
6. Compatible firmware image(s) appear in the spinner (filtered by chip family / profile):
   - **ESP32-C5:** packaged **Wigglefish passive scanner v0.1.0** from app assets (`firmware/esp32c5/…`, sourced from draft GitHub release `firmware-v0.1.0`).
   - **ESP32-C3 / classic ESP32:** honest stubs — Identify works; no packaged image yet.
7. Tap **Flash selected image**. Progress shows stages: `SYNC` → `ATTACH` → `ERASE` → `WRITE` → `VERIFY` → `RESET`.
8. After flash, the app best-effort hard-resets back to **run mode**. Open **Live Field** or **Connect** to resume survey JSON.

**Same VID/PID is expected** across those Wireless-Tag / CH343 boards — Identify must use the chip probe, not USB IDs alone. Profile stubs live in `BoardProfiles.kt` and `assets/board_profiles.json`. Flash eligibility is wired by **chip family** (catalog `firmware_catalog.json`).

### Flash protocol (Kotlin ROM, no stub)

Implemented in `esp/EspRomClient.kt` + `esp/EspRomFlasher.kt` on top of the Identify transport:

| Step | ROM op | Notes |
|------|--------|--------|
| Sync | `SYNC` `0x08` | After classic DTR/RTS download reset |
| Attach | `SPI_ATTACH` `0x0D` | Required on C5 ROM before flash |
| Params | `SPI_SET_PARAMS` `0x0B` | Default 4 MB flash layout |
| Optional | `CHANGE_BAUDRATE` `0x0F` | Tries 460800, falls back to 115200 |
| Erase+write | `FLASH_BEGIN` `0x02` / `FLASH_DATA` `0x03` | 0x400-byte blocks; ROM erases on BEGIN |
| Verify | `SPI_FLASH_MD5` `0x13` | Best-effort per part |
| Exit | `FLASH_END` + hard reset | Leave download mode → run app |

C5 flash map (matches `firmware/web-flash` / ESP-IDF):

| Part | Asset | Offset |
|------|-------|--------|
| bootloader | `firmware/esp32c5/bootloader.bin` | `0x2000` |
| partition table | `firmware/esp32c5/partition-table.bin` | `0x8000` |
| app | `firmware/esp32c5/wigglefish_firmware.bin` | `0x10000` |

~1.3 MB total. Keep the OTG cable still for 1–3 minutes. Desktop ESP Web Tools / `idf.py flash` remain valid alternatives when the draft release is published.

### What this is / is not

- **Is:** pragmatic Kotlin ROM identify + flash-write good enough to install the packaged C5 Wigglefish image over CH343 USB OTG.
- **Is not:** Python `esptool`, stub-loader upload, or NDK `esp-serial-flasher` (deferred if ROM path proves too limited on some bridges).
- If auto-reset fails on a particular bridge, hold **BOOT**, tap reset/EN, then Identify / Flash again.

### Power / OTG notes

- Phone must supply OTG host power; some phones need a powered hub for hungry boards.
- After Identify-only the chip may remain in download mode — Flash performs a run-mode reset; otherwise use **Connect** (or replug) before expecting survey JSON again.

## Behavior

- Prefers the CH343 by VID/PID `1A86:55D3`, recognizes common CYD CH340 `1A86:7523`, CP210x, FTDI, and native ESP USB IDs, then accepts any USB-serial device supported by usb-serial-for-android.
- Requests Android USB permission.
- Registers for the CH343 USB attach event so connecting the board can launch the controller.
- Opens the serial link at 115200 baud (flash may temporarily raise baud).
- Reads the firmware's newline-delimited passive-survey JSON stream.
- Displays Wi-Fi and BLE observations with separate live counters.
- Accepts the firmware's `type: "wifi"` and Wardrive Go-compatible `type: "bluetooth"` records.
- Normalizes common legacy text scan lines containing SSID/BSSID/MAC/RSSI fields from other passive firmware families.
- Parses WiGLE-style CSV rows used by HaleHound-style ESP32 CYD builds when those rows are streamed or imported.
- Keeps a separate USB-device JSONL audit log for connected-board events and passive board observations.

The survey is passive and intended for networks you are authorized to observe. The app does not join networks or send disruptive wireless frames. Identify / flash tooling does not add offensive RF features.

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

