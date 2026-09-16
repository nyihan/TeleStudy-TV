package com.telestudy.tv.core.update.model

import java.io.File

/**
 * Data model representing an available application update discovered from GitHub Releases.
 */
data class AppUpdateInfo(
    val latestVersionName: String,
    val latestVersionCode: Int,
    val releaseTag: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val sha256Digest: String? = null,
    val releasePublishedAt: String = ""
) {
    /**
     * Checks if this update is newer than the currently running build.
     */
    fun isNewerThan(currentVersionCode: Int, currentVersionName: String = ""): Boolean {
        if (latestVersionCode > currentVersionCode) return true
        if (latestVersionCode < currentVersionCode) return false
        // If version codes are equal, fallback to semantic version string comparison if available
        return compareSemVer(latestVersionName, currentVersionName) > 0
    }

    companion object {
        /**
         * Helper to compare semantic version strings (e.g., "1.0.4" vs "1.0.3").
         */
        fun compareSemVer(v1: String, v2: String): Int {
            val clean1 = v1.removePrefix("v").removePrefix("V").trim()
            val clean2 = v2.removePrefix("v").removePrefix("V").trim()
            if (clean1 == clean2) return 0

            val parts1 = clean1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = clean2.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) {
                    return p1.compareTo(p2)
                }
            }
            return 0
        }
    }
}

/**
 * Observable UI / business state for the in-app update workflow.
 */
sealed interface UpdateState {
    /** Update engine is inactive. */
    object Idle : UpdateState

    /** Actively querying remote release API. */
    object Checking : UpdateState

    /** A verified newer version is available to download. */
    data class Available(val updateInfo: AppUpdateInfo) : UpdateState

    /** APK download in progress with live fraction and byte counters. */
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val updateInfo: AppUpdateInfo
    ) : UpdateState

    /** Computing SHA-256 and verifying package identity and production certificate. */
    data class Verifying(
        val statusMessage: String,
        val updateInfo: AppUpdateInfo
    ) : UpdateState

    /** APK downloaded and cryptographically verified, ready for installation. */
    data class ReadyToInstall(
        val apkFile: File,
        val updateInfo: AppUpdateInfo
    ) : UpdateState

    /** Android Package Installer intent has been launched. */
    data class Installing(val updateInfo: AppUpdateInfo) : UpdateState

    /** Currently running version is up-to-date. */
    data class UpToDate(val currentVersionCode: Int, val currentVersionName: String) : UpdateState

    /** An error occurred during check, download, or verification. */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val cause: Throwable? = null
    ) : UpdateState

    /** User dismissed or cancelled the update. */
    object Cancelled : UpdateState
}

/**
 * Detailed result of cryptographic and package integrity verification.
 */
sealed interface VerificationResult {
    object Success : VerificationResult
    data class DigestMismatch(val expected: String, val actual: String) : VerificationResult
    data class InvalidPackage(val reason: String) : VerificationResult
    data class CertificateMismatch(val expected: String, val actualList: List<String>) : VerificationResult
    data class VersionNotNewer(val apkVersionCode: Long, val currentVersionCode: Int) : VerificationResult
    data class IoError(val message: String) : VerificationResult
}
