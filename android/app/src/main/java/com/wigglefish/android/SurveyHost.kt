package com.wigglefish.android

/**
 * Activity-hosted actions that fragments request for USB / phone / export work.
 * Hardware ownership stays in [MainActivity]; observational state lives in [SurveySessionViewModel].
 */
interface SurveyHost {
    fun requestUsbConnection()
    fun togglePhoneCollection()
    fun toggleUsbCollection()
    fun shareSessionJson()
    fun shareSessionCsv()
    fun clearSession()
    fun navigateTo(destinationId: Int)
}
