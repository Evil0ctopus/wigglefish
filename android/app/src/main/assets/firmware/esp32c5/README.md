# ESP32-C5 flash assets (in-app)

Sourced from draft GitHub release `firmware-v0.1.0` on `Evil0ctopus/wigglefish`
(2026-09-25). Offsets match ESP-IDF C5 / `firmware/web-flash`:

| File | Offset |
|------|--------|
| bootloader.bin | 0x2000 |
| partition-table.bin | 0x8000 |
| wigglefish_firmware.bin | 0x10000 |

Passive Wi-Fi/BLE scanner firmware only — no offensive RF.
