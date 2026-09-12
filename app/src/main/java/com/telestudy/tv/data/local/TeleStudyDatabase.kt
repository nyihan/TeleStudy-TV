package com.telestudy.tv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.telestudy.tv.data.local.dao.ChatDao
import com.telestudy.tv.data.local.dao.PlaybackProgressDao
import com.telestudy.tv.data.local.dao.VideoDao
import com.telestudy.tv.data.local.entity.PlaybackProgressEntity
import com.telestudy.tv.data.local.entity.TelegramChatEntity
import com.telestudy.tv.data.local.entity.TelegramVideoEntity

@Database(
    entities = [
        TelegramChatEntity::class,
        TelegramVideoEntity::class,
        PlaybackProgressEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class TeleStudyDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun videoDao(): VideoDao
    abstract fun playbackProgressDao(): PlaybackProgressDao

    companion object {
        private const val DATABASE_NAME = "telestudy_local.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telegram_videos ADD COLUMN thumbnailPath TEXT")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS telegram_videos")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `telegram_videos` (
                        `messageId` INTEGER NOT NULL,
                        `chatId` INTEGER NOT NULL,
                        `fileId` INTEGER NOT NULL,
                        `remoteFileId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `caption` TEXT NOT NULL,
                        `minithumbnailData` BLOB,
                        `thumbnailFileId` INTEGER,
                        `thumbnailPath` TEXT,
                        `isVideoDocument` INTEGER NOT NULL,
                        PRIMARY KEY(`chatId`, `messageId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_videos_chatId` ON `telegram_videos` (`chatId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_videos_date` ON `telegram_videos` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_videos_fileId` ON `telegram_videos` (`fileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telegram_videos_chatId_date` ON `telegram_videos` (`chatId`, `date`)")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playback_progress` (
                        `chatId` INTEGER NOT NULL,
                        `messageId` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`chatId`, `messageId`)
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: TeleStudyDatabase? = null

        fun getInstance(context: Context): TeleStudyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TeleStudyDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
