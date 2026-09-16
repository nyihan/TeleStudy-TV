package com.telestudy.tv.core.update.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Progress update emitted during APK download.
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
    val isDone: Boolean = false,
    val outputFile: File? = null
)

/**
 * High-performance APK streaming downloader supporting HTTP 301/302/307 redirects,
 * cancelation, progress reporting, and atomic rename.
 */
class ApkDownloader(
    private val connectTimeoutMs: Int = 20000,
    private val readTimeoutMs: Int = 30000
) {
    companion object {
        private const val MAX_REDIRECTS = 6
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer for TV streaming
    }

    /**
     * Downloads an APK from [downloadUrl] to [destinationFile].
     * Uses an atomic `.part` staging file during transfer.
     */
    fun download(
        downloadUrl: String,
        destinationFile: File
    ): Flow<DownloadProgress> = flow {
        val stagingFile = File(destinationFile.parentFile, "${destinationFile.name}.part")

        // Ensure parent directories exist
        destinationFile.parentFile?.mkdirs()
        if (stagingFile.exists()) {
            stagingFile.delete()
        }

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            connection = openConnectionWithRedirects(downloadUrl)

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Failed to download APK: HTTP $responseCode")
            }

            val totalBytes = connection.contentLengthLong
            Timber.i("Downloading update to %s (size: %d bytes)", stagingFile.absolutePath, totalBytes)

            inputStream = connection.inputStream
            outputStream = FileOutputStream(stagingFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesDownloaded = 0L
            var lastEmittedBytes = 0L
            val emitThreshold = (totalBytes / 100).coerceAtLeast(128 * 1024) // Emit every 1% or 128KB

            while (currentCoroutineContext().isActive) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                bytesDownloaded += read

                if (bytesDownloaded - lastEmittedBytes >= emitThreshold || bytesDownloaded == totalBytes) {
                    val progress = if (totalBytes > 0) {
                        (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    emit(
                        DownloadProgress(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            progress = progress,
                            isDone = false
                        )
                    )
                    lastEmittedBytes = bytesDownloaded
                }
            }

            if (!currentCoroutineContext().isActive) {
                Timber.w("Download cancelled by coroutine context")
                throw kotlinx.coroutines.CancellationException("Download cancelled")
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            inputStream.close()
            inputStream = null

            // Atomic rename from staging .part to final destination file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            val renamed = stagingFile.renameTo(destinationFile)
            if (!renamed) {
                // Fallback copy if rename fails across file systems
                stagingFile.copyTo(destinationFile, overwrite = true)
                stagingFile.delete()
            }

            Timber.i("Download complete: %s (%d bytes)", destinationFile.absolutePath, destinationFile.length())
            emit(
                DownloadProgress(
                    bytesDownloaded = destinationFile.length(),
                    totalBytes = totalBytes.takeIf { it > 0 } ?: destinationFile.length(),
                    progress = 1f,
                    isDone = true,
                    outputFile = destinationFile
                )
            )
        } catch (e: Throwable) {
            Timber.e(e, "APK download failed")
            // Clean up staging file on failure
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            throw e
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Resolves HTTP 301/302/303/307/308 redirects manually to handle cross-host redirects
     * (e.g., github.com to objects.githubusercontent.com / s3.amazonaws.com).
     */
    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount < MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "TeleStudyTV-InAppUpdate")
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            }

            val status = conn.responseCode
            if (status in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, // Temporary Redirect
                    308  // Permanent Redirect
                )
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrEmpty()) {
                    throw IllegalStateException("Redirect without Location header (HTTP $status)")
                }
                currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                    location
                } else {
                    URL(url, location).toString()
                }
                redirectCount++
                Timber.d("Following redirect %d -> %s", redirectCount, currentUrl)
            } else {
                return conn
            }
        }

        throw IllegalStateException("Too many HTTP redirects ($redirectCount)")
    }
}
