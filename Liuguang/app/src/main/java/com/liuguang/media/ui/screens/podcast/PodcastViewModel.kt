package com.liuguang.media.ui.screens.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.PodcastSubscriptionEntity
import com.liuguang.media.data.repository.PodcastSourceCheckResponse
import com.liuguang.media.data.repository.PodcastRepository
import com.liuguang.media.data.repository.classifySourceCheckFailure
import com.liuguang.media.data.repository.sourceCheckFailureMessage
import com.liuguang.media.data.repository.sourceCheckReturnedContent
import com.liuguang.media.domain.model.PodcastFeed
import com.liuguang.media.domain.model.PodcastLibraryEpisode
import com.liuguang.media.ui.components.SourceBatchUiState
import com.liuguang.media.ui.components.SourceCheckResultDialogState
import com.liuguang.media.ui.components.SourceCheckSummaryItem
import com.liuguang.media.ui.components.SourceImportUiState
import com.liuguang.media.ui.components.parseNamedSourceLines
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    private val _checkingSubscriptionId = MutableStateFlow<Long?>(null)
    val checkingSubscriptionId: StateFlow<Long?> = _checkingSubscriptionId.asStateFlow()

    private val _batchCheckingSubscriptionIds = MutableStateFlow<Set<Long>>(emptySet())
    val batchCheckingSubscriptionIds: StateFlow<Set<Long>> = _batchCheckingSubscriptionIds.asStateFlow()

    private val _checkResultDialog = MutableStateFlow<SourceCheckResultDialogState?>(null)
    val checkResultDialog: StateFlow<SourceCheckResultDialogState?> = _checkResultDialog.asStateFlow()

    private val _importUiState = MutableStateFlow(SourceImportUiState())
    val importUiState: StateFlow<SourceImportUiState> = _importUiState.asStateFlow()

    private val _batchUiState = MutableStateFlow(SourceBatchUiState())
    val batchUiState: StateFlow<SourceBatchUiState> = _batchUiState.asStateFlow()

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

    fun importSubscriptions(rawText: String) {
        if (_importUiState.value.isImporting || _batchUiState.value.isRunning) return

        val parsedUrls = parseNamedSourceLines(rawText, "播客源").map { it.second }
        if (parsedUrls.isEmpty()) {
            _importUiState.value = SourceImportUiState(message = "未识别到有效播客源")
            return
        }

        viewModelScope.launch {
            _importUiState.value = SourceImportUiState(isImporting = true, message = null)
            val existingUrls = podcastRepository.getAllSubscriptions()
                .map { it.url.normalizeSourceUrl() }
                .toSet()
            val urls = parsedUrls
                .filterNot { it.normalizeSourceUrl() in existingUrls }
                .distinctBy { it.normalizeSourceUrl() }

            val successCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val semaphore = Semaphore(PODCAST_REFRESH_PARALLELISM)
            coroutineScope {
                urls.map { url ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            podcastRepository.addSubscription(url).fold(
                                onSuccess = { successCount.incrementAndGet() },
                                onFailure = { failedCount.incrementAndGet() }
                            )
                        }
                    }
                }.awaitAll()
            }
            loadLibraryEpisodes().onSuccess { episodes ->
                _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
            }
            _importUiState.value = SourceImportUiState(
                isImporting = false,
                message = "新增 ${successCount.get()} 个，失败 ${failedCount.get()} 个，跳过重复 ${parsedUrls.size - urls.size} 个"
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

    fun toggleSubscriptionEnabled(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            podcastRepository.updateSubscriptionRaw(subscription.copy(enabled = !subscription.enabled))
            loadLibraryEpisodes().onSuccess { episodes ->
                _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
            }
        }
    }

    fun moveSubscriptionUp(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            podcastRepository.moveSubscriptionUp(subscription, subscriptions.value)
        }
    }

    fun moveSubscriptionDown(subscription: PodcastSubscriptionEntity) {
        viewModelScope.launch {
            podcastRepository.moveSubscriptionDown(subscription, subscriptions.value)
        }
    }

    fun clearAllSubscriptions() {
        if (_checkingSubscriptionId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            podcastRepository.clearAllSubscriptions()
            _uiState.value = _uiState.value.copy(
                selectedSubscriptionId = null,
                selectedFeed = null,
                libraryEpisodes = emptyList(),
                selectedSourceId = null,
                message = "已清空播客源"
            )
        }
    }

    fun checkSubscription(subscription: PodcastSubscriptionEntity) {
        if (_checkingSubscriptionId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            _checkingSubscriptionId.value = subscription.id
            try {
                podcastRepository.checkPodcastSource(subscription.url).fold(
                    onSuccess = { response ->
                        val now = System.currentTimeMillis()
                        val refreshed = subscription.copy(
                            title = response.feed.title,
                            description = response.feed.description,
                            imageUrl = response.feed.imageUrl,
                            link = response.feed.link,
                            episodeCount = response.feed.episodes.size,
                            lastRefreshTime = now,
                            enabled = true,
                            lastCheckStatus = "可用",
                            lastCheckTime = now
                        )
                        podcastRepository.updateSubscriptionRaw(refreshed)
                        _checkResultDialog.value = buildSuccessDialog(refreshed, response)
                    },
                    onFailure = { error ->
                        val reason = classifySourceCheckFailure(error)
                        podcastRepository.updateSubscriptionRaw(
                            subscription.copy(
                                lastCheckStatus = reason.statusText,
                                lastCheckTime = System.currentTimeMillis()
                            )
                        )
                        _checkResultDialog.value = SourceCheckResultDialogState(
                            title = "播客源检测失败",
                            sourceName = subscription.title,
                            success = false,
                            message = "${reason.label}：${sourceCheckFailureMessage(reason, error)}",
                            summary = listOf(
                                SourceCheckSummaryItem("检测地址", subscription.url),
                                SourceCheckSummaryItem("失败分类", reason.label)
                            ),
                            returnedContent = sourceCheckReturnedContent(error)?.toDialogContent()
                        )
                    }
                )
            } finally {
                _checkingSubscriptionId.value = null
            }
        }
    }

    fun batchCheckSubscriptions() {
        if (_checkingSubscriptionId.value != null || _batchUiState.value.isRunning) return

        viewModelScope.launch {
            val currentSubscriptions = podcastRepository.getAllSubscriptions()
            if (currentSubscriptions.isEmpty()) return@launch

            val completedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val semaphore = Semaphore(PODCAST_REFRESH_PARALLELISM)

            _batchUiState.value = SourceBatchUiState(
                isRunning = true,
                total = currentSubscriptions.size,
                message = "并行检测 ${currentSubscriptions.size} 个播客源"
            )

            try {
                coroutineScope {
                    currentSubscriptions.map { subscription ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                _batchCheckingSubscriptionIds.update { it + subscription.id }
                                try {
                                    val checked = checkSubscriptionForBatch(subscription)
                                    podcastRepository.updateSubscriptionRaw(checked)
                                    val completed = completedCount.incrementAndGet()
                                    if (checked.lastCheckStatus == "可用") {
                                        successCount.incrementAndGet()
                                    } else {
                                        failedCount.incrementAndGet()
                                    }
                                    _batchUiState.value = SourceBatchUiState(
                                        isRunning = true,
                                        currentIndex = completed,
                                        total = currentSubscriptions.size,
                                        message = "刚完成：${subscription.title}"
                                    )
                                } finally {
                                    _batchCheckingSubscriptionIds.update { it - subscription.id }
                                }
                            }
                        }
                    }.awaitAll()
                }
                loadLibraryEpisodes().onSuccess { episodes ->
                    _uiState.value = _uiState.value.copy(libraryEpisodes = episodes)
                }
                _batchUiState.value = SourceBatchUiState(
                    isRunning = false,
                    currentIndex = currentSubscriptions.size,
                    total = currentSubscriptions.size,
                    message = "批量检测完成：可用 ${successCount.get()} 个，异常 ${failedCount.get()} 个"
                )
            } finally {
                _batchCheckingSubscriptionIds.value = emptySet()
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

    fun dismissCheckResultDialog() {
        _checkResultDialog.value = null
    }

    fun consumeImportMessage() {
        _importUiState.value = _importUiState.value.copy(message = null)
    }

    fun consumeBatchMessage() {
        _batchUiState.value = _batchUiState.value.copy(message = null)
    }

    private suspend fun loadLibraryEpisodes() = podcastRepository.fetchLibraryEpisodes()

    private fun buildSuccessDialog(
        subscription: PodcastSubscriptionEntity,
        response: PodcastSourceCheckResponse
    ): SourceCheckResultDialogState {
        val sampleEpisodes = response.feed.episodes
            .take(6)
            .joinToString("、") { it.title }
            .ifBlank { "无" }
        val audioTypes = response.feed.episodes
            .map { it.audioType.ifBlank { "audio" } }
            .distinct()
            .take(6)
            .joinToString("、")
            .ifBlank { "未知" }

        return SourceCheckResultDialogState(
            title = "播客源检测成功",
            sourceName = subscription.title,
            success = true,
            message = "订阅源可访问，返回内容已成功解析出节目。",
            summary = listOf(
                SourceCheckSummaryItem("检测地址", subscription.url),
                SourceCheckSummaryItem("HTTP 状态", response.httpCode.toString()),
                SourceCheckSummaryItem("内容类型", response.contentType ?: "未知"),
                SourceCheckSummaryItem("节目数量", response.feed.episodes.size.toString()),
                SourceCheckSummaryItem("音频格式", audioTypes),
                SourceCheckSummaryItem("检测延迟", "${response.latencyMs} ms"),
                SourceCheckSummaryItem("样例节目", sampleEpisodes)
            ),
            returnedContent = response.rawContent.toDialogContent()
        )
    }

    private suspend fun checkSubscriptionForBatch(subscription: PodcastSubscriptionEntity): PodcastSubscriptionEntity {
        val result = podcastRepository.checkPodcastSource(subscription.url)
        return result.fold(
            onSuccess = { response ->
                val now = System.currentTimeMillis()
                subscription.copy(
                    title = response.feed.title,
                    description = response.feed.description,
                    imageUrl = response.feed.imageUrl,
                    link = response.feed.link,
                    episodeCount = response.feed.episodes.size,
                    lastRefreshTime = now,
                    enabled = true,
                    lastCheckStatus = "可用",
                    lastCheckTime = now
                )
            },
            onFailure = { error ->
                val status = if (error is TimeoutCancellationException) {
                    "超时"
                } else {
                    classifySourceCheckFailure(error).statusText
                }
                subscription.copy(
                    lastCheckStatus = status,
                    lastCheckTime = System.currentTimeMillis()
                )
            }
        )
    }

    private fun String.toDialogContent(maxLength: Int = 4000): String {
        if (length <= maxLength) return this
        return take(maxLength) + "\n\n...返回内容较长，仅展示前 ${maxLength} 字。"
    }

    private fun String.normalizeSourceUrl(): String = trim().trimEnd('/')

    private companion object {
        const val PODCAST_REFRESH_PARALLELISM = 4
    }
}
