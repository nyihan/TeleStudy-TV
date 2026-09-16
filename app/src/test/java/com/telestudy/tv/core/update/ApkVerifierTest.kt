package com.telestudy.tv.core.update

import android.content.Context
import com.telestudy.tv.core.update.model.VerificationResult
import com.telestudy.tv.core.update.service.ApkVerifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier = ApkVerifier()

    @Test
    fun computeSha256_matchesStandardMessageDigest() {
        val testFile = tempFolder.newFile("test_payload.bin")
        val testContent = "TeleStudy TV In-App Update Verification Payload 2026".toByteArray(Charsets.UTF_8)
        testFile.writeBytes(testContent)

        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(testContent)
            .joinToString("") { "%02x".format(it) }

        val actualSha256 = verifier.computeSha256(testFile)

        assertEquals(expectedSha256, actualSha256)
    }

    @Test
    fun computeBytesSha256_computesCorrectHex() {
        val bytes = "ProductionCertBytes".toByteArray(Charsets.UTF_8)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val actual = verifier.computeBytesSha256(bytes)

        assertEquals(expected, actual)
    }

    @Test
    fun verifyApk_digestMismatch_returnsDigestMismatchResult() {
        val context: Context = RuntimeEnvironment.getApplication()
        val testFile = tempFolder.newFile("fake_app.apk")
        testFile.writeBytes("Not a real apk".toByteArray())

        val result = verifier.verifyApk(
            context = context,
            apkFile = testFile,
            expectedSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            currentVersionCode = 4
        )

        assertTrue(result is VerificationResult.DigestMismatch)
        val mismatch = result as VerificationResult.DigestMismatch
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", mismatch.expected)
    }

    @Test
    fun verifyApk_missingFile_returnsIoError() {
        val context: Context = RuntimeEnvironment.getApplication()
        val missingFile = File(tempFolder.root, "does_not_exist.apk")

        val result = verifier.verifyApk(
            context = context,
            apkFile = missingFile,
            expectedSha256 = null,
            currentVersionCode = 4
        )

        assertTrue(result is VerificationResult.IoError)
    }
}
