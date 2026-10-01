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


def test_radio_tools_frame_generation() -> None:
    from wigglefish.tools import (
        create_beacon_frame,
        create_deauth_frame,
        create_apple_adv,
        create_fast_pair_adv,
        parse_mac,
    )

    ap_mac = parse_mac("00:11:22:33:44:55")
    assert ap_mac == b"\x00\x11\x22\x33\x44\x55"

    beacon = create_beacon_frame("TestSSID", bssid=ap_mac, channel=6, is_wpa2=True)
    assert len(beacon) > 40
    assert b"TestSSID" in beacon

    deauth = create_deauth_frame(ap_mac)
    assert len(deauth) == 26
    assert deauth[0] == 0xC0  # Deauth frame control

    apple_adv = create_apple_adv("0E20")
    assert len(apple_adv) > 10
    assert b"\x4c\x00" in apple_adv  # Apple ID

    fast_pair = create_fast_pair_adv(0xF37335)
    assert len(fast_pair) > 10
    assert b"\x2c\xfe" in fast_pair

    from wigglefish.tools import create_triones_rgb, create_lotus_rgb, create_probe_request
    triones = create_triones_rgb(255, 0, 128)
    assert len(triones) == 7
    assert triones[0] == 0x56

    lotus = create_lotus_rgb(0, 255, 0)
    assert len(lotus) == 9
    assert lotus[0] == 0x7E

    probe = create_probe_request("TestProbe")
    assert len(probe) > 24
    assert b"TestProbe" in probe


def test_parser_accepts_tools_command() -> None:
    parser = build_parser()
    args = parser.parse_args(["tools", "beacon", "--ssid", "FakeAP", "--channel", "6"])
    assert args.command == "tools"
    assert args.tool_action == "beacon"
    assert args.ssid == "FakeAP"
    assert args.channel == 6

    args_deauth = parser.parse_args(["tools", "deauth", "--ap", "11:22:33:44:55:66"])
    assert args_deauth.tool_action == "deauth"
    assert args_deauth.ap == "11:22:33:44:55:66"
