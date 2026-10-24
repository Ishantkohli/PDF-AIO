package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Core engine responsible for:
 * 1. Scanning GitHub Releases for new updates
 * 2. Downloading update APK with real-time percentage, byte metrics, and speed
 * 3. Invoking the Android Package Installer via FileProvider
 * 4. Permanently deleting cached APK files once updated
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "pdf_aio_updater_prefs"
    private const val KEY_OWNER = "github_owner"
    private const val KEY_REPO = "github_repo"
    private const val KEY_LAST_CHECK = "last_check_timestamp"

    const val DEFAULT_GITHUB_OWNER = "ishantkohlikohli"
    const val DEFAULT_GITHUB_REPO = "PDF-AIO"

    /**
     * Get configured GitHub repository owner.
     */
    fun getGithubOwner(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OWNER, DEFAULT_GITHUB_OWNER) ?: DEFAULT_GITHUB_OWNER
    }

    /**
     * Get configured GitHub repository name.
     */
    fun getGithubRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REPO, DEFAULT_GITHUB_REPO) ?: DEFAULT_GITHUB_REPO
    }

    /**
     * Save updated repository coordinates.
     */
    fun setGithubRepo(context: Context, owner: String, repo: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_OWNER, owner.trim())
            .putString(KEY_REPO, repo.trim())
            .apply()
    }

    /**
     * Query GitHub Releases REST API for the latest published release.
     */
    suspend fun checkForUpdates(
        context: Context,
        customOwner: String? = null,
        customRepo: String? = null
    ): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val owner = customOwner ?: getGithubOwner(context)
            val repo = customRepo ?: getGithubRepo(context)
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

            Log.d(TAG, "Checking update at: $apiUrl")

            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "PDF-AIO-Updater/1.0 (Android)")
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                Log.w(TAG, "No releases found on repository $owner/$repo (404)")
                return@withContext Result.failure(Exception("No published releases found on GitHub ($owner/$repo)"))
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("GitHub API returned error code $responseCode: ${connection.responseMessage}"))
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", "Version $tagName").ifBlank { "Version $tagName" }
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.").ifBlank { "Bug fixes and performance improvements." }
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "")

            // Locate APK asset in release assets
            val assetsArray = json.optJSONArray("assets") ?: JSONArray()
            var apkDownloadUrl: String? = null
            var apkFileName = "pdf_aio_update.apk"
            var apkSize = 0L

            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url")
                    apkFileName = assetName
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            // Record check timestamp
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            if (apkDownloadUrl.isNullOrEmpty()) {
                Log.i(TAG, "Latest release tag $tagName found, but no attached .apk asset was found.")
                return@withContext Result.failure(Exception("Release $tagName exists on GitHub, but no .apk asset is attached yet."))
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val currentCode = BuildConfig.VERSION_CODE
            val isNew = VersionComparator.isNewer(
                remoteVersion = tagName,
                currentVersion = currentVersion,
                remoteCode = 0,
                currentCode = currentCode
            )

            Log.i(TAG, "Current: $currentVersion ($currentCode) | Remote: $tagName | isNewer: $isNew")

            if (isNew) {
                val releaseInfo = AppReleaseInfo(
                    versionName = tagName.removePrefix("v").removePrefix("V"),
                    versionCode = 0,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    apkFileName = apkFileName,
                    apkSize = apkSize,
                    publishedAt = publishedAt,
                    htmlUrl = htmlUrl
                )

                // Post system shade notification & schedule periodic background alarm
                UpdateNotificationHelper.showUpdateNotification(context, releaseInfo)
                UpdateCheckAlarmReceiver.scheduleNextAlarm(context)

                Result.success(releaseInfo)
            } else {
                Result.success(null) // Already up-to-date
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download the release APK file with live progress reporting.
     * Supports cancellation: if the calling coroutine is cancelled, deletes partial download.
     */
    suspend fun downloadApk(
        context: Context,
        releaseInfo: AppReleaseInfo,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long, speedKbps: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        var connection: HttpURLConnection? = null

        val updatesDir = File(context.cacheDir, "app_updates").apply { mkdirs() }
        val tempFile = File(updatesDir, "download_temp.apk")
        val finalFile = File(updatesDir, "pdf_aio_${releaseInfo.versionName}.apk")

        try {
            if (tempFile.exists()) tempFile.delete()
            if (finalFile.exists()) finalFile.delete()

            var currentUrl = releaseInfo.downloadUrl
            var redirectCount = 0

            // Follow HTTP redirects (GitHub releases assets redirect to S3 storage)
            while (redirectCount < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "PDF-AIO-Updater/1.0 (Android)")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null) {
                        currentUrl = newUrl
                        redirectCount++
                        connection.disconnect()
                        continue
                    }
                }
                break
            }

            val totalBytes = connection?.contentLengthLong ?: releaseInfo.apkSize
            input = connection?.inputStream ?: throw Exception("Failed to open connection")
            output = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesReadTotal = 0L
            var lastUpdateMs = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L
            var lastSpeedKbps = 0L

            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                if (!coroutineContext.isActive) {
                    // Coroutine was cancelled by user
                    tempFile.delete()
                    return@withContext Result.failure(Exception("Download cancelled by user"))
                }

                output.write(buffer, 0, read)
                bytesReadTotal += read
                bytesSinceLastUpdate += read

                val now = System.currentTimeMillis()
                val delta = now - lastUpdateMs
                if (delta >= 250) {
                    val speed = (bytesSinceLastUpdate * 1000) / (delta * 1024)
                    lastSpeedKbps = speed
                    val percent = if (totalBytes > 0) ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

                    onProgress(percent, bytesReadTotal, totalBytes, lastSpeedKbps)

                    lastUpdateMs = now
                    bytesSinceLastUpdate = 0L
                }
            }

            output.flush()

            // Rename temp to target final APK
            if (tempFile.renameTo(finalFile)) {
                onProgress(100, bytesReadTotal, totalBytes, lastSpeedKbps)
                Log.d(TAG, "Download finished! Final APK: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
                Result.success(finalFile)
            } else {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                onProgress(100, bytesReadTotal, totalBytes, lastSpeedKbps)
                Result.success(finalFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        } finally {
            try { output?.close() } catch (_: Exception) {}
            try { input?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Check installation permissions and invoke the Android package installer.
     * Returns true if installer intent was launched, false if permission requested from user.
     */
    fun launchInstaller(context: Context, apkFile: File): Boolean {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Log.e(TAG, "APK file does not exist or is empty: ${apkFile.absolutePath}")
                return false
            }

            // On Android 8.0+ (Oreo), verify unknown sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.i(TAG, "Requesting ACTION_MANAGE_UNKNOWN_APP_SOURCES permission...")
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return false
                }
            }

            // Launch package installer intent with FileProvider content URI
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Log.d(TAG, "System package installer intent dispatched successfully for ${apkFile.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            return false
        }
    }

    /**
     * Permanently delete all cached APK update files from the app cache.
     * Called automatically when MY_PACKAGE_REPLACED is triggered or during app launch.
     */
    fun cleanCachedApks(context: Context) {
        try {
            val updatesDir = File(context.cacheDir, "app_updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                val files = updatesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    val deleted = file.delete()
                    Log.d(TAG, "Cleaned update file ${file.name}: $deleted")
                }
                updatesDir.delete()
            }

            // Clean any leftover APKs in root cache dir as well
            val cacheFiles = context.cacheDir.listFiles { _, name -> name.endsWith(".apk", ignoreCase = true) } ?: emptyArray()
            for (f in cacheFiles) {
                f.delete()
            }
            Log.d(TAG, "All update APK caches have been cleaned successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cached APKs: ${e.message}", e)
        }
    }
}
