package com.wigglefish.android.esp

import android.content.Context
import org.json.JSONObject

data class FirmwarePartSpec(
    val asset: String,
    val offset: Int,
    val label: String,
)

data class FirmwareImage(
    val id: String,
    val label: String,
    val chipFamilies: List<String>,
    val eligibleProfiles: List<String>,
    val available: Boolean,
    val flashSizeBytes: Int,
    val releaseTag: String?,
    val notes: String,
    val parts: List<FirmwarePartSpec>,
) {
    fun matchesChip(chipFamily: String?): Boolean {
        if (chipFamily.isNullOrBlank()) return false
        return chipFamilies.any { it.equals(chipFamily.trim(), ignoreCase = true) }
    }

    fun matchesProfile(profileId: String?): Boolean {
        if (profileId.isNullOrBlank()) return eligibleProfiles.isEmpty()
        return eligibleProfiles.any { it.equals(profileId, ignoreCase = true) }
    }
}

object FirmwareCatalog {
    fun load(context: Context): List<FirmwareImage> {
        val json = context.assets.open("firmware_catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val arr = root.getJSONArray("images")
        val out = mutableListOf<FirmwareImage>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val families = o.getJSONArray("chipFamilies").let { a ->
                List(a.length()) { a.getString(it) }
            }
            val profiles = if (o.has("eligibleProfiles")) {
                o.getJSONArray("eligibleProfiles").let { a -> List(a.length()) { a.getString(it) } }
            } else emptyList()
            val parts = if (o.has("parts")) {
                o.getJSONArray("parts").let { a ->
                    List(a.length()) { idx ->
                        val p = a.getJSONObject(idx)
                        FirmwarePartSpec(
                            asset = p.getString("asset"),
                            offset = p.getInt("offset"),
                            label = p.optString("label", "part$idx"),
                        )
                    }
                }
            } else emptyList()
            out += FirmwareImage(
                id = o.getString("id"),
                label = o.getString("label"),
                chipFamilies = families,
                eligibleProfiles = profiles,
                available = o.optBoolean("available", parts.isNotEmpty()),
                flashSizeBytes = o.optInt("flashSizeBytes", 4 * 1024 * 1024),
                releaseTag = if (o.has("releaseTag") && !o.isNull("releaseTag")) o.getString("releaseTag") else null,
                notes = o.optString("notes", ""),
                parts = parts,
            )
        }
        return out
    }

    fun compatible(images: List<FirmwareImage>, chipFamily: String?, profileId: String?): List<FirmwareImage> {
        val byChip = images.filter { it.matchesChip(chipFamily) }
        if (byChip.isEmpty()) return emptyList()
        val byProfile = byChip.filter { it.matchesProfile(profileId) }
        return if (byProfile.isNotEmpty()) byProfile else byChip
    }

    fun loadParts(context: Context, image: FirmwareImage): List<FlashPart> {
        if (!image.available || image.parts.isEmpty()) {
            throw IllegalStateException("Image ${image.id} is not available for flash")
        }
        return image.parts.map { spec ->
            val bytes = context.assets.open(spec.asset).use { it.readBytes() }
            FlashPart(label = spec.label, offset = spec.offset, data = bytes)
        }
    }
}
