package com.telestudy.tv.data.sync

import android.content.Context
import com.telestudy.tv.core.tdlib.TelegramClientManager
import com.telestudy.tv.data.local.dao.ChatDao
import com.telestudy.tv.data.local.dao.VideoDao
import com.telestudy.tv.data.local.entity.TelegramVideoEntity
import com.telestudy.tv.data.mapper.EntityMappers
import com.telestudy.tv.data.remote.TelegramRemoteDataSource
import com.telestudy.tv.data.thumbnail.TelegramThumbnailManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi
import timber.log.Timber

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Success(val newItemsCount: Int, val timestamp: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Coordinates automatic background synchronization:
 * - Listens for real-time TDLib updates (UpdateNewMessage)
 * - Performs safe delta syncs without duplicating history or missing videos
 * - Ensures every study group completes full history discovery at least once
 * - Triggers background thumbnail downloads
 * - Keeps Room database fresh without requiring manual sync buttons
 */
class SyncManager(
    private val context: Context,
    private val clientManager: TelegramClientManager,
    private val remoteDataSource: TelegramRemoteDataSource,
    private val chatDao: ChatDao,
    private val videoDao: VideoDao,
    private val thumbnailManager: TelegramThumbnailManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val syncMutex = Mutex()

    private val prefs by lazy {
        context.getSharedPreferences("telestudy_sync_prefs", Context.MODE_PRIVATE)
    }

    fun isHistoryExhausted(chatId: Long): Boolean =
        prefs.getBoolean("exhausted_$chatId", false)

    fun setHistoryExhausted(chatId: Long, exhausted: Boolean = true) {
        prefs.edit().putBoolean("exhausted_$chatId", exhausted).apply()
    }

    init {
        // Register live message listener for real-time video discovery
        clientManager.addUpdateListener { update ->
            if (update is TdApi.UpdateNewMessage) {
                onUpdateNewMessage(update.message)
            }
        }
    }

    private fun onUpdateNewMessage(message: TdApi.Message) {
        val videoEntity = EntityMappers.mapMessageToVideoEntity(message)
        if (videoEntity != null) {
            Timber.i("[SyncManager] Received new video message: ${message.id} in chat ${message.chatId}")
            scope.launch {
                try {
                    videoDao.upsertVideos(listOf(videoEntity))
                    videoEntity.thumbnailFileId?.let { fileId ->
                        if (fileId > 0) {
                            thumbnailManager.requestThumbnailDownload(fileId)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "[SyncManager] Failed to persist live video update")
                }
            }
        }
    }

    /**
     * Executes automatic background synchronization:
     * 1. Discovers all Telegram chats exhaustively and upserts to Room.
     * 2. Identifies active candidate chats and chats with videos in Room, running delta/exhaustive sync.
     * 3. Triggers thumbnail queue downloading.
     */
    suspend fun performAutoSync(): Result<Int> = syncMutex.withLock {
        Timber.i("[SyncManager] Starting auto-sync cycle...")
        _syncState.value = SyncState.Syncing("Checking for study lessons...")

        var totalNewVideos = 0

        try {
            // 1. Discover all chats
            val chatResult = remoteDataSource.loadAllChatsExhaustive()
            if (chatResult.isSuccess) {
                val chatPairs = chatResult.getOrThrow()
                val entities = chatPairs.map { (chat, isArchived) ->
                    EntityMappers.mapChatToEntity(chat, isArchived)
                }
                chatDao.upsertChats(entities)
                Timber.i("[SyncManager] Auto-sync updated ${entities.size} chats in Room.")
            } else {
                Timber.w(chatResult.exceptionOrNull(), "[SyncManager] Chat sync encountered an issue, proceeding with local chats...")
            }

            // 2. Sync ONLY educational study group chats.
            // These are small (≤50 pages), run forceFullSync for idempotency, and complete quickly.
            // Non-educational chats that accumulated videos in previous sessions are NOT re-scanned here —
            // they receive live new videos via the UpdateNewMessage listener registered in init{}.
            val educationalChatIds = chatDao.getEducationalStudyChatIds()

            Timber.i("[SyncManager] Auto-sync targeting ${educationalChatIds.size} educational study chats.")

            var processedChats = 0
            for (chatId in educationalChatIds) {
                processedChats++
                _syncState.value = SyncState.Syncing("Syncing lessons ($processedChats/${educationalChatIds.size})...")
                // Always forceFullSync=true for educational chats: idempotent, fast, complete.
                val newVideos = performDeltaSyncForChat(chatId, forceFullSync = true)
                totalNewVideos += newVideos
            }

            // 3. Queue pending thumbnail downloads
            thumbnailManager.downloadPendingThumbnails(100)

            _syncState.value = SyncState.Success(totalNewVideos)
            Timber.i("[SyncManager] Auto-sync finished successfully. Total new videos synced: $totalNewVideos")
            Result.success(totalNewVideos)
        } catch (e: Exception) {
            Timber.e(e, "[SyncManager] Auto-sync failed")
            _syncState.value = SyncState.Error(e.message ?: "Sync error")
            Result.failure(e)
        }
    }

    /**
     * Safe delta sync for a single chat.
     *
     * @param forceFullSync When true (used for educational chats), always run a full exhaustive
     *   sync regardless of the isHistoryExhausted flag. Since educational chats are small
     *   (≤50 pages), this is fast and upsertVideos is idempotent — guaranteeing complete data
     *   even if a previous run was killed mid-write.
     *   When false (used for large non-educational chats already in Room), uses the flag-based
     *   delta path to avoid re-scanning thousands of pages.
     */
    suspend fun performDeltaSyncForChat(chatId: Long, forceFullSync: Boolean = false): Int {
        val latestKnownMessageId = videoDao.getLatestMessageId(chatId) ?: 0L

        // Safety guard: if SharedPreferences says exhausted but Room has 0 videos,
        // the flag is stale (e.g. from a previous install or a killed process mid-write).
        if (!forceFullSync && isHistoryExhausted(chatId) && latestKnownMessageId == 0L) {
            Timber.w("[SyncManager] Chat $chatId flagged exhausted but has 0 videos in Room — resetting flag.")
            setHistoryExhausted(chatId, false)
        }

        // Full exhaustive sync when: forced (educational chats) OR flag not yet set
        if (forceFullSync || !isHistoryExhausted(chatId)) {
            Timber.i("[SyncManager] Chat $chatId running full exhaustive sync (forceFullSync=$forceFullSync)...")
            val result = remoteDataSource.loadChatVideosExhaustive(chatId)
            if (result.isSuccess) {
                val videos = result.getOrThrow()
                if (videos.isNotEmpty()) {
                    videoDao.upsertVideos(videos)
                    for (v in videos) {
                        v.thumbnailFileId?.let { fileId ->
                            if (fileId > 0) thumbnailManager.requestThumbnailDownload(fileId)
                        }
                    }
                }
                // Only persist exhausted flag for non-forced (large) chats
                if (!forceFullSync) {
                    setHistoryExhausted(chatId, true)
                }
                Timber.i("[SyncManager] Chat $chatId exhaustive sync completed (${videos.size} videos).")
                return videos.size
            }
            return 0
        }

        // Fast delta sync: fetch newest messages
        var fromMessageId = 0L
        var newVideosFound = 0
        var reachedOldBoundary = false

        while (!reachedOldBoundary) {
            val query = TdApi.GetChatHistory(chatId, fromMessageId, 0, 50, false)
            val result = clientManager.sendQuery(query)
            if (result.isFailure) break

            val messages = result.getOrThrow().messages
            if (messages.isEmpty()) break

            val newEntities = mutableListOf<TelegramVideoEntity>()
            for (msg in messages) {
                if (msg.id <= latestKnownMessageId) {
                    reachedOldBoundary = true
                    break
                }
                val entity = EntityMappers.mapMessageToVideoEntity(msg)
                if (entity != null) {
                    newEntities.add(entity)
                }
            }

            if (newEntities.isNotEmpty()) {
                videoDao.upsertVideos(newEntities)
                newVideosFound += newEntities.size
                for (v in newEntities) {
                    v.thumbnailFileId?.let { fileId ->
                        if (fileId > 0) thumbnailManager.requestThumbnailDownload(fileId)
                    }
                }
            }

            if (reachedOldBoundary || messages.size < 50) {
                break
            }

            fromMessageId = messages.last().id
        }

        if (newVideosFound > 0) {
            Timber.i("[SyncManager] Delta sync for chat $chatId found $newVideosFound new videos.")
        }
        return newVideosFound
    }
}
