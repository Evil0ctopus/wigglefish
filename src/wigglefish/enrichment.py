"""Enrich observations with vendor, device type, and security information.

Integrates MAC vendor lookup and security scoring into observation objects.
"""

from __future__ import annotations

from .mac_vendors import get_device_type, lookup_vendor
from .security_scoring import score_security
from .survey import WifiObservation


def enrich_wifi_observation(obs: WifiObservation) -> WifiObservation:
    """
    Enrich a Wi-Fi observation with vendor, device type, and security info.
    
    Args:
        obs: Original WifiObservation
    
    Returns:
        Enhanced WifiObservation with vendor, device_type, security_score, risk_level
    """
    # Lookup vendor from BSSID
    vendor = lookup_vendor(obs.bssid)
    
    # Detect device type
    device_type = get_device_type(vendor, obs.bssid)
    
    # Score security
    security_score_obj = score_security(
        ssid=obs.ssid,
        security=obs.security,
        channel=obs.channel,
    )
    
    # Create enriched observation
    return WifiObservation(
        ssid=obs.ssid,
        bssid=obs.bssid,
        channel=obs.channel,
        rssi=obs.rssi,
        security=obs.security,
        vendor=vendor,
        device_type=device_type,
        security_score=security_score_obj.score,
        risk_level=security_score_obj.risk_level.value,
        vulnerabilities=security_score_obj.vulnerabilities,
        recommendations=security_score_obj.recommendations,
    )


def enrich_observations_batch(observations: list[WifiObservation]) -> list[WifiObservation]:
    """
    Enrich a batch of observations.
    
    Args:
        observations: List of WifiObservation objects
    
    Returns:
        List of enriched WifiObservation objects
    """
    return [enrich_wifi_observation(obs) for obs in observations]
