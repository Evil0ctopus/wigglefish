package com.wigglefish.android

/**
 * On-device port of the Python backend's mac_vendors.py + security_scoring.py
 * so the USB-connected app can enrich networks without a Python bridge.
 */
object NetworkIntelligence {

    private val MAC_OUI_DATABASE: Map<String, String> = mapOf(
        // Apple
        "00:05:02" to "Apple", "00:1A:92" to "Apple", "00:1D:4F" to "Apple", "00:1E:52" to "Apple",
        "00:1E:C2" to "Apple", "00:25:86" to "Apple", "30:FD:38" to "Apple", "34:AB:95" to "Apple",
        "38:C0:96" to "Apple", "3C:37:86" to "Apple", "4C:B1:60" to "Apple", "50:EA:D6" to "Apple",
        "54:EE:75" to "Apple", "58:55:CA" to "Apple", "5C:F3:70" to "Apple", "60:F1:69" to "Apple",
        "64:4B:F0" to "Apple", "68:A8:6D" to "Apple", "6C:96:CF" to "Apple", "70:CD:60" to "Apple",
        "74:E2:F5" to "Apple", "78:D2:F7" to "Apple", "7C:D1:C3" to "Apple", "80:C1:6E" to "Apple",
        "84:B1:53" to "Apple", "88:63:DF" to "Apple", "8C:85:90" to "Apple", "90:8D:78" to "Apple",
        "94:65:9C" to "Apple", "98:01:A7" to "Apple", "9C:29:76" to "Apple", "A0:D6:73" to "Apple",
        "A4:12:69" to "Apple", "A8:5E:60" to "Apple", "AC:3C:0B" to "Apple", "B0:34:95" to "Apple",
        "B4:0B:44" to "Apple", "B8:63:3D" to "Apple", "BC:67:4D" to "Apple", "C0:25:06" to "Apple",
        "C4:2C:03" to "Apple", "C8:6C:87" to "Apple", "CC:29:F5" to "Apple", "D0:03:4B" to "Apple",
        "D4:6E:0E" to "Apple", "D8:BB:C1" to "Apple", "DC:A9:04" to "Apple", "E0:AC:CB" to "Apple",
        "E4:8B:46" to "Apple", "E8:6D:8F" to "Apple", "EC:51:FB" to "Apple", "F0:18:98" to "Apple",
        "F4:F5:D8" to "Apple", "F8:FF:C2" to "Apple", "FC:A6:67" to "Apple",
        // Google
        "00:1A:11" to "Google", "42:01:0A" to "Google", "4E:45:DA" to "Google", "54:04:A6" to "Google",
        "5A:78:3E" to "Google", "66:55:0A" to "Google", "6E:1B:19" to "Google", "74:6D:FC" to "Google",
        "7A:02:F9" to "Google", "8A:23:D0" to "Google", "92:2A:7D" to "Google",
        // Samsung
        "00:1C:4A" to "Samsung", "00:1E:33" to "Samsung", "08:08:C2" to "Samsung", "08:37:3B" to "Samsung",
        "0C:55:6A" to "Samsung", "10:F7:B1" to "Samsung", "1C:37:26" to "Samsung", "20:40:08" to "Samsung",
        "24:6E:96" to "Samsung", "28:86:3D" to "Samsung", "30:07:4D" to "Samsung", "34:E6:AD" to "Samsung",
        "38:0A:94" to "Samsung", "40:A6:D9" to "Samsung", "44:A2:E1" to "Samsung", "48:B0:2D" to "Samsung",
        "50:32:37" to "Samsung", "54:27:58" to "Samsung", "5C:0A:5B" to "Samsung", "60:45:BD" to "Samsung",
        "68:DF:DD" to "Samsung", "70:5A:0F" to "Samsung", "78:2B:46" to "Samsung",
        // Microsoft
        "00:50:F2" to "Microsoft", "4C:CC:6A" to "Microsoft",
        // Amazon
        "00:FE:C9" to "Amazon", "44:65:0D" to "Amazon", "4C:EF:C7" to "Amazon", "50:F5:DA" to "Amazon",
        "74:C7:00" to "Amazon",
        // Intel
        "00:02:B3" to "Intel", "00:03:47" to "Intel", "00:12:3F" to "Intel", "00:1A:A0" to "Intel",
        "00:21:6A" to "Intel", "00:30:48" to "Intel", "00:4E:45" to "Intel", "00:53:00" to "Intel",
        // Cisco
        "00:00:0C" to "Cisco", "00:01:42" to "Cisco", "00:01:63" to "Cisco", "00:01:96" to "Cisco",
        "00:01:C7" to "Cisco", "00:02:3D" to "Cisco", "00:02:B9" to "Cisco", "00:02:CA" to "Cisco",
        "00:02:FD" to "Cisco", "00:04:27" to "Cisco", "00:04:9B" to "Cisco", "00:04:C1" to "Cisco",
        "00:04:DD" to "Cisco", "00:05:00" to "Cisco", "00:05:73" to "Cisco", "00:05:DC" to "Cisco",
        "00:06:52" to "Cisco", "00:07:0E" to "Cisco", "00:07:85" to "Cisco", "00:08:21" to "Cisco",
        "00:08:74" to "Cisco", "00:09:11" to "Cisco", "00:0A:41" to "Cisco", "00:0A:B7" to "Cisco",
        "00:0B:46" to "Cisco", "00:0D:BD" to "Cisco", "00:0E:08" to "Cisco", "00:0F:F7" to "Cisco",
        // TP-Link
        "00:2B:81" to "TP-Link", "30:B4:9E" to "TP-Link", "50:C7:BF" to "TP-Link", "74:AC:B9" to "TP-Link",
        // Arista
        "00:1C:73" to "Arista", "00:50:56" to "Arista", "08:00:27" to "Arista",
        // Ubiquiti
        "00:15:6D" to "Ubiquiti", "24:A4:3C" to "Ubiquiti", "04:18:D6" to "Ubiquiti", "08:55:31" to "Ubiquiti",
        "44:D9:E7" to "Ubiquiti", "80:2A:A8" to "Ubiquiti",
        // Sony
        "00:02:33" to "Sony", "00:0D:EE" to "Sony", "00:15:34" to "Sony", "08:3E:8E" to "Sony",
        // LG
        "00:1A:7D" to "LG", "00:1E:8F" to "LG", "0C:37:DC" to "LG", "20:8E:AB" to "LG",
        "28:FE:F6" to "LG", "38:63:BB" to "LG", "78:7B:8A" to "LG", "A0:B3:CC" to "LG",
        // NVIDIA
        "00:04:4B" to "NVIDIA", "42:61:5F" to "NVIDIA",
        // Chromecast/Google Home
        "34:08:04" to "Google Chromecast", "52:74:F2" to "Google Chromecast",
        // AirTag
        "FC:58:FA" to "Apple AirTag",
        // Flipper Zero
        "80:E1:26" to "Flipper Zero",
    )

    fun lookupVendor(macAddress: String): String {
        if (macAddress.length < 8) return "Unknown"
        val oui = macAddress.substring(0, 8).uppercase()
        return MAC_OUI_DATABASE[oui] ?: "Unknown"
    }

    fun getDeviceType(vendor: String): String {
        if (vendor.isEmpty() || vendor == "Unknown") return "Unknown Device"
        return when {
            vendor == "Apple" -> "Apple Device"
            vendor == "Apple AirTag" -> "Apple AirTag"
            vendor == "Google" -> "Google Device"
            vendor == "Google Chromecast" -> "Streaming Device"
            vendor in listOf("Canon", "Nikon", "Sony", "FUJIFILM") -> "Camera/Imaging"
            vendor == "Flipper Zero" -> "Security Tool"
            vendor == "Amazon" -> "Smart Home Hub"
            vendor in listOf("Cisco", "TP-Link", "Ubiquiti", "Arista") -> "Networking Equipment"
            else -> "$vendor Device"
        }
    }

    enum class SecurityRisk { CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN }

    data class SecurityScore(
        val score: Int,
        val riskLevel: SecurityRisk,
        val vulnerabilities: List<String>,
        val recommendations: List<String>,
    )

    private val weakPatterns = listOf("password", "admin", "123", "qwerty", "abc", "default", "guest", "test", "demo", "login")
    private val defaultPatterns = listOf(
        "linksys", "netgear", "tp-link", "dlink", "asus", "belkin", "d-link",
        "airlink", "motorola", "default", "admin", "network", "setup", "wifi"
    )
    private val guestPatterns = listOf("guest", "public", "open", "-guest", "_guest")

    private fun looksLikeWeakPassword(ssid: String) = weakPatterns.any { ssid.lowercase().contains(it) }
    private fun looksLikeDefaultNetwork(ssid: String) = defaultPatterns.any { ssid.lowercase().contains(it) }
    private fun looksLikeGuestNetwork(ssid: String) = guestPatterns.any { ssid.lowercase().contains(it) }

    private fun increaseRisk(risk: SecurityRisk): SecurityRisk = when (risk) {
        SecurityRisk.LOW -> SecurityRisk.MEDIUM
        SecurityRisk.MEDIUM -> SecurityRisk.HIGH
        SecurityRisk.HIGH, SecurityRisk.CRITICAL -> SecurityRisk.CRITICAL
        SecurityRisk.UNKNOWN -> SecurityRisk.HIGH
    }

    fun scoreSecurity(ssid: String, security: String?, channel: Int?): SecurityScore {
        val vulnerabilities = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        var score: Int
        var riskLevel: SecurityRisk
        val secUpper = security?.uppercase().orEmpty()

        when {
            security.isNullOrEmpty() || secUpper == "OPEN" -> {
                score = 0
                riskLevel = SecurityRisk.CRITICAL
                vulnerabilities += "No encryption (Open network)"
                vulnerabilities += "All traffic is transmitted in plaintext"
                vulnerabilities += "Anyone can connect and intercept data"
                recommendations += "Enable WPA3 or WPA2 encryption immediately"
            }
            secUpper.contains("WEP") -> {
                score = 10
                riskLevel = SecurityRisk.CRITICAL
                vulnerabilities += "WEP encryption (deprecated and broken)"
                vulnerabilities += "WEP can be cracked in minutes"
                vulnerabilities += "Severely weakened security posture"
                recommendations += "Replace WEP with WPA2 or WPA3 urgently"
            }
            secUpper.contains("WPA") -> {
                when {
                    secUpper.contains("WPA3") -> {
                        score = 90
                        riskLevel = SecurityRisk.LOW
                        recommendations += "Excellent security configuration"
                        if (channel == 14) {
                            score -= 5
                            recommendations += "Consider enabling 5GHz for better performance"
                        }
                    }
                    secUpper.contains("WPA2") -> {
                        score = 75
                        riskLevel = SecurityRisk.MEDIUM
                        recommendations += "WPA2 is secure with proper configuration"
                        if (secUpper.contains("PSK") || secUpper.contains("CCMP")) score += 10
                        if (looksLikeWeakPassword(ssid)) {
                            score -= 20
                            riskLevel = SecurityRisk.HIGH
                            vulnerabilities += "SSID pattern suggests weak password"
                            recommendations += "Use strong, random passwords (20+ chars)"
                        }
                    }
                    else -> {
                        score = 60
                        riskLevel = SecurityRisk.HIGH
                        vulnerabilities += "WPA (original) has known vulnerabilities"
                        recommendations += "Upgrade to WPA2 or WPA3"
                    }
                }
            }
            else -> {
                score = 50
                riskLevel = SecurityRisk.UNKNOWN
                recommendations += "Could not determine security level"
            }
        }

        if (looksLikeDefaultNetwork(ssid)) {
            score -= 10
            riskLevel = increaseRisk(riskLevel)
            vulnerabilities += "Default network name suggests default credentials"
            recommendations += "Change default SSID and password"
        }
        if (looksLikeGuestNetwork(ssid)) {
            score -= 5
            recommendations += "Guest networks typically have weaker security"
        }

        score = score.coerceIn(0, 100)
        return SecurityScore(score, riskLevel, vulnerabilities, recommendations)
    }
}
