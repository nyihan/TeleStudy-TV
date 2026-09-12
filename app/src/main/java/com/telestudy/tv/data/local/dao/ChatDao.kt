package com.telestudy.tv.data.local.dao

import androidx.room.*
import com.telestudy.tv.data.local.entity.TelegramChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Telegram chats, groups, and channels.
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM telegram_chats ORDER BY `order` DESC, title ASC")
    fun observeChats(): Flow<List<TelegramChatEntity>>

    @Query("SELECT * FROM telegram_chats WHERE isArchived = 0 ORDER BY `order` DESC, title ASC")
    fun observeMainChats(): Flow<List<TelegramChatEntity>>

    @Query("SELECT * FROM telegram_chats WHERE isArchived = 1 ORDER BY `order` DESC, title ASC")
    fun observeArchivedChats(): Flow<List<TelegramChatEntity>>

    @Query("SELECT * FROM telegram_chats WHERE chatId = :chatId LIMIT 1")
    fun observeChatById(chatId: Long): Flow<TelegramChatEntity?>

    @Query("SELECT * FROM telegram_chats WHERE chatId = :chatId LIMIT 1")
    suspend fun getChatById(chatId: Long): TelegramChatEntity?

    @Query("SELECT COUNT(*) FROM telegram_chats")
    suspend fun getChatCount(): Int

    @Upsert
    suspend fun upsertChats(chats: List<TelegramChatEntity>): List<Long>

    @Upsert
    suspend fun upsertChat(chat: TelegramChatEntity): Long

    @Query("""
        SELECT * FROM telegram_chats 
        WHERE isArchived = 0 AND type != 'Private' 
        ORDER BY (SELECT COUNT(*) FROM telegram_videos v WHERE v.chatId = telegram_chats.chatId) DESC,
                 CASE WHEN (
                    title LIKE '%Math%' OR title LIKE '%Primary%' OR title LIKE '%Grade%' OR 
                    title LIKE '%Lesson%' OR title LIKE '%Class%' OR title LIKE '%Course%' OR 
                    title LIKE '%Study%' OR title LIKE '%Learn%' OR title LIKE '%English%' OR 
                    title LIKE '%School%' OR title LIKE '%Academy%' OR title LIKE '%Lecture%' OR
                    title LIKE '%Phonics%' OR title LIKE '%Edu%'
                 ) THEN 1 ELSE 0 END DESC,
                 `order` DESC, title ASC
    """)
    fun observeSubjectsRanked(): Flow<List<TelegramChatEntity>>

    @Query("""
        SELECT chatId FROM telegram_chats 
        WHERE isArchived = 0 AND type != 'Private' AND (
            title LIKE '%Primary%' OR
            title LIKE '%Mathematics%' OR title LIKE '%Maths%' OR title LIKE '%Math %' OR
            title LIKE '%Phonics%' OR
            title LIKE '%Grammar%' OR title LIKE '%Grammer%' OR
            title LIKE '%Singapore%' OR
            title LIKE '%Sight Words%' OR
            title LIKE '%Reception%'
        )
        ORDER BY `order` DESC
    """)
    suspend fun getEducationalStudyChatIds(): List<Long>

    @Query("""
        SELECT * FROM telegram_chats 
        WHERE (SELECT COUNT(*) FROM telegram_videos v WHERE v.chatId = telegram_chats.chatId) > 0 
        ORDER BY (SELECT COUNT(*) FROM telegram_videos v WHERE v.chatId = telegram_chats.chatId) DESC, `order` DESC, title ASC
    """)
    fun observeSubjectsWithVideos(): Flow<List<TelegramChatEntity>>

    @Query("SELECT chatId FROM telegram_chats WHERE isArchived = 0 AND type != 'Private' ORDER BY `order` DESC")
    suspend fun getEligibleChatIds(): List<Long>

    @Query("DELETE FROM telegram_chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: Long): Int

    @Query("DELETE FROM telegram_chats")
    suspend fun deleteAllChats(): Int
}
