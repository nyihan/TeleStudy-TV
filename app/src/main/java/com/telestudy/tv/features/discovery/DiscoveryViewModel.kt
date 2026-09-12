package com.telestudy.tv.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.telestudy.tv.data.repository.TelegramMediaRepository
import com.telestudy.tv.domain.model.TelegramChat
import com.telestudy.tv.domain.model.TelegramVideo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class DiscoveryUiState(
    val chats: List<TelegramChat> = emptyList(),
    val selectedChat: TelegramChat? = null,
    val videos: List<TelegramVideo> = emptyList(),
    val isSyncingChats: Boolean = false,
    val isSyncingVideos: Boolean = false,
    val syncPage: Int = 0,
    val syncVideosFound: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class DiscoveryViewModel(
    private val repository: TelegramMediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var videoObservationJob: Job? = null

    init {
        observeChats()
    }

    private fun observeChats() {
        viewModelScope.launch {
            repository.observeChats().collect { chatList ->
                _uiState.update { it.copy(chats = chatList) }
            }
        }
    }

    fun syncChats() {
        if (_uiState.value.isSyncingChats) return

        _uiState.update {
            it.copy(
                isSyncingChats = true,
                errorMessage = null,
                statusMessage = "Discovering Telegram chats & channels across Main and Archive..."
            )
        }

        viewModelScope.launch {
            val result = repository.syncChats()
            result.onSuccess { totalCount ->
                _uiState.update {
                    it.copy(
                        isSyncingChats = false,
                        statusMessage = "Exhaustive chat discovery complete! $totalCount chats saved in Room."
                    )
                }
            }
            result.onFailure { ex ->
                Timber.e(ex, "Failed to sync chats")
                _uiState.update {
                    it.copy(
                        isSyncingChats = false,
                        errorMessage = "Chat discovery error: ${ex.message}"
                    )
                }
            }
        }
    }

    fun selectChat(chat: TelegramChat) {
        _uiState.update {
            it.copy(
                selectedChat = chat,
                videos = emptyList(),
                syncPage = 0,
                syncVideosFound = 0,
                errorMessage = null
            )
        }

        // Observe local Room videos for this chat
        videoObservationJob?.cancel()
        videoObservationJob = viewModelScope.launch {
            repository.observeVideos(chat.id).collect { videoList ->
                _uiState.update { it.copy(videos = videoList) }
            }
        }
    }

    fun syncVideosForSelectedChat() {
        val chat = _uiState.value.selectedChat ?: return
        if (_uiState.value.isSyncingVideos) return

        _uiState.update {
            it.copy(
                isSyncingVideos = true,
                syncPage = 0,
                syncVideosFound = 0,
                errorMessage = null,
                statusMessage = "Starting exhaustive video pagination for '${chat.title}'..."
            )
        }

        viewModelScope.launch {
            val result = repository.syncChatVideos(chat.id) { page, count ->
                _uiState.update {
                    it.copy(
                        syncPage = page,
                        syncVideosFound = count,
                        statusMessage = "Paginating history: page $page ($count videos discovered so far)..."
                    )
                }
            }

            result.onSuccess { totalInRoom ->
                _uiState.update {
                    it.copy(
                        isSyncingVideos = false,
                        statusMessage = "Video pagination exhausted! $totalInRoom distinct videos stored in Room."
                    )
                }
            }

            result.onFailure { ex ->
                Timber.e(ex, "Failed to sync videos for chat ${chat.id}")
                _uiState.update {
                    it.copy(
                        isSyncingVideos = false,
                        errorMessage = "Video pagination error: ${ex.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
