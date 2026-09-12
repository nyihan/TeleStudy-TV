package com.telestudy.tv.data.local.dao

import androidx.room.*
import com.telestudy.tv.data.local.entity.TelegramVideoEntity
import kotlinx.coroutines.flow.Flow

data class ChatVideoCount(
    val chatId: Long,
    val count: Int
)

/**
 * Data Access Object for Telegram video messages and video documents.
 */
@Dao
interface VideoDao {
    @Query("SELECT * FROM telegram_videos WHERE chatId = :chatId ORDER BY date DESC, messageId DESC")
    fun observeVideosForChat(chatId: Long): Flow<List<TelegramVideoEntity>>

    @Query("SELECT * FROM telegram_videos WHERE chatId = :chatId ORDER BY date DESC, messageId DESC")
    suspend fun getVideosForChat(chatId: Long): List<TelegramVideoEntity>

    @Query("SELECT COUNT(*) FROM telegram_videos WHERE chatId = :chatId")
    suspend fun getVideoCountForChat(chatId: Long): Int

    @Query("SELECT COUNT(DISTINCT messageId) FROM telegram_videos WHERE chatId = :chatId")
    suspend fun getDistinctMessageIdCount(chatId: Long): Int

    @Query("SELECT COUNT(*) FROM telegram_videos")
    suspend fun getTotalVideoCount(): Int

    @Query("SELECT * FROM telegram_videos WHERE messageId = :messageId LIMIT 1")
    suspend fun getVideoByMessageId(messageId: Long): TelegramVideoEntity?

    @Upsert
    suspend fun upsertVideos(videos: List<TelegramVideoEntity>): List<Long>

    @Upsert
    suspend fun upsertVideo(video: TelegramVideoEntity): Long

    @Query("SELECT * FROM telegram_videos WHERE chatId = :chatId ORDER BY date ASC, messageId ASC")
    fun observeVideosChronological(chatId: Long): Flow<List<TelegramVideoEntity>>

    @Query("SELECT * FROM telegram_videos WHERE chatId = :chatId ORDER BY date ASC, messageId ASC")
    suspend fun getVideosChronological(chatId: Long): List<TelegramVideoEntity>

    @Query("SELECT messageId FROM telegram_videos WHERE chatId = :chatId ORDER BY messageId DESC LIMIT 1")
    suspend fun getLatestMessageId(chatId: Long): Long?

    @Query("UPDATE telegram_videos SET thumbnailPath = :path WHERE messageId = :messageId")
    suspend fun updateThumbnailPath(messageId: Long, path: String): Int

    @Query("UPDATE telegram_videos SET thumbnailPath = :path WHERE thumbnailFileId = :fileId")
    suspend fun updateThumbnailPathByFileId(fileId: Int, path: String): Int

    @Query("SELECT * FROM telegram_videos WHERE thumbnailPath IS NULL AND thumbnailFileId IS NOT NULL LIMIT :limit")
    suspend fun getVideosNeedingThumbnails(limit: Int = 100): List<TelegramVideoEntity>

    @Query("SELECT * FROM telegram_videos WHERE thumbnailFileId = :fileId")
    suspend fun getVideosByThumbnailFileId(fileId: Int): List<TelegramVideoEntity>

    @Query("SELECT chatId, COUNT(*) as count FROM telegram_videos GROUP BY chatId")
    fun observeVideoCounts(): Flow<List<ChatVideoCount>>

    @Query("SELECT * FROM telegram_videos ORDER BY date DESC LIMIT :limit")
    fun observeRecentVideos(limit: Int = 20): Flow<List<TelegramVideoEntity>>

    @Query("""
        SELECT v.* FROM telegram_videos v
        LEFT JOIN telegram_chats c ON v.chatId = c.chatId
        WHERE v.fileName LIKE '%' || :query || '%'
           OR v.caption LIKE '%' || :query || '%'
           OR c.title LIKE '%' || :query || '%'
        ORDER BY v.date DESC
        LIMIT :limit
    """)
    fun searchVideos(query: String, limit: Int = 100): Flow<List<TelegramVideoEntity>>

    @Query("SELECT * FROM telegram_videos WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    suspend fun getVideo(chatId: Long, messageId: Long): TelegramVideoEntity?

    @Query("SELECT * FROM telegram_videos WHERE messageId IN (:messageIds)")
    suspend fun getVideosByMessageIds(messageIds: List<Long>): List<TelegramVideoEntity>

    @Query("DELETE FROM telegram_videos WHERE chatId = :chatId")
    suspend fun deleteAllVideosForChat(chatId: Long): Int

    @Query("DELETE FROM telegram_videos")
    suspend fun deleteAllVideos(): Int
}
