"""Security scoring system for Wi-Fi networks.

Evaluates network security posture based on encryption, standards, and known vulnerabilities.
"""

from __future__ import annotations

from enum import Enum


class SecurityRisk(Enum):
    """Risk level classification."""
    CRITICAL = "CRITICAL"  # Open or WEP
    HIGH = "HIGH"  # WPA with weak config
    MEDIUM = "MEDIUM"  # WPA2 standard
    LOW = "LOW"  # WPA3 or strong WPA2
    UNKNOWN = "UNKNOWN"  # Unknown encryption


class SecurityScore:
    """Represents the security evaluation of a network."""
    
    def __init__(
        self,
        score: int,
        risk_level: SecurityRisk,
        vulnerabilities: list[str],
        recommendations: list[str]
    ):
        self.score = score  # 0-100, higher is more secure
        self.risk_level = risk_level
        self.vulnerabilities = vulnerabilities
        self.recommendations = recommendations
    
    def to_dict(self) -> dict:
        return {
            "score": self.score,
            "risk_level": self.risk_level.value,
            "vulnerabilities": self.vulnerabilities,
            "recommendations": self.recommendations,
        }


def score_security(
    ssid: str,
    security: str | None = None,
    channel: int | None = None,
) -> SecurityScore:
    """
    Score the security of a Wi-Fi network.
    
    Args:
        ssid: Network SSID name
        security: Security type (e.g., "WPA3", "WPA2-PSK", "WEP", "Open")
        channel: Wi-Fi channel (2.4GHz or 5GHz/6GHz indicator)
    
    Returns:
        SecurityScore object with detailed assessment
    """
    vulnerabilities = []
    recommendations = []
    score = 0
    risk_level = SecurityRisk.UNKNOWN
    
    if not security or security.upper() == "OPEN":
        # No encryption at all
        score = 0
        risk_level = SecurityRisk.CRITICAL
        vulnerabilities.append("No encryption (Open network)")
        vulnerabilities.append("All traffic is transmitted in plaintext")
        vulnerabilities.append("Anyone can connect and intercept data")
        recommendations.append("Enable WPA3 or WPA2 encryption immediately")
        
    elif "WEP" in (security or "").upper():
        # WEP is deprecated and broken
        score = 10
        risk_level = SecurityRisk.CRITICAL
        vulnerabilities.append("WEP encryption (deprecated and broken)")
        vulnerabilities.append("WEP can be cracked in minutes")
        vulnerabilities.append("Severely weakened security posture")
        recommendations.append("Replace WEP with WPA2 or WPA3 urgently")
        
    elif "WPA" in (security or "").upper():
        # WPA family - need to evaluate details
        sec_upper = (security or "").upper()
        
        if "WPA3" in sec_upper:
            # Strongest encryption available
            score = 90
            risk_level = SecurityRisk.LOW
            recommendations.append("Excellent security configuration")
            
            if channel and channel in [14]:  # 2.4GHz only
                score -= 5
                recommendations.append("Consider enabling 5GHz for better performance")
                
        elif "WPA2" in sec_upper:
            # Modern but not newest
            score = 75
            risk_level = SecurityRisk.MEDIUM
            recommendations.append("WPA2 is secure with proper configuration")
            
            if "PSK" in sec_upper or "CCMP" in sec_upper:
                score += 10
                
            # Check for PSK (Pre-Shared Key) patterns
            if _looks_like_weak_password(ssid):
                score -= 20
                risk_level = SecurityRisk.HIGH
                vulnerabilities.append("SSID pattern suggests weak password")
                recommendations.append("Use strong, random passwords (20+ chars)")
                
        else:
            # Original WPA (rare)
            score = 60
            risk_level = SecurityRisk.HIGH
            vulnerabilities.append("WPA (original) has known vulnerabilities")
            recommendations.append("Upgrade to WPA2 or WPA3")
    else:
        # Unknown security type
        score = 50
        risk_level = SecurityRisk.UNKNOWN
        recommendations.append("Could not determine security level")
    
    # Additional heuristic checks
    if _looks_like_default_network(ssid):
        score -= 10
        risk_level = _increase_risk(risk_level)
        vulnerabilities.append("Default network name suggests default credentials")
        recommendations.append("Change default SSID and password")
    
    if _looks_like_guest_network(ssid):
        score -= 5
        recommendations.append("Guest networks typically have weaker security")
    
    # Ensure score bounds
    score = max(0, min(100, score))
    
    return SecurityScore(
        score=score,
        risk_level=risk_level,
        vulnerabilities=vulnerabilities,
        recommendations=recommendations,
    )


def _looks_like_weak_password(ssid: str) -> bool:
    """Heuristic: check if SSID suggests weak password."""
    weak_patterns = [
        "password", "admin", "123", "qwerty", "abc", 
        "default", "guest", "test", "demo", "login"
    ]
    ssid_lower = ssid.lower()
    return any(pattern in ssid_lower for pattern in weak_patterns)


def _looks_like_default_network(ssid: str) -> bool:
    """Heuristic: check if SSID is likely default."""
    default_patterns = [
        "linksys", "netgear", "tp-link", "dlink", "asus",
        "belkin", "d-link", "airlink", "motorola",
        "default", "admin", "network", "setup", "wifi"
    ]
    ssid_lower = ssid.lower()
    return any(pattern in ssid_lower for pattern in default_patterns)


def _looks_like_guest_network(ssid: str) -> bool:
    """Heuristic: check if SSID is a guest network."""
    guest_patterns = ["guest", "public", "open", "-guest", "_guest"]
    ssid_lower = ssid.lower()
    return any(pattern in ssid_lower for pattern in guest_patterns)


def _increase_risk(current_risk: SecurityRisk) -> SecurityRisk:
    """Escalate risk level by one step."""
    risk_escalation = {
        SecurityRisk.LOW: SecurityRisk.MEDIUM,
        SecurityRisk.MEDIUM: SecurityRisk.HIGH,
        SecurityRisk.HIGH: SecurityRisk.CRITICAL,
        SecurityRisk.CRITICAL: SecurityRisk.CRITICAL,
        SecurityRisk.UNKNOWN: SecurityRisk.HIGH,
    }
    return risk_escalation.get(current_risk, current_risk)
