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
}
