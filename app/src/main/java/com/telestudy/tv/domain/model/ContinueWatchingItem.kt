package com.telestudy.tv.domain.model

/**
 * Domain item representing a partially watched video ready to be resumed.
 */
data class ContinueWatchingItem(
    val video: TelegramVideo,
    val subjectTitle: String,
    val progress: WatchProgress
)
