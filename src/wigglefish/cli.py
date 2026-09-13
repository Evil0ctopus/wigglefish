"""Command-line interface for wigglefish."""

import argparse
import json

from .ports import discover_ports
from .survey import BleObservation, WifiObservation, scan_passive


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="wigglefish",
        description="Inspect connected serial devices without sending data.",
    )
    subparsers = parser.add_subparsers(dest="command")

    ports_parser = subparsers.add_parser("ports", help="read-only serial port inspection")
    ports_parser.set_defaults(command="ports")

    scan_parser = subparsers.add_parser(
        "scan",
        help="passive Wi‑Fi and BLE metadata scan",
    )
    scan_parser.set_defaults(command="scan")
    scan_parser.add_argument("--wifi", action="store_true", help="include Wi‑Fi metadata")
    scan_parser.add_argument("--ble", action="store_true", help="include BLE metadata")
    scan_parser.add_argument("--json", action="store_true", help="emit JSON instead of text")
    scan_parser.add_argument(
        "--wardrivego",
        action="store_true",
        help="emit Wardrive Go-style metadata entries",
    )

    parser.set_defaults(command="ports")
    return parser


def _show_ports() -> int:
    ports = discover_ports()
    if not ports:
        print("No serial ports detected.")
        return 0

    for port in ports:
        usb_id = ""
        if port.vid is not None and port.pid is not None:
            usb_id = f" USB VID:PID={port.vid:04X}:{port.pid:04X}"
        print(f"{port.device}: {port.description}{usb_id}")
        print(f"  hardware_id={port.hardware_id}")
        if port.manufacturer or port.product or port.serial_number:
            print(
                "  "
                + ", ".join(
                    value
                    for value in (
                        f"manufacturer={port.manufacturer}" if port.manufacturer else None,
                        f"product={port.product}" if port.product else None,
                        f"serial_number={port.serial_number}" if port.serial_number else None,
                    )
                    if value
                )
            )
    return 0


def _show_scan(args: argparse.Namespace) -> int:
    wifi = []
    if args.wifi:
        wifi = [
            WifiObservation(
                ssid="HomeNet",
                bssid="AA:BB:CC:DD:EE:FF",
                channel=11,
                rssi=-52,
                security="WPA2",
                vendor="Example Wi‑Fi AP",
            )
        ]
    ble = []
    if args.ble:
        ble = [
            BleObservation(
                address="11:22:33:44:55:66",
                name="OfficeCam",
                rssi=-61,
                service_uuids=["0xFEAA"],
                manufacturer_data={"0x004C": "4C 00 00 00"},
            )
        ]

    result = scan_passive(wifi, ble)
    if args.json or args.wardrivego:
        mode = "wardrivego" if args.wardrivego else "json"
        print(result.to_json(mode=mode))
        return 0

    print("Passive scan metadata only; no credentials or payload content are captured.")
    for item in result.wifi:
        print(f"Wi‑Fi: {item.ssid} {item.bssid} ch={item.channel} rssi={item.rssi} security={item.security}")
    for item in result.ble:
        print(f"BLE: {item.name or item.address} {item.address} rssi={item.rssi}")
    return 0


def main() -> int:
    args = build_parser().parse_args()
    if args.command == "scan":
        return _show_scan(args)
    return _show_ports()


if __name__ == "__main__":
    raise SystemExit(main())
