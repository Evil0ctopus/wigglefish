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
