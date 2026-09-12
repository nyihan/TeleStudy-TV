package com.telestudy.tv.features.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.telestudy.tv.core.player.TeleStudyPlayerManager
import com.telestudy.tv.data.local.dao.PlaybackProgressDao
import com.telestudy.tv.data.local.entity.PlaybackProgressEntity
import com.telestudy.tv.data.mapper.EntityMappers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class PlayerViewModel(
    val playerManager: TeleStudyPlayerManager,
    private val playbackProgressDao: PlaybackProgressDao,
    val mediaItem: PlayerMediaItem
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            mediaItem = mediaItem,
            status = PlaybackStatus.IDLE,
            durationMs = mediaItem.durationSeconds.toLong() * 1000L
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressPollingJob: Job? = null
    private var lastSavedTimestamp: Long = 0L
    private var resolvedPlayableItem: PlayerMediaItem? = null
    private var savedProgressEntity: PlaybackProgressEntity? = null

    init {
        observePlayerState()
        loadInitialProgress()
    }

    private fun loadInitialProgress() {
        viewModelScope.launch {
            var activeFileId = mediaItem.fileId
            try {
                val resolved = playerManager.resolveActiveFileId(mediaItem.chatId, mediaItem.messageId)
                if (resolved > 0) {
                    activeFileId = resolved
                    Timber.i("[TeleStudyPlayer] Resolved active session fileId for %s: %s (db was %s)",
                        mediaItem.fileName, activeFileId, mediaItem.fileId)
                }
            } catch (e: Exception) {
                Timber.w(e, "[TeleStudyPlayer] Failed resolving active fileId")
            }

            val playableItem = if (activeFileId != mediaItem.fileId) {
                mediaItem.copy(fileId = activeFileId)
            } else {
                mediaItem
            }
            resolvedPlayableItem = playableItem

            var saved: PlaybackProgressEntity? = null
            try {
                saved = playbackProgressDao.getProgress(mediaItem.chatId, mediaItem.messageId)
                savedProgressEntity = saved
            } catch (e: Exception) {
                Timber.w(e, "[TeleStudyPlayer] Failed reading saved progress")
            }

            val duration = if (saved != null && saved.durationMs > 0) {
                saved.durationMs
            } else {
                mediaItem.durationSeconds * 1000L
            }

            val lessonTitle = formatDialogTitle(mediaItem.fileName)

            if (saved == null || saved.positionMs <= 2000L) {
                // Case A: No saved progress or <= 2000ms -> start immediately from beginning
                startPlayback(PlaybackStartMode.START_OVER)
            } else if (saved.isCompleted || (duration > 0 && saved.positionMs >= duration)) {
                // Case C: Lesson is completed
                _uiState.update {
                    it.copy(
                        status = PlaybackStatus.IDLE,
                        resumeDialogState = ResumeDialogState.Completed(lessonTitle = lessonTitle)
                    )
                }
            } else {
                // Case B: Saved progress > 2000ms and not completed
                _uiState.update {
                    it.copy(
                        status = PlaybackStatus.IDLE,
                        resumeDialogState = ResumeDialogState.Prompt(
                            lessonTitle = lessonTitle,
                            savedPositionMs = saved.positionMs,
                            durationMs = duration
                        )
                    )
                }
            }
        }
    }

    fun onResumeSelected() {
        startPlayback(PlaybackStartMode.RESUME)
    }

    fun onStartOverSelected() {
        startPlayback(PlaybackStartMode.START_OVER)
    }

    fun onWatchAgainSelected() {
        startPlayback(PlaybackStartMode.START_OVER)
    }

    fun startPlayback(mode: PlaybackStartMode) {
        _uiState.update { it.copy(resumeDialogState = null, status = PlaybackStatus.BUFFERING) }
        viewModelScope.launch {
            val item = resolvedPlayableItem ?: mediaItem
            val startPosMs = when (mode) {
                PlaybackStartMode.RESUME -> {
                    savedProgressEntity?.positionMs ?: 0L
                }
                PlaybackStartMode.START_OVER -> {
                    0L
                }
            }
            Timber.i("[TeleStudyPlayer] Starting playback with mode %s at %d ms for %s",
                mode, startPosMs, item.fileName)
            playerManager.prepareAndPlay(item, startPosMs)
        }
    }

    fun formatDialogTitle(fileName: String): String {
        val lessonNum = EntityMappers.extractLessonNumber(fileName)
        val topic = EntityMappers.extractTopicTitle(fileName)
        return if (lessonNum != null && topic.isNotBlank() && !topic.equals("Lesson $lessonNum", ignoreCase = true)) {
            "Lesson $lessonNum — $topic"
        } else if (lessonNum != null) {
            "Lesson $lessonNum"
        } else {
            EntityMappers.cleanLessonTitle(fileName)
        }
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            playerManager.playbackState.collect { state ->
                val status = when (state) {
                    Player.STATE_IDLE -> PlaybackStatus.IDLE
                    Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                    Player.STATE_READY -> PlaybackStatus.READY
                    Player.STATE_ENDED -> {
                        markCompleted()
                        PlaybackStatus.ENDED
                    }
                    else -> PlaybackStatus.IDLE
                }
                _uiState.update { it.copy(status = status) }
            }
        }

        viewModelScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressPolling()
                } else {
                    progressPollingJob?.cancel()
                }
            }
        }

        viewModelScope.launch {
            playerManager.playerError.collect { error ->
                if (error != null) {
                    val userMsg = mapErrorToUserMessage(error)
                    _uiState.update { it.copy(errorMessage = userMsg) }
                } else {
                    _uiState.update { it.copy(errorMessage = null) }
                }
            }
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = viewModelScope.launch {
            while (isActive) {
                val currentPos = playerManager.currentPosition
                val duration = playerManager.duration
                val buffered = playerManager.player?.bufferedPosition ?: 0L

                _uiState.update {
                    it.copy(
                        currentPositionMs = currentPos,
                        durationMs = if (duration > 0) duration else it.durationMs,
                        bufferedPositionMs = buffered
                    )
                }

                val now = System.currentTimeMillis()
                if (now - lastSavedTimestamp > 5000L && currentPos > 0L) {
                    saveProgress(currentPos, duration, isCompleted = false)
                    lastSavedTimestamp = now
                }

                delay(250L)
            }
        }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekBy(deltaMs: Long) {
        playerManager.seekBy(deltaMs)
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(isControlsVisible = visible) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null, status = PlaybackStatus.BUFFERING) }
        playerManager.retry()
    }

    private fun markCompleted() {
        viewModelScope.launch {
            saveProgress(_uiState.value.durationMs, _uiState.value.durationMs, isCompleted = true)
        }
    }

    private fun saveProgress(positionMs: Long, durationMs: Long, isCompleted: Boolean) {
        viewModelScope.launch {
            try {
                playbackProgressDao.saveProgress(
                    PlaybackProgressEntity(
                        chatId = mediaItem.chatId,
                        messageId = mediaItem.messageId,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isCompleted = isCompleted,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "[TeleStudyPlayer] Error saving playback progress")
            }
        }
    }

    private fun mapErrorToUserMessage(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network connection lost. Please check your internet connection."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Telegram video file was not found or is expired."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "Unable to decode video format on this device."
            else ->
                "Playback error encountered (${error.errorCodeName}). Tap to retry."
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressPollingJob?.cancel()
        val currentPos = _uiState.value.currentPositionMs
        val duration = _uiState.value.durationMs
        if (currentPos > 0L) {
            saveProgress(currentPos, duration, isCompleted = false)
        }
        playerManager.release()
    }
}
