# wigglefish ESP32-C5 firmware

This firmware is designed for an ESP32-C5 connected to a phone through a USB-to-UART bridge such as the CH343.

## Behavior

- Uses the default ESP-IDF console UART at 115200 baud.
- Does not join a Wi-Fi network.
- Performs passive Wi-Fi scans only.
- Performs passive BLE advertisement scans through NimBLE, including device names and manufacturer data.
- Accepts only JSON commands over serial.
- Uses the onboard status LED: solid during startup, three pulses when ready, solid while scanning, three pulses when networks are found, and five pulses on scan errors.

The current default uses the Wireless Tag board's onboard LED on GPIO6 and assumes it is active-high. If the LED behaves inverted on this board revision, change `STATUS_LED_ACTIVE_HIGH` near the top of `main/main.c` to `0` before flashing.

The current firmware starts passive scanning automatically at boot and repeats every 2 seconds, while BLE advertisements stream continuously, so a phone only needs to connect at 115200 baud to receive results. No command is required.

The automatic serial stream emits newline-delimited JSON. Wi-Fi records use `type: "wifi"` with SSID, BSSID, channel, RSSI, `security`, and Wardrive Go-compatible `encryption`. Bluetooth records use `type: "bluetooth"` with both `address` and `mac`, RSSI, optional name, and manufacturer data. A `scan_end` event marks the end of each Wi-Fi pass.

## Build and flash

Install ESP-IDF for the exact ESP32-C5 board, then from this directory run:

```powershell
idf.py set-target esp32c5
idf.py build
idf.py -p COM11 flash monitor
```

The project uses the custom `partitions.csv` layout with a 2 MB factory app partition. Confirm the actual COM port before flashing. Flashing replaces the existing firmware, so the exact board model and flash size must be verified first.
