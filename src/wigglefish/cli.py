"""Command-line interface for wigglefish."""

import argparse
import json

from .ports import discover_ports
from .survey import BleObservation, WifiObservation, scan_passive
from .enrichment import enrich_observations_batch
from .api import WigglefishAPI


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
    scan_parser.add_argument(
        "--enriched",
        action="store_true",
        help="include vendor, device type, and security scoring",
    )
    scan_parser.add_argument(
        "--analytics",
        action="store_true",
        help="show analytics dashboard",
    )
    scan_parser.add_argument(
        "--security-only",
        action="store_true",
        help="show only vulnerable networks",
    )

    tools_parser = subparsers.add_parser(
        "tools",
        help="active and defensive wireless tools frame generator",
    )
    tools_subparsers = tools_parser.add_subparsers(dest="tool_action")

    beacon_tool = tools_subparsers.add_parser("beacon", help="generate raw 802.11 beacon frame")
    beacon_tool.add_argument("--ssid", type=str, default="Wigglefish-AP", help="target SSID")
    beacon_tool.add_argument("--channel", type=int, default=1, help="channel (1-14)")
    beacon_tool.add_argument("--wpa2", action="store_true", default=True, help="include WPA2 RSN element")

    deauth_tool = tools_subparsers.add_parser("deauth", help="generate raw 802.11 deauth frame")
    deauth_tool.add_argument("--ap", type=str, required=True, help="Access point BSSID (e.g. 00:11:22:33:44:55)")
    deauth_tool.add_argument("--client", type=str, default="FF:FF:FF:FF:FF:FF", help="Client MAC (default broadcast)")
    deauth_tool.add_argument("--reason", type=int, default=7, help="Deauth reason code")

    ble_tool = tools_subparsers.add_parser("blespam", help="generate BLE ecosystem advertisement payload")
    ble_tool.add_argument("--type", choices=["apple", "fastpair"], default="apple", help="ecosystem type")
    ble_tool.add_argument("--model", type=str, default="0e20", help="Apple device model hex or FastPair ID")

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
            ),
            WifiObservation(
                ssid="GuestWiFi",
                bssid="00:1A:92:00:00:01",
                channel=6,
                rssi=-68,
                security="WPA2",
            ),
            WifiObservation(
                ssid="OpenNetwork",
                bssid="00:11:22:33:44:55",
                channel=1,
                rssi=-75,
                security="Open",
            ),
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

    # Basic scan
    result = scan_passive(wifi, ble)
    
    # Handle enriched output
    if args.enriched or args.security_only or args.analytics:
        api = WigglefishAPI()
        api.add_observations(wifi)
        
        if args.analytics:
            print("\n=== ANALYTICS DASHBOARD ===\n")
            stats = api.get_statistics_dashboard()
            print(f"Total Networks: {stats['metrics']['total_networks']}")
            print(f"Unique Vendors: {stats['metrics']['unique_vendors']}")
            print(f"Channels in Use: {stats['metrics']['channels_in_use']}")
            
            print("\n--- Encryption Breakdown ---")
            for enc_type, count in stats['encryption'].items():
                if count > 0:
                    print(f"  {enc_type}: {count}")
            
            print("\n--- Risk Distribution ---")
            for risk, count in stats['risk_distribution'].items():
                if count > 0:
                    print(f"  {risk}: {count}")
            
            if stats['signal_distribution']:
                print("\n--- Signal Strength ---")
                sig = stats['signal_distribution']
                print(f"  Average RSSI: {sig['avg_rssi']} dBm")
                print(f"  Range: {sig['min_rssi']} to {sig['max_rssi']} dBm")
                print(f"  Strong (>-67 dBm): {sig['signal_distribution']['strong']}")
                print(f"  Medium (-80 to -67 dBm): {sig['signal_distribution']['medium']}")
                print(f"  Weak (<-80 dBm): {sig['signal_distribution']['weak']}")
            
            print("\n--- Top Vendors ---")
            for vendor, count in stats['top_vendors'][:5]:
                print(f"  {vendor}: {count}")
            
            return 0
        
        elif args.security_only:
            print("\n=== VULNERABLE NETWORKS ===\n")
            vulnerable = api.filter_networks(risk_level="CRITICAL")
            if not vulnerable:
                vulnerable = api.filter_networks(risk_level="HIGH")
            
            if not vulnerable:
                print("No vulnerable networks found!")
                return 0
            
            for net in vulnerable:
                print(f"\n[{net['risk_level']}] {net['ssid']}")
                print(f"  BSSID: {net['bssid']}")
                print(f"  Security: {net['security']}")
                print(f"  Score: {net['security_score']}/100")
                for vuln in net['vulnerabilities']:
                    print(f"  ⚠ {vuln}")
                for rec in net['recommendations']:
                    print(f"  ✓ {rec}")
            
            return 0
        
        else:  # enriched
            print("\nPassive scan with enriched data (vendor & security):\n")
            for item in api.get_network_list("rssi"):
                print(f"Wi‑Fi: {item['ssid']}")
                print(f"  BSSID: {item['bssid']} | Vendor: {item['vendor']}")
                print(f"  Channel: {item['channel']} | RSSI: {item['rssi']} dBm")
                print(f"  Security: {item['security']} | Score: {item['security_score']}/100")
                print(f"  Device Type: {item['device_type']}")
                print(f"  Risk Level: {item['risk_level']}")
                print()
            
            return 0
    
    # Standard output
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


def _show_tools(args: argparse.Namespace) -> int:
    from .tools import (
        create_beacon_frame,
        create_deauth_frame,
        create_apple_adv,
        create_fast_pair_adv,
        parse_mac,
    )

    if args.tool_action == "beacon":
        frame = create_beacon_frame(args.ssid, channel=args.channel, is_wpa2=args.wpa2)
        print(f"Generated 802.11 Beacon Frame ({len(frame)} bytes) for '{args.ssid}' on ch {args.channel}:")
        print(frame.hex())
        cmd = {"cmd": "tx_raw", "channel": args.channel, "data_hex": frame.hex(), "count": 1}
        print(f"\nESP32 Command:\n{json.dumps(cmd)}")
        return 0

    if args.tool_action == "deauth":
        ap_bytes = parse_mac(args.ap)
        client_bytes = parse_mac(args.client)
        frame = create_deauth_frame(ap_bytes, client_bytes, reason_code=args.reason)
        print(f"Generated 802.11 Deauth Frame ({len(frame)} bytes) AP={args.ap} Client={args.client} Reason={args.reason}:")
        print(frame.hex())
        cmd = {"cmd": "tx_raw", "channel": 1, "data_hex": frame.hex(), "count": 15, "delay_ms": 8}
        print(f"\nESP32 Command:\n{json.dumps(cmd)}")
        return 0

    if args.tool_action == "blespam":
        if args.type == "apple":
            payload = create_apple_adv(args.model)
        else:
            try:
                model_id = int(args.model, 16) if args.model.startswith("0x") else int(args.model)
            except ValueError:
                model_id = 0xF37335
            payload = create_fast_pair_adv(model_id)

        print(f"Generated BLE Advertisement Payload ({len(payload)} bytes) Type={args.type}:")
        print(payload.hex())
        cmd = {"cmd": "ble_adv_raw", "payload_hex": payload.hex()}
        print(f"\nESP32 Command:\n{json.dumps(cmd)}")
        return 0

    print("Specify a tool action: beacon, deauth, blespam (e.g. `wigglefish tools beacon --ssid TestNet`)")
    return 1


def main() -> int:
    args = build_parser().parse_args()
    if args.command == "scan":
        return _show_scan(args)
    if args.command == "tools":
        return _show_tools(args)
    return _show_ports()


if __name__ == "__main__":
    raise SystemExit(main())
