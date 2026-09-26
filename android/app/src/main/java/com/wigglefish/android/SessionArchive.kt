package com.wigglefish.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Persists completed wardrive sessions as JSON packs under filesDir/sessions/.
 * Enables list / reopen summary / re-export / delete without relying on in-memory state alone.
 */
class SessionArchive(context: Context) {
    private val root = File(context.filesDir, "sessions").also { it.mkdirs() }

    data class SessionMeta(
        val id: String,
        val fileName: String,
        val savedAtMs: Long,
        val summary: SessionSummary,
        val label: String,
    )

    fun listSessions(): List<SessionMeta> {
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file -> readMeta(file) }
            ?.sortedByDescending { it.savedAtMs }
            ?: emptyList()
    }

    fun saveSession(
        records: List<ObservationRecord>,
        summary: SessionSummary,
        gpsFixes: List<GpsFix>,
        label: String? = null,
    ): SessionMeta? {
        if (records.isEmpty() && gpsFixes.isEmpty()) return null
        val id = UUID.randomUUID().toString().take(8)
        val stamp = localStamp(System.currentTimeMillis())
        val fileName = "session_${stamp}_$id.json"
        val display = label?.ifBlank { null } ?: "Session $stamp"
        val payload = JSONObject().apply {
            put("format", "wigglefish-wardrive-json")
            put("version", WardriveExporter.FORMAT_VERSION)
            put("passiveOnly", true)
            put("sessionId", id)
            put("savedAtMs", System.currentTimeMillis())
            put("label", display)
            put("summary", WardriveExporter.summaryToJson(summary))
            put("observations", JSONArray().apply { records.forEach { put(it.toJson()) } })
            put("gpsTimeline", JSONArray().apply { gpsFixes.forEach { put(it.toJson()) } })
        }
        File(root, fileName).writeText(payload.toString())
        return SessionMeta(id, fileName, System.currentTimeMillis(), summary, display)
    }

    fun loadSession(fileName: String): Triple<List<ObservationRecord>, SessionSummary, List<GpsFix>>? {
        val file = File(root, fileName)
        if (!file.exists()) return null
        return try {
            val rootObj = JSONObject(file.readText())
            val summary = WardriveExporter.summaryFromJson(rootObj.optJSONObject("summary"))
            val records = mutableListOf<ObservationRecord>()
            val arr = rootObj.optJSONArray("observations") ?: JSONArray()
            for (i in 0 until arr.length()) {
                records += ObservationRecord.fromJson(arr.getJSONObject(i))
            }
            val fixes = mutableListOf<GpsFix>()
            val gpsArr = rootObj.optJSONArray("gpsTimeline") ?: JSONArray()
            for (i in 0 until gpsArr.length()) {
                GpsFix.fromJson(gpsArr.getJSONObject(i))?.let { fixes += it }
            }
            Triple(records, summary, fixes)
        } catch (_: Exception) {
            null
        }
    }

    fun loadRawJson(fileName: String): String? {
        val file = File(root, fileName)
        return if (file.exists()) file.readText() else null
    }

    fun deleteSession(fileName: String): Boolean {
        val file = File(root, fileName)
        return file.exists() && file.delete()
    }

    private fun readMeta(file: File): SessionMeta? {
        return try {
            val obj = JSONObject(file.readText())
            val summary = WardriveExporter.summaryFromJson(obj.optJSONObject("summary"))
            SessionMeta(
                id = obj.optString("sessionId", file.nameWithoutExtension),
                fileName = file.name,
                savedAtMs = obj.optLong("savedAtMs", file.lastModified()),
                summary = summary,
                label = obj.optString("label", file.nameWithoutExtension),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun localStamp(ms: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        fmt.timeZone = TimeZone.getDefault()
        return fmt.format(Date(ms))
    }
}
