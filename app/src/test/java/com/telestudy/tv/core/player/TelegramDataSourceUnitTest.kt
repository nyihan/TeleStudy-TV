package com.telestudy.tv.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelegramDataSourceUnitTest {

    private val testFileId = 1001
    private val totalSize = 100 * 1024 * 1024L // 100 MB
    private lateinit var fakeManager: FakeTdlibFileManager
    private lateinit var dataSource: TelegramDataSource

    @Before
    fun setUp() {
        fakeManager = FakeTdlibFileManager(testFileId = testFileId, totalFileSize = totalSize)
        dataSource = TelegramDataSource(fakeManager)
    }

    @Test
    fun testProgressivePlaybackBegins_beforeFullDownload() {
        val uri = Uri.parse("tg://file/$testFileId?size=$totalSize")
        val dataSpec = DataSpec(uri)

        val remaining = dataSource.open(dataSpec)
        assertEquals(totalSize, remaining)
        assertEquals(1, fakeManager.downloadCallsCount)
        assertEquals(0L, fakeManager.activeDownloadOffset)

        // Read initial 128 KB chunk (typical initial audio/video header read by ExoPlayer)
        val readBuffer = ByteArray(64 * 1024)
        val firstRead = dataSource.read(readBuffer, 0, readBuffer.size)
        assertEquals(readBuffer.size, firstRead)

        // Verify content correctness
        for (i in 0 until firstRead) {
            assertEquals(fakeManager.getExpectedByte(i.toLong()), readBuffer[i])
        }

        // HARD TECHNICAL FEASIBILITY GATE:
        // Playback read began while downloadedSize < totalFileSize!
        val file = fakeManager.getFile(testFileId)
        assertNotNull(file)
        val downloadedSize = file!!.local.downloadedSize
        assertTrue(
            "Progressive playback gate: downloadedSize ($downloadedSize) must be < totalSize ($totalSize)",
            downloadedSize < totalSize
        )
        assertTrue(
            "Initial download must be a small fraction of the 100MB file",
            downloadedSize <= 2 * 1024 * 1024L // Less than 2MB
        )

        dataSource.close()
    }

    @Test
    fun testSeekToUnbufferedPosition_triggersNewOffsetDownload() {
        val uri = Uri.parse("tg://file/$testFileId?size=$totalSize")

        // 1. Initial start at offset 0
        dataSource.open(DataSpec(uri, 0L, C.LENGTH_UNSET.toLong()))
        val buffer = ByteArray(32 * 1024)
        dataSource.read(buffer, 0, buffer.size)
        dataSource.close()

        assertEquals(1, fakeManager.cancelCallsCount)

        // 2. Seek to 50 MB offset (unbuffered position)
        val seekOffset = 50 * 1024 * 1024L
        val seekDataSpec = DataSpec(uri, seekOffset, C.LENGTH_UNSET.toLong())
        val remainingAfterSeek = dataSource.open(seekDataSpec)

        assertEquals(totalSize - seekOffset, remainingAfterSeek)
        assertEquals(2, fakeManager.downloadCallsCount)
        assertEquals(seekOffset, fakeManager.activeDownloadOffset)
        assertTrue("Download must be requested with seek offset", fakeManager.requestedOffsets.contains(seekOffset))

        // Read from seek position
        val seekBuffer = ByteArray(64 * 1024)
        val seekRead = dataSource.read(seekBuffer, 0, seekBuffer.size)
        assertEquals(seekBuffer.size, seekRead)

        // Verify seek content matches byte generator at 50 MB
        for (i in 0 until seekRead) {
            assertEquals(fakeManager.getExpectedByte(seekOffset + i), seekBuffer[i])
        }

        // Verify bytes between 1 MB and 50 MB were SKIPPED and not downloaded
        assertTrue(
            "Total downloaded bytes should be far less than 50MB because intermediate bytes were skipped",
            fakeManager.totalDownloadedBytes < 10 * 1024 * 1024L
        )

        dataSource.close()
    }

    @Test
    fun testClose_cancelsInFlightDownload() {
        val uri = Uri.parse("tg://file/$testFileId?size=$totalSize")
        dataSource.open(DataSpec(uri))

        assertTrue(fakeManager.isDownloadActive)
        assertEquals(0, fakeManager.cancelCallsCount)

        dataSource.close()

        assertEquals(1, fakeManager.cancelCallsCount)
        assertTrue(!fakeManager.isDownloadActive)
    }

    @Test
    fun testBoundedMemory_heapRemainsConstantDuringStream() {
        val uri = Uri.parse("tg://file/$testFileId?size=$totalSize")
        dataSource.open(DataSpec(uri))

        val buffer = ByteArray(64 * 1024)
        var totalRead = 0L
        val targetRead = 5 * 1024 * 1024L // Read 5 MB sequentially in 64KB chunks

        val runtime = Runtime.getRuntime()
        System.gc()
        val initialUsedMemory = runtime.totalMemory() - runtime.freeMemory()

        while (totalRead < targetRead) {
            val bytes = dataSource.read(buffer, 0, buffer.size)
            if (bytes <= 0) break
            totalRead += bytes
        }

        assertEquals(targetRead, totalRead)

        System.gc()
        val finalUsedMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = Math.abs(finalUsedMemory - initialUsedMemory)

        // Verifies streaming does not buffer full stream in RAM; heap delta should be bounded (< 10 MB)
        assertTrue(
            "Memory growth must remain bounded during streaming. Delta: ${memoryDelta / (1024 * 1024)} MB",
            memoryDelta < 10 * 1024 * 1024L
        )

        dataSource.close()
    }

    @Test
    fun testInvalidFileId_throwsDataSourceException() {
        val invalidUri = Uri.parse("tg://file/notanumber")
        try {
            dataSource.open(DataSpec(invalidUri))
            fail("Should have thrown DataSourceException for invalid URI")
        } catch (e: DataSourceException) {
            assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, e.reason)
        }
    }

    @Test
    fun testPositionOutOfRange_throwsDataSourceException() {
        val uri = Uri.parse("tg://file/$testFileId?size=$totalSize")
        val outOfRangeSpec = DataSpec(uri, totalSize + 1000L, 100L)
        try {
            dataSource.open(outOfRangeSpec)
            fail("Should have thrown DataSourceException for position out of range")
        } catch (e: DataSourceException) {
            assertEquals(androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE, e.reason)
        }
    }
}
