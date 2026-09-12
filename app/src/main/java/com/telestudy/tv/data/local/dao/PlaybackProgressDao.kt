package com.telestudy.tv.data.local.dao

import androidx.room.*
import com.telestudy.tv.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Upsert
    suspend fun saveProgress(progress: PlaybackProgressEntity): Long

    @Query("SELECT * FROM playback_progress WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    suspend fun getProgress(chatId: Long, messageId: Long): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE chatId = :chatId AND messageId = :messageId LIMIT 1")
    fun observeProgress(chatId: Long, messageId: Long): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE isCompleted = 0 AND positionMs > 2000 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeContinueWatching(limit: Int = 20): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress")
    fun observeAllProgress(): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE chatId = :chatId AND messageId = :messageId")
    suspend fun clearProgress(chatId: Long, messageId: Long): Int
}
