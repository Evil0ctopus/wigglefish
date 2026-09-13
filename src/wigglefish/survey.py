"""Passive Wi‑Fi and BLE metadata scanning.

The project intentionally records only metadata that is visible in the air or
available through local discovery. It never joins a network, captures
credentials, or injects traffic.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(frozen=True)
class WifiObservation:
    """Metadata observed from a Wi‑Fi access point or client beacon."""

    ssid: str
    bssid: str
    channel: int | None = None
    rssi: int | None = None
    security: str | None = None
    vendor: str | None = None


@dataclass(frozen=True)
class BleObservation:
    """Metadata observed from a BLE advertisement."""

    address: str
    name: str | None = None
    rssi: int | None = None
    service_uuids: list[str] = field(default_factory=list)
    manufacturer_data: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class ScanResult:
    """A passive metadata-only scan result."""

    wifi: list[WifiObservation] = field(default_factory=list)
    ble: list[BleObservation] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "wifi": [asdict(item) for item in self.wifi],
            "ble": [asdict(item) for item in self.ble],
        }

    def to_json(self, *, mode: str = "json") -> str:
        payload = self.to_dict()
        if mode == "wardrivego":
            wardrive_payload = []
            for item in payload["wifi"]:
                wardrive_payload.append(
                    {
                        "ssid": item.get("ssid"),
                        "bssid": item.get("bssid"),
                        "channel": item.get("channel"),
                        "rssi": item.get("rssi"),
                        "security": item.get("security"),
                        "vendor": item.get("vendor"),
                        "type": "wifi",
                    }
                )
            for item in payload["ble"]:
                wardrive_payload.append(
                    {
                        "name": item.get("name"),
                        "address": item.get("address"),
                        "rssi": item.get("rssi"),
                        "service_uuids": item.get("service_uuids", []),
                        "manufacturer_data": item.get("manufacturer_data", {}),
                        "type": "ble",
                    }
                )
            return json.dumps(wardrive_payload, indent=2, sort_keys=True)
        return json.dumps(payload, indent=2, sort_keys=True)


def scan_passive(
    wifi: list[WifiObservation] | None = None,
    ble: list[BleObservation] | None = None,
) -> ScanResult:
    """Return a passive scan result containing only metadata.

    This is the project's safe default. It does not join any network, send
    packets, or capture credentials.
    """

    return ScanResult(
        wifi=list(wifi or []),
        ble=list(ble or []),
    )
