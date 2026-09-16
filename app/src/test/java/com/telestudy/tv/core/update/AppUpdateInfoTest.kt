package com.telestudy.tv.core.update

import com.telestudy.tv.core.update.model.AppUpdateInfo
import org.junit.Assert.*
import org.junit.Test

class AppUpdateInfoTest {

    @Test
    fun isNewerThan_higherVersionCode_returnsTrue() {
        val info = createUpdateInfo(versionCode = 5, versionName = "1.0.5")
        assertTrue(info.isNewerThan(currentVersionCode = 4, currentVersionName = "1.0.4"))
    }

    @Test
    fun isNewerThan_lowerVersionCode_returnsFalse() {
        val info = createUpdateInfo(versionCode = 3, versionName = "1.0.3")
        assertFalse(info.isNewerThan(currentVersionCode = 4, currentVersionName = "1.0.4"))
    }

    @Test
    fun isNewerThan_sameVersionCode_newerVersionName_returnsTrue() {
        val info = createUpdateInfo(versionCode = 4, versionName = "1.0.4.1")
        assertTrue(info.isNewerThan(currentVersionCode = 4, currentVersionName = "1.0.4"))
    }

    @Test
    fun isNewerThan_sameVersionCode_sameVersionName_returnsFalse() {
        val info = createUpdateInfo(versionCode = 4, versionName = "1.0.4")
        assertFalse(info.isNewerThan(currentVersionCode = 4, currentVersionName = "1.0.4"))
    }

    @Test
    fun compareSemVer_correctOrdering() {
        assertEquals(1, AppUpdateInfo.compareSemVer("1.0.4", "1.0.3"))
        assertEquals(-1, AppUpdateInfo.compareSemVer("1.0.3", "1.0.4"))
        assertEquals(0, AppUpdateInfo.compareSemVer("1.0.4", "1.0.4"))
        assertEquals(1, AppUpdateInfo.compareSemVer("v1.1.0", "v1.0.9"))
        assertEquals(1, AppUpdateInfo.compareSemVer("2.0.0", "1.99.99"))
        assertEquals(0, AppUpdateInfo.compareSemVer("v1.0.4", "1.0.4"))
    }

    private fun createUpdateInfo(versionCode: Int, versionName: String): AppUpdateInfo {
        return AppUpdateInfo(
            latestVersionName = versionName,
            latestVersionCode = versionCode,
            releaseTag = "v$versionName",
            releaseName = "TeleStudy TV v$versionName",
            releaseNotes = "Test notes",
            apkDownloadUrl = "https://example.com/download.apk",
            apkFileName = "TeleStudy-TV-v$versionName.apk",
            apkSize = 100_000_000L
        )
    }
}
