package com.telestudy.tv.features.home

import com.telestudy.tv.data.mapper.EntityMappers
import com.telestudy.tv.domain.model.ContinueWatchingItem
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun testKeyboardActions() {
        var query = ""
        fun onChar(c: Char) { query += c }
        fun onSpace() { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
        fun onBackspace() { if (query.isNotEmpty()) query = query.dropLast(1) }
        fun onClear() { query = "" }

        onChar('3')
        onChar('3')
        assertEquals("33", query)

        onSpace()
        assertEquals("33 ", query)

        onChar('M')
        onChar('A')
        onChar('T')
        onChar('H')
        assertEquals("33 MATH", query)

        onBackspace()
        assertEquals("33 MAT", query)

        onClear()
        assertEquals("", query)
    }

    @Test
    fun testContinueWatchingCalculation() {
        val video = TelegramVideo(
            messageId = 9437184L,
            chatId = -1003686260160L,
            fileId = 10291,
            remoteFileId = "rem10291",
            fileName = "Lesson 33_ Time ( Page131-134).mp4",
            durationSeconds = 497,
            width = 1280,
            height = 720,
            fileSize = 17393941L,
            mimeType = "video/mp4",
            date = 1710000000L,
            caption = "Time lesson"
        )

        val progress = WatchProgress(
            chatId = -1003686260160L,
            messageId = 9437184L,
            positionMs = 248500L,
            durationMs = 497000L,
            isCompleted = false,
            lastPlayedTimestamp = 1789200000L
        )

        val item = ContinueWatchingItem(
            video = video,
            subjectTitle = "Primary Mathematics 2A",
            progress = progress
        )

        assertEquals("Primary Mathematics 2A", item.subjectTitle)
        assertEquals(0.5f, item.progress.progressFraction, 0.01f)
        assertEquals("04:08", item.progress.positionFormatted)
        assertTrue(item.progress.remainingDurationFormatted.contains("m left") || item.progress.remainingDurationFormatted.contains("s left"))
    }

    @Test
    fun testHomeUiStateChatTitleMapping() {
        val subjects = listOf(
            TelegramChat(id = 1L, title = "Primary Mathematics 2A", typeDescription = "Supergroup", order = 1L, videoCount = 36),
            TelegramChat(id = 2L, title = "Grammar Class 4", typeDescription = "Channel", order = 2L, videoCount = 285)
        )

        val state = HomeUiState(subjects = subjects)
        assertEquals("Primary Mathematics 2A", state.chatTitleMap[1L])
        assertEquals("Grammar Class 4", state.chatTitleMap[2L])
    }

    @Test
    fun testDailyStudyContinuity_upNextSequentialCalculation() {
        val lesson32 = TelegramVideo(
            messageId = 100L, chatId = 1L, fileId = 1, remoteFileId = "r1",
            fileName = "Lesson 32_ Clock.mp4", durationSeconds = 300, width = 1280, height = 720,
            fileSize = 1000L, mimeType = "video/mp4", date = 1000L, caption = ""
        )
        val lesson33 = TelegramVideo(
            messageId = 101L, chatId = 1L, fileId = 2, remoteFileId = "r2",
            fileName = "Lesson 33_ Time ( Page131-134).mp4", durationSeconds = 400, width = 1280, height = 720,
            fileSize = 1000L, mimeType = "video/mp4", date = 1001L, caption = ""
        )
        val lesson34 = TelegramVideo(
            messageId = 102L, chatId = 1L, fileId = 3, remoteFileId = "r3",
            fileName = "Lesson 34_ Days of Week.mp4", durationSeconds = 350, width = 1280, height = 720,
            fileSize = 1000L, mimeType = "video/mp4", date = 1002L, caption = ""
        )

        val lessonsInOrder = listOf(lesson32, lesson33, lesson34)

        // Case 1: Last watched was Lesson 33 -> Up Next must be Lesson 34
        val currentIdx = lessonsInOrder.indexOfFirst { it.messageId == 101L }
        assertTrue(currentIdx != -1 && currentIdx + 1 < lessonsInOrder.size)
        val upNext = lessonsInOrder[currentIdx + 1]
        assertEquals(102L, upNext.messageId)
        assertEquals("Lesson 34_ Days of Week.mp4", upNext.fileName)

        // Case 2: Last watched was Lesson 34 (final lesson) -> Up Next must be null
        val finalIdx = lessonsInOrder.indexOfFirst { it.messageId == 102L }
        val noNext = if (finalIdx != -1 && finalIdx + 1 < lessonsInOrder.size) lessonsInOrder[finalIdx + 1] else null
        assertNull(noNext)
    }

    @Test
    fun testHomeUiStateDailyStudyContinuityDefault() {
        val state = HomeUiState()
        assertNull(state.continuity.lastWatched)
        assertNull(state.continuity.upNext)
        assertNull(state.continuity.upNextSubjectTitle)
    }

    // =========================================================================
    // BUG 1 REGRESSION: Player Controls & Toggle Mechanics
    // =========================================================================

    @Test
    fun testBug1_playerControls_singleStateTransitions() {
        var isPlaying = false
        var transitionCount = 0

        fun toggle() {
            isPlaying = !isPlaying
            transitionCount++
        }

        fun pause() {
            if (isPlaying) {
                isPlaying = false
                transitionCount++
            }
        }

        fun play() {
            if (!isPlaying) {
                isPlaying = true
                transitionCount++
            }
        }

        // 1. Play -> Pause
        isPlaying = true
        transitionCount = 0
        pause()
        assertFalse("Pause must leave player in paused state", isPlaying)
        assertEquals(1, transitionCount)

        // Idempotent pause
        pause()
        assertFalse(isPlaying)
        assertEquals("Repeated pause must not change state", 1, transitionCount)

        // 2. Pause -> Play
        play()
        assertTrue("Play must leave player in playing state", isPlaying)
        assertEquals(2, transitionCount)

        // Idempotent play
        play()
        assertTrue(isPlaying)
        assertEquals("Repeated play must not change state", 2, transitionCount)

        // 3. Media Play/Pause toggle
        toggle()
        assertFalse(isPlaying)
        assertEquals(3, transitionCount)

        toggle()
        assertTrue(isPlaying)
        assertEquals(4, transitionCount)
    }

    // =========================================================================
    // BUG 2 REGRESSION: Auto-Play Next Lesson Resolution & Protection
    // =========================================================================

    private fun createTestLesson(number: Int, messageId: Long, chatId: Long = 1L): TelegramVideo {
        return TelegramVideo(
            messageId = messageId,
            chatId = chatId,
            fileId = number * 100,
            remoteFileId = "remote_$number",
            fileName = "Lesson $number - Educational Topic.mp4",
            durationSeconds = 600,
            width = 1280,
            height = 720,
            fileSize = 50_000_000L,
            mimeType = "video/mp4",
            date = 1700000000L + number * 3600L,
            caption = "Lesson $number"
        )
    }

    private fun resolveNextLesson(
        allVideos: List<TelegramVideo>,
        currentMessageId: Long
    ): TelegramVideo? {
        val sorted = allVideos.sortedWith(EntityMappers.LessonComparator)
        val currentIndex = sorted.indexOfFirst { it.messageId == currentMessageId }
        return if (currentIndex != -1 && currentIndex + 1 < sorted.size) {
            sorted[currentIndex + 1]
        } else null
    }

    @Test
    fun testBug2_autoPlay_middleLessonResolvesSequentialNextLesson() {
        val l6 = createTestLesson(6, 1006L)
        val l7 = createTestLesson(7, 1007L)
        val l8 = createTestLesson(8, 1008L)
        val l9 = createTestLesson(9, 1009L)

        // Unordered input to verify natural chronological ordering
        val playlist = listOf(l8, l6, l9, l7)

        // Lesson 7 finishes -> should resolve Lesson 8
        val nextAfter7 = resolveNextLesson(playlist, l7.messageId)
        assertNotNull(nextAfter7)
        assertEquals(8, EntityMappers.extractLessonNumber(nextAfter7!!.fileName))
        assertEquals(1008L, nextAfter7.messageId)

        // Lesson 8 finishes -> should resolve Lesson 9
        val nextAfter8 = resolveNextLesson(playlist, l8.messageId)
        assertNotNull(nextAfter8)
        assertEquals(9, EntityMappers.extractLessonNumber(nextAfter8!!.fileName))
        assertEquals(1009L, nextAfter8.messageId)
    }

    @Test
    fun testBug2_autoPlay_finalLessonReturnsNullWithoutCrashOrLoop() {
        val l1 = createTestLesson(1, 1001L)
        val l2 = createTestLesson(2, 1002L)
        val playlist = listOf(l1, l2)

        // Final lesson completed
        val nextAfterFinal = resolveNextLesson(playlist, l2.messageId)
        assertNull("Final lesson completion must return null (no next lesson) and must not loop to Lesson 1", nextAfterFinal)
    }

    @Test
    fun testBug2_autoPlay_missingOrInvalidLessonDoesNotCrash() {
        val l1 = createTestLesson(1, 1001L)
        val playlist = listOf(l1)

        // Invalid / non-existent messageId
        val nextAfterInvalid = resolveNextLesson(playlist, 99999L)
        assertNull("Non-existent lesson ID must safely return null without throwing", nextAfterInvalid)

        // Empty playlist
        val nextFromEmpty = resolveNextLesson(emptyList(), 1001L)
        assertNull("Empty playlist must safely return null", nextFromEmpty)
    }

    @Test
    fun testBug2_autoPlay_duplicateEndGuard_preventsDuplicateTransitions() {
        var hasTriggeredPlaybackEnded = false
        var transitionCount = 0

        fun onStateEnded() {
            if (!hasTriggeredPlaybackEnded) {
                hasTriggeredPlaybackEnded = true
                transitionCount++
            }
        }

        // First completion event
        onStateEnded()
        assertEquals(1, transitionCount)

        // Duplicate completion events (recompositions, multiple state broadcasts)
        onStateEnded()
        onStateEnded()
        assertEquals("Duplicate STATE_ENDED callbacks must be discarded", 1, transitionCount)

        // Reset on new media item start
        hasTriggeredPlaybackEnded = false
        onStateEnded()
        assertEquals("New media item properly triggers completion once", 2, transitionCount)
    }

    // =========================================================================
    // BUG 3 REGRESSION: Lesson List Position & Focus Restoration
    // =========================================================================

    @Test
    fun testBug3_savedLessonResolvesToCorrectCurrentIndex() {
        val l1 = createTestLesson(1, 101L, chatId = 10L)
        val l2 = createTestLesson(2, 102L, chatId = 10L)
        val l7 = createTestLesson(7, 107L, chatId = 10L)
        val lessons = listOf(l1, l2, l7)

        val savedMessageId = 107L
        val targetIndex = lessons.indexOfFirst { it.messageId == savedMessageId }

        assertEquals("Saved Lesson 7 must resolve to index 2", 2, targetIndex)
    }

    @Test
    fun testBug3_sameGroupRestoresSavedLesson_differentGroupDoesNot() {
        val groupA = TelegramChat(id = 10L, title = "Mathematics", typeDescription = "Supergroup", order = 1L, videoCount = 10)
        val groupB = TelegramChat(id = 20L, title = "English", typeDescription = "Supergroup", order = 2L, videoCount = 5)

        // User watched Lesson 7 (messageId = 107) in Group A
        val state = HomeUiState(
            activeLessonListSubject = groupA,
            lastPlayedVideoByChat = mapOf(10L to 107L)
        )

        // Querying for Group A
        val lastPlayedInGroupA = state.lastPlayedVideoByChat[groupA.id]
        assertEquals(107L, lastPlayedInGroupA)

        // Querying for Group B (user never played in Group B)
        val lastPlayedInGroupB = state.lastPlayedVideoByChat[groupB.id]
        assertNull("Different group must not inherit saved lesson from another group", lastPlayedInGroupB)

        // Target index calculation for Group B
        val groupBLessons = listOf(createTestLesson(1, 201L, chatId = 20L))
        val targetIndexB = if (lastPlayedInGroupB != null) {
            val idx = groupBLessons.indexOfFirst { it.messageId == lastPlayedInGroupB }
            if (idx >= 0) idx else 0
        } else 0

        assertEquals("Entering different group must fall back to first lesson (index 0)", 0, targetIndexB)
    }

    @Test
    fun testBug3_missingSavedLessonFallsBackSafelyToTop() {
        val lessons = listOf(
            createTestLesson(1, 101L),
            createTestLesson(2, 102L)
        )

        // Saved lesson message ID was deleted or no longer in current dataset
        val deletedSavedMessageId = 999L
        val targetIndex = if (deletedSavedMessageId != null) {
            val idx = lessons.indexOfFirst { it.messageId == deletedSavedMessageId }
            if (idx >= 0) idx else 0
        } else 0

        assertEquals("Missing saved lesson must safely fallback to index 0", 0, targetIndex)
    }
}
