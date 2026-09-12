package com.telestudy.tv.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity representing a discovered Telegram video lesson or video document.
 * Stable Telegram message ID is used as the primary key to prevent duplicate video entries.
 */
@Entity(
    tableName = "telegram_videos",
    primaryKeys = ["chatId", "messageId"],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["date"]),
        Index(value = ["fileId"]),
        Index(value = ["chatId", "date"])
    ]
)
data class TelegramVideoEntity(
    val messageId: Long,
    val chatId: Long,
    val fileId: Int,
    val remoteFileId: String,
    val fileName: String,
    val durationSeconds: Int,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String,
    val date: Long, // Unix timestamp of the message
    val caption: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val minithumbnailData: ByteArray? = null,
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val isVideoDocument: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TelegramVideoEntity

        if (messageId != other.messageId) return false
        if (chatId != other.chatId) return false
        if (fileId != other.fileId) return false
        if (remoteFileId != other.remoteFileId) return false
        if (fileName != other.fileName) return false
        if (durationSeconds != other.durationSeconds) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fileSize != other.fileSize) return false
        if (mimeType != other.mimeType) return false
        if (date != other.date) return false
        if (caption != other.caption) return false
        if (thumbnailFileId != other.thumbnailFileId) return false
        if (thumbnailPath != other.thumbnailPath) return false
        if (isVideoDocument != other.isVideoDocument) return false
        if (minithumbnailData != null) {
            if (other.minithumbnailData == null) return false
            if (!minithumbnailData.contentEquals(other.minithumbnailData)) return false
        } else if (other.minithumbnailData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + chatId.hashCode()
        result = 31 * result + fileId
        result = 31 * result + remoteFileId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + durationSeconds
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + caption.hashCode()
        result = 31 * result + (minithumbnailData?.contentHashCode() ?: 0)
        result = 31 * result + (thumbnailFileId ?: 0)
        result = 31 * result + (thumbnailPath?.hashCode() ?: 0)
        result = 31 * result + isVideoDocument.hashCode()
        return result
    }
}
