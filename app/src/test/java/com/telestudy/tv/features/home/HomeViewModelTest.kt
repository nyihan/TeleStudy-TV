package com.telestudy.tv.features.home

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
}
