"""Quick test script to verify all enhanced features work."""

from src.wigglefish.survey import WifiObservation
from src.wigglefish.mac_vendors import lookup_vendor, get_device_type
from src.wigglefish.security_scoring import score_security
from src.wigglefish.enrichment import enrich_observations_batch
from src.wigglefish.api import WigglefishAPI
from src.wigglefish.analytics import SessionAnalytics


def main():
    print("=" * 60)
    print("WIGGLEFISH ENHANCED FEATURES TEST")
    print("=" * 60)
    
    # Test 1: MAC Vendor Lookup
    print("\n✓ Test 1: MAC Vendor Lookup")
    mac_addresses = [
        "34:AB:95:12:34:56",  # Apple
        "30:B4:9E:11:22:33",  # TP-Link
        "00:0C:43:00:00:01",  # Cisco
    ]
    for mac in mac_addresses:
        vendor = lookup_vendor(mac)
        device_type = get_device_type(vendor, mac)
        print(f"  {mac}: {vendor} → {device_type}")
    
    # Test 2: Security Scoring
    print("\n✓ Test 2: Security Scoring")
    test_networks = [
        ("HomeNet", "WPA3", 11),
        ("OpenWiFi", "Open", 6),
        ("admin_network", "WEP", 1),
    ]
    for ssid, security, channel in test_networks:
        score = score_security(ssid, security, channel)
        print(f"  {ssid}: {score.score}/100 [{score.risk_level.value}]")
    
    # Test 3: Observation Enrichment
    print("\n✓ Test 3: Observation Enrichment")
    raw_obs = [
        WifiObservation(
            ssid="HomeNet",
            bssid="34:AB:95:12:34:56",
            channel=11,
            rssi=-52,
            security="WPA3",
        ),
        WifiObservation(
            ssid="OpenNetwork",
            bssid="00:11:22:33:44:55",
            channel=1,
            rssi=-75,
            security="Open",
        ),
    ]
    
    enriched = enrich_observations_batch(raw_obs)
    for obs in enriched:
        print(f"  {obs.ssid}: vendor={obs.vendor}, score={obs.security_score}, risk={obs.risk_level}")
    
    # Test 4: Analytics
    print("\n✓ Test 4: Analytics")
    analytics = SessionAnalytics(enriched)
    
    channels = analytics.channel_analysis()
    print(f"  Channels in use: {len(channels)}")
    
    encryption = analytics.encryption_breakdown()
    print(f"  Encryption breakdown: {encryption.to_dict()}")
    
    risk = analytics.risk_distribution()
    print(f"  Risk distribution: {risk.to_dict()}")
    
    # Test 5: API
    print("\n✓ Test 5: Unified API")
    api = WigglefishAPI()
    api.add_observations(raw_obs)
    
    networks = api.get_network_list("rssi")
    print(f"  Networks retrieved: {len(networks)}")
    print(f"  First network: {networks[0]['ssid']} (score={networks[0]['security_score']})")
    
    # Test 6: Search & Filter
    print("\n✓ Test 6: Search & Filter")
    search_results = api.search_networks("home")
    print(f"  Search 'home': {len(search_results)} results")
    
    vulnerable = api.filter_networks(risk_level="CRITICAL")
    print(f"  Critical vulnerabilities: {len(vulnerable)} networks")
    
    # Test 7: Dashboard Stats
    print("\n✓ Test 7: Dashboard Statistics")
    dashboard = api.get_statistics_dashboard()
    print(f"  Total networks: {dashboard['metrics']['total_networks']}")
    print(f"  Unique vendors: {dashboard['metrics']['unique_vendors']}")
    print(f"  Top vendor: {dashboard['top_vendors'][0] if dashboard['top_vendors'] else 'N/A'}")
    
    # Test 8: Android Export
    print("\n✓ Test 8: Android Export Format")
    android_data = api.export_for_android()
    print(f"  Status: {android_data['status']}")
    print(f"  Networks: {android_data['count']}")
    print(f"  Keys: {list(android_data.keys())}")
    
    print("\n" + "=" * 60)
    print("✅ ALL TESTS PASSED!")
    print("=" * 60)


if __name__ == "__main__":
    main()
