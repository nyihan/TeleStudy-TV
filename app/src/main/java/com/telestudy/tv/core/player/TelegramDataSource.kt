package com.telestudy.tv.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceException
import com.telestudy.tv.core.tdlib.TdlibFileManager
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Media3 DataSource that streams Telegram video files progressively via TDLib.
 * 
 * Supports:
 * - Immediate playback start before full file is downloaded (downloadedSize < totalSize)
 * - Dynamic byte-range requests and seeking via TDLib offset downloads
 * - Direct local disk reads when file is fully downloaded or sparse chunk is present
 * - Bounded memory consumption (reads in small chunks, never loads entire video into RAM)
 * - Safe cancellation on seek and close
 */
class TelegramDataSource(
    private val tdlibFileManager: TdlibFileManager
) : BaseDataSource(/* isNetwork = */ true) {

    private var currentDataSpec: DataSpec? = null
    private var currentUri: Uri? = null
    private var fileId: Int = -1
    private var totalFileSize: Long = C.LENGTH_UNSET.toLong()
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var isOpened: Boolean = false

    private var localRaf: RandomAccessFile? = null

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        currentUri = dataSpec.uri
        val uri = dataSpec.uri

        // Parse fileId from URI: "tg://file/12345?size=1000000" or "tg://file?id=12345&size=1000000"
        fileId = parseFileId(uri)
            ?: throw DataSourceException(
                "Invalid Telegram file URI: $uri",
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )

        // Parse or query total file size
        val sizeParam = uri.getQueryParameter("size")?.toLongOrNull()
        val tdFile = tdlibFileManager.getFile(fileId)
        totalFileSize = when {
            sizeParam != null && sizeParam > 0L -> sizeParam
            tdFile != null && tdFile.size > 0L -> tdFile.size
            tdFile != null && tdFile.expectedSize > 0L -> tdFile.expectedSize
            else -> C.LENGTH_UNSET.toLong()
        }

        currentPosition = dataSpec.position

        if (totalFileSize != C.LENGTH_UNSET.toLong() && currentPosition > totalFileSize) {
            throw DataSourceException(
                "Position out of range: $currentPosition > $totalFileSize",
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalFileSize != C.LENGTH_UNSET.toLong()) {
            totalFileSize - currentPosition
        } else {
            C.LENGTH_UNSET.toLong()
        }

        transferInitializing(dataSpec)

        Timber.i(
            "[TeleStudySpike] TelegramDataSource OPEN: fileId=$fileId, position=$currentPosition, " +
            "length=${dataSpec.length}, remaining=$bytesRemaining, totalSize=$totalFileSize"
        )

        // Check if local file is already fully available on disk
        val localPath = tdFile?.local?.path
        if (!localPath.isNullOrEmpty() && tdFile.local.isDownloadingCompleted) {
            val f = File(localPath)
            if (f.exists() && f.canRead()) {
                Timber.i("[TeleStudySpike] Reading from COMPLETED local file on disk: $localPath")
                localRaf = RandomAccessFile(f, "r")
                localRaf?.seek(currentPosition)
            }
        } else {
            // Request TDLib to download chunk starting at currentPosition
            // Priority 32 = maximum foreground streaming priority
            val currentDlSize = tdFile?.local?.downloadedSize ?: 0L
            Timber.i(
                "[TeleStudySpike] PROGRESSIVE STREAMING REQUEST: fileId=$fileId at offset=$currentPosition. " +
                "Current downloadedSize on disk = $currentDlSize / $totalFileSize bytes " +
                "(isCompleted = ${tdFile?.local?.isDownloadingCompleted})"
            )
            tdlibFileManager.downloadFile(
                fileId = fileId,
                priority = 32,
                offset = currentPosition,
                limit = 0L // Download to end of stream or until cancelled on seek
            )
        }

        isOpened = true
        transferStarted(dataSpec)

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val remainingInFile = if (totalFileSize > 0L) (totalFileSize - currentPosition) else bytesRemaining
        if (remainingInFile <= 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining, remainingInFile).toInt()
        } else {
            minOf(length.toLong(), remainingInFile).toInt()
        }

        if (bytesToRead <= 0) return C.RESULT_END_OF_INPUT

        // Cap individual chunk read to 64 KB and strictly within remaining bytes to avoid TDLib out-of-bounds error
        val chunkLimit = minOf(bytesToRead.toLong(), 65536L).toInt()

        var bytesRead = -1

        // 1. If we have a direct RandomAccessFile on a completed local file, read directly
        val raf = localRaf
        if (raf != null) {
            bytesRead = raf.read(buffer, offset, chunkLimit)
            if (bytesRead > 0) {
                currentPosition += bytesRead
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesRead
                }
                bytesTransferred(bytesRead)
                return bytesRead
            } else if (bytesRead == -1 && bytesRemaining == 0L) {
                return C.RESULT_END_OF_INPUT
            }
        }

        // 2. Progressive streaming mode: Wait for TDLib to download at least a minimal chunk
        val minRequired = minOf(chunkLimit.toLong(), 4096L)
        val waitSuccess = tdlibFileManager.waitForBytes(
            fileId = fileId,
            offset = currentPosition,
            requiredBytes = minRequired,
            timeoutMs = 15000L
        )

        if (!waitSuccess && !isOpened) {
            // Closed / cancelled while waiting
            return C.RESULT_END_OF_INPUT
        }

        // Check again if file completed while waiting and opened disk handle
        val fileAfterWait = tdlibFileManager.getFile(fileId)
        val completedPath = fileAfterWait?.local?.path
        if (!completedPath.isNullOrEmpty() && fileAfterWait.local.isDownloadingCompleted && localRaf == null) {
            val f = File(completedPath)
            if (f.exists() && f.canRead()) {
                val newRaf = RandomAccessFile(f, "r")
                newRaf.seek(currentPosition)
                localRaf = newRaf
                bytesRead = newRaf.read(buffer, offset, chunkLimit)
                if (bytesRead > 0) {
                    currentPosition += bytesRead
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                        bytesRemaining -= bytesRead
                    }
                    bytesTransferred(bytesRead)
                    return bytesRead
                }
            }
        }

        // 3. Read partial chunk from TDLib cache via readFilePart adaptively.
        // TDLib returns error if 'count' exceeds currently downloaded bytes at offset.
        // First check cached downloadedSize if available to query exact available range.
        var partData: ByteArray? = null
        val downloaded = fileAfterWait?.local?.downloadedSize ?: 0L
        val available = (downloaded - currentPosition).coerceAtLeast(0L)
        if (available > 0L) {
            val countToTry = minOf(chunkLimit.toLong(), available)
            partData = tdlibFileManager.readFilePart(
                fileId = fileId,
                offset = currentPosition,
                count = countToTry
            )
        }

        // If not found or downloadedSize unhelpful, step down gracefully through standard sizes
        if (partData == null) {
            val stepSizes = longArrayOf(65536L, 32768L, 16384L, 8192L, 4096L, 2048L, 1024L, 512L, 256L, 128L, 64L)
            for (step in stepSizes) {
                if (step <= chunkLimit) {
                    partData = tdlibFileManager.readFilePart(
                        fileId = fileId,
                        offset = currentPosition,
                        count = step
                    )
                    if (partData != null && partData.isNotEmpty()) {
                        break
                    }
                }
            }
        }

        if (partData == null && chunkLimit > 0) {
            // Last resort: query minimal chunk
            partData = tdlibFileManager.readFilePart(
                fileId = fileId,
                offset = currentPosition,
                count = minOf(chunkLimit.toLong(), 256L)
            )
        }

        if (partData != null && partData.isNotEmpty()) {
            val count = minOf(partData.size, length)
            System.arraycopy(partData, 0, buffer, offset, count)
            bytesRead = count
            currentPosition += bytesRead
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead
            }
            bytesTransferred(bytesRead)
            Timber.d("[TeleStudySpike] TelegramDataSource READ: fileId=$fileId at pos=${currentPosition-bytesRead}, got=$bytesRead bytes, newPos=$currentPosition, remaining=$bytesRemaining")
            return bytesRead
        }

        Timber.w(
            "[TeleStudySpike] TelegramDataSource READ_FAILED: fileId=$fileId at pos=$currentPosition, " +
            "partData=${if (partData == null) "null" else "empty"}, waitSuccess=$waitSuccess, remaining=$bytesRemaining"
        )

        if (!isOpened) {
            return C.RESULT_END_OF_INPUT
        }

        throw IOException("Timed out or failed reading Telegram file $fileId at offset $currentPosition (remaining=$bytesRemaining)")
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (isOpened) {
            isOpened = false
            Timber.i("[TeleStudySpike] TelegramDataSource CLOSE: fileId=$fileId at position=$currentPosition. Cancelling in-flight download.")

            // Cancel in-flight offset download so network bandwidth is not wasted
            if (fileId != -1) {
                tdlibFileManager.cancelDownloadFile(fileId)
            }

            try {
                localRaf?.close()
            } catch (e: Exception) {
                Timber.w(e, "[TeleStudySpike] Error closing RandomAccessFile for file $fileId")
            } finally {
                localRaf = null
            }

            transferEnded()
        }
    }

    private fun parseFileId(uri: Uri): Int? {
        // e.g. tg://file/12345 or tg://file?id=12345 or tg://12345
        val queryId = uri.getQueryParameter("id")?.toIntOrNull()
        if (queryId != null) return queryId

        val pathSegment = uri.lastPathSegment?.toIntOrNull()
        if (pathSegment != null) return pathSegment

        val host = uri.host?.toIntOrNull()
        if (host != null) return host

        return null
    }
}
