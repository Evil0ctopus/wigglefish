"""Analytics and statistics for scan sessions.

Provides insights like channel congestion, encryption breakdown, signal distribution.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass

from .survey import WifiObservation


@dataclass
class ChannelStats:
    """Statistics for a single Wi-Fi channel."""
    channel: int
    count: int
    avg_rssi: float
    strongest_rssi: int
    weakest_rssi: int
    networks: list[str]  # List of SSIDs on this channel


@dataclass
class EncryptionStats:
    """Breakdown of encryption types."""
    open: int
    wep: int
    wpa: int
    wpa2: int
    wpa3: int
    unknown: int
    
    def to_dict(self) -> dict:
        return {
            "open": self.open,
            "wep": self.wep,
            "wpa": self.wpa,
            "wpa2": self.wpa2,
            "wpa3": self.wpa3,
            "unknown": self.unknown,
        }


@dataclass
class SignalDistribution:
    """Signal strength distribution statistics."""
    min_rssi: int
    max_rssi: int
    avg_rssi: float
    median_rssi: int
    strong_count: int  # -50 to -67 dBm
    medium_count: int  # -68 to -80 dBm
    weak_count: int    # < -80 dBm
    
    def to_dict(self) -> dict:
        return {
            "min_rssi": self.min_rssi,
            "max_rssi": self.max_rssi,
            "avg_rssi": round(self.avg_rssi, 2),
            "median_rssi": self.median_rssi,
            "signal_distribution": {
                "strong": self.strong_count,
                "medium": self.medium_count,
                "weak": self.weak_count,
            }
        }


@dataclass
class RiskSummary:
    """Summary of security risk distribution."""
    critical_count: int
    high_count: int
    medium_count: int
    low_count: int
    unknown_count: int
    
    def to_dict(self) -> dict:
        return {
            "critical": self.critical_count,
            "high": self.high_count,
            "medium": self.medium_count,
            "low": self.low_count,
            "unknown": self.unknown_count,
        }


class SessionAnalytics:
    """Analyze a collection of Wi-Fi observations."""
    
    def __init__(self, observations: list[WifiObservation]):
        self.observations = observations
        self.total_networks = len(observations)
    
    def channel_analysis(self) -> dict[int, ChannelStats]:
        """Analyze network distribution across channels."""
        if not self.observations:
            return {}
        
        channel_groups = {}
        for obs in self.observations:
            if obs.channel is None:
                continue
            
            if obs.channel not in channel_groups:
                channel_groups[obs.channel] = []
            channel_groups[obs.channel].append(obs)
        
        stats = {}
        for channel, networks in channel_groups.items():
            rssi_values = [n.rssi for n in networks if n.rssi is not None]
            stats[channel] = ChannelStats(
                channel=channel,
                count=len(networks),
                avg_rssi=sum(rssi_values) / len(rssi_values) if rssi_values else 0,
                strongest_rssi=max(rssi_values) if rssi_values else 0,
                weakest_rssi=min(rssi_values) if rssi_values else 0,
                networks=[n.ssid for n in networks],
            )
        
        return stats
    
    def encryption_breakdown(self) -> EncryptionStats:
        """Count networks by encryption type."""
        encryption_counts = {
            "open": 0,
            "wep": 0,
            "wpa": 0,
            "wpa2": 0,
            "wpa3": 0,
            "unknown": 0,
        }
        
        for obs in self.observations:
            sec = (obs.security or "Unknown").upper()
            
            if sec == "OPEN":
                encryption_counts["open"] += 1
            elif "WEP" in sec:
                encryption_counts["wep"] += 1
            elif "WPA3" in sec:
                encryption_counts["wpa3"] += 1
            elif "WPA2" in sec:
                encryption_counts["wpa2"] += 1
            elif "WPA" in sec:
                encryption_counts["wpa"] += 1
            else:
                encryption_counts["unknown"] += 1
        
        return EncryptionStats(**encryption_counts)
    
    def signal_distribution(self) -> SignalDistribution | None:
        """Analyze signal strength distribution."""
        rssi_values = [o.rssi for o in self.observations if o.rssi is not None]
        
        if not rssi_values:
            return None
        
        rssi_values_sorted = sorted(rssi_values)
        strong = sum(1 for r in rssi_values if -67 <= r <= -50)
        medium = sum(1 for r in rssi_values if -80 <= r < -67)
        weak = sum(1 for r in rssi_values if r < -80)
        
        return SignalDistribution(
            min_rssi=min(rssi_values),
            max_rssi=max(rssi_values),
            avg_rssi=sum(rssi_values) / len(rssi_values),
            median_rssi=rssi_values_sorted[len(rssi_values_sorted) // 2],
            strong_count=strong,
            medium_count=medium,
            weak_count=weak,
        )
    
    def risk_distribution(self) -> RiskSummary:
        """Analyze security risk distribution."""
        risk_counts = {
            "critical_count": 0,
            "high_count": 0,
            "medium_count": 0,
            "low_count": 0,
            "unknown_count": 0,
        }
        
        for obs in self.observations:
            risk = (obs.risk_level or "unknown").lower()
            if risk == "critical":
                risk_counts["critical_count"] += 1
            elif risk == "high":
                risk_counts["high_count"] += 1
            elif risk == "medium":
                risk_counts["medium_count"] += 1
            elif risk == "low":
                risk_counts["low_count"] += 1
            else:
                risk_counts["unknown_count"] += 1
        
        return RiskSummary(**risk_counts)
    
    def device_type_breakdown(self) -> dict[str, int]:
        """Count networks by detected device type."""
        device_counts = Counter(
            obs.device_type or "Unknown"
            for obs in self.observations
        )
        return dict(device_counts)
    
    def vendor_breakdown(self) -> dict[str, int]:
        """Count networks by detected vendor."""
        vendor_counts = Counter(
            obs.vendor or "Unknown"
            for obs in self.observations
        )
        return dict(vendor_counts)
    
    def hotspot_clustering(self, rssi_threshold: int = -70) -> dict[str, list[str]]:
        """Identify networks clustered by strong signal strength.
        
        Networks above rssi_threshold are considered "strong" hotspots.
        """
        strong_networks = [
            obs for obs in self.observations
            if obs.rssi is not None and obs.rssi >= rssi_threshold
        ]
        
        # Group by channel (rough clustering)
        clusters = {}
        for obs in strong_networks:
            channel_key = f"Channel {obs.channel}" if obs.channel else "Unknown Channel"
            if channel_key not in clusters:
                clusters[channel_key] = []
            clusters[channel_key].append(obs.ssid)
        
        return clusters
    
    def generate_summary(self) -> dict:
        """Generate comprehensive analytics summary."""
        encryption = self.encryption_breakdown()
        signals = self.signal_distribution()
        risk = self.risk_distribution()
        
        return {
            "total_networks": self.total_networks,
            "encryption": encryption.to_dict(),
            "signal_distribution": signals.to_dict() if signals else None,
            "risk_distribution": risk.to_dict(),
            "vendors": self.vendor_breakdown(),
            "device_types": self.device_type_breakdown(),
            "channels": {
                ch: {
                    "count": stats.count,
                    "avg_rssi": round(stats.avg_rssi, 2),
                    "strongest": stats.strongest_rssi,
                    "weakest": stats.weakest_rssi,
                }
                for ch, stats in self.channel_analysis().items()
            }
        }
