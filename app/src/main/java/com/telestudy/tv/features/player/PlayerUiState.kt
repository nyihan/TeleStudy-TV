package com.telestudy.tv.features.player

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    ENDED
}

enum class PlaybackStartMode {
    RESUME,
    START_OVER
}

sealed class ResumeDialogState {
    data class Prompt(
        val lessonTitle: String,
        val savedPositionMs: Long,
        val durationMs: Long
    ) : ResumeDialogState() {
        val watchedText: String
            get() = if (durationMs > 0) {
                "You watched ${PlayerUiState.formatTime(savedPositionMs)} of ${PlayerUiState.formatTime(durationMs)}."
            } else {
                "You watched ${PlayerUiState.formatTime(savedPositionMs)}."
            }
    }

    data class Completed(
        val lessonTitle: String
    ) : ResumeDialogState()
}

data class PlayerUiState(
    val mediaItem: PlayerMediaItem? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isControlsVisible: Boolean = true,
    val errorMessage: String? = null,
    val resumeDialogState: ResumeDialogState? = null
) {
    val isBuffering: Boolean
        get() = status == PlaybackStatus.BUFFERING

    val isError: Boolean
        get() = errorMessage != null

    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val currentPositionFormatted: String
        get() = formatTime(currentPositionMs)

    val durationFormatted: String
        get() = formatTime(durationMs)

    companion object {
        fun formatTime(timeMs: Long): String {
            val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

