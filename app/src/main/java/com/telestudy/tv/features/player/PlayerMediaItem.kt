package com.telestudy.tv.features.player

/**
 * Lightweight domain model passed to the Player screen representing a video lesson.
 */
data class PlayerMediaItem(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val fileName: String,
    val durationSeconds: Int,
    val fileSize: Long,
    val thumbnailPath: String? = null,
    val width: Int = 0,
    val height: Int = 0
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
