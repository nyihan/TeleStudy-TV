package com.telestudy.tv.data.local

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.telestudy.tv.data.local.dao.ChatDao
import com.telestudy.tv.data.local.dao.VideoDao
import com.telestudy.tv.data.local.entity.TelegramChatEntity
import com.telestudy.tv.data.local.entity.TelegramVideoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDaoTest {

    private lateinit var database: TeleStudyDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var videoDao: VideoDao

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, TeleStudyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = database.chatDao()
        videoDao = database.videoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- ChatDao Tests ---

    @Test
    fun testChatInsertAndRetrieve() = runBlocking {
        val chat = TelegramChatEntity(
            chatId = -100123456789L,
            title = "Grade 5 Mathematics",
            type = "Channel",
            memberCount = 120,
            isChannel = true
        )

        chatDao.upsertChat(chat)

        val retrieved = chatDao.getChatById(-100123456789L)
        assertNotNull(retrieved)
        assertEquals("Grade 5 Mathematics", retrieved?.title)
        assertEquals("Channel", retrieved?.type)
        assertEquals(1, chatDao.getChatCount())
    }

    @Test
    fun testDuplicateChatPrevention_upsertsWithoutDuplicates() = runBlocking {
        val chatV1 = TelegramChatEntity(
            chatId = -100111L,
            title = "Physics 101",
            type = "Supergroup",
            memberCount = 50
        )
        val chatV2 = TelegramChatEntity(
            chatId = -100111L,
            title = "Physics 101 - Advanced",
            type = "Supergroup",
            memberCount = 55
        )

        chatDao.upsertChat(chatV1)
        chatDao.upsertChat(chatV2) // Same primary key (chatId)

        val count = chatDao.getChatCount()
        assertEquals("Duplicate chat IDs must not create extra rows", 1, count)

        val retrieved = chatDao.getChatById(-100111L)
        assertEquals("Updated title must overwrite older record", "Physics 101 - Advanced", retrieved?.title)
        assertEquals(55, retrieved?.memberCount)
    }

    @Test
    fun testChatArchiveFilter() = runBlocking {
        val mainChat = TelegramChatEntity(chatId = 1L, title = "Main Chat", type = "Group", isArchived = false)
        val archivedChat = TelegramChatEntity(chatId = 2L, title = "Archived Study", type = "Channel", isArchived = true)

        chatDao.upsertChats(listOf(mainChat, archivedChat))

        val mainList = chatDao.observeMainChats().first()
        val archivedList = chatDao.observeArchivedChats().first()

        assertEquals(1, mainList.size)
        assertEquals("Main Chat", mainList[0].title)

        assertEquals(1, archivedList.size)
        assertEquals("Archived Study", archivedList[0].title)
    }

    // --- VideoDao Tests ---

    @Test
    fun testVideoInsertAndQueryForChat() = runBlocking {
        val chatId = -1003686260160L
        val video1 = TelegramVideoEntity(
            messageId = 1001L,
            chatId = chatId,
            fileId = 10,
            remoteFileId = "remote_10",
            fileName = "Lesson 1.mp4",
            durationSeconds = 600,
            width = 1920,
            height = 1080,
            fileSize = 50_000_000L,
            mimeType = "video/mp4",
            date = 1700000000L,
            caption = "Intro to Calculus"
        )
        val video2 = TelegramVideoEntity(
            messageId = 1002L,
            chatId = chatId,
            fileId = 11,
            remoteFileId = "remote_11",
            fileName = "Lesson 2.mp4",
            durationSeconds = 720,
            width = 1920,
            height = 1080,
            fileSize = 65_000_000L,
            mimeType = "video/mp4",
            date = 1700003600L,
            caption = "Derivatives"
        )

        videoDao.upsertVideos(listOf(video1, video2))

        val videosInDb = videoDao.getVideosForChat(chatId)
        assertEquals(2, videosInDb.size)
        // Ordered by date DESC -> Lesson 2 should be first
        assertEquals("Lesson 2.mp4", videosInDb[0].fileName)
        assertEquals("Lesson 1.mp4", videosInDb[1].fileName)

        assertEquals(2, videoDao.getVideoCountForChat(chatId))
        assertEquals(2, videoDao.getDistinctMessageIdCount(chatId))
    }

    @Test
    fun testDuplicateVideoPrevention_upsertsWithoutDuplicates() = runBlocking {
        val chatId = -100999L
        val initialVideo = TelegramVideoEntity(
            messageId = 500L,
            chatId = chatId,
            fileId = 20,
            remoteFileId = "remote_20",
            fileName = "Grammar 1.mp4",
            durationSeconds = 300,
            width = 1280,
            height = 720,
            fileSize = 15_000_000L,
            mimeType = "video/mp4",
            date = 1700000000L,
            caption = ""
        )
        val updatedVideo = TelegramVideoEntity(
            messageId = 500L, // Same message ID
            chatId = chatId,
            fileId = 20,
            remoteFileId = "remote_20",
            fileName = "Grammar 1 (Corrected).mp4",
            durationSeconds = 310,
            width = 1280,
            height = 720,
            fileSize = 15_500_000L,
            mimeType = "video/mp4",
            date = 1700000000L,
            caption = "Revised edition"
        )

        videoDao.upsertVideo(initialVideo)
        videoDao.upsertVideo(updatedVideo)

        assertEquals("Duplicate message ID must not create second entry", 1, videoDao.getVideoCountForChat(chatId))
        assertEquals(1, videoDao.getDistinctMessageIdCount(chatId))

        val fetched = videoDao.getVideoByMessageId(500L)
        assertEquals("Grammar 1 (Corrected).mp4", fetched?.fileName)
        assertEquals("Revised edition", fetched?.caption)
    }

    @Test
    fun testChatIsolation_videosBelongOnlyToTheirChat() = runBlocking {
        val chatA = -1001L
        val chatB = -1002L

        val videoA = TelegramVideoEntity(
            messageId = 1L, chatId = chatA, fileId = 1, remoteFileId = "r1",
            fileName = "Video A.mp4", durationSeconds = 100, width = 720, height = 480,
            fileSize = 1000L, mimeType = "video/mp4", date = 100L, caption = ""
        )
        val videoB = TelegramVideoEntity(
            messageId = 2L, chatId = chatB, fileId = 2, remoteFileId = "r2",
            fileName = "Video B.mp4", durationSeconds = 200, width = 720, height = 480,
            fileSize = 2000L, mimeType = "video/mp4", date = 200L, caption = ""
        )

        videoDao.upsertVideos(listOf(videoA, videoB))

        assertEquals(1, videoDao.getVideoCountForChat(chatA))
        assertEquals(1, videoDao.getVideoCountForChat(chatB))
        assertEquals(2, videoDao.getTotalVideoCount())
    }
}
