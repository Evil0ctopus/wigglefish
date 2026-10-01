# Android Integration Quick Start

## Overview

The Python backend now provides enriched network data with vendor information, device type classification, and security scoring. This guide shows how to integrate these features into the Android app.

---

## 📝 Data Format

### Network Object (JSON)
```json
{
  "ssid": "HomeNetwork",
  "bssid": "34:AB:95:12:34:56",
  "channel": 11,
  "rssi": -52,
  "security": "WPA2",
  "vendor": "Apple",
  "device_type": "Apple Device",
  "security_score": 85,
  "risk_level": "LOW",
  "vulnerabilities": [],
  "recommendations": ["Excellent security configuration"]
}
```

### Analytics Object (Dashboard Stats)
```json
{
  "metrics": {
    "total_networks": 12,
    "unique_vendors": 5,
    "channels_in_use": 3
  },
  "encryption": {
    "open": 1,
    "wep": 0,
    "wpa": 2,
    "wpa2": 8,
    "wpa3": 1
  },
  "risk_distribution": {
    "critical": 1,
    "high": 2,
    "medium": 4,
    "low": 5
  },
  "signal_distribution": {
    "avg_rssi": -62.3,
    "signal_distribution": {
      "strong": 5,
      "medium": 5,
      "weak": 2
    }
  },
  "top_vendors": [
    ["Apple", 4],
    ["Samsung", 3]
  ]
}
```

---

## 🔧 Implementation Steps

### Step 1: Update Data Models (Kotlin)

Add these fields to your `WifiNetwork` data class:

```kotlin
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val channel: Int?,
    val rssi: Int?,
    val security: String?,
    
    // NEW FIELDS
    val vendor: String? = null,
    val deviceType: String? = null,
    val securityScore: Int? = null,        // 0-100
    val riskLevel: String? = null,         // CRITICAL, HIGH, MEDIUM, LOW
    val vulnerabilities: List<String> = emptyList(),
    val recommendations: List<String> = emptyList()
)
```

### Step 2: Update Parsing

Update your serial/USB handler to parse the new fields:

```kotlin
// Example using kotlinx.serialization or Gson
val network = json.decodeFromString<WifiNetwork>(jsonString)
```

### Step 3: Display Vendor Badge

Add vendor indicator to network list:

```kotlin
@Composable
fun NetworkListItem(network: WifiNetwork) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(network.ssid, style = MaterialTheme.typography.headlineSmall)
            
            // Show vendor if available
            if (network.vendor != null && network.vendor != "Unknown") {
                Text(
                    network.vendor,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
        
        // Security score badge
        SecurityBadge(network.securityScore)
    }
}

@Composable
fun SecurityBadge(score: Int?) {
    if (score == null) return
    
    val (color, text) = when {
        score < 25 -> Color.Red to "🔴"
        score < 50 -> Color.hsl(30f, 1f, 0.5f) to "🟠"
        score < 75 -> Color.Yellow to "🟡"
        else -> Color.Green to "🟢"
    }
    
    Text(text, color = color, fontSize = 20.sp)
}
```

### Step 4: Show Security Details

When user taps a network, show vulnerabilities and recommendations:

```kotlin
@Composable
fun NetworkDetailScreen(network: WifiNetwork) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Header
        Text("${network.ssid}", style = MaterialTheme.typography.headlineMedium)
        Text("Risk: ${network.riskLevel}", color = riskColor(network.riskLevel))
        Text("Score: ${network.securityScore}/100")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Vulnerabilities
        if (network.vulnerabilities.isNotEmpty()) {
            Text("Vulnerabilities:", style = MaterialTheme.typography.titleMedium)
            network.vulnerabilities.forEach { vuln ->
                Text("⚠️ $vuln", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        // Recommendations
        if (network.recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recommendations:", style = MaterialTheme.typography.titleMedium)
            network.recommendations.forEach { rec ->
                Text("✓ $rec", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun riskColor(riskLevel: String?): Color = when (riskLevel?.uppercase()) {
    "CRITICAL" -> Color.Red
    "HIGH" -> Color.hsl(30f, 1f, 0.5f)
    "MEDIUM" -> Color.Yellow
    "LOW" -> Color.Green
    else -> Color.Gray
}
```

### Step 5: Add Search/Filter

```kotlin
// Filter UI
var searchQuery by remember { mutableStateOf("") }
var filterRisk by remember { mutableStateOf("ALL") }

val filteredNetworks = networks.filter { net ->
    (searchQuery.isEmpty() || 
     net.ssid.contains(searchQuery, ignoreCase = true) ||
     net.vendor?.contains(searchQuery, ignoreCase = true) == true ||
     net.bssid.contains(searchQuery)) &&
    (filterRisk == "ALL" || net.riskLevel == filterRisk)
}

// Filter buttons
Row {
    listOf("ALL", "CRITICAL", "HIGH", "MEDIUM", "LOW").forEach { risk ->
        Button(
            onClick = { filterRisk = risk },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (filterRisk == risk) Color.Blue else Color.Gray
            )
        ) {
            Text(risk)
        }
    }
}
```

### Step 6: Add Analytics Dashboard

```kotlin
@Composable
fun AnalyticsDashboard(analytics: Analytics) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Metrics
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                title = "Total Networks",
                value = analytics.metrics.totalNetworks.toString()
            )
            MetricCard(
                title = "Vendors",
                value = analytics.metrics.uniqueVendors.toString()
            )
            MetricCard(
                title = "Channels",
                value = analytics.metrics.channelsInUse.toString()
            )
        }
        
        // Encryption Pie Chart
        PieChart(
            data = analytics.encryption,
            title = "Encryption Types"
        )
        
        // Risk Distribution
        BarChart(
            data = analytics.riskDistribution,
            title = "Security Risk"
        )
        
        // Signal Distribution
        if (analytics.signalDistribution != null) {
            BarChart(
                data = analytics.signalDistribution.signalDistribution,
                title = "Signal Strength"
            )
        }
        
        // Top Vendors
        Text("Top Vendors", style = MaterialTheme.typography.titleMedium)
        analytics.topVendors.forEach { (vendor, count) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(vendor, modifier = Modifier.weight(1f))
                Text(count.toString())
            }
        }
    }
}
```

---

## 🔌 Python Backend Commands

Get data from backend using these commands:

```bash
# Get enriched network list
wigglefish scan --wifi --enriched --json

# Get analytics
wigglefish scan --wifi --analytics --json

# Get only vulnerable networks
wigglefish scan --wifi --security-only --json
```

Or use the Python API directly:

```python
from wigglefish.api import WigglefishAPI

api = WigglefishAPI()
api.add_observations(observations)

# For Android app
android_data = api.export_for_android()
print(android_data)
```

---

## 🎨 Color Scheme

Use this scheme for risk levels (matches WDGWars aesthetic):

```kotlin
val riskColors = mapOf(
    "CRITICAL" to Color(0xFFD32F2F),  // Red
    "HIGH" to Color(0xFFFF6F00),      // Orange
    "MEDIUM" to Color(0xFFFDD835),    // Yellow
    "LOW" to Color(0xFF388E3C),       // Green
    "UNKNOWN" to Color(0xFF9E9E9E)    // Gray
)
```

---

## 📊 Priority Implementation Order

1. **Phase 1** ✅ (Immediate)
   - Update data models with new fields
   - Display vendor next to SSID
   - Show security score color badge

2. **Phase 2** (Quick)
   - Tap network to show vulnerabilities
   - Add search functionality
   - Add risk level filter buttons

3. **Phase 3** (Medium)
   - Analytics dashboard with charts
   - Encryption breakdown pie chart
   - Signal distribution histogram

4. **Phase 4** (Advanced)
   - Hotspot map visualization
   - Session comparison
   - Export/sharing features

---

## 🧪 Testing

Test data is available via CLI:

```bash
# Test enriched data
python -m wigglefish scan --wifi --enriched

# Example output:
# Wi‑Fi: HomeNet
#   BSSID: 34:AB:95:12:34:56 | Vendor: Apple
#   Channel: 11 | RSSI: -52 dBm
#   Security: WPA2 | Score: 85/100
#   Device Type: Apple Device
#   Risk Level: LOW
```

---

## 📚 Resources

- **IMPLEMENTATION_GUIDE.md** - Detailed technical documentation
- **FEATURES_SUMMARY.md** - Overview of all capabilities
- **test_features.py** - Python test showing all features in action
- **api.py** - Full API source code with docstrings

---

## ❓ Questions?

Refer to the module docstrings in:
- `src/wigglefish/api.py` - API methods
- `src/wigglefish/mac_vendors.py` - Vendor lookups
- `src/wigglefish/security_scoring.py` - Security evaluation
- `src/wigglefish/analytics.py` - Statistics

**All modules have comprehensive docstrings explaining usage.**
