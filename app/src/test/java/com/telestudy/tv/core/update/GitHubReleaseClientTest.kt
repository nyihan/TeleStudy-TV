package com.telestudy.tv.core.update

import com.telestudy.tv.core.update.service.GitHubReleaseClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubReleaseClientTest {

    private val client = GitHubReleaseClient()

    @Test
    fun parseReleaseJson_validReleaseWithSha256Digest_success() {
        val sampleJson = """
            {
                "tag_name": "v1.0.4",
                "name": "TeleStudy TV v1.0.4",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-09-16T12:00:00Z",
                "body": "### Features\n- Added In-App Updates",
                "assets": [
                    {
                        "name": "TeleStudy-TV-v1.0.4.apk",
                        "size": 105439665,
                        "digest": "sha256:7639d1611488d0c97d506339548bef59cf2b82df475e6710f3a373dcc0a5d068",
                        "browser_download_url": "https://github.com/nyihan/TeleStudy-TV/releases/download/v1.0.4/TeleStudy-TV-v1.0.4.apk"
                    }
                ]
            }
        """.trimIndent()

        val info = client.parseReleaseJson(sampleJson)

        assertEquals("1.0.4", info.latestVersionName)
        assertEquals(4, info.latestVersionCode)
        assertEquals("v1.0.4", info.releaseTag)
        assertEquals("TeleStudy-TV-v1.0.4.apk", info.apkFileName)
        assertEquals(105439665L, info.apkSize)
        assertEquals("7639d1611488d0c97d506339548bef59cf2b82df475e6710f3a373dcc0a5d068", info.sha256Digest)
        assertEquals("https://github.com/nyihan/TeleStudy-TV/releases/download/v1.0.4/TeleStudy-TV-v1.0.4.apk", info.apkDownloadUrl)
    }

    @Test
    fun parseReleaseJson_sha256InBodyFallback_extractsDigest() {
        val sampleJson = """
            {
                "tag_name": "v1.0.5",
                "name": "TeleStudy TV v1.0.5",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-09-20T10:00:00Z",
                "body": "Bugfixes\n\nSHA-256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "assets": [
                    {
                        "name": "TeleStudy-TV-v1.0.5.apk",
                        "size": 106000000,
                        "browser_download_url": "https://github.com/nyihan/TeleStudy-TV/releases/download/v1.0.5/TeleStudy-TV-v1.0.5.apk"
                    }
                ]
            }
        """.trimIndent()

        val info = client.parseReleaseJson(sampleJson)

        assertEquals("1.0.5", info.latestVersionName)
        assertEquals(5, info.latestVersionCode)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", info.sha256Digest)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseReleaseJson_draftRelease_throwsException() {
        val sampleJson = """
            {
                "tag_name": "v1.0.4",
                "draft": true,
                "prerelease": false,
                "assets": []
            }
        """.trimIndent()

        client.parseReleaseJson(sampleJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseReleaseJson_prerelease_throwsException() {
        val sampleJson = """
            {
                "tag_name": "v1.0.4-beta",
                "draft": false,
                "prerelease": true,
                "assets": []
            }
        """.trimIndent()

        client.parseReleaseJson(sampleJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseReleaseJson_noApkAsset_throwsException() {
        val sampleJson = """
            {
                "tag_name": "v1.0.4",
                "draft": false,
                "prerelease": false,
                "assets": [
                    {
                        "name": "source-code.zip",
                        "size": 1000,
                        "browser_download_url": "https://example.com/source.zip"
                    }
                ]
            }
        """.trimIndent()

        client.parseReleaseJson(sampleJson)
    }

    @Test
    fun parseVersionCode_fromBodyExplicit() {
        val code1 = client.parseVersionCode("v1.0.4", "versionCode: 42\nBug fixes")
        assertEquals(42, code1)

        val code2 = client.parseVersionCode("v1.0.4", "version_code = 100")
        assertEquals(100, code2)
    }

    @Test
    fun parseVersionCode_fromTagSemantic() {
        // v1.0.X series maps to patch number
        assertEquals(4, client.parseVersionCode("v1.0.4", ""))
        assertEquals(12, client.parseVersionCode("v1.0.12", ""))

        // Future version bump
        assertEquals(10102, client.parseVersionCode("v1.1.2", ""))
    }
}
