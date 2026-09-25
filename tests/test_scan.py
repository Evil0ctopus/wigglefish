import json

from wigglefish.cli import build_parser
from wigglefish.survey import BleObservation, WifiObservation, scan_passive


def test_parser_accepts_scan_command() -> None:
    args = build_parser().parse_args(["scan", "--wifi", "--ble", "--json"])
    assert args.command == "scan"
    assert args.wifi is True
    assert args.ble is True
    assert args.json is True


def test_scan_passive_returns_metadata_only() -> None:
    wifi = WifiObservation(
        ssid="HomeNet",
        bssid="AA:BB:CC:DD:EE:FF",
        channel=11,
        rssi=-52,
        security="WPA2",
    )
    ble = BleObservation(
        address="11:22:33:44:55:66",
        name="OfficeCam",
        rssi=-61,
        service_uuids=["0xFEAA"],
        manufacturer_data={"0x004C": "4C 00 00 00"},
    )

    result = scan_passive([wifi], [ble])
    payload = json.loads(result.to_json())

    assert payload["wifi"][0]["ssid"] == "HomeNet"
    assert payload["wifi"][0]["bssid"] == "AA:BB:CC:DD:EE:FF"
    assert payload["ble"][0]["name"] == "OfficeCam"
    assert "password" not in json.dumps(payload).lower()
    assert "credential" not in json.dumps(payload).lower()


def test_parser_accepts_wardrivego_flag() -> None:
    args = build_parser().parse_args(["scan", "--wifi", "--ble", "--wardrivego"])
    assert args.command == "scan"
    assert args.wifi is True
    assert args.ble is True
    assert args.wardrivego is True


def test_wardrivego_export_shape_is_metadata_only() -> None:
    wifi = WifiObservation(
        ssid="HomeNet",
        bssid="AA:BB:CC:DD:EE:FF",
        channel=11,
        rssi=-52,
        security="WPA2",
        vendor="Example Wi‑Fi AP",
    )
    ble = BleObservation(
        address="11:22:33:44:55:66",
        name="OfficeCam",
        rssi=-61,
        service_uuids=["0xFEAA"],
        manufacturer_data={"0x004C": "4C 00 00 00"},
    )

    result = scan_passive([wifi], [ble])
    payload = json.loads(result.to_json(mode="wardrivego"))
    serialized = json.dumps(payload).lower()

    assert isinstance(payload, list)
    assert len(payload) == 2

    wifi_row = next(item for item in payload if item["type"] == "wifi")
    ble_row = next(item for item in payload if item["type"] == "ble")

    assert wifi_row["ssid"] == "HomeNet"
    assert wifi_row["bssid"] == "AA:BB:CC:DD:EE:FF"
    assert wifi_row["channel"] == 11
    assert wifi_row["rssi"] == -52
    assert wifi_row["security"] == "WPA2"
    assert wifi_row["vendor"] == "Example Wi‑Fi AP"

    assert ble_row["name"] == "OfficeCam"
    assert ble_row["address"] == "11:22:33:44:55:66"
    assert ble_row["rssi"] == -61
    assert ble_row["service_uuids"] == ["0xFEAA"]
    assert ble_row["manufacturer_data"] == {"0x004C": "4C 00 00 00"}

    assert "password" not in serialized
    assert "credential" not in serialized
    assert "handshake" not in serialized
