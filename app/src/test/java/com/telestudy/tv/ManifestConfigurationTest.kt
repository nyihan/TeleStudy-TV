package com.telestudy.tv

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestConfigurationTest {

    @Test
    fun testManifestContainsDualLaunchersAndTvFeatures() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val content = manifestFile.readText()

        // Verify standard Phone/Tablet Launcher
        assertTrue(
            "Manifest must declare android.intent.category.LAUNCHER for phones/tablets",
            content.contains("android.intent.category.LAUNCHER")
        )

        // Verify Android TV 10-foot Leanback Launcher
        assertTrue(
            "Manifest must declare android.intent.category.LEANBACK_LAUNCHER for Android TV",
            content.contains("android.intent.category.LEANBACK_LAUNCHER")
        )

        // Verify hardware features are optional for cross-device compatibility
        assertTrue(
            "Manifest must declare android.software.leanback with required=false",
            content.contains("android:name=\"android.software.leanback\"") && content.contains("android:required=\"false\"")
        )
        assertTrue(
            "Manifest must declare android.hardware.touchscreen with required=false",
            content.contains("android:name=\"android.hardware.touchscreen\"") && content.contains("android:required=\"false\"")
        )

        // Verify TV banner declaration
        assertTrue(
            "Manifest must declare android:banner for TV launcher",
            content.contains("android:banner=\"@drawable/tv_banner\"")
        )
    }
}
