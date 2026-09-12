package com.telestudy.tv.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity representing a discovered Telegram chat, group, or channel.
 * Stable Telegram chat ID is used as the primary key to prevent duplicates.
 */
@Entity(
    tableName = "telegram_chats",
    indices = [
        Index(value = ["order"]),
        Index(value = ["type"]),
        Index(value = ["isArchived"])
    ]
)
data class TelegramChatEntity(
    @PrimaryKey
    val chatId: Long,
    val title: String,
    val type: String, // "channel", "supergroup", "basic_group"
    val memberCount: Int = 0,
    val photoFileId: Int = 0,
    val lastMessageId: Long = 0L,
    val lastSyncTimestamp: Long = 0L,
    val isArchived: Boolean = false,
    val order: Long = 0L,
    val isChannel: Boolean = false
)
