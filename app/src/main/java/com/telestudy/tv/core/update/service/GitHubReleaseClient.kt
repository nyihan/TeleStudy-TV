package com.telestudy.tv.core.update.service

import com.telestudy.tv.core.update.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Service to query GitHub Releases API and parse release metadata for in-app updates.
 */
class GitHubReleaseClient(
    private val repoOwner: String = DEFAULT_REPO_OWNER,
    private val repoName: String = DEFAULT_REPO_NAME
) {
    companion object {
        const val DEFAULT_REPO_OWNER = "nyihan"
        const val DEFAULT_REPO_NAME = "TeleStudy-TV"
        private const val API_BASE_URL = "https://api.github.com/repos"
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 20000

        private val SHA256_BODY_PATTERN = Pattern.compile(
            "(?i)(?:sha-?256|digest)[\\s:=]+([a-f0-9]{64})"
        )
        private val VERSION_CODE_BODY_PATTERN = Pattern.compile(
            "(?i)(?:versioncode|version_code|vc)[\\s:=]+(\\d+)"
        )
        private val SEMVER_PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)"
        )
    }

    /**
     * Fetches the latest published release from GitHub and parses update info.
     */
    suspend fun fetchLatestRelease(
        customEndpointUrl: String? = null
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val endpoint = customEndpointUrl ?: "$API_BASE_URL/$repoOwner/$repoName/releases/latest"
        Timber.d("Checking for updates from: %s", endpoint)

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "TeleStudyTV-InAppUpdate")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Timber.w("GitHub release check failed: HTTP %d: %s", responseCode, errorBody)
                return@withContext Result.failure(
                    IllegalStateException("GitHub API returned HTTP $responseCode")
                )
            }

            val responseBody = BufferedReader(InputStreamReader(connection.inputStream)).use {
                it.readText()
            }

            val updateInfo = parseReleaseJson(responseBody)
            Result.success(updateInfo)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching latest GitHub release")
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses the JSON payload from GitHub Release API.
     * Can be called directly in unit tests.
     */
    fun parseReleaseJson(jsonString: String): AppUpdateInfo {
        val json = JSONObject(jsonString)

        val isDraft = json.optBoolean("draft", false)
        if (isDraft) {
            throw IllegalArgumentException("Release is a draft, ignoring")
        }

        val isPrerelease = json.optBoolean("prerelease", false)
        if (isPrerelease) {
            throw IllegalArgumentException("Release is marked as prerelease, ignoring")
        }

        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isEmpty()) {
            throw IllegalArgumentException("Release missing tag_name")
        }

        val releaseName = json.optString("name", tagName)
        val releaseBody = json.optString("body", "")
        val publishedAt = json.optString("published_at", "")

        val assetsArray = json.optJSONArray("assets")
            ?: throw IllegalArgumentException("Release contains no assets")

        // Find best matching APK asset
        var selectedAsset: JSONObject? = null
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.startsWith("TeleStudy-TV-") && name.endsWith(".apk")) {
                selectedAsset = asset
                break
            }
        }

        // Fallback: any .apk asset
        if (selectedAsset == null) {
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    selectedAsset = asset
                    break
                }
            }
        }

        val asset = selectedAsset
            ?: throw IllegalArgumentException("No APK asset found in release $tagName")

        val apkName = asset.getString("name")
        val downloadUrl = asset.getString("browser_download_url")
        val apkSize = asset.optLong("size", 0L)

        // Extract SHA-256 digest
        var digest: String? = null
        val assetDigest = asset.optString("digest", "")
        if (assetDigest.isNotBlank()) {
            digest = assetDigest.removePrefix("sha256:").trim().lowercase()
        }

        if (digest.isNullOrBlank()) {
            val bodyMatcher = SHA256_BODY_PATTERN.matcher(releaseBody)
            if (bodyMatcher.find()) {
                digest = bodyMatcher.group(1)?.lowercase()
            }
        }

        // Extract Version Name
        val versionName = tagName.removePrefix("v").removePrefix("V").trim()

        // Extract Version Code
        val versionCode = parseVersionCode(tagName, releaseBody)

        return AppUpdateInfo(
            latestVersionName = versionName,
            latestVersionCode = versionCode,
            releaseTag = tagName,
            releaseName = releaseName,
            releaseNotes = releaseBody,
            apkDownloadUrl = downloadUrl,
            apkFileName = apkName,
            apkSize = apkSize,
            sha256Digest = digest,
            releasePublishedAt = publishedAt
        )
    }

    /**
     * Resolves an integer versionCode from release notes or tag name.
     */
    fun parseVersionCode(tagName: String, releaseBody: String): Int {
        // Priority 1: Explicit annotation in release body (e.g. versionCode: 4 or vc: 4)
        val bodyMatcher = VERSION_CODE_BODY_PATTERN.matcher(releaseBody)
        if (bodyMatcher.find()) {
            val codeStr = bodyMatcher.group(1)
            val parsed = codeStr?.toIntOrNull()
            if (parsed != null && parsed > 0) {
                return parsed
            }
        }

        // Priority 2: Parse tag (e.g. "v1.0.4" -> 4 if 1.0.X, or semver formula)
        val semverMatcher = SEMVER_PATTERN.matcher(tagName.trim())
        if (semverMatcher.find()) {
            val major = semverMatcher.group(1)?.toIntOrNull() ?: 0
            val minor = semverMatcher.group(2)?.toIntOrNull() ?: 0
            val patch = semverMatcher.group(3)?.toIntOrNull() ?: 0

            // Project convention for 1.0.x: versionCode matches the patch number (e.g. 1.0.4 -> 4)
            if (major == 1 && minor == 0) {
                return patch
            }

            // General formula for future versions (e.g., 1.1.0 -> 10100)
            return major * 10000 + minor * 100 + patch
        }

        // Fallback: extract any digits from tag
        val digitsOnly = tagName.filter { it.isDigit() }
        return digitsOnly.toIntOrNull() ?: 1
    }
}
