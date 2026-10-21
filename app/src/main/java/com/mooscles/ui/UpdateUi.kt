package com.mooscles.ui

enum class UpdatePhase {
    IDLE, CHECKING, AVAILABLE, WAITING_FOR_WIFI, DOWNLOADING, VERIFYING,
    READY, DEFERRED, PERMISSION_REQUIRED, APPROVAL_REQUIRED, INSTALLING,
    SUCCEEDED, FAILED, CANCELLED,
}

/** Update presentation contains release information only, never workout or signing data. */
data class UpdateUi(
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0,
    val lastCheckedAt: Long? = null,
    val automaticUpdates: Boolean = true,
    val wifiOnly: Boolean = true,
    val includePrereleases: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val availableVersionName: String? = null,
    val availableVersionCode: Long? = null,
    val releaseNotes: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
    val verifiedApkReady: Boolean = false,
)

interface UpdateActions {
    fun checkForUpdates()
    fun setAutomaticUpdates(enabled: Boolean)
    fun setUpdateWifiOnly(enabled: Boolean)
    fun setIncludePrereleases(enabled: Boolean)
    fun downloadUpdate()
    fun cancelUpdate()
    fun installUpdate()
}
