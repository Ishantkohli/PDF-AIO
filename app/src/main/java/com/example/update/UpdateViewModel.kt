package com.example.update

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _showProgressCard = MutableStateFlow(false)
    val showProgressCard: StateFlow<Boolean> = _showProgressCard.asStateFlow()

    private val _showReadyDialog = MutableStateFlow(false)
    val showReadyDialog: StateFlow<Boolean> = _showReadyDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    init {
        // Startup cleanup: delete any leftover update APKs from previous updates
        UpdateManager.cleanCachedApks(application)
    }

    /**
     * Scan GitHub Releases in the background.
     * If [manual] is true, shows immediate UI feedback even if already up to date.
     */
    fun checkForUpdates(context: Context, manual: Boolean = false) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            if (manual) {
                _userMessage.value = "Checking GitHub for PDF AIO updates..."
            }

            val result = UpdateManager.checkForUpdates(context)
            result.onSuccess { releaseInfo ->
                if (releaseInfo != null) {
                    _updateState.value = UpdateState.UpdateAvailable(releaseInfo)
                    _showUpdateDialog.value = true
                } else {
                    val current = BuildConfig.VERSION_NAME
                    _updateState.value = UpdateState.UpToDate(current)
                    if (manual) {
                        _userMessage.value = "PDF AIO is up to date (v$current)!"
                    }
                }
            }.onFailure { err ->
                _updateState.value = UpdateState.Error(err.message ?: "Failed to check for updates")
                if (manual) {
                    _userMessage.value = "Update check: ${err.message ?: "Could not connect to GitHub"}"
                }
            }
        }
    }

    /**
     * Start downloading the APK file for the current release.
     */
    fun startDownload(context: Context) {
        val current = _updateState.value
        val releaseInfo = when (current) {
            is UpdateState.UpdateAvailable -> current.releaseInfo
            is UpdateState.Downloading -> current.releaseInfo
            is UpdateState.ReadyToInstall -> current.releaseInfo
            else -> return
        }

        _showUpdateDialog.value = false
        _showProgressCard.value = true

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(
                releaseInfo = releaseInfo,
                progressPercent = 0,
                bytesDownloaded = 0,
                totalBytes = releaseInfo.apkSize,
                speedKbps = 0
            )

            val downloadResult = UpdateManager.downloadApk(context, releaseInfo) { percent, bytes, total, speed ->
                _updateState.value = UpdateState.Downloading(
                    releaseInfo = releaseInfo,
                    progressPercent = percent,
                    bytesDownloaded = bytes,
                    totalBytes = total,
                    speedKbps = speed
                )
            }

            downloadResult.onSuccess { file ->
                downloadedApkFile = file
                _showProgressCard.value = false
                _showReadyDialog.value = true
                _updateState.value = UpdateState.ReadyToInstall(releaseInfo, file.absolutePath)

                // Launch installer automatically once downloaded!
                val launched = UpdateManager.launchInstaller(context, file)
                if (!launched) {
                    _userMessage.value = "Please allow install permission to update PDF AIO"
                }
            }.onFailure { err ->
                _showProgressCard.value = false
                _updateState.value = UpdateState.Error(err.message ?: "Download failed")
                _userMessage.value = "Download failed: ${err.message}"
            }
        }
    }

    /**
     * Cancel ongoing download and delete temp file.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _showProgressCard.value = false
        _updateState.value = UpdateState.Idle
        _userMessage.value = "Download cancelled"
    }

    /**
     * Manually trigger the installer again if user dismissed it.
     */
    fun installDownloadedApk(context: Context) {
        val file = downloadedApkFile
        if (file != null && file.exists()) {
            val launched = UpdateManager.launchInstaller(context, file)
            if (!launched) {
                _userMessage.value = "Please enable unknown sources permission to install"
            }
        } else {
            _userMessage.value = "Update file not found. Please download again."
            _showReadyDialog.value = false
            _updateState.value = UpdateState.Idle
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun dismissReadyDialog() {
        _showReadyDialog.value = false
    }

    fun dismissProgressCard() {
        cancelDownload()
    }

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun dismissSettingsDialog() {
        _showSettingsDialog.value = false
    }

    fun saveRepoSettings(context: Context, owner: String, repo: String) {
        UpdateManager.setGithubRepo(context, owner, repo)
        _showSettingsDialog.value = false
        _userMessage.value = "Repository updated to $owner/$repo"
        checkForUpdates(context, manual = true)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
