package com.telestudy.tv.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.telestudy.tv.core.tdlib.TdlibFileManager
import com.telestudy.tv.features.player.PlayerMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@OptIn(UnstableApi::class)
open class TeleStudyPlayerManager(
    private val context: Context,
    private val fileManager: TdlibFileManager,
    private val clientProvider: () -> org.drinkless.tdlib.Client? = { null }
) {
    private var exoPlayer: ExoPlayer? = null
    private var activeMediaItem: PlayerMediaItem? = null

    open suspend fun resolveActiveFileId(chatId: Long, messageId: Long): Int {
        val client = clientProvider() ?: return -1
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.send(
                org.drinkless.tdlib.TdApi.GetMessage(chatId, messageId),
                { obj ->
                    if (obj is org.drinkless.tdlib.TdApi.Message) {
                        val content = obj.content
                        val id = when (content) {
                            is org.drinkless.tdlib.TdApi.MessageVideo -> content.video.video.id
                            is org.drinkless.tdlib.TdApi.MessageDocument -> content.document.document.id
                            else -> -1
                        }
                        cont.resume(id, null)
                    } else {
                        cont.resume(-1, null)
                    }
                },
                {
                    cont.resume(-1, null)
                }
            )
        }
    }

    protected val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    open val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    protected val _isPlaying = MutableStateFlow(false)
    open val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    protected val _playerError = MutableStateFlow<PlaybackException?>(null)
    open val playerError: StateFlow<PlaybackException?> = _playerError.asStateFlow()

    private val dataSourceFactory: DataSource.Factory = TelegramDataSourceFactory(fileManager)
    private val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = state
            Timber.d("[TeleStudyPlayer] PlaybackState changed: %s (pos=%s, duration=%s)", state, currentPosition, duration)
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            Timber.d("[TeleStudyPlayer] isPlaying changed: %s", playing)
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerError.value = error
            Timber.e(error, "[TeleStudyPlayer] PlaybackException: [%s] %s", error.errorCode, error.message)
        }
    }

    @Synchronized
    fun getOrCreatePlayer(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                addListener(playerListener)
                playWhenReady = true
            }
        exoPlayer = player
        return player
    }

    open val player: ExoPlayer?
        get() = exoPlayer

    open val currentPosition: Long
        get() = exoPlayer?.currentPosition ?: 0L

    open val duration: Long
        get() {
            val d = exoPlayer?.duration ?: C.TIME_UNSET
            return if (d != C.TIME_UNSET) d else (activeMediaItem?.durationSeconds?.toLong()?.times(1000L) ?: 0L)
        }

    open fun prepareAndPlay(mediaItem: PlayerMediaItem, startPositionMs: Long = 0L) {
        activeMediaItem = mediaItem
        _playerError.value = null

        val player = getOrCreatePlayer()
        val uri = Uri.parse("tg://file/${mediaItem.fileId}?size=${mediaItem.fileSize}")
        val exMediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(mediaItem.messageId.toString())
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(exMediaItem)

        Timber.i("[TeleStudyPlayer] Preparing video: %s (fileId=%s, size=%s, startPosMs=%s)",
            mediaItem.fileName, mediaItem.fileId, mediaItem.fileSize, startPositionMs)

        player.setMediaSource(mediaSource, startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    open fun pause() {
        exoPlayer?.pause()
    }

    open fun resume() {
        exoPlayer?.play()
    }

    open fun togglePlayPause() {
        val p = exoPlayer ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    open fun seekTo(positionMs: Long) {
        val p = exoPlayer ?: return
        val target = positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
        Timber.i("[TeleStudyPlayer] seekTo: targetMs=%s", target)
        p.seekTo(target)
    }

    open fun seekBy(deltaMs: Long) {
        val current = currentPosition
        seekTo(current + deltaMs)
    }

    open fun retry() {
        val item = activeMediaItem ?: return
        val lastPos = currentPosition
        Timber.i("[TeleStudyPlayer] Retrying playback from posMs=%s", lastPos)
        prepareAndPlay(item, lastPos)
    }

    @Synchronized
    open fun release() {
        Timber.i("[TeleStudyPlayer] Releasing player and cleaning up resources")
        activeMediaItem?.let {
            fileManager.cancelDownloadFile(it.fileId)
        }
        activeMediaItem = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = Player.STATE_IDLE
        _isPlaying.value = false
        _playerError.value = null
    }
}
