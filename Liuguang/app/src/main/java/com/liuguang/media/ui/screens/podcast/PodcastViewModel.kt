package com.liuguang.media.ui.screens.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.data.repository.PodcastRepository
import com.liuguang.media.domain.model.PodcastFeed
import com.liuguang.media.domain.model.PodcastLibraryEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class PodcastUiState(
    val isAdding: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val isRefreshingLibrary: Boolean = false,
    val isRefreshingSubscriptions: Boolean = false,
    val isRefreshingSubscriptionId: Long? = null,
    val selectedSubscriptionId: Long? = null,
    val selectedSourceId: Long? = null,
    val searchQuery: String = "",
    val selectedFeed: PodcastFeed? = null,
    val libraryEpisodes: List<PodcastLibraryEpisode> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PodcastUiState())
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    val subscriptions = podcastRepository.observeSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var hasLoadedLibrary = false

    fun addSubscription(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdding = true, message = null)
            podcastRepository.addSubscription(trimmedUrl).fold(
                onSuccess = { subscription ->
                    loadLibraryEpisodes().onSuccess { episodes ->
                        _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
                    }
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        message = "已订阅 ${subscription.title}"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isAdding = false,
                        message = error.message ?: "订阅添加失败"
                    )
                }
            )
        }
    }

    fun updateSubscription(subscription: PodcastSubscriptionEntity) {
        val trimmedUrl = subscription.url.trim()
        if (trimmedUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshingSubscriptionId = subscription.id,
                message = null
            )
            podcastRepository.updateSubscription(subscription.copy(url = trimmedUrl)).fold(
                onSuccess = { refreshed ->
                    loadLibraryEpisodes().onSuccess { episodes ->
                        _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
                    }
                    _uiState.value = _uiState.value.copy(
                        isRefreshingSubscriptionId = null,
                        message = "已更新 ${refreshed.title}"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingSubscriptionId = null,
                        message = error.message ?: "播客源更新失败"
                    )
                }
            )
        }
    }

    fun refreshSubscriptionItem(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshingSubscriptionId = subscription.id,
                message = null
            )
            podcastRepository.refreshSubscription(subscription).fold(
                onSuccess = { refreshed ->
                    loadLibraryEpisodes().onSuccess { episodes ->
                        _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
                    }
                    _uiState.value = _uiState.value.copy(
                        isRefreshingSubscriptionId = null,
                        message = "已刷新 ${refreshed.title}"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingSubscriptionId = null,
                        message = error.message ?: "播客源刷新失败"
                    )
                }
            )
        }
    }

    fun refreshSubscriptions() {
        viewModelScope.launch {
            val currentSubscriptions = subscriptions.value
            if (currentSubscriptions.isEmpty()) {
                _uiState.value = _uiState.value.copy(message = "还没有播客源")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isRefreshingSubscriptions = true,
                message = null
            )

            try {
                val semaphore = Semaphore(PODCAST_REFRESH_PARALLELISM)
                val results = coroutineScope {
                    currentSubscriptions.map { subscription ->
                        async {
                            semaphore.withPermit {
                                podcastRepository.refreshSubscription(subscription)
                            }
                        }
                    }.awaitAll()
                }

                val successCount = results.count { it.isSuccess }
                val failedCount = results.size - successCount
                loadLibraryEpisodes().onSuccess { episodes ->
                    _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
                }
                _uiState.value = _uiState.value.copy(
                    isRefreshingSubscriptions = false,
                    isRefreshingSubscriptionId = null,
                    message = if (failedCount == 0) {
                        "已刷新 $successCount 个播客源"
                    } else {
                        "已刷新 $successCount 个播客源，$failedCount 个失败"
                    }
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshingSubscriptions = false,
                    isRefreshingSubscriptionId = null,
                    message = error.message ?: "播客源刷新失败"
                )
            }
        }
    }

    fun refreshLibrary() {
        refreshLibrary(force = true)
    }

    fun refreshLibraryIfNeeded() {
        if (hasLoadedLibrary || _uiState.value.isRefreshingLibrary) return
        refreshLibrary(force = false)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectSource(sourceId: Long?) {
        _uiState.value = _uiState.value.copy(selectedSourceId = sourceId)
    }

    private fun refreshLibrary(force: Boolean) {
        if (!force && hasLoadedLibrary) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingLibrary = true)
            loadLibraryEpisodes().fold(
                onSuccess = { episodes ->
                    hasLoadedLibrary = true
                    _uiState.value = _uiState.value.copy(
                        isRefreshingLibrary = false,
                        libraryEpisodes = episodes
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingLibrary = false,
                        message = error.message ?: "聚合节目刷新失败"
                    )
                }
            )
        }
    }

    fun openSubscription(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingFeed = true,
                selectedSubscriptionId = subscription.id,
                selectedFeed = null,
                message = null
            )
            podcastRepository.fetchPodcastFeed(subscription.url).fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        selectedFeed = feed
                    )
                    podcastRepository.refreshSubscription(subscription)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingFeed = false,
                        message = error.message ?: "订阅刷新失败"
                    )
                }
            )
        }
    }

    fun closeSubscription() {
        _uiState.value = _uiState.value.copy(
            selectedSubscriptionId = null,
            selectedFeed = null,
            isLoadingFeed = false,
            message = null
        )
    }

    fun deleteSubscription(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            podcastRepository.deleteSubscription(subscription)
            val current = _uiState.value
            _uiState.value = current.copy(
                selectedSubscriptionId = if (current.selectedSubscriptionId == subscription.id) null else current.selectedSubscriptionId,
                selectedFeed = if (current.selectedSubscriptionId == subscription.id) null else current.selectedFeed,
                message = "已删除 ${subscription.title}"
            )
            loadLibraryEpisodes().onSuccess { episodes ->
                _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
            }
        }
    }

    fun clearMessage() {
        if (_uiState.value.message != null) {
            _uiState.value = _uiState.value.copy(message = null)
        }
    }

    private suspend fun loadLibraryEpisodes() = podcastRepository.fetchLibraryEpisodes()

    private companion object {
        const val PODCAST_REFRESH_PARALLELISM = 4
    }
}
