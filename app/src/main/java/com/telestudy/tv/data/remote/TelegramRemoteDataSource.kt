package com.telestudy.tv.data.remote

import com.telestudy.tv.core.tdlib.TdlibException
import com.telestudy.tv.core.tdlib.TelegramClientManager
import com.telestudy.tv.data.local.entity.TelegramVideoEntity
import com.telestudy.tv.data.mapper.EntityMappers
import kotlinx.coroutines.delay
import org.drinkless.tdlib.TdApi
import timber.log.Timber

/**
 * Remote data source interacting with TDLib for chat discovery and message history pagination.
 *
 * Implements exhaustive pagination:
 * 1. Chat Discovery: Traverses ChatListMain and ChatListArchive until TDLib emits code 404 (CHAT_LIST_EXHAUSTED).
 * 2. Video Discovery: Traverses GetChatHistory until messages list is empty (END_OF_HISTORY).
 */
class TelegramRemoteDataSource(
    private val clientManager: TelegramClientManager
) {

    /**
     * Discovers all chats from Telegram without an arbitrary limit.
     * Iteratively loads ChatListMain and ChatListArchive until exhaustion.
     */
    suspend fun loadAllChatsExhaustive(): Result<List<Pair<TdApi.Chat, Boolean>>> {
        Timber.i("[ChatDiscovery] Starting exhaustive chat discovery...")

        val discoveredChatPairs = mutableListOf<Pair<TdApi.Chat, Boolean>>()
        val seenChatIds = mutableSetOf<Long>()

        // 1. Load Main Chat List to exhaustion
        Timber.i("[ChatDiscovery] Loading ChatListMain to exhaustion...")
        var mainPages = 0
        var mainExhausted = false

        while (!mainExhausted) {
            mainPages++
            val result = clientManager.sendQuery(TdApi.LoadChats(TdApi.ChatListMain(), 100))
            if (result.isSuccess) {
                Timber.d("[ChatDiscovery] Main page $mainPages loaded successfully.")
                delay(50) // Yield to allow update dispatching
            } else {
                val ex = result.exceptionOrNull()
                if (ex is TdlibException && (ex.code == 404 || ex.message.contains("exhausted", ignoreCase = true))) {
                    Timber.i("[ChatDiscovery] ChatListMain exhausted after $mainPages iterations.")
                    mainExhausted = true
                } else {
                    Timber.w(ex, "[ChatDiscovery] Error loading ChatListMain on iteration $mainPages")
                    // If error is not 404, we stop main loop
                    mainExhausted = true
                }
            }
        }

        // 2. Load Archive Chat List to exhaustion
        Timber.i("[ChatDiscovery] Loading ChatListArchive to exhaustion...")
        var archivePages = 0
        var archiveExhausted = false

        while (!archiveExhausted) {
            archivePages++
            val result = clientManager.sendQuery(TdApi.LoadChats(TdApi.ChatListArchive(), 100))
            if (result.isSuccess) {
                Timber.d("[ChatDiscovery] Archive page $archivePages loaded successfully.")
                delay(50)
            } else {
                val ex = result.exceptionOrNull()
                if (ex is TdlibException && (ex.code == 404 || ex.message.contains("exhausted", ignoreCase = true))) {
                    Timber.i("[ChatDiscovery] ChatListArchive exhausted after $archivePages iterations.")
                    archiveExhausted = true
                } else {
                    Timber.w(ex, "[ChatDiscovery] Error loading ChatListArchive on iteration $archivePages")
                    archiveExhausted = true
                }
            }
        }

        // 3. Collect and verify all known chats
        val knownMap = clientManager.knownChats
        Timber.i("[ChatDiscovery] Known chats in client cache: ${knownMap.size}")

        for ((chatId, chat) in knownMap) {
            if (seenChatIds.add(chatId)) {
                val isArchived = chat.positions?.any { it.list is TdApi.ChatListArchive } ?: false
                discoveredChatPairs.add(Pair(chat, isArchived))
            }
        }

        Timber.i("[ChatDiscovery] Exhaustive chat discovery complete. Total distinct chats: ${discoveredChatPairs.size}")
        return Result.success(discoveredChatPairs)
    }

    /**
     * Paginates the complete message history of a selected Telegram chat to discover all video messages.
     * Continues through history until messages list is empty.
     *
     * @param chatId Telegram chat ID
     * @param onProgress Callback receiving (pagesProcessed, videosFoundCount)
     */
    suspend fun loadChatVideosExhaustive(
        chatId: Long,
        maxPages: Int = 500,
        onProgress: (pagesProcessed: Int, videosFoundCount: Int) -> Unit = { _, _ -> }
    ): Result<List<TelegramVideoEntity>> {
        Timber.i("[VideoDiscovery] Starting exhaustive video pagination for chat $chatId (maxPages=$maxPages)...")

        val videos = mutableListOf<TelegramVideoEntity>()
        val seenMessageIds = mutableSetOf<Long>()
        var fromMessageId = 0L
        var pageIndex = 0
        var isHistoryExhausted = false
        var retryCount = 0

        while (!isHistoryExhausted) {
            pageIndex++

            // Safety cap: don't let a single chat block the sync loop indefinitely
            if (pageIndex > maxPages) {
                Timber.w("[VideoDiscovery] Page cap ($maxPages) reached for chat $chatId after ${videos.size} videos. Stopping sync.")
                break
            }

            Timber.i("[VideoDiscovery] Requesting page $pageIndex for chat $chatId fromMessageId=$fromMessageId...")

            val query = TdApi.GetChatHistory(
                chatId,
                fromMessageId,
                0,    // offset
                100,  // limit
                false // onlyLocal
            )

            val result = clientManager.sendQuery(query)

            if (result.isSuccess) {
                retryCount = 0
                val messagesObj = result.getOrThrow()
                val messageList = messagesObj.messages

                if (messageList.isEmpty()) {
                    Timber.i("[VideoDiscovery] END_OF_HISTORY reached for chat $chatId at page $pageIndex. Total messages traversed.")
                    isHistoryExhausted = true
                    break
                }

                Timber.d("[VideoDiscovery] Page $pageIndex returned ${messageList.size} messages.")

                var newVideosThisPage = 0
                for (msg in messageList) {
                    if (seenMessageIds.add(msg.id)) {
                        val videoEntity = EntityMappers.mapMessageToVideoEntity(msg)
                        if (videoEntity != null) {
                            videos.add(videoEntity)
                            newVideosThisPage++
                        }
                    }
                }

                Timber.i("[VideoDiscovery] Page $pageIndex parsed: $newVideosThisPage video(s) found (cumulative: ${videos.size}).")
                onProgress(pageIndex, videos.size)

                // Advance the pointer to the oldest message in the batch
                val oldestMessage = messageList.last()
                fromMessageId = oldestMessage.id

                // Slight delay to be network & TDLib friendly
                delay(20)
            } else {
                val ex = result.exceptionOrNull()
                Timber.w(ex, "[VideoDiscovery] Page $pageIndex query failed (retryCount=$retryCount).")
                retryCount++
                if (retryCount <= 3) {
                    Timber.i("[VideoDiscovery] Retrying page $pageIndex in 500ms...")
                    delay(500)
                } else {
                    Timber.e(ex, "[VideoDiscovery] Exhausted retries for page $pageIndex on chat $chatId.")
                    return Result.failure(ex ?: Exception("Failed to load chat history"))
                }
            }
        }

        Timber.i("[VideoDiscovery] COMPLETE: chat $chatId yielded ${videos.size} distinct videos across $pageIndex pages.")
        return Result.success(videos)
    }
}
