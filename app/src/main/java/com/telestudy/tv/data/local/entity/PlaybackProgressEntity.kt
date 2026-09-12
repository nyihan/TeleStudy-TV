package com.telestudy.tv.data.local.entity

import androidx.room.Entity

/**
 * Room database entity storing minimal playback resume position and completion state.
 */
@Entity(
    tableName = "playback_progress",
    primaryKeys = ["chatId", "messageId"]
)
data class PlaybackProgressEntity(
    val chatId: Long,
    val messageId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAt: Long
)
