# ⚡ Wigglefish Cyber Tools & Dumb Radio Guide

This document describes the Bruce / Marauder / Wireless Wizard tool suite implemented in Wigglefish using the **Smart Host (Android/Python) / Dumb Transceiver (ESP32)** architecture.

---

## 🏗️ Architecture Overview

```
 ┌─────────────────────────────────────────────────────────────┐
 │                     ANDROID APP / PYTHON                    │
 │  • Cyber Ops Menu & Controls  • Frame Crafting Engine       │
 │  • State Machines & Timers    • Live Dissector & PCAP Saver │
 │  • Hashcat .22000 Exporter    • Geiger Audio/Haptics        │
 └──────────────────────────────▲──────────────────────────────┘
                                │ High-Speed Serial JSON Framing
                                │ (115200 / 921600 baud)
 ┌──────────────────────────────▼──────────────────────────────┐
 │                      ESP32 TRANSCEIVER                      │
 │  • esp_wifi_80211_tx (Raw Frame Injection)                  │
 │  • Promiscuous RX streaming (Raw 802.11 frames)             │
 │  • Channel Control & Hopping                                │
 │  • BLE Raw Advertisement Broadcasting                       │
 └─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Implemented Tools & Features

### 1. Wi-Fi Attack & Recon Suite
* **Beacon Flooder**:
  * *Random SSIDs*: Continuously floods the airspace with funny and realistic tech SSIDs.
  * *Rickroll*: Broadcasts the lyrics of "Never Gonna Give You Up" across channels.
  * *Clone Flooder*: Clones all detected Access Points in real-time.
* **Targeted Deauthentication**:
  * Broadcast or client-specific deauthentication / disassociation frame injection.
  * Configurable reason codes (e.g., Code 7: Class 3 frame from non-associated station).
* **Handshake Hunter**:
  * Automatically tunes ESP32 channel to the target AP.
  * Enables promiscuous sniffer.
  * Transmits calibrated deauth pulse to force client reconnect.
  * Captures EAPOL 4-way handshakes and PMKID keys.
* **Karma & Client Probe Recon**:
  * Sniffs broadcast probe requests to identify networks remembered by nearby phones and laptops.

### 2. Bluetooth Low Energy (BLE) Tools
* **BLE Ecosystem Spammer**:
  * *Apple*: AirPods Pro, AirPods Max, Apple TV setup, AirDrop, and transfer modals.
  * *Android*: Google Fast Pair popup prompts.
  * *Samsung*: Galaxy Buds & Galaxy Watch pairing alerts.
  * *Windows*: Swift Pair proximity beacons.
  * *All-In-One*: Cycles across all platforms in an interleaved loop.
* **Smart RGB Light & Bulb Hijacker**:
  * Broadcasts raw unauthenticated GATT/UDP commands to override nearby BLE & Wi-Fi LED controllers (Triones, Lotus Lantern, Elk-BLEDOM, MagicHome).
  * Supports Power ON/OFF, Color presets (Red, Green, Blue, Gold), Rainbow cycle, and Strobe override.

### 3. Offensive & Phishing Tools
* **Evil Captive Portal**:
  * Spins up a Rogue AP with integrated DNS Catch-All (`*` -> `192.168.4.1`) and HTTP web server.
  * Includes 3 built-in phishing templates: Router Firmware Update, Free Wi-Fi Login, and Google Sign-in.
  * Harvests submitted credentials live over USB serial and stores them in the app.
* **Probe Flooder & IDS Crasher**:
  * Floods channels with thousands of randomized 802.11 probe requests to stress WIDS/WIPS intrusion detection systems.

### 4. Defensive & Tracking Tools
* **Geiger Target Hunter**:
  * Locks onto any Wi-Fi BSSID, Client MAC, or BLE Tracker (e.g. AirTag).
  * Plays audio clicks and triggers haptic vibrations proportional to real-time signal strength (RSSI).
* **Deauth Storm Detector**:
  * Monitors the airspace and triggers an alert if deauth frames are detected targeting your network.
* **Evil Twin Detector**:
  * Detects duplicate SSIDs appearing with altered MAC addresses or downgraded security.
* **Connected Client Station Mapper**:
  * Maps active client devices (phones, laptops) talking to surrounding APs.
* **BLE Skimmer & Rogue Tracker Detector**:
  * Identifies unencrypted ATM/Gas-pump Bluetooth skimmer modules (HC-05/HC-06/CC2541) and rogue Apple AirTags.
* **WPS & 802.11w PMF Security Auditor**:
  * Audits APs for WPS locked status and Protected Management Frame (PMF) enforcement.

### 5. Hardware WS2812 Addressable RGB LED (GPIO 8)
* Onboard WS2812 LED driven by RMT hardware controller with dynamic color states:
  * 🌊 Cyan breathing: Idle passive scanning
  * 🔵 Bright Cyan: Wi-Fi AP discovered
  * 🟣 Magenta: BLE device discovered
  * 🟢 Lime Green: Promiscuous packet RX
  * 🟠 Orange: Packet injected (`tx_raw`)
  * 🟡 Gold: Handshake or phishing credential captured
  * 🔴 Red: Threat / Deauth storm / Skimmer alert

### 6. Capture & Exporters
* **Live PCAP Streamer**: Writes standard `.pcap` capture files directly to Android storage (Wireshark / Aircrack compatible).
* **Hashcat `.22000` Exporter**: One-tap export of captured PMKIDs and 4-way handshakes for hash cracking.
* **CSV & GPX Exporters**: Full wardriving export compatible with WiGLE.net.

---

## 💻 ESP32 Serial Command Protocol

All commands are exchanged over serial as single-line JSON objects:

| Command | Payload | Description |
|---|---|---|
| `tx_raw` | `{"cmd":"tx_raw","channel":6,"data_hex":"...","count":15,"delay_ms":8}` | Inject raw 802.11 IEEE frame |
| `promisc_on` | `{"cmd":"promisc_on","filter":"all"}` | Enable promiscuous packet streaming |
| `promisc_off` | `{"cmd":"promisc_off"}` | Disable promiscuous streaming |
| `set_channel` | `{"cmd":"set_channel","channel":11}` | Tune radio to specific channel |
| `hop_on` | `{"cmd":"hop_on","start":1,"end":13,"dwell_ms":200}` | Start automatic channel hopping |
| `hop_off` | `{"cmd":"hop_off"}` | Stop channel hopping |
| `ble_adv_raw` | `{"cmd":"ble_adv_raw","payload_hex":"..."}` | Start broadcasting custom BLE advertisement |
| `ble_adv_stop` | `{"cmd":"ble_adv_stop"}` | Stop BLE advertisement broadcast |
| `portal_start` | `{"cmd":"portal_start","ssid":"Free-WiFi","template":"router"}` | Launch Evil Captive Portal |
| `portal_stop` | `{"cmd":"portal_stop"}` | Stop Evil Captive Portal |
| `led_alert` | `{"cmd":"led_alert","color":"red"\|"gold"\|"cyan"\|"magenta"}` | Trigger hardware RGB LED alert |
| `led_rgb` | `{"cmd":"led_rgb","r":255,"g":0,"b":128}` | Set exact RGB color on WS2812 LED |

---

## 📱 Using the Android App

1. Connect your ESP32 board via USB OTG cable and tap **CONNECT USB**.
2. Tap **`⚡ CYBER OPS & TOOLS`** to open the tool selection menu.
3. Tap any discovered network in the live list to:
   * View full security analysis and recommendations.
   * Lock onto it with **Geiger Hunter**.
   * Transmit a **Deauth Pulse**.
   * Launch **Handshake Hunter**.
4. Use the **PCAP** or **EXPORT** buttons to export captures at any time.
