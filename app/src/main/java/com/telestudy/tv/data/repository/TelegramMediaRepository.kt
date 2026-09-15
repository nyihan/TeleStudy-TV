package com.telestudy.tv.data.repository

import com.telestudy.tv.data.local.dao.ChatDao
import com.telestudy.tv.data.local.dao.PlaybackProgressDao
import com.telestudy.tv.data.local.dao.VideoDao
import com.telestudy.tv.data.mapper.EntityMappers
import com.telestudy.tv.data.remote.TelegramRemoteDataSource
import com.telestudy.tv.domain.model.ContinueWatchingItem
import com.telestudy.tv.domain.model.DailyStudyContinuity
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Repository coordinating Telegram remote queries with local Room persistence.
 * Completely isolates the UI/presentation layer from direct TDLib interactions.
 */
class TelegramMediaRepository(
    private val remoteDataSource: TelegramRemoteDataSource,
    private val chatDao: ChatDao,
    private val videoDao: VideoDao,
    private val playbackProgressDao: PlaybackProgressDao? = null
) {

    /**
     * Observes all locally stored Telegram chats, ordered by importance and title.
     */
    fun observeChats(): Flow<List<TelegramChat>> {
        return chatDao.observeChats().map { entities ->
            entities.map { entity ->
                // Map to domain model
                EntityMappers.mapEntityToDomain(entity)
            }
        }
    }

    /**
     * Observes subjects dynamically ranked by video content (chats with videos first).
     * Does not exclude non-study chats from the underlying dataset.
     */
    fun observeSubjectsRanked(): Flow<List<TelegramChat>> {
        return combine(chatDao.observeSubjectsRanked(), videoDao.observeVideoCounts()) { chats, counts ->
            val countMap = counts.associate { it.chatId to it.count }
            chats.map { entity ->
                EntityMappers.mapEntityToDomain(entity, countMap[entity.chatId] ?: 0)
            }
        }
    }

    /**
     * Observes subjects that have at least 1 video locally in Room.
     */
    fun observeSubjectsWithVideos(): Flow<List<TelegramChat>> {
        return combine(chatDao.observeSubjectsWithVideos(), videoDao.observeVideoCounts()) { chats, counts ->
            val countMap = counts.associate { it.chatId to it.count }
            chats.map { entity ->
                EntityMappers.mapEntityToDomain(entity, countMap[entity.chatId] ?: 0)
            }
        }
    }

    /**
     * Observes video lessons for a specific chat from the local database in natural lesson order.
     * Orders lessons sequentially (e.g. Lesson 1 -> Lesson 36).
     */
    fun observeVideos(chatId: Long): Flow<List<TelegramVideo>> {
        return videoDao.observeVideosChronological(chatId).map { entities ->
            entities.map { EntityMappers.mapEntityToDomain(it) }
                .sortedWith(EntityMappers.LessonComparator)
        }
    }

    /**
     * Observes the most recently posted videos across all chats.
     */
    fun observeRecentVideos(limit: Int = 20): Flow<List<TelegramVideo>> {
        return videoDao.observeRecentVideos(limit).map { entities ->
            entities.map { EntityMappers.mapEntityToDomain(it) }
        }
    }

    /**
     * Local Room-powered search across video filenames, captions, and subject titles.
     * Orders matches naturally.
     */
    fun searchVideos(query: String, limit: Int = 100): Flow<List<TelegramVideo>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(emptyList())
        return videoDao.searchVideos(trimmed, limit).map { entities ->
            entities.map { EntityMappers.mapEntityToDomain(it) }
        }
    }

    /**
     * Observes in-progress video lessons for Continue Watching shelf.
     */
    fun observeContinueWatching(limit: Int = 20): Flow<List<ContinueWatchingItem>> {
        val dao = playbackProgressDao ?: return flowOf(emptyList())
        return dao.observeContinueWatching(limit).map { progressList ->
            if (progressList.isEmpty()) return@map emptyList<ContinueWatchingItem>()
            val items = mutableListOf<ContinueWatchingItem>()
            for (p in progressList) {
                val videoEntity = videoDao.getVideo(p.chatId, p.messageId)
                if (videoEntity != null) {
                    val chatEntity = chatDao.getChatById(p.chatId)
                    val chatTitle = chatEntity?.title ?: "Telegram Chat"
                    val video = EntityMappers.mapEntityToDomain(videoEntity)
                    val progress = WatchProgress(
                        chatId = p.chatId,
                        messageId = p.messageId,
                        positionMs = p.positionMs,
                        durationMs = p.durationMs,
                        isCompleted = p.isCompleted,
                        lastPlayedTimestamp = p.updatedAt
                    )
                    items.add(ContinueWatchingItem(video, chatTitle, progress))
                }
            }
            items
        }
    }

    /**
     * Observes the daily study continuity:
     * 1. The most recent lesson opened/watched (in-progress or completed) -> "မနေ့က ဘာဖွင့်ခဲ့လဲ"
     * 2. The subsequent sequential lesson in the same study group -> "ဖွင့်ခဲ့တဲ့ သင်ခန်းစာရဲ့ နောက်တစ်ခု"
     */
    fun observeDailyStudyContinuity(): Flow<DailyStudyContinuity> {
        val dao = playbackProgressDao ?: return flowOf(DailyStudyContinuity())
        return dao.observeRecentlyWatched(limit = 1).map { progressList ->
            if (progressList.isEmpty()) return@map DailyStudyContinuity()

            val p = progressList.first()
            val videoEntity = videoDao.getVideo(p.chatId, p.messageId)
            val chatEntity = chatDao.getChatById(p.chatId)
            val chatTitle = chatEntity?.title ?: "Telegram Chat"

            val lastWatchedItem = if (videoEntity != null) {
                val video = EntityMappers.mapEntityToDomain(videoEntity)
                val progress = WatchProgress(
                    chatId = p.chatId,
                    messageId = p.messageId,
                    positionMs = p.positionMs,
                    durationMs = p.durationMs,
                    isCompleted = p.isCompleted,
                    lastPlayedTimestamp = p.updatedAt
                )
                ContinueWatchingItem(video, chatTitle, progress)
            } else null

            // Determine subsequent sequential lesson in the same chat
            val allVideosInChat = videoDao.getVideosChronological(p.chatId)
                .map { EntityMappers.mapEntityToDomain(it) }
                .sortedWith(EntityMappers.LessonComparator)

            val currentIndex = allVideosInChat.indexOfFirst { it.messageId == p.messageId }
            val upNextVideo = if (currentIndex != -1 && currentIndex + 1 < allVideosInChat.size) {
                allVideosInChat[currentIndex + 1]
            } else null

            DailyStudyContinuity(
                lastWatched = lastWatchedItem,
                upNext = upNextVideo,
                upNextSubjectTitle = chatTitle
            )
        }
    }

    /**
     * Observes all playback progress mapped by composite key (chatId, messageId).
     */
    fun observeAllPlaybackProgress(): Flow<Map<Pair<Long, Long>, WatchProgress>> {
        val dao = playbackProgressDao ?: return flowOf(emptyMap())
        return dao.observeAllProgress().map { list ->
            list.associate { p ->
                Pair(p.chatId, p.messageId) to WatchProgress(
                    chatId = p.chatId,
                    messageId = p.messageId,
                    positionMs = p.positionMs,
                    durationMs = p.durationMs,
                    isCompleted = p.isCompleted,
                    lastPlayedTimestamp = p.updatedAt
                )
            }
        }
    }

    /**
     * Synchronizes all accessible Telegram groups and channels.
     * Uses exhaustive pagination to guarantee no missing channels.
     */
    suspend fun syncChats(): Result<Int> {
        Timber.i("[Repository] Triggering exhaustive chat synchronization...")
        val remoteResult = remoteDataSource.loadAllChatsExhaustive()

        return if (remoteResult.isSuccess) {
            val chatPairs = remoteResult.getOrThrow()
            val entities = chatPairs.map { (chat, isArchived) ->
                EntityMappers.mapChatToEntity(chat, isArchived)
            }

            chatDao.upsertChats(entities)
            val totalInDb = chatDao.getChatCount()
            Timber.i("[Repository] Upserted ${entities.size} chats into Room. Total in Room: $totalInDb")
            Result.success(totalInDb)
        } else {
            val error = remoteResult.exceptionOrNull() ?: Exception("Failed to sync chats")
            Timber.e(error, "[Repository] Chat sync failed")
            Result.failure(error)
        }
    }

    /**
     * Synchronizes video messages for a specific chat/channel to completion.
     * Traverses the full message history and upserts each batch to Room.
     */
    suspend fun syncChatVideos(
        chatId: Long,
        onProgress: (pagesLoaded: Int, totalVideosFound: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        Timber.i("[Repository] Triggering exhaustive video synchronization for chat $chatId...")
        val remoteResult = remoteDataSource.loadChatVideosExhaustive(chatId, onProgress = onProgress)

        return if (remoteResult.isSuccess) {
            val videos = remoteResult.getOrThrow()
            if (videos.isNotEmpty()) {
                videoDao.upsertVideos(videos)
            }
            val totalForChat = videoDao.getVideoCountForChat(chatId)
            val distinctMessageIds = videoDao.getDistinctMessageIdCount(chatId)
            Timber.i("[Repository] Sync complete for chat $chatId: $totalForChat videos in Room (distinct IDs: $distinctMessageIds).")
            Result.success(totalForChat)
        } else {
            val error = remoteResult.exceptionOrNull() ?: Exception("Failed to sync videos")
            Timber.e(error, "[Repository] Video sync failed for chat $chatId")
            Result.failure(error)
        }
    }

    suspend fun getChatCount(): Int = chatDao.getChatCount()

    suspend fun getVideoCountForChat(chatId: Long): Int = videoDao.getVideoCountForChat(chatId)

    suspend fun getDistinctMessageIdCount(chatId: Long): Int = videoDao.getDistinctMessageIdCount(chatId)
}
