"""Command-line interface for wigglefish."""

from __future__ import annotations

import argparse
import sys

from serial import SerialException

from .ports import discover_ports
from .serial_ingest import DEFAULT_BAUD, DEFAULT_DURATION_SECONDS, read_passive_serial
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
        help="passive Wi‑Fi and BLE metadata scan (demo by default; opt-in serial)",
        description=(
            "By default, scan emits built-in demo/stub metadata so the export "
            "formats can be exercised without hardware. Pass --serial PORT to "
            "passively read the ESP32 newline-delimited JSON stream (115200 baud, "
            "read-only; no commands are written)."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Real field capture today is Android + ESP32-C5 USB serial. "
            "Python --serial is a bounded, passive metadata ingest of that same stream."
        ),
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
    scan_parser.add_argument(
        "--serial",
        metavar="PORT",
        help=(
            "passively ingest ESP32 NDJSON from PORT (read-only). "
            "Without this flag, scan uses demo/stub observations."
        ),
    )
    scan_parser.add_argument(
        "--duration",
        type=float,
        default=DEFAULT_DURATION_SECONDS,
        metavar="SECONDS",
        help=f"seconds to listen when using --serial (default: {DEFAULT_DURATION_SECONDS:g})",
    )
    scan_parser.add_argument(
        "--baud",
        type=int,
        default=DEFAULT_BAUD,
        help=f"serial baud rate for --serial (default: {DEFAULT_BAUD})",
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


def _demo_observations(
    include_wifi: bool,
    include_ble: bool,
) -> tuple[list[WifiObservation], list[BleObservation]]:
    wifi: list[WifiObservation] = []
    ble: list[BleObservation] = []
    if include_wifi:
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
    if include_ble:
        ble = [
            BleObservation(
                address="11:22:33:44:55:66",
                name="OfficeCam",
                rssi=-61,
                service_uuids=["0xFEAA"],
                manufacturer_data={"0x004C": "4C 00 00 00"},
            )
        ]
    return wifi, ble


def _show_scan(args: argparse.Namespace) -> int:
    include_wifi = args.wifi
    include_ble = args.ble
    # Serial mode defaults to both radios when the caller did not filter.
    if args.serial and not include_wifi and not include_ble:
        include_wifi = True
        include_ble = True

    source_label = "demo/stub"
    if args.serial:
        source_label = f"serial:{args.serial}"
        try:
            wifi, ble = read_passive_serial(
                args.serial,
                baudrate=args.baud,
                duration_seconds=args.duration,
            )
        except SerialException as error:
            print(f"Serial ingest failed: {error}", file=sys.stderr)
            return 1
        if not include_wifi:
            wifi = []
        if not include_ble:
            ble = []
    else:
        wifi, ble = _demo_observations(include_wifi, include_ble)

    result = scan_passive(wifi, ble)
    if args.json or args.wardrivego:
        mode = "wardrivego" if args.wardrivego else "json"
        print(result.to_json(mode=mode))
        return 0

    print(
        "Passive scan metadata only; no credentials or payload content are captured. "
        f"Source={source_label}."
    )
    if not args.serial:
        print(
            "Demo/stub observations (no --serial). "
            "Real field capture is Android + ESP32 today; "
            "pass --serial PORT for opt-in passive NDJSON ingest."
        )
    for item in result.wifi:
        print(
            f"Wi‑Fi: {item.ssid} {item.bssid} ch={item.channel} "
            f"rssi={item.rssi} security={item.security}"
        )
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
