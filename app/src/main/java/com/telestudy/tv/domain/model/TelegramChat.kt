package com.telestudy.tv.domain.model

/**
 * Domain model representing an educational Telegram chat, group, or channel.
 */
data class TelegramChat(
    val id: Long,
    val title: String,
    val typeDescription: String,
    val memberCount: Int = 0,
    val photoFileId: Int = 0,
    val isArchived: Boolean = false,
    val isChannel: Boolean = false,
    val order: Long = 0L,
    val videoCount: Int = 0
)
