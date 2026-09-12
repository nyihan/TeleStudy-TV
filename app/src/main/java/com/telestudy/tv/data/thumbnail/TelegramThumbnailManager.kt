package com.telestudy.tv.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.telestudy.tv.core.tdlib.TelegramClientManager
import com.telestudy.tv.data.local.dao.VideoDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the progressive two-tier thumbnail pipeline for Telegram videos:
 * 1. Instant bundled minithumbnail (blurred low-res bitmap from TDLib payload).
 * 2. Asynchronous high-resolution thumbnail downloading via TDLib DownloadFile, cached directly to Room.
 */
class TelegramThumbnailManager(
    private val clientManager: TelegramClientManager,
    private val videoDao: VideoDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val requestedFileIds = ConcurrentHashMap.newKeySet<Int>()

    init {
        // Register listener for TDLib file update events
        clientManager.addUpdateListener { update ->
            if (update is TdApi.UpdateFile) {
                onUpdateFile(update.file)
            }
        }
    }

    /**
     * Handles file progress/completion events from TDLib.
     * When a thumbnail completes downloading, persists the absolute local path to Room.
     */
    private fun onUpdateFile(file: TdApi.File) {
        val local = file.local
        if (local != null && local.isDownloadingCompleted && !local.path.isNullOrBlank()) {
            val fileId = file.id
            val path = local.path
            Timber.d("[ThumbnailManager] Thumbnail file $fileId downloaded: $path")
            scope.launch {
                try {
                    videoDao.updateThumbnailPathByFileId(fileId, path)
                    requestedFileIds.remove(fileId)
                } catch (e: Exception) {
                    Timber.w(e, "[ThumbnailManager] Failed to update thumbnail path for file $fileId")
                }
            }
        }
    }

    /**
     * Requests TDLib to download a high-resolution thumbnail file.
     * Uses priority 16 (background image priority).
     */
    fun requestThumbnailDownload(thumbnailFileId: Int) {
        if (thumbnailFileId <= 0 || !requestedFileIds.add(thumbnailFileId)) {
            return
        }

        val client = clientManager.getClient()
        if (client == null) {
            requestedFileIds.remove(thumbnailFileId)
            return
        }

        Timber.d("[ThumbnailManager] Requesting download for thumbnail file $thumbnailFileId...")
        client.send(
            TdApi.DownloadFile(
                thumbnailFileId,
                16,    // Priority (1-32)
                0,     // Offset
                0,     // Limit
                false  // Synchronous
            ),
            { obj ->
                if (obj is TdApi.File) {
                    onUpdateFile(obj)
                } else if (obj is TdApi.Error) {
                    Timber.w("[ThumbnailManager] DownloadFile error for $thumbnailFileId: [${obj.code}] ${obj.message}")
                    requestedFileIds.remove(thumbnailFileId)
                }
            },
            { ex ->
                Timber.w(ex, "[ThumbnailManager] DownloadFile exception for $thumbnailFileId")
                requestedFileIds.remove(thumbnailFileId)
            }
        )
    }

    /**
     * Scans Room for videos that have a thumbnailFileId but no local thumbnailPath yet,
     * and queues them for downloading.
     */
    suspend fun downloadPendingThumbnails(limit: Int = 100) {
        try {
            val videos = videoDao.getVideosNeedingThumbnails(limit)
            Timber.i("[ThumbnailManager] Found ${videos.size} videos needing thumbnail downloads.")
            for (video in videos) {
                val fileId = video.thumbnailFileId
                if (fileId != null && fileId > 0) {
                    requestThumbnailDownload(fileId)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[ThumbnailManager] Error queuing pending thumbnails")
        }
    }

    companion object {
        /**
         * Decodes raw minithumbnail byte array into an Android [Bitmap].
         * Safe against malformed data. Returns null on decode failure.
         */
        fun decodeMinithumbnail(data: ByteArray?): Bitmap? {
            if (data == null || data.isEmpty()) return null
            return try {
                BitmapFactory.decodeByteArray(data, 0, data.size)
            } catch (e: Exception) {
                Timber.w(e, "[ThumbnailManager] Error decoding minithumbnail data")
                null
            }
        }
    }
}
