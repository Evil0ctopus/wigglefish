import json
from unittest.mock import MagicMock, patch

import pytest

from wigglefish.cli import build_parser, main
from wigglefish.serial_ingest import observations_from_lines, parse_stream_line
from wigglefish.survey import BleObservation, WifiObservation, scan_passive


def test_parser_accepts_scan_command() -> None:
    args = build_parser().parse_args(["scan", "--wifi", "--ble", "--json"])
    assert args.command == "scan"
    assert args.wifi is True
    assert args.ble is True
    assert args.json is True
    assert args.serial is None


def test_parser_accepts_serial_flag() -> None:
    args = build_parser().parse_args(
        ["scan", "--serial", "/dev/ttyUSB0", "--duration", "2.5", "--baud", "115200"]
    )
    assert args.command == "scan"
    assert args.serial == "/dev/ttyUSB0"
    assert args.duration == 2.5
    assert args.baud == 115200


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


def test_parser_accepts_csv_flag() -> None:
    args = build_parser().parse_args(["scan", "--wifi", "--ble", "--csv"])
    assert args.command == "scan"
    assert args.wifi is True
    assert args.ble is True
    assert args.csv is True


def test_csv_export_matches_android_header_and_is_metadata_only() -> None:
    wifi = WifiObservation(
        ssid='Cafe "Main"',
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
    csv_text = result.to_csv()
    lines = csv_text.splitlines()
    lowered = csv_text.lower()

    assert lines[0] == "MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME"
    assert len(lines) == 3

    wifi_row = lines[1]
    ble_row = lines[2]

    assert wifi_row == (
        '"AA:BB:CC:DD:EE:FF","Cafe ""Main""","WPA2","11","-52","wifi",""'
    )
    assert ble_row == (
        '"11:22:33:44:55:66","","","0","-61","bluetooth","OfficeCam"'
    )

    assert "password" not in lowered
    assert "credential" not in lowered
    assert "handshake" not in lowered
    assert "vendor" not in lowered
    assert "manufacturer" not in lowered


def test_csv_scan_cli_prints_android_shape(capsys: pytest.CaptureFixture[str]) -> None:
    with patch("sys.argv", ["wigglefish", "scan", "--wifi", "--ble", "--csv"]):
        assert main() == 0
    out = capsys.readouterr().out.strip()
    lines = out.splitlines()
    assert lines[0] == "MAC,SSID,AUTH,CHANNEL,RSSI,TYPE,NAME"
    assert '"HomeNet"' in lines[1]
    assert '"wifi"' in lines[1]
    assert '"OfficeCam"' in lines[2]
    assert '"bluetooth"' in lines[2]
    assert "password" not in out.lower()


def test_parse_stream_line_wifi_and_events() -> None:
    wifi = parse_stream_line(
        '{"type":"wifi","ssid":"Example","bssid":"AA:BB:CC:DD:EE:FF",'
        '"channel":11,"rssi":-52,"security":"WPA2","encryption":"WPA2"}'
    )
    assert isinstance(wifi, WifiObservation)
    assert wifi.ssid == "Example"
    assert wifi.bssid == "AA:BB:CC:DD:EE:FF"
    assert wifi.channel == 11
    assert wifi.rssi == -52
    assert wifi.security == "WPA2"

    assert parse_stream_line('{"event":"scan_end"}') is None
    assert parse_stream_line('{"event":"ready","mode":"auto_scan"}') is None
    assert parse_stream_line("not-json") is None
    assert parse_stream_line("") is None


def test_parse_stream_line_bluetooth_manufacturer_bytes() -> None:
    # Company ID 0x004C little-endian as emitted by firmware AD type 0xFF.
    ble = parse_stream_line(
        '{"type":"bluetooth","address":"11:22:33:44:55:66",'
        '"mac":"11:22:33:44:55:66","rssi":-61,"name":"Beacon",'
        '"manufacturer_data":[76,0,0,0]}'
    )
    assert isinstance(ble, BleObservation)
    assert ble.address == "11:22:33:44:55:66"
    assert ble.name == "Beacon"
    assert ble.rssi == -61
    assert ble.manufacturer_data == {"0x004C": "00 00"}

    alias = parse_stream_line(
        '{"type":"ble","address":"AA:BB:CC:DD:EE:11","rssi":-70}'
    )
    assert isinstance(alias, BleObservation)
    assert alias.address == "AA:BB:CC:DD:EE:11"


def test_observations_from_lines_dedupes_by_strongest_rssi() -> None:
    lines = [
        '{"event":"scan_start"}',
        '{"type":"wifi","ssid":"A","bssid":"AA:BB:CC:DD:EE:FF","channel":1,"rssi":-80,"security":"OPEN"}',
        '{"type":"wifi","ssid":"A","bssid":"AA:BB:CC:DD:EE:FF","channel":1,"rssi":-40,"security":"OPEN"}',
        '{"type":"bluetooth","address":"11:22:33:44:55:66","rssi":-90,"name":"cam"}',
        '{"type":"bluetooth","address":"11:22:33:44:55:66","rssi":-50,"name":"cam"}',
        '{"event":"scan_end"}',
    ]
    wifi, ble = observations_from_lines(lines)
    assert len(wifi) == 1
    assert wifi[0].rssi == -40
    assert len(ble) == 1
    assert ble[0].rssi == -50
    blob = json.dumps([wifi[0].__dict__, ble[0].__dict__]).lower()
    assert "password" not in blob
    assert "deauth" not in blob
    assert "handshake" not in blob


def test_demo_scan_cli_labels_stub(capsys: pytest.CaptureFixture[str]) -> None:
    with patch("sys.argv", ["wigglefish", "scan", "--wifi", "--ble"]):
        assert main() == 0
    out = capsys.readouterr().out
    assert "Source=demo/stub" in out
    assert "Demo/stub observations" in out
    assert "HomeNet" in out
    assert "OfficeCam" in out


def test_serial_scan_cli_uses_passive_reader(capsys: pytest.CaptureFixture[str]) -> None:
    wifi = [
        WifiObservation(
            ssid="FieldNet",
            bssid="DE:AD:BE:EF:00:01",
            channel=6,
            rssi=-45,
            security="WPA2-PSK",
        )
    ]
    ble = [
        BleObservation(address="01:02:03:04:05:06", name="Sensor", rssi=-55)
    ]
    with (
        patch("sys.argv", ["wigglefish", "scan", "--serial", "/dev/ttyUSB9", "--json"]),
        patch("wigglefish.cli.read_passive_serial", return_value=(wifi, ble)) as reader,
    ):
        assert main() == 0
    reader.assert_called_once_with("/dev/ttyUSB9", baudrate=115200, duration_seconds=5.0)
    payload = json.loads(capsys.readouterr().out)
    assert payload["wifi"][0]["ssid"] == "FieldNet"
    assert payload["ble"][0]["name"] == "Sensor"
    assert "password" not in json.dumps(payload).lower()


def test_serial_scan_cli_reports_open_failure(capsys: pytest.CaptureFixture[str]) -> None:
    from serial import SerialException

    with (
        patch("sys.argv", ["wigglefish", "scan", "--serial", "/dev/does-not-exist"]),
        patch(
            "wigglefish.cli.read_passive_serial",
            side_effect=SerialException("could not open port"),
        ),
    ):
        assert main() == 1
    err = capsys.readouterr().err
    assert "Serial ingest failed" in err


def test_serial_ingest_never_writes_to_port() -> None:
    handle = MagicMock()
    handle.read.side_effect = [
        b'{"type":"wifi","ssid":"X","bssid":"AA:BB:CC:DD:EE:FF","channel":1,"rssi":-30,"security":"OPEN"}\n',
        b"",
        b"",
    ]
    with (
        patch("wigglefish.serial_ingest.serial.Serial") as serial_ctor,
        patch("wigglefish.serial_ingest.time.monotonic", side_effect=[0.0, 0.1, 0.2, 10.0]),
    ):
        serial_ctor.return_value.__enter__.return_value = handle
        from wigglefish.serial_ingest import read_passive_serial

        wifi, ble = read_passive_serial("/dev/ttyUSB0", duration_seconds=1.0)

    serial_ctor.assert_called_once()
    assert serial_ctor.call_args.kwargs["port"] == "/dev/ttyUSB0"
    assert handle.write.call_count == 0
    assert len(wifi) == 1
    assert wifi[0].ssid == "X"
    assert ble == []
