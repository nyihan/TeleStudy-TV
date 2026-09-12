package com.telestudy.tv.domain.model

/**
 * Domain model representing a video lesson or educational clip discovered from Telegram.
 */
data class TelegramVideo(
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
    val date: Long,
    val caption: String,
    val minithumbnailData: ByteArray? = null,
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val isVideoDocument: Boolean = false
) {
    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val formattedSize: String
        get() {
            val mb = fileSize.toDouble() / (1024 * 1024)
            return String.format("%.2f MB", mb)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TelegramVideo

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
