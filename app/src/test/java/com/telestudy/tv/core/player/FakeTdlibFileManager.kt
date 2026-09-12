package com.telestudy.tv.core.player

import com.telestudy.tv.core.tdlib.TdlibFileManager
import org.drinkless.tdlib.TdApi

/**
 * Test fake simulating TDLib file download, offset seeks, partial prefix progression, and cancellation.
 */
class FakeTdlibFileManager(
    private val testFileId: Int = 1001,
    val totalFileSize: Long = 100 * 1024 * 1024L // 100 MB
) : TdlibFileManager {

    var activeDownloadOffset: Long = 0L
    var activeDownloadedPrefix: Long = 0L
    var totalDownloadedBytes: Long = 0L
    var isDownloadActive: Boolean = false
    var isCompleted: Boolean = false

    var downloadCallsCount: Int = 0
    var cancelCallsCount: Int = 0
    val requestedOffsets = mutableListOf<Long>()

    // Simulated byte generator: byte at position i is (i % 256).toByte()
    fun getExpectedByte(position: Long): Byte = (position % 256).toByte()

    override fun getFile(fileId: Int): TdApi.File? {
        if (fileId != testFileId) return null
        val local = TdApi.LocalFile().apply {
            canBeDownloaded = true
            canBeDeleted = false
            isDownloadingActive = isDownloadActive
            isDownloadingCompleted = isCompleted
            downloadOffset = activeDownloadOffset
            downloadedPrefixSize = activeDownloadedPrefix
            downloadedSize = totalDownloadedBytes
            path = ""
        }
        return TdApi.File(testFileId, totalFileSize, totalFileSize, local, TdApi.RemoteFile())
    }

    override fun downloadFile(fileId: Int, priority: Int, offset: Long, limit: Long) {
        if (fileId != testFileId) return
        downloadCallsCount++
        requestedOffsets.add(offset)
        activeDownloadOffset = offset
        isDownloadActive = true

        // Simulate initial chunk available immediately upon download request (e.g. 512 KB)
        val initialChunk = minOf(512 * 1024L, totalFileSize - offset)
        activeDownloadedPrefix = initialChunk
        totalDownloadedBytes += initialChunk
    }

    override fun cancelDownloadFile(fileId: Int) {
        if (fileId != testFileId) return
        cancelCallsCount++
        isDownloadActive = false
    }

    override fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray? {
        if (fileId != testFileId) return null
        if (offset >= totalFileSize) return null

        // Check if requested offset is within currently available downloaded prefix
        if (offset < activeDownloadOffset || offset >= (activeDownloadOffset + activeDownloadedPrefix)) {
            return null
        }

        val available = (activeDownloadOffset + activeDownloadedPrefix) - offset
        val toRead = minOf(count, available, totalFileSize - offset).toInt()
        val data = ByteArray(toRead)
        for (i in 0 until toRead) {
            data[i] = getExpectedByte(offset + i)
        }
        return data
    }

    override fun waitForBytes(fileId: Int, offset: Long, requiredBytes: Long, timeoutMs: Long): Boolean {
        if (fileId != testFileId) return false
        if (isCompleted) return true

        // If offset is already within current prefix, return true
        if (offset >= activeDownloadOffset && (offset + requiredBytes) <= (activeDownloadOffset + activeDownloadedPrefix)) {
            return true
        }

        // Otherwise simulate progressive arrival of bytes up to requiredBytes
        if (offset >= activeDownloadOffset) {
            val needed = (offset + requiredBytes) - (activeDownloadOffset + activeDownloadedPrefix)
            val increase = maxOf(needed, 64 * 1024L)
            activeDownloadedPrefix = minOf(activeDownloadedPrefix + increase, totalFileSize - activeDownloadOffset)
            totalDownloadedBytes += increase
            return true
        }

        return false
    }

    override fun addFileListener(fileId: Int, listener: (TdApi.File) -> Unit) {}
    override fun removeFileListener(fileId: Int, listener: (TdApi.File) -> Unit) {}
}
