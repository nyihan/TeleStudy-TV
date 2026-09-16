package com.telestudy.tv.core.update.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.telestudy.tv.core.update.model.VerificationResult
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Validates downloaded APK integrity, package identity, version monotonic progression,
 * and cryptographic signing certificate against the permanent production release identity.
 */
class ApkVerifier(
    private val expectedPackageName: String = DEFAULT_PACKAGE_NAME,
    private val expectedCertSha256: String = PRODUCTION_CERT_SHA256
) {
    companion object {
        const val DEFAULT_PACKAGE_NAME = "com.telestudy.tv"

        /**
         * Permanent TeleStudy TV Production Release Certificate SHA-256 Fingerprint.
         * Established in Gate A: CN=TeleStudy TV, O=TeleStudy, C=MM.
         */
        const val PRODUCTION_CERT_SHA256 =
            "7639d1611488d0c97d506339548bef59cf2b82df475e6710f3a373dcc0a5d068"
    }

    /**
     * Executes the complete two-tier verification process:
     * 1. Checksum verification against GitHub asset digest (if provided).
     * 2. Package and cryptographic signing verification using Android's PackageManager.
     */
    fun verifyApk(
        context: Context,
        apkFile: File,
        expectedSha256: String?,
        currentVersionCode: Int
    ): VerificationResult {
        if (!apkFile.exists() || !apkFile.canRead() || apkFile.length() == 0L) {
            return VerificationResult.IoError("APK file not found or empty: ${apkFile.absolutePath}")
        }

        // --- Tier 1: Checksum Verification ---
        if (!expectedSha256.isNullOrBlank()) {
            val cleanExpected = expectedSha256.removePrefix("sha256:").trim().lowercase()
            val actualFileHash = computeSha256(apkFile)
            Timber.i("Computed APK SHA-256: %s (expected: %s)", actualFileHash, cleanExpected)

            if (!actualFileHash.equals(cleanExpected, ignoreCase = true)) {
                Timber.e("SHA-256 mismatch! Expected %s, got %s", cleanExpected, actualFileHash)
                return VerificationResult.DigestMismatch(expected = cleanExpected, actual = actualFileHash)
            }
        }

        // --- Tier 2: Android Archive & Signature Verification ---
        return verifyArchiveAndSignature(context, apkFile, currentVersionCode)
    }

    /**
     * Inspects the APK archive headers and signatures via Android PackageManager.
     */
    fun verifyArchiveAndSignature(
        context: Context,
        apkFile: File,
        currentVersionCode: Int
    ): VerificationResult {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return VerificationResult.InvalidPackage("PackageManager could not parse APK archive")

        // 1. Verify Package Name
        if (packageInfo.packageName != expectedPackageName) {
            Timber.e("Package mismatch: expected %s, found %s", expectedPackageName, packageInfo.packageName)
            return VerificationResult.InvalidPackage(
                "Package name mismatch: expected $expectedPackageName, got ${packageInfo.packageName}"
            )
        }

        // 2. Verify Version Code Progression
        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        if (apkVersionCode <= currentVersionCode) {
            Timber.e("VersionCode not newer: APK has %d, current is %d", apkVersionCode, currentVersionCode)
            return VerificationResult.VersionNotNewer(
                apkVersionCode = apkVersionCode,
                currentVersionCode = currentVersionCode
            )
        }

        // 3. Extract and Verify Signing Certificate
        val certSignatures = extractSignatures(packageInfo)
        if (certSignatures.isEmpty()) {
            Timber.e("No signatures found in APK")
            return VerificationResult.InvalidPackage("No signing certificates found in APK")
        }

        val actualCertHashes = certSignatures.map { bytes ->
            computeBytesSha256(bytes)
        }

        val targetHash = expectedCertSha256.lowercase().trim()
        val hasMatchingCert = actualCertHashes.any { it.equals(targetHash, ignoreCase = true) }

        if (!hasMatchingCert) {
            Timber.e("Certificate mismatch! Target: %s, Found: %s", targetHash, actualCertHashes)
            return VerificationResult.CertificateMismatch(
                expected = targetHash,
                actualList = actualCertHashes
            )
        }

        Timber.i("APK verification succeeded: %s v%d (Cert SHA-256 verified)", packageInfo.packageName, apkVersionCode)
        return VerificationResult.Success
    }

    /**
     * Extracts raw signature byte arrays from PackageInfo across all Android versions.
     */
    private fun extractSignatures(packageInfo: android.content.pm.PackageInfo): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.let { signingInfo ->
                val signers = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signers?.forEach { certs.add(it.toByteArray()) }
            }
        }

        if (certs.isEmpty()) {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.forEach { certs.add(it.toByteArray()) }
        }

        return certs
    }

    /**
     * Computes the SHA-256 hex digest of a file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the SHA-256 hex digest of a byte array.
     */
    fun computeBytesSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
