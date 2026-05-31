package com.liuguang.media.ui.screens.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liuguang.media.data.local.entity.VideoSiteEntity
import com.liuguang.media.data.remote.VodItem
import com.liuguang.media.data.repository.SiteRepository
import com.liuguang.media.data.repository.VodRepository
import com.liuguang.media.domain.model.EpisodeGroup
import com.liuguang.media.domain.parser.VodPlayUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DetailSourceOption(
    val siteId: Long,
    val siteName: String,
    val vodId: String,
    val vodDetail: VodItem,
    val episodeGroups: List<EpisodeGroup>
) {
    val key: String = "$siteId:$vodId"
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(
        val selectedSource: DetailSourceOption,
        val sourceOptions: List<DetailSourceOption>,
        val isLoadingSources: Boolean = false
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val siteRepository: SiteRepository,
    private val vodRepository: VodRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "DetailViewModel"
        private const val SOURCE_AGGREGATION_PARALLELISM = 4
        private const val SOURCE_OPTIONS_BATCH_SIZE = 3
    }

    private val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L
    private val vodId: String = savedStateHandle.get<String>("vodId") ?: ""

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _isAscending = MutableStateFlow(true)
    val isAscending: StateFlow<Boolean> = _isAscending.asStateFlow()

    private var sourceOptions: List<DetailSourceOption> = emptyList()

    init {
        Log.d(TAG, "init - siteId=$siteId, vodId=$vodId")
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            Log.d(TAG, "loadDetail - Start loading detail for vodId=$vodId")

            val enabledSites = siteRepository.getEnabledSites()
            val site = enabledSites.find { it.id == siteId }
            if (site == null) {
                Log.e(TAG, "loadDetail - Site not found for siteId=$siteId")
                _uiState.value = DetailUiState.Error("视频源不存在")
                return@launch
            }

            Log.d(TAG, "loadDetail - Found site: ${site.name}, apiUrl=${site.apiUrl}")

            val primarySource = loadSourceDetail(site, vodId)
            if (primarySource == null) {
                Log.e(TAG, "loadDetail - VodDetail is null in response")
                _uiState.value = DetailUiState.Error("视频详情不存在")
                return@launch
            }

            sourceOptions = listOf(primarySource)
            _uiState.value = DetailUiState.Success(
                selectedSource = primarySource,
                sourceOptions = sourceOptions,
                isLoadingSources = enabledSites.size > 1
            )

            sourceOptions = loadSourceOptions(
                enabledSites = enabledSites,
                primarySource = primarySource
            )
            val currentState = _uiState.value as? DetailUiState.Success
            val selected = currentState
                ?.selectedSource
                ?.let { currentSelected ->
                    sourceOptions.firstOrNull { it.key == currentSelected.key }
                }
                ?: sourceOptions.firstOrNull { it.key == primarySource.key }
                ?: primarySource
            _uiState.value = DetailUiState.Success(
                selectedSource = selected,
                sourceOptions = sourceOptions,
                isLoadingSources = false
            )
        }
    }

    fun selectSource(sourceKey: String) {
        val currentState = _uiState.value as? DetailUiState.Success ?: return
        val selected = currentState.sourceOptions.firstOrNull { it.key == sourceKey } ?: return
        Log.d(TAG, "selectSource - source=${selected.siteName}, vodId=${selected.vodId}")
        _uiState.value = currentState.copy(selectedSource = selected)
    }

    fun toggleSortOrder() {
        _isAscending.value = !_isAscending.value
        Log.d(TAG, "toggleSortOrder - isAscending=${_isAscending.value}")
    }

    private suspend fun loadSourceOptions(
        enabledSites: List<VideoSiteEntity>,
        primarySource: DetailSourceOption
    ): List<DetailSourceOption> = coroutineScope {
        val options = mutableListOf(primarySource)
        val title = primarySource.vodDetail.vod_name
        val extraSites = enabledSites.filterNot { it.id == primarySource.siteId }
        val resultChannel = Channel<DetailSourceOption?>(Channel.UNLIMITED)
        val semaphore = Semaphore(SOURCE_AGGREGATION_PARALLELISM)
        var pendingUiUpdateCount = 0
        var receivedCount = 0

        extraSites.forEach { site ->
            launch(Dispatchers.IO) {
                val source = semaphore.withPermit {
                    val matchedVod = vodRepository.getVodList(
                        baseUrl = site.apiUrl,
                        page = 1,
                        keyword = title
                    ).getOrNull()
                            ?.list
                            .orEmpty()
                            .let { list ->
                                list.firstOrNull { normalizeTitle(it.vod_name) == normalizeTitle(title) }
                                    ?: list.firstOrNull { normalizeTitle(it.vod_name).contains(normalizeTitle(title)) }
                            }

                    matchedVod?.let { vod ->
                        loadSourceDetail(site, vod.vod_id.toString())
                    }
                }

                resultChannel.send(source)
            }
        }

        repeat(extraSites.size) {
            receivedCount++
            val source = resultChannel.receive()
            if (source != null) {
                options += source
                pendingUiUpdateCount++
            }

            if (pendingUiUpdateCount > 0 && shouldPublishSourceOptions(receivedCount, extraSites.size, pendingUiUpdateCount)) {
                val currentState = _uiState.value as? DetailUiState.Success
                if (currentState != null) {
                    val mergedOptions = (currentState.sourceOptions + options).distinctBy { it.key }
                    _uiState.value = currentState.copy(
                        sourceOptions = mergedOptions,
                        isLoadingSources = true
                    )
                }
                pendingUiUpdateCount = 0
            }
        }
        resultChannel.close()

        Log.d(TAG, "loadSourceOptions - loaded ${options.size} source options")
        options.distinctBy { it.key }
    }

    private fun shouldPublishSourceOptions(
        receivedCount: Int,
        totalCount: Int,
        pendingCount: Int
    ): Boolean {
        return pendingCount >= SOURCE_OPTIONS_BATCH_SIZE || receivedCount >= totalCount
    }

    private suspend fun loadSourceDetail(
        site: VideoSiteEntity,
        sourceVodId: String
    ): DetailSourceOption? {
        return vodRepository.getVodDetail(site.apiUrl, sourceVodId).getOrNull()
            ?.list
            ?.firstOrNull()
            ?.let { vodDetail ->
                Log.d(TAG, "loadSourceDetail - ${site.name}, vodName=${vodDetail.vod_name}, vodId=$sourceVodId")
                Log.d(TAG, "loadSourceDetail - vod_play_from: ${vodDetail.vod_play_from}")
                Log.d(TAG, "loadSourceDetail - vod_play_url: ${vodDetail.vod_play_url}")

                val episodeGroups = withContext(Dispatchers.Default) {
                    VodPlayUrlParser.parseGroups(
                        vodDetail.vod_play_from,
                        vodDetail.vod_play_url
                    )
                }
                if (episodeGroups.isEmpty()) {
                    Log.d(TAG, "loadSourceDetail - source=${site.name}, no m3u8 episodes, skipped")
                    return@let null
                }

                Log.d(
                    TAG,
                    "loadSourceDetail - source=${site.name}, groups=${episodeGroups.size}, episodes=${episodeGroups.sumOf { it.episodes.size }}"
                )
                DetailSourceOption(
                    siteId = site.id,
                    siteName = site.name,
                    vodId = vodDetail.vod_id.toString(),
                    vodDetail = vodDetail,
                    episodeGroups = episodeGroups
                )
            }
    }

    private fun normalizeTitle(value: String): String {
        return value
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
            .replace(":", "")
            .replace("：", "")
    }
}
