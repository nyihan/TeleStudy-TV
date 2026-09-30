package com.telestudy.tv.data.mapper

import com.telestudy.tv.data.local.entity.TelegramChatEntity
import com.telestudy.tv.data.local.entity.TelegramVideoEntity
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import org.drinkless.tdlib.TdApi

object EntityMappers {

    fun mapChatToEntity(chat: TdApi.Chat, isArchived: Boolean = false): TelegramChatEntity {
        var typeDesc = "Chat"
        var isChannel = false

        when (val chatType = chat.type) {
            is TdApi.ChatTypeSupergroup -> {
                isChannel = chatType.isChannel
                typeDesc = if (chatType.isChannel) "Channel" else "Supergroup"
            }
            is TdApi.ChatTypeBasicGroup -> {
                typeDesc = "Group"
                isChannel = false
            }
            is TdApi.ChatTypePrivate -> {
                typeDesc = "Private"
            }
            is TdApi.ChatTypeSecret -> {
                typeDesc = "Secret"
            }
            else -> {}
        }

        val primaryPositionOrder = chat.positions?.firstOrNull()?.order ?: 0L
        val photoId = chat.photo?.small?.id ?: 0

        return TelegramChatEntity(
            chatId = chat.id,
            title = chat.title.ifBlank { "Telegram Chat ${chat.id}" },
            type = typeDesc,
            photoFileId = photoId,
            lastMessageId = chat.lastMessage?.id ?: 0L,
            lastSyncTimestamp = System.currentTimeMillis(),
            isArchived = isArchived,
            order = primaryPositionOrder,
            isChannel = isChannel
        )
    }

    fun mapEntityToDomain(entity: TelegramChatEntity, videoCount: Int = 0): TelegramChat {
        return TelegramChat(
            id = entity.chatId,
            title = entity.title,
            typeDescription = entity.type,
            memberCount = entity.memberCount,
            photoFileId = entity.photoFileId,
            isArchived = entity.isArchived,
            isChannel = entity.isChannel,
            order = entity.order,
            videoCount = videoCount
        )
    }

    fun mapMessageToVideoEntity(message: TdApi.Message): TelegramVideoEntity? {
        val chatId = message.chatId
        val messageId = message.id
        val date = message.date.toLong()

        when (val content = message.content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                val minithumbnailData = video.minithumbnail?.data
                val thumbnailFileId = video.thumbnail?.file?.id
                val caption = content.caption?.text ?: ""

                return TelegramVideoEntity(
                    messageId = messageId,
                    chatId = chatId,
                    fileId = file.id,
                    remoteFileId = file.remote?.id ?: "",
                    fileName = video.fileName.ifBlank { "video_${messageId}.mp4" },
                    durationSeconds = video.duration,
                    width = video.width,
                    height = video.height,
                    fileSize = file.size,
                    mimeType = video.mimeType.ifBlank { "video/mp4" },
                    date = date,
                    caption = caption,
                    minithumbnailData = minithumbnailData,
                    thumbnailFileId = thumbnailFileId,
                    isVideoDocument = false
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val mime = doc.mimeType.lowercase()
                val fileName = doc.fileName.lowercase()

                val isVideo = mime.startsWith("video/") ||
                        fileName.endsWith(".mp4") ||
                        fileName.endsWith(".mkv") ||
                        fileName.endsWith(".mov") ||
                        fileName.endsWith(".webm") ||
                        fileName.endsWith(".avi")

                if (!isVideo) return null

                val file = doc.document
                val minithumbnailData = doc.minithumbnail?.data
                val thumbnailFileId = doc.thumbnail?.file?.id
                val caption = content.caption?.text ?: ""

                return TelegramVideoEntity(
                    messageId = messageId,
                    chatId = chatId,
                    fileId = file.id,
                    remoteFileId = file.remote?.id ?: "",
                    fileName = doc.fileName.ifBlank { "document_${messageId}.mp4" },
                    durationSeconds = 0,
                    width = 0,
                    height = 0,
                    fileSize = file.size,
                    mimeType = doc.mimeType.ifBlank { "video/mp4" },
                    date = date,
                    caption = caption,
                    minithumbnailData = minithumbnailData,
                    thumbnailFileId = thumbnailFileId,
                    isVideoDocument = true
                )
            }
            else -> return null
        }
    }

    fun mapEntityToDomain(entity: TelegramVideoEntity): TelegramVideo {
        return TelegramVideo(
            messageId = entity.messageId,
            chatId = entity.chatId,
            fileId = entity.fileId,
            remoteFileId = entity.remoteFileId,
            fileName = entity.fileName,
            durationSeconds = entity.durationSeconds,
            width = entity.width,
            height = entity.height,
            fileSize = entity.fileSize,
            mimeType = entity.mimeType,
            date = entity.date,
            caption = entity.caption,
            minithumbnailData = entity.minithumbnailData,
            thumbnailFileId = entity.thumbnailFileId,
            thumbnailPath = entity.thumbnailPath,
            isVideoDocument = entity.isVideoDocument
        )
    }

    private val LESSON_REGEX = Regex("""(?:lesson|ep|chapter|unit|lec|part)\s*[#_.-]?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER_REGEX = Regex("""^(\d+)\s*[-_.]""")

    /**
     * Extracts an integer lesson or episode number from a filename or caption.
     * E.g. "Lesson 33_ Time.mp4" -> 33, "01 - Overview.mp4" -> 1.
     */
    fun extractLessonNumber(text: String): Int? {
        if (text.isBlank()) return null
        val match = LESSON_REGEX.find(text)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        val leadingMatch = LEADING_NUMBER_REGEX.find(text.trim())
        if (leadingMatch != null) {
            return leadingMatch.groupValues[1].toIntOrNull()
        }
        return null
    }

    private val UNIT_REGEX = Regex("""\b(?:unit|u)\s*[#_.-]?\s*(\d+[a-zA-Z]?)""", RegexOption.IGNORE_CASE)
    private val PAGE_REGEX = Regex("""\b(?:page|pg|p)\s*[#_.:-]?\s*(\d+(?:\s*-\s*\d+)?)""", RegexOption.IGNORE_CASE)

    /**
     * Extracts Unit information, e.g. "Unit 1", "Unit 2B".
     */
    fun extractUnitInfo(text: String): String? {
        if (text.isBlank()) return null
        val match = UNIT_REGEX.find(text) ?: return null
        return "Unit ${match.groupValues[1]}"
    }

    /**
     * Extracts Page information, e.g. "Page 131-134", "Page 45".
     */
    fun extractPageInfo(text: String): String? {
        if (text.isBlank()) return null
        val match = PAGE_REGEX.find(text) ?: return null
        return "Page ${match.groupValues[1]}"
    }

    /**
     * Cleans up raw Telegram video filenames into readable lesson titles.
     */
    fun cleanLessonTitle(fileName: String, caption: String = ""): String {
        var title = fileName
            .removeSuffix(".mp4")
            .removeSuffix(".mkv")
            .removeSuffix(".mov")
            .removeSuffix(".webm")
            .removeSuffix(".avi")
            .replace("_", " ")
            .trim()

        if ((title.isBlank() || title.startsWith("video ") || title.startsWith("document ")) && caption.isNotBlank()) {
            title = caption.lineSequence().firstOrNull()?.trim() ?: title
        }

        // Collapse multiple spaces
        return title.replace(Regex("""\s+"""), " ")
    }

    /**
     * Strips leading lesson prefixes (e.g. "Lesson 33_", "Lesson 1: ", "05 - ") and trailing
     * parentheticals / page metadata (e.g. "( Page131-134)") to extract the pure educational topic title.
     * E.g. "Lesson 33_ Time ( Page131-134).mp4" -> "Time"
     */
    fun extractTopicTitle(fileName: String, caption: String = ""): String {
        val baseTitle = cleanLessonTitle(fileName, caption)

        // Strip trailing parentheticals, e.g. "( Page131-134)", "(1)", etc.
        val withoutParens = baseTitle.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()

        // Strip leading lesson/chapter/unit/episode number prefixes:
        // Examples: "Lesson 33", "Lesson 33:", "Lesson 33 -", "01 -", "Unit 1 -", "Ep 5:"
        val prefixRegex = Regex("""^(?:(?:lesson|ep|chapter|unit|lec|part)\s*[#_.-]?\s*\d+|\d+)\s*[-_:.]*\s*""", RegexOption.IGNORE_CASE)
        val candidate = withoutParens.replaceFirst(prefixRegex, "").trim()

        // If candidate is meaningful and not blank, return it
        if (candidate.isNotBlank()) {
            return candidate
        }

        // Fallback: if withoutParens is meaningful, return it; otherwise return baseTitle
        return if (withoutParens.isNotBlank()) withoutParens else baseTitle
    }

    /**
     * Reconstructs the canonical instructional sequence (Lesson 1 -> Lesson N) for a list of video messages.
     *
     * 1. Preserves unnumbered videos (e.g. Intro, Outro, Reviews) at their original relative anchor indices.
     * 2. Detects the natural sequence direction (descending N -> 1 vs ascending 1 -> N) via adjacent pair voting.
     * 3. Repairs misplaced/displaced outlier lessons caused by Telegram upload failures or retries using
     *    Longest Monotonic Subsequence (LMS) repositioning.
     * 4. Canonicalizes descending instructional series (N -> 1) into forward instructional viewing order (1 -> N).
     * 5. Non-destructive: Does NOT perform a naive global sortBy, preserving original relative positioning of non-numbered items.
     */
    fun toCanonicalLessonSequence(videos: List<TelegramVideo>): List<TelegramVideo> {
        if (videos.size <= 1) return videos

        // 1. Identify videos that have an extractable lesson number
        val numberedIndices = mutableListOf<Int>()
        val numberedItems = mutableListOf<Pair<TelegramVideo, Int>>()

        videos.forEachIndexed { index, video ->
            val num = extractLessonNumber(video.fileName) ?: extractLessonNumber(video.caption)
            if (num != null) {
                numberedIndices.add(index)
                numberedItems.add(video to num)
            }
        }

        // If 0 or 1 numbered videos, no sequence reordering is needed
        if (numberedItems.size <= 1) return videos

        // 2. Determine sequence orientation (descending vs ascending)
        var ascVotes = 0
        var descVotes = 0
        for (i in 0 until numberedItems.size - 1) {
            val diff = numberedItems[i + 1].second - numberedItems[i].second
            if (diff > 0) ascVotes++
            else if (diff < 0) descVotes++
        }
        val isDescending = if (descVotes != ascVotes) {
            descVotes > ascVotes
        } else {
            numberedItems.first().second > numberedItems.last().second
        }

        // 3. Compute Longest Monotonic Subsequence (LMS)
        val n = numberedItems.size
        val dp = IntArray(n) { 1 }
        val parent = IntArray(n) { -1 }

        for (i in 0 until n) {
            for (j in 0 until i) {
                val isMonotonic = if (isDescending) {
                    numberedItems[j].second >= numberedItems[i].second
                } else {
                    numberedItems[j].second <= numberedItems[i].second
                }
                if (isMonotonic && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1
                    parent[i] = j
                }
            }
        }

        var bestEnd = 0
        for (i in 1 until n) {
            if (dp[i] > dp[bestEnd]) {
                bestEnd = i
            }
        }

        val lmsIndices = mutableSetOf<Int>()
        var curr = bestEnd
        while (curr != -1) {
            lmsIndices.add(curr)
            curr = parent[curr]
        }

        // 4. Construct the repaired list of numbered videos
        // Start with the LMS elements in their relative order
        val repairedList = mutableListOf<Pair<TelegramVideo, Int>>()
        for (i in 0 until n) {
            if (i in lmsIndices) {
                repairedList.add(numberedItems[i])
            }
        }

        // Gather outliers (items not in the LMS)
        val outliers = mutableListOf<Pair<TelegramVideo, Int>>()
        for (i in 0 until n) {
            if (i !in lmsIndices) {
                outliers.add(numberedItems[i])
            }
        }

        // Reposition outliers into their proper logical positions
        if (isDescending) {
            outliers.sortByDescending { it.second }
            for (outlier in outliers) {
                val insertIndex = repairedList.indexOfFirst { it.second < outlier.second }
                if (insertIndex != -1) {
                    repairedList.add(insertIndex, outlier)
                } else {
                    repairedList.add(outlier)
                }
            }
        } else {
            outliers.sortBy { it.second }
            for (outlier in outliers) {
                val insertIndex = repairedList.indexOfFirst { it.second > outlier.second }
                if (insertIndex != -1) {
                    repairedList.add(insertIndex, outlier)
                } else {
                    repairedList.add(outlier)
                }
            }
        }

        // 5. Canonicalize to forward instructional order (1 -> N)
        if (isDescending) {
            repairedList.reverse()
        }

        // 6. Reintegrate into original video list preserving unnumbered video anchor positions
        val result = videos.toMutableList()
        for (k in numberedIndices.indices) {
            result[numberedIndices[k]] = repairedList[k].first
        }

        return result
    }

    /**
     * Natural comparator ordering lessons sequentially (Lesson 1 -> Lesson 36).
     * Falls back to chronological message order when no lesson number can be extracted.
     */
    val LessonComparator: Comparator<TelegramVideo> = Comparator { v1, v2 ->
        val num1 = extractLessonNumber(v1.fileName) ?: extractLessonNumber(v1.caption)
        val num2 = extractLessonNumber(v2.fileName) ?: extractLessonNumber(v2.caption)

        if (num1 != null && num2 != null) {
            val cmp = num1.compareTo(num2)
            if (cmp != 0) return@Comparator cmp
        } else if (num1 != null) {
            return@Comparator -1
        } else if (num2 != null) {
            return@Comparator 1
        }

        // Fallback: chronological by messageId ascending (earliest posted lesson first)
        v1.messageId.compareTo(v2.messageId)
    }
}
