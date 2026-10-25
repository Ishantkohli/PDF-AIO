package com.example.update

/**
 * Metadata for a released version of PDF AIO fetched from GitHub Releases or custom update endpoint.
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val publishedAt: String,
    val htmlUrl: String = ""
)

/**
 * Reactive states for the update lifecycle in PDF AIO.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(val releaseInfo: AppReleaseInfo) : UpdateState
    data class Downloading(
        val releaseInfo: AppReleaseInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedKbps: Long
    ) : UpdateState
    data class ReadyToInstall(
        val releaseInfo: AppReleaseInfo,
        val apkFilePath: String
    ) : UpdateState
    data class UpToDate(val checkedVersion: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Robust semantic version parser and comparator.
 * Handles forms like "v1.2.0", "1.2.3-beta", "2.0", etc.
 */
object VersionComparator {
    fun isNewer(remoteVersion: String, currentVersion: String, remoteCode: Int = 0, currentCode: Int = 0): Boolean {
        if (remoteCode > 0 && currentCode > 0 && remoteCode != currentCode) {
            return remoteCode > currentCode
        }

        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
