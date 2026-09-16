package com.telestudy.tv.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.telestudy.tv.BuildConfig
import com.telestudy.tv.core.update.model.AppUpdateInfo
import com.telestudy.tv.core.update.model.UpdateState
import com.telestudy.tv.core.update.model.VerificationResult
import com.telestudy.tv.core.update.service.ApkDownloader
import com.telestudy.tv.core.update.service.ApkVerifier
import com.telestudy.tv.core.update.service.GitHubReleaseClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File

/**
 * Central coordinator for the TeleStudy TV in-app auto-update system.
 * Manages GitHub release discovery, downloads, cryptographic verification,
 * and invocation of the Android TV package installer.
 */
class AppUpdateManager(
    private val context: Context,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val downloader: ApkDownloader = ApkDownloader(),
    private val verifier: ApkVerifier = ApkVerifier(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val currentVersionName: String = BuildConfig.VERSION_NAME
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var downloadJob: Job? = null
    private var lastAutoCheckTimestamp: Long = 0L

    companion object {
        private const val PREFS_NAME = "telestudy_update_prefs"
        private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
        private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lastAutoCheckTimestamp = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    }

    /**
     * Checks GitHub Releases for a newer version of TeleStudy TV.
     * @param isManual True if triggered by user action (bypasses 6-hour interval throttle).
     */
    fun checkForUpdates(isManual: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!isManual && (now - lastAutoCheckTimestamp) < AUTO_CHECK_INTERVAL_MS) {
            Timber.d("Skipping automatic update check (throttled). Last check: %d", lastAutoCheckTimestamp)
            return
        }

        // Avoid triggering another check if already in progress
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking

        scope.launch {
            try {
                val result = releaseClient.fetchLatestRelease()
                result.fold(
                    onSuccess = { info ->
                        recordCheckTimestamp()
                        if (info.isNewerThan(currentVersionCode, currentVersionName)) {
                            Timber.i("Update found: %s (v%d) > current v%d", info.latestVersionName, info.latestVersionCode, currentVersionCode)
                            _updateState.value = UpdateState.Available(info)
                        } else {
                            Timber.i("TeleStudy TV is up-to-date (v%d, %s)", currentVersionCode, currentVersionName)
                            _updateState.value = UpdateState.UpToDate(currentVersionCode, currentVersionName)
                        }
                    },
                    onFailure = { error ->
                        Timber.w(error, "Update check failed")
                        if (isManual) {
                            _updateState.value = UpdateState.Error(
                                message = "Unable to check for updates: ${error.localizedMessage ?: "Network error"}",
                                cause = error
                            )
                        } else {
                            _updateState.value = UpdateState.Idle
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error in update check")
                if (isManual) {
                    _updateState.value = UpdateState.Error(
                        message = "Update check failed: ${e.localizedMessage ?: "Unknown error"}",
                        cause = e
                    )
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * Starts downloading the update APK with real-time progress reporting.
     */
    fun startDownload(info: AppUpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.cacheDir, "updates")
        val destinationFile = File(downloadsDir, "TeleStudy-TV-${info.latestVersionName}.apk")

        _updateState.value = UpdateState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = info.apkSize,
            updateInfo = info
        )

        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                downloader.download(info.apkDownloadUrl, destinationFile)
                    .collect { progress ->
                        if (progress.isDone && progress.outputFile != null) {
                            // Download finished -> Proceed directly to verification
                            verifyDownloadedApk(progress.outputFile, info)
                        } else {
                            _updateState.value = UpdateState.Downloading(
                                progress = progress.progress,
                                downloadedBytes = progress.bytesDownloaded,
                                totalBytes = progress.totalBytes,
                                updateInfo = info
                            )
                        }
                    }
            } catch (e: CancellationException) {
                Timber.i("APK download cancelled by user")
                _updateState.value = UpdateState.Cancelled
            } catch (e: Throwable) {
                Timber.e(e, "APK download error")
                _updateState.value = UpdateState.Error(
                    message = "Download failed: ${e.localizedMessage ?: "Network interruption"}",
                    cause = e
                )
            }
        }
    }

    /**
     * Verifies cryptographic checksum, package identity, and production signature.
     */
    private suspend fun verifyDownloadedApk(apkFile: File, info: AppUpdateInfo) = withContext(Dispatchers.Default) {
        _updateState.value = UpdateState.Verifying(
            statusMessage = "Verifying package integrity & production certificate...",
            updateInfo = info
        )

        when (val result = verifier.verifyApk(context, apkFile, info.sha256Digest, currentVersionCode)) {
            is VerificationResult.Success -> {
                Timber.i("APK successfully verified: %s", apkFile.name)
                _updateState.value = UpdateState.ReadyToInstall(
                    apkFile = apkFile,
                    updateInfo = info
                )
            }
            is VerificationResult.DigestMismatch -> {
                Timber.e("Verification failed: Digest mismatch")
                apkFile.delete()
                _updateState.value = UpdateState.Error(
                    message = "Security verification failed: File checksum mismatch.",
                    canRetry = true
                )
            }
            is VerificationResult.CertificateMismatch -> {
                Timber.e("Verification failed: Certificate mismatch! Rejecting untrusted APK.")
                apkFile.delete()
                _updateState.value = UpdateState.Error(
                    message = "Security verification failed: Signing certificate does not match official TeleStudy TV production key.",
                    canRetry = false
                )
            }
            is VerificationResult.InvalidPackage -> {
                Timber.e("Verification failed: Invalid package: %s", result.reason)
                apkFile.delete()
                _updateState.value = UpdateState.Error(
                    message = "Invalid update file: ${result.reason}",
                    canRetry = true
                )
            }
            is VerificationResult.VersionNotNewer -> {
                Timber.w("Verification failed: APK version %d is not newer than current %d", result.apkVersionCode, result.currentVersionCode)
                apkFile.delete()
                _updateState.value = UpdateState.Error(
                    message = "Update package is not newer than currently installed version.",
                    canRetry = false
                )
            }
            is VerificationResult.IoError -> {
                Timber.e("Verification failed: IO error: %s", result.message)
                _updateState.value = UpdateState.Error(
                    message = "File access error: ${result.message}",
                    canRetry = true
                )
            }
        }
    }

    /**
     * Cancels an ongoing download or verification.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _updateState.value = UpdateState.Cancelled
    }

    /**
     * Dismisses any current update prompt or error state.
     */
    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Launches the Android Package Installer for the verified APK file.
     */
    fun installUpdate(apkFile: File, info: AppUpdateInfo) {
        try {
            // Check Unknown Sources Permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Timber.w("Unknown sources permission not granted. Opening Settings...")
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            Timber.i("Launching Package Installer with content URI: %s", contentUri)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            _updateState.value = UpdateState.Installing(info)
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _updateState.value = UpdateState.Error(
                message = "Could not open installer: ${e.localizedMessage ?: "Unknown error"}",
                canRetry = true,
                cause = e
            )
        }
    }

    private fun recordCheckTimestamp() {
        lastAutoCheckTimestamp = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECK_TIME, lastAutoCheckTimestamp)
            .apply()
    }
}
