package com.wigglefish.android

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * OUI → vendor lookup backed by a compressed Wireshark `manuf` asset.
 * Graceful: unknown prefixes return empty string. Init once from Application/Activity.
 */
object OuiLookup {
    @Volatile
    private var ready = false
    private val lock = Any()

    /** 24-bit (6 hex) prefix → vendor */
    private val vendors24 = HashMap<String, String>(65536)

    /** Longer prefixes (28/36-bit etc.), keyed by full hex prefix (length > 6). */
    private val vendorsLong = HashMap<String, String>(4096)

    fun ensureLoaded(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            try {
                context.applicationContext.assets.open("oui_manuf.gz").use { raw ->
                    GZIPInputStream(raw).use { gz ->
                        BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).use { reader ->
                            var line: String?
                            while (true) {
                                line = reader.readLine() ?: break
                                if (line.isEmpty() || line.startsWith("#")) continue
                                val tab = line.indexOf('\t')
                                if (tab <= 0) continue
                                val key = line.substring(0, tab).trim().uppercase()
                                val vendor = line.substring(tab + 1).trim()
                                if (key.length < 6 || vendor.isEmpty()) continue
                                if (key.length == 6) {
                                    vendors24[key] = vendor
                                } else {
                                    vendorsLong[key] = vendor
                                }
                            }
                        }
                    }
                }
                ready = true
            } catch (_: Exception) {
                // Keep empty tables; vendorFor still returns "".
                ready = true
            }
        }
    }

    fun isReady(): Boolean = ready

    fun entryCount(): Int = vendors24.size + vendorsLong.size

    fun vendorFor(mac: String): String {
        val cleaned = mac.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (cleaned.length < 6) return ""
        // Longest-prefix match among known longer OUIs first (e.g. 9 hex = 36-bit).
        if (vendorsLong.isNotEmpty()) {
            var len = cleaned.length.coerceAtMost(12)
            while (len > 6) {
                val hit = vendorsLong[cleaned.substring(0, len)]
                if (hit != null) return hit
                len--
            }
        }
        return vendors24[cleaned.substring(0, 6)] ?: ""
    }
}
