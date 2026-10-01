"""Active and defensive wireless tool frame generators and controller for ESP32 transceiver."""

from __future__ import annotations

import os
import random
import struct
import time
from typing import Any


def parse_mac(mac_str: str) -> bytes:
    clean = mac_str.replace(":", "").replace("-", "").strip()
    if len(clean) != 12:
        return b"\xff\xff\xff\xff\xff\xff"
    return bytes.fromhex(clean)


def format_mac(data: bytes, offset: int = 0) -> str:
    if len(data) < offset + 6:
        return "00:00:00:00:00:00"
    return ":".join(f"{b:02X}" for b in data[offset : offset + 6])


def generate_random_mac() -> bytes:
    mac = bytearray(os.urandom(6))
    mac[0] = (mac[0] & 0xFC) | 0x02  # locally administered, unicast
    return bytes(mac)


def create_beacon_frame(
    ssid: str,
    bssid: bytes | None = None,
    channel: int = 1,
    is_wpa2: bool = True,
) -> bytes:
    """Build a raw 802.11 Beacon frame."""
    if bssid is None:
        bssid = generate_random_mac()

    # Frame Control: Management (0x00), Subtype Beacon (0x08) -> 0x80 0x00
    # Duration: 0x00 0x00
    # DA: FF:FF:FF:FF:FF:FF
    # SA: BSSID
    # BSSID: BSSID
    # Seq: random
    seq_num = (random.randint(0, 4095) << 4)
    header = struct.pack(
        "<HH6s6s6sH",
        0x0080,
        0x0000,
        b"\xff\xff\xff\xff\xff\xff",
        bssid,
        bssid,
        seq_num,
    )

    # Body: Timestamp (8 bytes), Interval (100 TU), Caps (ESS + Privacy if WPA2)
    timestamp_us = int(time.time() * 1_000_000) & 0xFFFFFFFFFFFFFFFF
    caps = 0x0001 | (0x0010 if is_wpa2 else 0x0000) | 0x0020
    body = struct.pack("<QHH", timestamp_us, 0x0064, caps)

    # Tag 0: SSID
    ssid_bytes = ssid.encode("utf-8")
    tag_ssid = struct.pack("BB", 0x00, len(ssid_bytes)) + ssid_bytes

    # Tag 1: Supported Rates (1, 2, 5.5, 11, 6, 9, 12, 18 Mbps)
    rates = bytes([0x82, 0x84, 0x8B, 0x96, 0x0C, 0x12, 0x18, 0x24])
    tag_rates = struct.pack("BB", 0x01, len(rates)) + rates

    # Tag 3: Channel
    tag_channel = struct.pack("BBB", 0x03, 0x01, channel & 0xFF)

    # Tag 5: TIM
    tim = bytes([0x00, 0x01, 0x00, 0x00])
    tag_tim = struct.pack("BB", 0x05, len(tim)) + tim

    # Tag 48: RSN (WPA2)
    tag_rsn = b""
    if is_wpa2:
        rsn_payload = (
            b"\x01\x00"  # Version 1
            b"\x00\x0f\xac\x04"  # Group CCMP
            b"\x01\x00"  # 1 Pairwise
            b"\x00\x0f\xac\x04"  # CCMP
            b"\x01\x00"  # 1 AKM
            b"\x00\x0f\xac\x02"  # PSK
            b"\x00\x00"  # Caps
        )
        tag_rsn = struct.pack("BB", 0x30, len(rsn_payload)) + rsn_payload

    return header + body + tag_ssid + tag_rates + tag_channel + tag_tim + tag_rsn


def create_deauth_frame(
    ap_mac: bytes,
    client_mac: bytes = b"\xff\xff\xff\xff\xff\xff",
    reason_code: int = 7,
) -> bytes:
    """Build an 802.11 Deauthentication frame."""
    seq_num = (random.randint(0, 4095) << 4)
    # Frame Control: 0xC0 0x00, Duration: 0x3A 0x01
    return struct.pack(
        "<HH6s6s6sHH",
        0x00C0,
        0x013A,
        client_mac,
        ap_mac,
        ap_mac,
        seq_num,
        reason_code,
    )


def create_apple_adv(device_model: str = "0e20") -> bytes:
    """Build an Apple Action / AirPods BLE advertisement payload."""
    model_bytes = bytes.fromhex(device_model)
    payload = bytearray()
    payload.extend([0x02, 0x01, 0x06])  # Flags
    mfr = bytearray([0x4C, 0x00, 0x07, 0x13, 0x01])  # Apple ID + Continuity Proximity
    mfr.extend(model_bytes)
    mfr.extend([0x55, 0x10] + [0x00] * 14)
    payload.extend([len(mfr) + 1, 0xFF])
    payload.extend(mfr[:24])
    return bytes(payload)


def create_fast_pair_adv(model_id: int = 0xF37335) -> bytes:
    """Build a Google Fast Pair BLE advertisement payload."""
    payload = bytearray([0x02, 0x01, 0x06])  # Flags
    payload.extend([0x03, 0x03, 0x2C, 0xFE])  # 16-bit Service UUID 0xFE2C
    model_bytes = bytes([(model_id >> 16) & 0xFF, (model_id >> 8) & 0xFF, model_id & 0xFF])
    payload.extend([0x06, 0x16, 0x2C, 0xFE])  # Service Data 0xFE2C
    payload.extend(model_bytes)
    payload.append(0x00)  # Tx Power
    return bytes(payload)


def create_triones_rgb(r: int, g: int, b: int) -> bytes:
    """Build a Triones / HappyLighting BLE RGB packet."""
    return bytes([0x56, r & 0xFF, g & 0xFF, b & 0xFF, 0x00, 0xF0, 0xAA])


def create_lotus_rgb(r: int, g: int, b: int) -> bytes:
    """Build a Lotus Lantern BLE RGB packet."""
    return bytes([0x7E, 0x07, 0x05, 0x03, r & 0xFF, g & 0xFF, b & 0xFF, 0x00, 0xEF])


def create_elk_bledom_rgb(r: int, g: int, b: int) -> bytes:
    """Build an Elk-BLEDOM BLE RGB packet."""
    return bytes([0x7E, 0x04, 0x04, r & 0xFF, g & 0xFF, b & 0xFF, 0xFF, 0x00, 0xEF])


def create_magichome_udp_rgb(r: int, g: int, b: int) -> bytes:
    """Build a MagicHome Wi-Fi LED controller UDP payload (Port 5577)."""
    cr = r & 0xFF
    cg = g & 0xFF
    cb = b & 0xFF
    chk = (0x31 + cr + cg + cb + 0x00 + 0xF0 + 0x0F) & 0xFF
    return bytes([0x31, cr, cg, cb, 0x00, 0xF0, 0x0F, chk])


def create_probe_request(ssid: str = "", client_mac: bytes | None = None) -> bytes:
    """Build an 802.11 Probe Request frame."""
    if client_mac is None:
        client_mac = generate_random_mac()
    seq_num = (random.randint(0, 4095) << 4)
    header = struct.pack(
        "<HH6s6s6sH",
        0x0040,
        0x0000,
        b"\xff\xff\xff\xff\xff\xff",
        client_mac,
        b"\xff\xff\xff\xff\xff\xff",
        seq_num,
    )
    ssid_bytes = ssid.encode("utf-8")
    tag_ssid = struct.pack("BB", 0x00, len(ssid_bytes)) + ssid_bytes
    rates = bytes([0x82, 0x84, 0x8B, 0x96, 0x0C, 0x12, 0x18, 0x24])
    tag_rates = struct.pack("BB", 0x01, len(rates)) + rates
    return header + tag_ssid + tag_rates

