"""Passive, read-only ingest of the ESP32 newline-delimited JSON stream.

The firmware emits Wi-Fi and BLE metadata automatically. This module only opens
a serial port for reading, never writes commands, and never invents active or
credential-bearing fields.
"""

from __future__ import annotations

import json
import time
from collections.abc import Iterable, Iterator
from typing import Any

import serial

from .survey import BleObservation, WifiObservation

DEFAULT_BAUD = 115200
DEFAULT_DURATION_SECONDS = 5.0


def _normalize_manufacturer_data(raw: Any) -> dict[str, str]:
    """Map firmware manufacturer_data into BleObservation's string dict shape."""
    if raw is None:
        return {}
    if isinstance(raw, dict):
        return {str(key): str(value) for key, value in raw.items()}
    if isinstance(raw, list):
        try:
            values = [int(item) & 0xFF for item in raw]
        except (TypeError, ValueError):
            return {}
        if len(values) >= 2:
            company_id = values[0] | (values[1] << 8)
            payload = " ".join(f"{byte:02X}" for byte in values[2:])
            return {f"0x{company_id:04X}": payload}
        if values:
            return {"raw": " ".join(f"{byte:02X}" for byte in values)}
        return {}
    return {}


def parse_stream_line(line: str) -> WifiObservation | BleObservation | None:
    """Parse one firmware/web NDJSON line into a metadata observation.

    Event lines (``scan_start``, ``scan_end``, ``ready``, …) and malformed
    input return ``None``. Only ``type: wifi`` / ``bluetooth`` / ``ble``
    records are accepted.
    """
    text = line.strip()
    if not text or not text.startswith("{"):
        return None
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None

    record_type = str(payload.get("type") or "").lower()
    if record_type == "wifi":
        bssid = str(payload.get("bssid") or "").strip()
        if not bssid:
            return None
        security = payload.get("security") or payload.get("encryption")
        channel = payload.get("channel")
        rssi = payload.get("rssi")
        return WifiObservation(
            ssid=str(payload.get("ssid") or ""),
            bssid=bssid,
            channel=int(channel) if channel is not None else None,
            rssi=int(rssi) if rssi is not None else None,
            security=str(security) if security is not None else None,
            vendor=str(payload["vendor"]) if payload.get("vendor") is not None else None,
        )

    if record_type in {"bluetooth", "ble"}:
        address = str(payload.get("address") or payload.get("mac") or "").strip()
        if not address:
            return None
        rssi = payload.get("rssi")
        name = payload.get("name")
        service_uuids = payload.get("service_uuids") or []
        if not isinstance(service_uuids, list):
            service_uuids = []
        return BleObservation(
            address=address,
            name=str(name) if name else None,
            rssi=int(rssi) if rssi is not None else None,
            service_uuids=[str(item) for item in service_uuids],
            manufacturer_data=_normalize_manufacturer_data(payload.get("manufacturer_data")),
        )

    return None


def observations_from_lines(
    lines: Iterable[str],
) -> tuple[list[WifiObservation], list[BleObservation]]:
    """Collect unique Wi-Fi/BLE observations from NDJSON lines (strongest RSSI wins)."""
    wifi_by_bssid: dict[str, WifiObservation] = {}
    ble_by_address: dict[str, BleObservation] = {}

    for line in lines:
        observation = parse_stream_line(line)
        if isinstance(observation, WifiObservation):
            existing = wifi_by_bssid.get(observation.bssid)
            if existing is None or _rssi_better(observation.rssi, existing.rssi):
                wifi_by_bssid[observation.bssid] = observation
        elif isinstance(observation, BleObservation):
            existing = ble_by_address.get(observation.address)
            if existing is None or _rssi_better(observation.rssi, existing.rssi):
                ble_by_address[observation.address] = observation

    return list(wifi_by_bssid.values()), list(ble_by_address.values())


def _rssi_better(candidate: int | None, current: int | None) -> bool:
    if candidate is None:
        return False
    if current is None:
        return True
    return candidate > current


def validate_duration_seconds(duration_seconds: float) -> float:
    """Require ``duration_seconds >= 0``; ``0`` means listen until interrupted."""
    if duration_seconds < 0:
        raise ValueError(
            "duration_seconds must be >= 0 (0 means listen until interrupted)"
        )
    return duration_seconds


def _iter_serial_lines(
    port: str,
    *,
    baudrate: int = DEFAULT_BAUD,
    duration_seconds: float = DEFAULT_DURATION_SECONDS,
    read_timeout: float = 0.2,
) -> Iterator[str]:
    """Yield newline-delimited text from ``port`` for a listen window.

    ``duration_seconds`` must be >= 0. A positive value listens for that many
    seconds; ``0`` listens until KeyboardInterrupt (Ctrl-C). The port is opened
    read-only for application purposes: this helper never writes bytes to the
    device.
    """
    duration_seconds = validate_duration_seconds(duration_seconds)
    unbounded = duration_seconds == 0
    deadline = float("inf") if unbounded else time.monotonic() + duration_seconds
    buffer = ""
    with serial.Serial(port=port, baudrate=baudrate, timeout=read_timeout) as handle:
        try:
            while time.monotonic() < deadline:
                chunk = handle.read(512)
                if not chunk:
                    continue
                buffer += chunk.decode("utf-8", errors="replace")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    yield line.rstrip("\r")
        except KeyboardInterrupt:
            # Stop listening; lines already yielded are kept by the caller.
            return


def read_passive_serial(
    port: str,
    *,
    baudrate: int = DEFAULT_BAUD,
    duration_seconds: float = DEFAULT_DURATION_SECONDS,
) -> tuple[list[WifiObservation], list[BleObservation]]:
    """Passively ingest firmware NDJSON from a serial port.

    Default listen window is ``DEFAULT_DURATION_SECONDS``. Pass
    ``duration_seconds=0`` to listen until interrupted (Ctrl-C). Negative
    durations are rejected.
    """
    return observations_from_lines(
        _iter_serial_lines(
            port,
            baudrate=baudrate,
            duration_seconds=duration_seconds,
        )
    )
