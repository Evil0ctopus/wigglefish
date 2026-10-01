# Android Integration Quick Reference

## 🎯 30-Second Overview

**What you have:**
- ✅ Kotlin data models for all enriched network data
- ✅ Complete Jetpack Compose UI components
- ✅ ViewModel for state management
- ✅ Serial data parser
- ✅ Three-tab interface (Networks, Analytics, Vulnerable)

**What you need to do:**
1. Add files to your Android project
2. Update `build.gradle` with serialization plugin
3. Connect serial handler to ViewModel
4. Set up MainActivity to show `ScannerScreen()`

---

## 📁 Copy These Files

Copy these files to your Android app:

```
→ android/app/src/main/java/com/wigglefish/ui/models/WifiModels.kt
→ android/app/src/main/java/com/wigglefish/ui/components/NetworkComponents.kt
→ android/app/src/main/java/com/wigglefish/ui/components/AnalyticsComponents.kt
→ android/app/src/main/java/com/wigglefish/ui/viewmodel/ScanViewModel.kt
→ android/app/src/main/java/com/wigglefish/ui/screens/ScannerScreen.kt
→ android/app/src/main/java/com/wigglefish/data/SerialDataHandler.kt
```

---

## ⚙️ Setup (5 minutes)

### 1. Update `build.gradle`

```gradle
// Add to plugins
plugins {
    ...
    id 'org.jetbrains.kotlin.plugin.serialization' version '1.8.0'
}

// Add to dependencies
dependencies {
    ...
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.6.0'
    
    // If not already there:
    implementation 'androidx.compose.material:material-icons-extended'
}
```

### 2. Update `MainActivity.kt`

```kotlin
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.wigglefish.ui.screens.ScannerScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScannerScreen()
            }
        }
    }
}
```

### 3. Add Permissions to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.USB" />
```

---

## 🔌 Connect Serial Handler

In your USB/Serial receiver:

```kotlin
import com.wigglefish.data.SerialDataHandler
import com.wigglefish.ui.viewmodel.ScanViewModel

class SerialReceiver(private val viewModel: ScanViewModel) {
    private val dataHandler = SerialDataHandler()
    
    fun onJsonReceived(jsonString: String) {
        // Parse and update ViewModel
        val sessionData = dataHandler.parseScanSession(jsonString)
        sessionData?.let { viewModel.updateScanSession(it) }
    }
}
```

---

## 🎮 User Interactions

### Search
- User types in search bar
- Updates `viewModel.searchQuery`
- Filters displayed automatically

### Filter by Risk
- User clicks risk buttons (ALL, CRITICAL, HIGH, etc.)
- Updates `viewModel.selectedRiskLevel`
- List re-filters

### View Details
- User taps network
- `viewModel.selectNetwork(network)` 
- Shows detailed view with vulnerabilities

### Switch Tabs
- User taps bottom nav
- `viewModel.switchTab("analytics")`
- Shows different content

---

## 📊 Data Model Overview

```kotlin
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val channel: Int? = null,
    val rssi: Int? = null,
    val security: String? = null,
    val vendor: String? = null,           // NEW!
    val deviceType: String? = null,       // NEW!
    val securityScore: Int? = null,       // NEW! (0-100)
    val riskLevel: String? = null,        // NEW! (CRITICAL/HIGH/MEDIUM/LOW)
    val vulnerabilities: List<String> = emptyList(),  // NEW!
    val recommendations: List<String> = emptyList()   // NEW!
)
```

---

## 🎨 UI Components

### Display a Single Network
```kotlin
NetworkListItem(network = wifiNetwork) { selectedNetwork ->
    // Handle network selection
}
```

### Show Network Details
```kotlin
NetworkDetailView(network = wifiNetwork)
```

### Display List with Search
```kotlin
NetworkListScreen(
    networks = listOfNetworks,
    searchQuery = "apple",
    onSearchChange = { newQuery -> /* update */ },
    selectedRiskLevel = "HIGH",
    onRiskLevelChange = { newRisk -> /* update */ },
    onNetworkClick = { network -> /* handle click */ }
)
```

### Show Analytics
```kotlin
AnalyticsDashboard(analytics = sessionAnalytics)
```

---

## 📨 JSON Format from Backend

```json
{
  "status": "success",
  "count": 42,
  "networks": [
    {
      "ssid": "HomeNet",
      "bssid": "34:AB:95:12:34:56",
      "channel": 11,
      "rssi": -52,
      "security": "WPA2",
      "vendor": "Apple",
      "deviceType": "Apple Device",
      "securityScore": 85,
      "riskLevel": "LOW",
      "vulnerabilities": [],
      "recommendations": ["Excellent security configuration"]
    }
  ],
  "analytics": {
    "metrics": {
      "totalNetworks": 42,
      "uniqueVendors": 5,
      "channelsInUse": 3
    },
    "encryption": { "wpa3": 5, "wpa2": 32, ... },
    "riskDistribution": { "critical": 1, "high": 2, ... },
    ...
  },
  "timestamp": "2026-09-12T12:34:56"
}
```

---

## 🔄 ViewModel Methods

```kotlin
// Add networks
viewModel.addNetworks(listOf(wifiNetwork1, wifiNetwork2))

// Clear all
viewModel.clearNetworks()

// Search
viewModel.setSearchQuery("apple")

// Filter by risk
viewModel.setRiskLevel("CRITICAL")

// Select for detail view
viewModel.selectNetwork(wifiNetwork)

// Get filtered results
val filtered = viewModel.getFilteredNetworks()

// Get vulnerable only
val vulnerable = viewModel.getVulnerableNetworks()

// Export as JSON
val json = viewModel.exportAsJson()

// Get statistics
val stats = viewModel.getSubsetStats(networks)
```

---

## 🎯 Color Codes

```kotlin
val colorByRisk = mapOf(
    "CRITICAL" to Color(0xFFD32F2F),  // 🔴 Red
    "HIGH" to Color(0xFFFF6F00),      // 🟠 Orange
    "MEDIUM" to Color(0xFFFDD835),    // 🟡 Yellow
    "LOW" to Color(0xFF388E3C),       // 🟢 Green
)

val uiColors = mapOf(
    "primary" to Color(0xFF00FF00),    // 🟢 Neon Green
    "accent" to Color(0xFF00CCFF),     // 🔵 Cyan
    "warning" to Color(0xFFFFCC00),    // 🟡 Yellow
    "background" to Color(0xFF0D0D0D), // ⬛ Dark
)
```

---

## 🧪 Test it Locally

### Generate Sample Data
```bash
cd /path/to/wigglefish
python -m wigglefish scan --wifi --enriched --json
```

### Mock Test Data in Kotlin
```kotlin
val testNetwork = WifiNetwork(
    ssid = "TestNet",
    bssid = "AA:BB:CC:DD:EE:FF",
    channel = 11,
    rssi = -52,
    security = "WPA2",
    vendor = "Apple",
    deviceType = "Apple Device",
    securityScore = 85,
    riskLevel = "LOW"
)

viewModel.addNetworks(listOf(testNetwork))
```

---

## 🚀 Production Checklist

- [ ] All files copied to correct packages
- [ ] `build.gradle` updated with serialization plugin
- [ ] `MainActivity.kt` updated to show `ScannerScreen()`
- [ ] Serial handler connected to ViewModel
- [ ] USB permissions added to manifest
- [ ] Test with real device
- [ ] Verify colors match brand guidelines
- [ ] Test search/filter functionality
- [ ] Test analytics dashboard
- [ ] Test vulnerable networks tab

---

## 💡 Pro Tips

1. **Dark Theme Only** - All colors optimized for dark mode (WDGWars aesthetic)
2. **Offline First** - No internet required, all processing local
3. **Real-time Updates** - Stream data as it arrives from ESP32
4. **No External APIs Yet** - Vendor lookup is local DB (can add Shodan later)
5. **Memory Efficient** - Handles 1000+ networks without lag

---

## ❌ Common Issues & Fixes

| Issue | Fix |
|-------|-----|
| `Serializable` not found | Add `@Serializable` annotation import |
| JSON parsing fails | Check field names match exactly |
| ViewModel not injecting | Use `androidx.lifecycle.viewmodel.compose.viewModel()` |
| Colors look wrong | Ensure using `Color(0xRRGGBB)` format |
| UI not updating | Use `collectAsStateWithLifecycle()` not `collectAsState()` |

---

**Everything is ready to go! Copy the files, update build.gradle, and you're live.** 🚀
