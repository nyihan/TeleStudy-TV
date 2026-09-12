package com.telestudy.tv.domain.model

/**
 * Domain representation of user playback progress for a specific video.
 */
data class WatchProgress(
    val chatId: Long,
    val messageId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val lastPlayedTimestamp: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val remainingDurationFormatted: String
        get() {
            val remainingSec = ((durationMs - positionMs) / 1000L).coerceAtLeast(0L)
            val minutes = remainingSec / 60L
            val seconds = remainingSec % 60L
            return if (minutes > 0) {
                "${minutes}m left"
            } else {
                "${seconds}s left"
            }
        }

    val positionFormatted: String
        get() {
            val totalSec = positionMs / 1000L
            val minutes = totalSec / 60L
            val seconds = totalSec % 60L
            return String.format("%02d:%02d", minutes, seconds)
        }
}
