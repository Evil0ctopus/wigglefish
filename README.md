# wigglefish

A small, read-only tool for finding out what devices are connected to serial/COM ports.

## Setup

Create or select a Python 3.10+ environment, then install the project:

```powershell
python -m pip install -e .
```

## First command

List detected ports and operating-system metadata:

```powershell
wigglefish ports
```

The discovery command does not open ports or send bytes. Future device-specific actions should be added only after a device has been identified and its protocol is understood.

## ESP32-C5 firmware

The `firmware` directory contains an ESP-IDF project for replacing the board firmware. It exposes a 115200-baud JSON protocol and performs passive Wi-Fi surveys without joining networks or sending control frames.

Build and flash it only after confirming the exact board model and flash size:

```powershell
idf.py -C firmware set-target esp32c5
idf.py -C firmware build
idf.py -C firmware -p COM11 flash monitor
```

## Android controller

The `android` directory contains a native Android controller for the Galaxy S26 Ultra. It detects the CH343 over USB-C OTG, requests permission, connects at 115200 baud, and displays the firmware's automatic passive survey stream.
```
