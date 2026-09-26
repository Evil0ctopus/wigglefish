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
    fun togglePhoneCollection()
    fun toggleUsbCollection()
    fun shareSessionJson()
    fun shareSessionCsv()
    fun clearSession()
    fun navigateTo(destinationId: Int)
}
