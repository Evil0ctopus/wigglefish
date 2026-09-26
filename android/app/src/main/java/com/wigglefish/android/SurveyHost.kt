package com.wigglefish.android

/**
 * Activity-hosted actions that fragments request for USB / phone / export / flash work.
 * Hardware ownership stays in [MainActivity]; observational state lives in [SurveySessionViewModel].
 */
interface SurveyHost {
    fun requestUsbConnection()
    /** Open / reclaim USB for Flash page even when survey collection is toggled off. */
    fun requestUsbConnectionForFlash()
    /** ROM bootloader identify (chip family / MAC / USB descriptors). */
    fun requestEspIdentify()
    /** Flash a catalog image id over USB OTG (Kotlin ROM protocol). */
    fun requestEspFlash(imageId: String)
    fun togglePhoneCollection()
    fun toggleUsbCollection()
    /** Stop phone + USB collection in one tap (safe utility). */
    fun stopAllCollection()
    fun shareSessionJson()
    fun shareSessionCsv()
    fun shareWardriveGoCsv()
    fun shareWigleCsv()
    fun shareGeoJson()
    fun clearSession()
    /** Persist current in-memory session to session archive. */
    fun saveCurrentSession()
    /** Refresh archived session list UI (Exports). */
    fun listArchivedSessions(): List<SessionArchive.SessionMeta>
    fun loadArchivedSession(fileName: String)
    fun deleteArchivedSession(fileName: String)
    fun exportArchivedSession(fileName: String, format: String)
    fun navigateTo(destinationId: Int)
}
