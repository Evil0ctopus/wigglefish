"""API module for dashboard and Android app integration.

Provides structured endpoints for accessing enriched observation data, analytics, and insights.
"""

from __future__ import annotations

from typing import Any

from .analytics import SessionAnalytics
from .enrichment import enrich_observations_batch
from .survey import WifiObservation


class WigglefishAPI:
    """API interface for dashboard and mobile clients."""
    
    def __init__(self):
        self.observations: list[WifiObservation] = []
    
    def add_observations(self, obs: list[WifiObservation]) -> None:
        """Add new observations to the session."""
        enriched = enrich_observations_batch(obs)
        self.observations.extend(enriched)
    
    def clear_observations(self) -> None:
        """Clear all observations."""
        self.observations = []
    
    def get_network_list(self, sort_by: str = "rssi") -> list[dict]:
        """
        Get all networks as JSON-serializable dicts.
        
        Args:
            sort_by: Sort field ("rssi", "security_score", "risk_level", "ssid")
        
        Returns:
            List of network information dicts
        """
        networks = []
        for obs in self.observations:
            networks.append({
                "ssid": obs.ssid,
                "bssid": obs.bssid,
                "channel": obs.channel,
                "rssi": obs.rssi,
                "security": obs.security,
                "vendor": obs.vendor,
                "device_type": obs.device_type,
                "security_score": obs.security_score,
                "risk_level": obs.risk_level,
                "vulnerabilities": obs.vulnerabilities,
                "recommendations": obs.recommendations,
            })
        
        # Sort results
        if sort_by == "rssi" and self.observations:
            networks.sort(key=lambda x: x["rssi"] if x["rssi"] else -999, reverse=True)
        elif sort_by == "security_score":
            networks.sort(key=lambda x: x["security_score"] or 0)
        elif sort_by == "ssid":
            networks.sort(key=lambda x: x["ssid"].lower())
        
        return networks
    
    def search_networks(self, query: str) -> list[dict]:
        """
        Search networks by SSID, BSSID, vendor, or device type.
        
        Args:
            query: Search string (case-insensitive)
        
        Returns:
            List of matching networks
        """
        query_lower = query.lower()
        results = []
        
        for obs in self.observations:
            if (query_lower in obs.ssid.lower() or
                query_lower in obs.bssid.lower() or
                (obs.vendor and query_lower in obs.vendor.lower()) or
                (obs.device_type and query_lower in obs.device_type.lower())):
                
                results.append({
                    "ssid": obs.ssid,
                    "bssid": obs.bssid,
                    "channel": obs.channel,
                    "rssi": obs.rssi,
                    "security": obs.security,
                    "vendor": obs.vendor,
                    "device_type": obs.device_type,
                    "security_score": obs.security_score,
                    "risk_level": obs.risk_level,
                    "vulnerabilities": obs.vulnerabilities,
                    "recommendations": obs.recommendations,
                })
        
        return results
    
    def filter_networks(
        self,
        min_rssi: int | None = None,
        max_rssi: int | None = None,
        security_type: str | None = None,
        risk_level: str | None = None,
        vendor: str | None = None,
    ) -> list[dict]:
        """
        Filter networks by various criteria.
        
        Args:
            min_rssi: Minimum RSSI value
            max_rssi: Maximum RSSI value
            security_type: Filter by encryption ("Open", "WEP", "WPA", "WPA2", "WPA3")
            risk_level: Filter by risk ("CRITICAL", "HIGH", "MEDIUM", "LOW")
            vendor: Filter by vendor name
        
        Returns:
            List of matching networks
        """
        filtered = self.observations
        
        if min_rssi is not None:
            filtered = [o for o in filtered if o.rssi and o.rssi >= min_rssi]
        
        if max_rssi is not None:
            filtered = [o for o in filtered if o.rssi and o.rssi <= max_rssi]
        
        if security_type:
            sec_upper = security_type.upper()
            filtered = [o for o in filtered if o.security and sec_upper in o.security.upper()]
        
        if risk_level:
            filtered = [o for o in filtered if o.risk_level and risk_level.upper() in o.risk_level.upper()]
        
        if vendor:
            vendor_lower = vendor.lower()
            filtered = [o for o in filtered if o.vendor and vendor_lower in o.vendor.lower()]
        
        results = []
        for obs in filtered:
            results.append({
                "ssid": obs.ssid,
                "bssid": obs.bssid,
                "channel": obs.channel,
                "rssi": obs.rssi,
                "security": obs.security,
                "vendor": obs.vendor,
                "device_type": obs.device_type,
                "security_score": obs.security_score,
                "risk_level": obs.risk_level,
                "vulnerabilities": obs.vulnerabilities,
                "recommendations": obs.recommendations,
            })
        
        return results
    
    def get_vendor_info(self, bssid: str) -> dict:
        """
        Get vendor and device info for a specific BSSID.
        
        Args:
            bssid: MAC address to lookup
        
        Returns:
            Dict with vendor and device type info
        """
        obs = next((o for o in self.observations if o.bssid == bssid), None)
        
        if obs:
            return {
                "bssid": obs.bssid,
                "vendor": obs.vendor,
                "device_type": obs.device_type,
                "ssid": obs.ssid,
            }
        
        # If not in current observations, still do lookup
        from .mac_vendors import get_device_type, lookup_vendor
        vendor = lookup_vendor(bssid)
        return {
            "bssid": bssid,
            "vendor": vendor,
            "device_type": get_device_type(vendor, bssid),
            "ssid": None,
        }
    
    def get_security_assessment(self, bssid: str) -> dict | None:
        """
        Get detailed security assessment for a network.
        
        Args:
            bssid: Network BSSID to assess
        
        Returns:
            Dict with security details or None if not found
        """
        obs = next((o for o in self.observations if o.bssid == bssid), None)
        
        if not obs:
            return None
        
        return {
            "bssid": obs.bssid,
            "ssid": obs.ssid,
            "security": obs.security,
            "security_score": obs.security_score,
            "risk_level": obs.risk_level,
            "vulnerabilities": obs.vulnerabilities,
            "recommendations": obs.recommendations,
        }
    
    def get_analytics(self) -> dict:
        """
        Get comprehensive session analytics.
        
        Returns:
            Dict with statistics, distributions, insights
        """
        analytics = SessionAnalytics(self.observations)
        return analytics.generate_summary()
    
    def get_hotspots(self, rssi_threshold: int = -70) -> dict[str, list[str]]:
        """
        Get networks clustered by signal strength.
        
        Args:
            rssi_threshold: Minimum RSSI for "hotspot" classification
        
        Returns:
            Dict mapping channel to list of strong networks
        """
        analytics = SessionAnalytics(self.observations)
        return analytics.hotspot_clustering(rssi_threshold)
    
    def get_statistics_dashboard(self) -> dict:
        """
        Get data formatted for statistics dashboard display.
        
        Returns:
            Dict with charts/metrics ready for frontend
        """
        analytics = SessionAnalytics(self.observations)
        summary = analytics.generate_summary()
        
        return {
            "metrics": {
                "total_networks": summary["total_networks"],
                "unique_vendors": len(summary["vendors"]),
                "unique_device_types": len(summary["device_types"]),
                "channels_in_use": len(summary["channels"]),
            },
            "encryption": summary["encryption"],
            "risk_distribution": summary["risk_distribution"],
            "signal_distribution": summary["signal_distribution"],
            "top_vendors": sorted(
                summary["vendors"].items(),
                key=lambda x: x[1],
                reverse=True
            )[:10],
            "top_device_types": sorted(
                summary["device_types"].items(),
                key=lambda x: x[1],
                reverse=True
            )[:10],
            "channel_data": summary["channels"],
        }
    
    def export_for_android(self) -> dict:
        """
        Export data in format optimized for Android UI.
        
        Returns:
            Dict with all observations and analytics ready for mobile display
        """
        return {
            "status": "success",
            "count": len(self.observations),
            "networks": self.get_network_list("rssi"),
            "analytics": self.get_statistics_dashboard(),
            "timestamp": self._get_timestamp(),
        }
    
    @staticmethod
    def _get_timestamp() -> str:
        """Get current timestamp."""
        from datetime import datetime
        return datetime.now().isoformat()


# Global API instance
_api_instance = WigglefishAPI()


def get_api() -> WigglefishAPI:
    """Get the global API instance."""
    return _api_instance
