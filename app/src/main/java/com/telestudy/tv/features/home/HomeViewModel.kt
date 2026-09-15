package com.telestudy.tv.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telestudy.tv.data.repository.TelegramMediaRepository
import com.telestudy.tv.data.sync.SyncManager
import com.telestudy.tv.data.sync.SyncState
import com.telestudy.tv.domain.model.ContinueWatchingItem
import com.telestudy.tv.domain.model.DailyStudyContinuity
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import com.telestudy.tv.domain.model.WatchProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

data class HomeUiState(
    val subjects: List<TelegramChat> = emptyList(),
    val selectedSubject: TelegramChat? = null,
    val lessons: List<TelegramVideo> = emptyList(),
    val continuity: DailyStudyContinuity = DailyStudyContinuity(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val progressMap: Map<Pair<Long, Long>, WatchProgress> = emptyMap(),
    val searchResults: List<TelegramVideo> = emptyList(),
    val syncState: SyncState = SyncState.Idle,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val chatTitleMap: Map<Long, String>
        get() = subjects.associate { it.id to it.title }
}

class HomeViewModel(
    private val repository: TelegramMediaRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lessonsJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeSyncState()
        observeSubjects()
        observeDailyStudyContinuity()
        observeContinueWatching()
        observePlaybackProgress()
        triggerAutoSync()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { state ->
                _uiState.update { it.copy(syncState = state) }
            }
        }
    }

    private fun observeSubjects() {
        viewModelScope.launch {
            repository.observeSubjectsRanked().collect { subjectList ->
                _uiState.update { current ->
                    val selected = current.selectedSubject ?: subjectList.firstOrNull { it.videoCount > 0 }
                    ?: subjectList.firstOrNull()

                    current.copy(
                        subjects = subjectList,
                        selectedSubject = selected
                    )
                }

                val currentSelected = _uiState.value.selectedSubject
                if (currentSelected != null && (lessonsJob == null || !lessonsJob!!.isActive)) {
                    observeLessonsForSubject(currentSelected.id)
                }
            }
        }
    }

    private fun observeDailyStudyContinuity() {
        viewModelScope.launch {
            repository.observeDailyStudyContinuity().collect { continuity ->
                _uiState.update { it.copy(continuity = continuity) }
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            repository.observeContinueWatching().collect { items ->
                _uiState.update { it.copy(continueWatching = items) }
            }
        }
    }

    private fun observePlaybackProgress() {
        viewModelScope.launch {
            repository.observeAllPlaybackProgress().collect { map ->
                _uiState.update { it.copy(progressMap = map) }
            }
        }
    }

    fun selectSubject(subject: TelegramChat) {
        if (_uiState.value.selectedSubject?.id == subject.id) return
        _uiState.update { it.copy(selectedSubject = subject) }
        observeLessonsForSubject(subject.id)
        viewModelScope.launch {
            try {
                syncManager.performDeltaSyncForChat(subject.id)
            } catch (e: Exception) {
                Timber.w(e, "Error syncing subject on select")
            }
        }
    }

    private fun observeLessonsForSubject(chatId: Long) {
        lessonsJob?.cancel()
        lessonsJob = viewModelScope.launch {
            repository.observeVideos(chatId).collect { lessonList ->
                _uiState.update { it.copy(lessons = lessonList) }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update { it.copy(isSearchActive = active) }
        if (!active && _uiState.value.searchQuery.isNotBlank()) {
            updateSearchQuery("")
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(200L) // debounce
            repository.searchVideos(trimmed).collect { results ->
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            }
        }
    }

    // --- TV On-Screen Keyboard Action Callbacks ---

    fun onKeyboardChar(char: Char) {
        val current = _uiState.value.searchQuery
        updateSearchQuery(current + char)
    }

    fun onKeyboardSpace() {
        val current = _uiState.value.searchQuery
        if (current.isNotEmpty() && !current.endsWith(" ")) {
            updateSearchQuery("$current ")
        }
    }

    fun onKeyboardBackspace() {
        val current = _uiState.value.searchQuery
        if (current.isNotEmpty()) {
            updateSearchQuery(current.dropLast(1))
        }
    }

    fun onKeyboardClear() {
        updateSearchQuery("")
    }

    fun triggerAutoSync() {
        viewModelScope.launch {
            try {
                syncManager.performAutoSync()
            } catch (e: Exception) {
                Timber.e(e, "Auto sync failed in HomeViewModel")
            }
        }
    }
}
