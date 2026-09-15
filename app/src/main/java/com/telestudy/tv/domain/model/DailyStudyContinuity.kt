package com.telestudy.tv.domain.model

/**
 * Domain model representing the student's daily study continuity.
 *
 * @property lastWatched The most recent lesson opened or watched by the user (with progress).
 * @property upNext The subsequent sequential lesson (Lesson N+1) in the same subject/channel.
 * @property upNextSubjectTitle The title of the subject/channel containing the upNext video.
 */
data class DailyStudyContinuity(
    val lastWatched: ContinueWatchingItem? = null,
    val upNext: TelegramVideo? = null,
    val upNextSubjectTitle: String? = null
)
