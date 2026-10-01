"""MAC address OUI (Organizationally Unique Identifier) vendor lookup.

Provides local lookup of MAC address manufacturers without external API calls.
Database built from IEEE OUI registry.
"""

from __future__ import annotations

# Common MAC OUI prefixes mapped to vendors
# Format: "AA:BB:CC" -> "Vendor Name"
# This is a curated subset of common IoT, networking, and device manufacturers
MAC_OUI_DATABASE = {
    # Apple
    "00:05:02": "Apple",
    "00:1A:92": "Apple",
    "00:1D:4F": "Apple",
    "00:1E:52": "Apple",
    "00:1E:C2": "Apple",
    "00:25:86": "Apple",
    "30:FD:38": "Apple",
    "34:AB:95": "Apple",
    "38:C0:96": "Apple",
    "3C:37:86": "Apple",
    "4C:B1:60": "Apple",
    "50:EA:D6": "Apple",
    "54:EE:75": "Apple",
    "58:55:CA": "Apple",
    "5C:F3:70": "Apple",
    "60:F1:69": "Apple",
    "64:4B:F0": "Apple",
    "68:A8:6D": "Apple",
    "6C:96:CF": "Apple",
    "70:CD:60": "Apple",
    "74:E2:F5": "Apple",
    "78:D2:F7": "Apple",
    "7C:D1:C3": "Apple",
    "80:C1:6E": "Apple",
    "84:B1:53": "Apple",
    "88:63:DF": "Apple",
    "8C:85:90": "Apple",
    "90:8D:78": "Apple",
    "94:65:9C": "Apple",
    "98:01:A7": "Apple",
    "9C:29:76": "Apple",
    "A0:D6:73": "Apple",
    "A4:12:69": "Apple",
    "A8:5E:60": "Apple",
    "AC:3C:0B": "Apple",
    "B0:34:95": "Apple",
    "B4:0B:44": "Apple",
    "B8:63:3D": "Apple",
    "BC:67:4D": "Apple",
    "C0:25:06": "Apple",
    "C4:2C:03": "Apple",
    "C8:6C:87": "Apple",
    "CC:29:F5": "Apple",
    "D0:03:4B": "Apple",
    "D4:6E:0E": "Apple",
    "D8:BB:C1": "Apple",
    "DC:A9:04": "Apple",
    "E0:AC:CB": "Apple",
    "E4:8B:46": "Apple",
    "E8:6D:8F": "Apple",
    "EC:51:FB": "Apple",
    "F0:18:98": "Apple",
    "F4:F5:D8": "Apple",
    "F8:FF:C2": "Apple",
    "FC:A6:67": "Apple",
    
    # Google
    "00:1A:11": "Google",
    "42:01:0A": "Google",
    "4E:45:DA": "Google",
    "54:04:A6": "Google",
    "5A:78:3E": "Google",
    "66:55:0A": "Google",
    "6E:1B:19": "Google",
    "74:6D:FC": "Google",
    "7A:02:F9": "Google",
    "8A:23:D0": "Google",
    "92:2A:7D": "Google",
    
    # Samsung
    "00:1C:4A": "Samsung",
    "00:1E:33": "Samsung",
    "08:08:C2": "Samsung",
    "08:37:3B": "Samsung",
    "0C:55:6A": "Samsung",
    "10:F7:B1": "Samsung",
    "1C:37:26": "Samsung",
    "20:40:08": "Samsung",
    "24:6E:96": "Samsung",
    "28:86:3D": "Samsung",
    "30:07:4D": "Samsung",
    "34:E6:AD": "Samsung",
    "38:0A:94": "Samsung",
    "40:A6:D9": "Samsung",
    "44:A2:E1": "Samsung",
    "48:B0:2D": "Samsung",
    "50:32:37": "Samsung",
    "54:27:58": "Samsung",
    "5C:0A:5B": "Samsung",
    "60:45:BD": "Samsung",
    "68:DF:DD": "Samsung",
    "70:5A:0F": "Samsung",
    "78:2B:46": "Samsung",
    
    # Microsoft
    "00:50:F2": "Microsoft",
    "4C:CC:6A": "Microsoft",
    "54:EE:75": "Microsoft",
    
    # Amazon
    "00:FE:C9": "Amazon",
    "44:65:0D": "Amazon",
    "4C:EF:C7": "Amazon",
    "50:F5:DA": "Amazon",
    "74:C7:00": "Amazon",
    
    # Intel
    "00:02:B3": "Intel",
    "00:03:47": "Intel",
    "00:12:3F": "Intel",
    "00:1A:A0": "Intel",
    "00:21:6A": "Intel",
    "00:25:86": "Intel",
    "00:30:48": "Intel",
    "00:4E:45": "Intel",
    "00:53:00": "Intel",
    
    # Cisco
    "00:00:0C": "Cisco",
    "00:01:42": "Cisco",
    "00:01:63": "Cisco",
    "00:01:96": "Cisco",
    "00:01:C7": "Cisco",
    "00:02:3D": "Cisco",
    "00:02:B9": "Cisco",
    "00:02:CA": "Cisco",
    "00:02:FD": "Cisco",
    "00:04:27": "Cisco",
    "00:04:9B": "Cisco",
    "00:04:C1": "Cisco",
    "00:04:DD": "Cisco",
    "00:05:00": "Cisco",
    "00:05:73": "Cisco",
    "00:05:DC": "Cisco",
    "00:06:52": "Cisco",
    "00:07:0E": "Cisco",
    "00:07:85": "Cisco",
    "00:08:21": "Cisco",
    "00:08:74": "Cisco",
    "00:09:11": "Cisco",
    "00:0A:41": "Cisco",
    "00:0A:B7": "Cisco",
    "00:0B:46": "Cisco",
    "00:0D:BD": "Cisco",
    "00:0E:08": "Cisco",
    "00:0F:F7": "Cisco",
    
    # TP-Link
    "00:2B:81": "TP-Link",
    "30:B4:9E": "TP-Link",
    "50:C7:BF": "TP-Link",
    "74:AC:B9": "TP-Link",
    "A8:5E:60": "TP-Link",
    
    # Arista
    "00:1C:73": "Arista",
    "00:50:56": "Arista",
    "08:00:27": "Arista",
    
    # Ubiquiti
    "00:15:6D": "Ubiquiti",
    "24:A4:3C": "Ubiquiti",
    "04:18:D6": "Ubiquiti",
    "08:55:31": "Ubiquiti",
    "44:D9:E7": "Ubiquiti",
    "80:2A:A8": "Ubiquiti",
    
    # Sony
    "00:02:33": "Sony",
    "00:0D:EE": "Sony",
    "00:15:34": "Sony",
    "08:3E:8E": "Sony",
    "50:C7:BF": "Sony",
    
    # LG
    "00:1A:7D": "LG",
    "00:1E:8F": "LG",
    "0C:37:DC": "LG",
    "20:8E:AB": "LG",
    "28:FE:F6": "LG",
    "38:63:BB": "LG",
    "5C:F3:70": "LG",
    "78:7B:8A": "LG",
    "A0:B3:CC": "LG",
    
    # NVIDIA
    "00:04:4B": "NVIDIA",
    "42:61:5F": "NVIDIA",
    
    # Chromecast/Google Home
    "34:08:04": "Google Chromecast",
    "52:74:F2": "Google Chromecast",
    
    # AirTag (Apple)
    "FC:58:FA": "Apple AirTag",
    
    # Flipper Zero (common BLE MAC pattern)
    "80:E1:26": "Flipper Zero",
    
    # Unknown/Generic
}


def lookup_vendor(mac_address: str) -> str:
    """
    Lookup MAC address vendor by OUI prefix.
    
    Args:
        mac_address: MAC address in format "AA:BB:CC:DD:EE:FF"
    
    Returns:
        Vendor name or "Unknown" if not found
    """
    if not mac_address or len(mac_address) < 8:
        return "Unknown"
    
    # Extract OUI (first 3 octets)
    oui = mac_address[:8].upper()
    
    return MAC_OUI_DATABASE.get(oui, "Unknown")


def get_device_type(vendor: str, bssid: str = "") -> str:
    """
    Classify device type based on vendor and BSSID patterns.
    
    Args:
        vendor: Vendor name from lookup
        bssid: MAC address for additional pattern matching
    
    Returns:
        Device type classification
    """
    if not vendor or vendor == "Unknown":
        return "Unknown Device"
    
    # Categorize by vendor
    if vendor == "Apple":
        if "AirTag" in vendor:
            return "Apple AirTag"
        return "Apple Device"
    elif vendor == "Google":
        if "Chromecast" in vendor:
            return "Streaming Device"
        return "Google Device"
    elif vendor in ["Canon", "Nikon", "Sony", "FUJIFILM"]:
        return "Camera/Imaging"
    elif vendor == "Flipper Zero":
        return "Security Tool"
    elif vendor in ["Amazon", "Google", "Apple"]:
        return "Smart Home Hub"
    elif vendor in ["Cisco", "TP-Link", "Ubiquiti", "Arista"]:
        return "Networking Equipment"
    else:
        return f"{vendor} Device"
