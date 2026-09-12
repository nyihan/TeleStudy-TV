package com.telestudy.tv.data.mapper

import com.telestudy.tv.data.local.entity.TelegramChatEntity
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class EntityMappersTest {

    @Test
    fun testMapChannelChatToEntity() {
        val chat = TdApi.Chat().apply {
            id = -100987654321L
            title = "Primary English Channel"
            type = TdApi.ChatTypeSupergroup().apply {
                isChannel = true
            }
            positions = arrayOf(
                TdApi.ChatPosition().apply {
                    order = 1000L
                    list = TdApi.ChatListMain()
                }
            )
        }

        val entity = EntityMappers.mapChatToEntity(chat, isArchived = false)

        assertEquals(-100987654321L, entity.chatId)
        assertEquals("Primary English Channel", entity.title)
        assertEquals("Channel", entity.type)
        assertTrue(entity.isChannel)
        assertFalse(entity.isArchived)
        assertEquals(1000L, entity.order)
    }

    @Test
    fun testMapGroupChatToEntity() {
        val chat = TdApi.Chat().apply {
            id = -500123L
            title = "Study Discussion Group"
            type = TdApi.ChatTypeBasicGroup()
        }

        val entity = EntityMappers.mapChatToEntity(chat, isArchived = true)

        assertEquals(-500123L, entity.chatId)
        assertEquals("Study Discussion Group", entity.title)
        assertEquals("Group", entity.type)
        assertFalse(entity.isChannel)
        assertTrue(entity.isArchived)
    }

    @Test
    fun testMapMessageVideoToEntity() {
        val message = TdApi.Message().apply {
            id = 456789L
            chatId = -100111222L
            date = 1710000000
            content = TdApi.MessageVideo().apply {
                video = TdApi.Video().apply {
                    video = TdApi.File().apply {
                        id = 42
                        size = 25_000_000L
                        remote = TdApi.RemoteFile().apply { id = "remote_42" }
                    }
                    duration = 480
                    width = 1920
                    height = 1080
                    fileName = "geometry_chapter_1.mp4"
                    mimeType = "video/mp4"
                }
                caption = TdApi.FormattedText().apply { text = "Geometry Chapter 1: Triangles" }
            }
        }

        val videoEntity = EntityMappers.mapMessageToVideoEntity(message)

        assertNotNull(videoEntity)
        assertEquals(456789L, videoEntity?.messageId)
        assertEquals(-100111222L, videoEntity?.chatId)
        assertEquals(42, videoEntity?.fileId)
        assertEquals("geometry_chapter_1.mp4", videoEntity?.fileName)
        assertEquals(480, videoEntity?.durationSeconds)
        assertEquals(25_000_000L, videoEntity?.fileSize)
        assertEquals("Geometry Chapter 1: Triangles", videoEntity?.caption)
        assertFalse(videoEntity?.isVideoDocument ?: true)
    }

    @Test
    fun testMapMessageDocumentVideoToEntity() {
        val message = TdApi.Message().apply {
            id = 789123L
            chatId = -100111222L
            date = 1710005000
            content = TdApi.MessageDocument().apply {
                document = TdApi.Document().apply {
                    document = TdApi.File().apply {
                        id = 99
                        size = 120_000_000L
                        remote = TdApi.RemoteFile().apply { id = "remote_99" }
                    }
                    fileName = "lecture_biology.mkv"
                    mimeType = "video/x-matroska"
                }
                caption = TdApi.FormattedText().apply { text = "Cell Biology Lecture" }
            }
        }

        val videoEntity = EntityMappers.mapMessageToVideoEntity(message)

        assertNotNull(videoEntity)
        assertEquals(789123L, videoEntity?.messageId)
        assertEquals("lecture_biology.mkv", videoEntity?.fileName)
        assertTrue(videoEntity?.isVideoDocument ?: false)
    }

    @Test
    fun testMapNonVideoDocumentReturnsNull() {
        val message = TdApi.Message().apply {
            id = 9999L
            chatId = -100111222L
            date = 1710009000
            content = TdApi.MessageDocument().apply {
                document = TdApi.Document().apply {
                    fileName = "syllabus.pdf"
                    mimeType = "application/pdf"
                }
            }
        }

        val videoEntity = EntityMappers.mapMessageToVideoEntity(message)
        assertNull("PDF document must not be treated as a video", videoEntity)
    }

    @Test
    fun testMapTextMessageReturnsNull() {
        val message = TdApi.Message().apply {
            id = 8888L
            chatId = -100111222L
            date = 1710008000
            content = TdApi.MessageText().apply {
                text = TdApi.FormattedText().apply { text = "Hello class!" }
            }
        }

        val videoEntity = EntityMappers.mapMessageToVideoEntity(message)
        assertNull("Text message must not be treated as a video", videoEntity)
    }

    @Test
    fun testLessonNumberExtraction() {
        assertEquals(33, EntityMappers.extractLessonNumber("Lesson 33_ Time ( Page131-134).mp4"))
        assertEquals(1, EntityMappers.extractLessonNumber("Lesson 1: Introduction"))
        assertEquals(2, EntityMappers.extractLessonNumber("Lesson 02 - Addition.mp4"))
        assertEquals(5, EntityMappers.extractLessonNumber("05 - Fractions.mp4"))
        assertEquals(12, EntityMappers.extractLessonNumber("Ep 12 - Advanced Geometry.mp4"))
        assertNull(EntityMappers.extractLessonNumber("Introduction to Algebra.mp4"))
    }

    @Test
    fun testLessonNaturalOrdering() {
        val lesson33 = com.telestudy.tv.domain.model.TelegramVideo(
            messageId = 104L, chatId = 1L, fileId = 4, remoteFileId = "r4",
            fileName = "Lesson 33_ Time ( Page131-134).mp4",
            durationSeconds = 100, width = 1280, height = 720, fileSize = 1000L,
            mimeType = "video/mp4", date = 1004L, caption = ""
        )
        val lesson2 = com.telestudy.tv.domain.model.TelegramVideo(
            messageId = 102L, chatId = 1L, fileId = 2, remoteFileId = "r2",
            fileName = "Lesson 2: Numbers.mp4",
            durationSeconds = 100, width = 1280, height = 720, fileSize = 1000L,
            mimeType = "video/mp4", date = 1002L, caption = ""
        )
        val lesson1 = com.telestudy.tv.domain.model.TelegramVideo(
            messageId = 101L, chatId = 1L, fileId = 1, remoteFileId = "r1",
            fileName = "Lesson 1: Introduction.mp4",
            durationSeconds = 100, width = 1280, height = 720, fileSize = 1000L,
            mimeType = "video/mp4", date = 1001L, caption = ""
        )
        val lesson10 = com.telestudy.tv.domain.model.TelegramVideo(
            messageId = 103L, chatId = 1L, fileId = 3, remoteFileId = "r3",
            fileName = "Lesson 10: Geometry.mp4",
            durationSeconds = 100, width = 1280, height = 720, fileSize = 1000L,
            mimeType = "video/mp4", date = 1003L, caption = ""
        )

        val unorganized = listOf(lesson33, lesson2, lesson10, lesson1)
        val sorted = unorganized.sortedWith(EntityMappers.LessonComparator)

        assertEquals("Lesson 1: Introduction.mp4", sorted[0].fileName)
        assertEquals("Lesson 2: Numbers.mp4", sorted[1].fileName)
        assertEquals("Lesson 10: Geometry.mp4", sorted[2].fileName)
        assertEquals("Lesson 33_ Time ( Page131-134).mp4", sorted[3].fileName)
    }

    @Test
    fun testThumbnailPathMapping() {
        val entity = com.telestudy.tv.data.local.entity.TelegramVideoEntity(
            messageId = 100L,
            chatId = -100L,
            fileId = 55,
            remoteFileId = "rem55",
            fileName = "lesson.mp4",
            durationSeconds = 60,
            width = 1280,
            height = 720,
            fileSize = 10000L,
            mimeType = "video/mp4",
            date = 123456L,
            caption = "Test",
            thumbnailPath = "/data/user/0/com.telestudy.tv/files/tdlib/files/thumb_55.jpg"
        )

        val domain = EntityMappers.mapEntityToDomain(entity)
        assertEquals("/data/user/0/com.telestudy.tv/files/tdlib/files/thumb_55.jpg", domain.thumbnailPath)
    }

    @Test
    fun testExtractUnitInfo() {
        assertEquals("Unit 1", EntityMappers.extractUnitInfo("Lesson 1 _ Unit 1 _ Noun (1).mp4"))
        assertEquals("Unit 2B", EntityMappers.extractUnitInfo("Primary English Unit 2B Review.mp4"))
        assertNull(EntityMappers.extractUnitInfo("Random Video.mp4"))
    }

    @Test
    fun testExtractPageInfo() {
        assertEquals("Page 131-134", EntityMappers.extractPageInfo("Lesson 33_ Time ( Page131-134).mp4"))
        assertEquals("Page 45", EntityMappers.extractPageInfo("Science pg. 45 exercises.mp4"))
        assertNull(EntityMappers.extractPageInfo("Lesson 1 Intro.mp4"))
    }

    @Test
    fun testCleanLessonTitle() {
        assertEquals("Lesson 33 Time ( Page131-134)", EntityMappers.cleanLessonTitle("Lesson 33_ Time ( Page131-134).mp4"))
        assertEquals("Lesson 1 Unit 1 Noun (1)", EntityMappers.cleanLessonTitle("Lesson_1___Unit_1___Noun_(1).mp4"))
    }

    @Test
    fun testExtractTopicTitle() {
        assertEquals("Time", EntityMappers.extractTopicTitle("Lesson 33_ Time ( Page131-134).mp4"))
        assertEquals("Introduction", EntityMappers.extractTopicTitle("Lesson 1: Introduction.mp4"))
        assertEquals("Fractions", EntityMappers.extractTopicTitle("05 - Fractions.mp4"))
        assertEquals("Lesson 12", EntityMappers.extractTopicTitle("Lesson 12.mp4"))
        assertEquals("Cell Biology", EntityMappers.extractTopicTitle("", "Cell Biology"))
        assertEquals("Cell Biology", EntityMappers.extractTopicTitle("video_12345.mp4", "Cell Biology"))
        assertEquals("lecture biology", EntityMappers.extractTopicTitle("lecture_biology.mkv"))
    }
}
