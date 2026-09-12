package com.telestudy.tv.features.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.telestudy.tv.core.player.TeleStudyPlayerManager
import com.telestudy.tv.core.tdlib.TdlibFileManager
import com.telestudy.tv.data.local.dao.PlaybackProgressDao
import com.telestudy.tv.data.local.entity.PlaybackProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePlayerManager: FakeTeleStudyPlayerManager
    private lateinit var fakeProgressDao: FakePlaybackProgressDao
    private lateinit var testMediaItem: PlayerMediaItem

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakePlayerManager = FakeTeleStudyPlayerManager(context)
        fakeProgressDao = FakePlaybackProgressDao()
        testMediaItem = PlayerMediaItem(
            chatId = -1003686260160L,
            messageId = 1048576L,
            fileId = 12345,
            fileName = "Lesson 33_ Time ( Page131-134).mp4",
            durationSeconds = 1200,
            fileSize = 17393941L,
            thumbnailPath = "/tmp/thumb.jpg"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_noProgress_startsImmediatelyFromZero() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(testMediaItem, state.mediaItem)
        assertEquals(PlaybackStatus.BUFFERING, state.status)
        assertEquals(1200_000L, state.durationMs)
        assertFalse(state.isPlaying)
        assertNull(state.errorMessage)
        assertNull(state.resumeDialogState)
        assertEquals(0L, fakePlayerManager.preparedStartPositionMs)
    }

    @Test
    fun testProgressThreshold_lessThanOrEqual2000ms_playsImmediatelyWithoutDialog() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 1800L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.resumeDialogState)
        assertEquals(PlaybackStatus.BUFFERING, state.status)
        assertEquals(0L, fakePlayerManager.preparedStartPositionMs)
    }

    @Test
    fun testProgressThreshold_greaterThan2000ms_showsResumePromptDialog() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 45_000L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.resumeDialogState)
        assertTrue(state.resumeDialogState is ResumeDialogState.Prompt)

        val prompt = state.resumeDialogState as ResumeDialogState.Prompt
        assertEquals("Lesson 33 — Time", prompt.lessonTitle)
        assertEquals(45_000L, prompt.savedPositionMs)
        assertEquals(1200_000L, prompt.durationMs)
        assertEquals("You watched 00:45 of 20:00.", prompt.watchedText)
        assertEquals(-1L, fakePlayerManager.preparedStartPositionMs) // Player NOT prepared yet!
    }

    @Test
    fun testResumePrompt_userSelectsResume_startsAtSavedPosition() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 65_000L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        // User clicks Resume
        viewModel.onResumeSelected()
        testDispatcher.scheduler.runCurrent()

        assertEquals(65_000L, fakePlayerManager.preparedStartPositionMs)
        assertNull(viewModel.uiState.value.resumeDialogState)
        assertEquals(PlaybackStatus.BUFFERING, viewModel.uiState.value.status)
    }

    @Test
    fun testResumePrompt_userSelectsStartOver_startsAtZero() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 65_000L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        // User clicks Start Over
        viewModel.onStartOverSelected()
        testDispatcher.scheduler.runCurrent()

        assertEquals(0L, fakePlayerManager.preparedStartPositionMs)
        assertNull(viewModel.uiState.value.resumeDialogState)
        assertEquals(PlaybackStatus.BUFFERING, viewModel.uiState.value.status)
    }

    @Test
    fun testCompletedLesson_showsCompletedDialog_andWatchAgainStartsAtZero() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 1200_000L,
            durationMs = 1200_000L,
            isCompleted = true,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.resumeDialogState)
        assertTrue(state.resumeDialogState is ResumeDialogState.Completed)

        val completed = state.resumeDialogState as ResumeDialogState.Completed
        assertEquals("Lesson 33 — Time", completed.lessonTitle)
        assertEquals(-1L, fakePlayerManager.preparedStartPositionMs)

        // User clicks Watch Again
        viewModel.onWatchAgainSelected()
        testDispatcher.scheduler.runCurrent()

        assertEquals(0L, fakePlayerManager.preparedStartPositionMs)
        assertNull(viewModel.uiState.value.resumeDialogState)
    }

    @Test
    fun testLessonNearOrAtEnd_showsCompletedDialog() {
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = testMediaItem.messageId,
            positionMs = 1200_000L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.resumeDialogState)
        assertTrue(state.resumeDialogState is ResumeDialogState.Completed)
    }

    @Test
    fun testInvalidDuration_withProgress_handlesGracefully() {
        val unknownDurationItem = testMediaItem.copy(durationSeconds = 0)
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = unknownDurationItem.chatId,
            messageId = unknownDurationItem.messageId,
            positionMs = 30_000L,
            durationMs = 0L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, unknownDurationItem)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.resumeDialogState)
        assertTrue(state.resumeDialogState is ResumeDialogState.Prompt)
        val prompt = state.resumeDialogState as ResumeDialogState.Prompt
        assertEquals("You watched 00:30.", prompt.watchedText)
    }

    @Test
    fun testCompositeIdentity_isolatedProgressPerChatAndMessage() {
        // Progress belongs to a DIFFERENT messageId
        fakeProgressDao.savedProgress = PlaybackProgressEntity(
            chatId = testMediaItem.chatId,
            messageId = 999999L,
            positionMs = 45_000L,
            durationMs = 1200_000L,
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )

        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        // Should NOT trigger resume dialog for testMediaItem
        assertNull(viewModel.uiState.value.resumeDialogState)
        assertEquals(0L, fakePlayerManager.preparedStartPositionMs)
    }

    @Test
    fun testPlayerState_transitionsToReadyAndPlaying() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        fakePlayerManager.emitState(Player.STATE_READY)
        fakePlayerManager.emitIsPlaying(true)
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.READY, state.status)
        assertTrue(state.isPlaying)

        fakePlayerManager.emitIsPlaying(false)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun testTogglePlayPause_delegatesToPlayerManager() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        viewModel.togglePlayPause()
        assertEquals(1, fakePlayerManager.togglePlayPauseCallCount)
    }

    @Test
    fun testSeekTo_updatesPositionAndDelegates() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        viewModel.seekTo(30_000L)
        assertEquals(30_000L, fakePlayerManager.lastSeekPositionMs)
        assertEquals(30_000L, viewModel.uiState.value.currentPositionMs)
    }

    @Test
    fun testErrorMapping_networkError_mapsToUserMessage() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        fakePlayerManager.emitError(
            PlaybackException(
                "Network down",
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            )
        )
        testDispatcher.scheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isError)
        assertEquals("Network connection lost. Please check your internet connection.", state.errorMessage)
    }

    @Test
    fun testPlaybackEnded_marksCompletedInDao() {
        val viewModel = PlayerViewModel(fakePlayerManager, fakeProgressDao, testMediaItem)
        testDispatcher.scheduler.runCurrent()

        fakePlayerManager.emitState(Player.STATE_ENDED)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.ENDED, viewModel.uiState.value.status)
        assertNotNull(fakeProgressDao.savedProgress)
        assertTrue(fakeProgressDao.savedProgress!!.isCompleted)
    }

    // --- Fakes ---

    private class FakeTeleStudyPlayerManager(context: Context) :
        TeleStudyPlayerManager(context, FakeTdlibFileManager()) {

        var togglePlayPauseCallCount = 0
        var lastSeekPositionMs = -1L
        var preparedStartPositionMs = -1L

        fun emitState(state: Int) {
            _playbackState.value = state
        }

        fun emitIsPlaying(playing: Boolean) {
            _isPlaying.value = playing
        }

        fun emitError(error: PlaybackException?) {
            _playerError.value = error
        }

        override fun prepareAndPlay(mediaItem: PlayerMediaItem, startPositionMs: Long) {
            preparedStartPositionMs = startPositionMs
            _playbackState.value = Player.STATE_BUFFERING
        }

        override fun togglePlayPause() {
            togglePlayPauseCallCount++
        }

        override fun seekTo(positionMs: Long) {
            lastSeekPositionMs = positionMs
        }

        override fun seekBy(deltaMs: Long) {
            lastSeekPositionMs = currentPosition + deltaMs
        }

        override fun release() {}
    }

    private class FakePlaybackProgressDao : PlaybackProgressDao {
        var savedProgress: PlaybackProgressEntity? = null

        override suspend fun saveProgress(progress: PlaybackProgressEntity): Long {
            savedProgress = progress
            return 1L
        }

        override suspend fun getProgress(chatId: Long, messageId: Long): PlaybackProgressEntity? {
            return if (savedProgress?.chatId == chatId && savedProgress?.messageId == messageId) {
                savedProgress
            } else null
        }

        override fun observeProgress(chatId: Long, messageId: Long): Flow<PlaybackProgressEntity?> {
            return flowOf(savedProgress)
        }

        override fun observeContinueWatching(limit: Int): Flow<List<PlaybackProgressEntity>> {
            return flowOf(savedProgress?.let { listOf(it) } ?: emptyList())
        }

        override fun observeRecentlyWatched(limit: Int): Flow<List<PlaybackProgressEntity>> {
            return flowOf(savedProgress?.let { listOf(it) } ?: emptyList())
        }

        override fun observeAllProgress(): Flow<List<PlaybackProgressEntity>> {
            return flowOf(savedProgress?.let { listOf(it) } ?: emptyList())
        }

        override suspend fun clearProgress(chatId: Long, messageId: Long): Int {
            return if (savedProgress?.chatId == chatId && savedProgress?.messageId == messageId) {
                savedProgress = null
                1
            } else 0
        }
    }

    private class FakeTdlibFileManager : TdlibFileManager {
        override fun getFile(fileId: Int): org.drinkless.tdlib.TdApi.File? = null
        override fun downloadFile(fileId: Int, priority: Int, offset: Long, limit: Long) {}
        override fun cancelDownloadFile(fileId: Int) {}
        override fun readFilePart(fileId: Int, offset: Long, count: Long): ByteArray? = null
        override fun waitForBytes(fileId: Int, offset: Long, requiredBytes: Long, timeoutMs: Long): Boolean = true
        override fun addFileListener(fileId: Int, listener: (org.drinkless.tdlib.TdApi.File) -> Unit) {}
        override fun removeFileListener(fileId: Int, listener: (org.drinkless.tdlib.TdApi.File) -> Unit) {}
    }
}
