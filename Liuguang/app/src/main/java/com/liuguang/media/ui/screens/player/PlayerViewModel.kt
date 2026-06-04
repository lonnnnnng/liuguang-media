package com.liuguang.media.ui.screens.player

import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import com.liuguang.media.data.repository.HistoryRepository
import com.liuguang.media.data.repository.LiveRepository
import com.liuguang.media.data.repository.NetworkSettingsRepository
import com.liuguang.media.data.repository.SiteRepository
import com.liuguang.media.data.repository.VodRepository
import com.liuguang.media.domain.model.EpisodeGroup
import com.liuguang.media.domain.model.EpisodeItem
import com.liuguang.media.domain.parser.VodPlayUrlParser
import com.liuguang.media.player.AudioPlaybackQueueStore
import com.liuguang.media.player.AudioPlaybackService
import com.liuguang.media.player.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.content.Context

data class PlaybackUiState(
    val sourceName: String = "线路",
    val message: String = "正在检测线路",
    val isRecovering: Boolean = true,
    val isFailed: Boolean = false
)

data class PlayerSourceOption(
    val key: String,
    val sourceName: String,
    val episodeLabel: String,
    val isCurrent: Boolean
)

data class EpisodeNavigationState(
    val currentLabel: String = "",
    val previousLabel: String? = null,
    val nextLabel: String? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false
)

data class PlaybackStats(
    val resolutionWidth: Int = 0,
    val resolutionHeight: Int = 0,
    val videoBitrateBitsPerSecond: Long = 0L,
    val networkSpeedBitsPerSecond: Long = 0L
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val historyRepository: HistoryRepository,
    private val siteRepository: SiteRepository,
    private val vodRepository: VodRepository,
    private val okHttpClient: OkHttpClient,
    private val networkSettingsRepository: NetworkSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val MIN_VIDEO_BITRATE_BITS_PER_SECOND = 50_000
        private const val NETWORK_SPEED_UPDATE_INTERVAL_MS = 1_000L
        private const val NETWORK_SPEED_WINDOW_MS = 3_000L
    }

    private val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L
    private val vodId: String = savedStateHandle.get<String>("vodId") ?: ""
    val episodeUrl: String = savedStateHandle.get<String>("episodeUrl") ?: ""
    private val title: String = savedStateHandle.get<String>("title").orEmpty()
    private val episodeLabel: String = savedStateHandle.get<String>("episodeLabel").orEmpty()
    private val startPositionMs: Long = savedStateHandle.get<Long>("startPositionMs")
        ?.coerceAtLeast(0L)
        ?: 0L

    private data class PlaybackCandidate(
        val siteId: Long,
        val vodId: String,
        val url: String,
        val sourceName: String,
        val episodeLabel: String
    ) {
        val key: String = url
    }

    private data class EpisodeNavigationItem(
        val label: String,
        val url: String,
        val groupName: String
    )

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _activeEpisodeUrl = MutableStateFlow(episodeUrl)
    val activeEpisodeUrl: StateFlow<String> = _activeEpisodeUrl.asStateFlow()

    private val _activeEpisodeLabel = MutableStateFlow(episodeLabel)
    val activeEpisodeLabel: StateFlow<String> = _activeEpisodeLabel.asStateFlow()

    private val _playbackUiState = MutableStateFlow(PlaybackUiState())
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _sourceOptions = MutableStateFlow<List<PlayerSourceOption>>(emptyList())
    val sourceOptions: StateFlow<List<PlayerSourceOption>> = _sourceOptions.asStateFlow()

    private val _episodeNavigation = MutableStateFlow(
        EpisodeNavigationState(currentLabel = episodeLabel)
    )
    val episodeNavigation: StateFlow<EpisodeNavigationState> = _episodeNavigation.asStateFlow()

    private val _playbackStats = MutableStateFlow(PlaybackStats())
    val playbackStats: StateFlow<PlaybackStats> = _playbackStats.asStateFlow()

    private var progressUpdateJob: Job? = null
    private val playbackCandidates = mutableListOf<PlaybackCandidate>()
    private var currentCandidateIndex = 0
    private var fallbackCandidatesLoaded = false
    private var fallbackLoadJob: Job? = null
    private var currentEpisodeLabel = episodeLabel
    private var episodeNavigationSiteId = siteId
    private var episodeNavigationVodId = vodId
    private var episodeNavigationGroupName = ""
    private var episodeNavigationItems = emptyList<EpisodeNavigationItem>()
    private var episodeNavigationIndex = -1
    private var episodeNavigationJob: Job? = null
    private var episodeNavigationRequestKey = ""
    private var isRecoveringPlayback = false
    private var networkSpeedUpdateJob: Job? = null
    private var pendingInitialSeekMs = startPositionMs
    private val transferSamples = ArrayDeque<TransferSample>()
    private val transferSamplesLock = Any()

    private data class TransferSample(
        val timestampMs: Long,
        val bytes: Long
    )

    private val transferByteListener: (Int) -> Unit = { bytes ->
        recordTransferredBytes(bytes)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged - isPlaying=$isPlaying")
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateStr = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "onPlaybackStateChanged - state=$stateStr")
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val sourceName = playbackCandidates.getOrNull(currentCandidateIndex)?.sourceName ?: "线路"
                    _playbackUiState.value = PlaybackUiState(
                        sourceName = sourceName,
                        message = "$sourceName 正在缓冲",
                        isRecovering = false
                    )
                }
                Player.STATE_READY -> {
                    _duration.value = playerManager.getDuration().coerceAtLeast(0L)
                    seekToPendingInitialPosition()
                    val sourceName = playbackCandidates.getOrNull(currentCandidateIndex)?.sourceName ?: "线路"
                    _playbackUiState.value = PlaybackUiState(
                        sourceName = sourceName,
                        message = "$sourceName 已接入",
                        isRecovering = false
                    )
                    Log.d(TAG, "onPlaybackStateChanged - duration=${_duration.value}ms")
                }
                Player.STATE_ENDED -> {
                    val sourceName = playbackCandidates.getOrNull(currentCandidateIndex)?.sourceName ?: "线路"
                    _playbackUiState.value = PlaybackUiState(
                        sourceName = sourceName,
                        message = "播放完成",
                        isRecovering = false
                    )
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError - errorCode=${error.errorCode}, message=${error.message}", error)
            recoverFromPlaybackError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateVideoFormat(selectCurrentVideoFormat(tracks))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoSize(videoSize.width, videoSize.height)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData
        ) {
            if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) {
                updateVideoFormat(mediaLoadData.trackFormat)
            }
        }
    }

    init {
        Log.d(TAG, "init - siteId=$siteId, vodId=$vodId")
        Log.d(TAG, "init - episodeUrl=$episodeUrl")
        playerManager.addListener(playerListener)
        playerManager.addAnalyticsListener(analyticsListener)
        playerManager.addTransferByteListener(transferByteListener)
        startNetworkSpeedUpdates()
        if (episodeUrl.isNotBlank()) {
            playbackCandidates += PlaybackCandidate(
                siteId = siteId,
                vodId = vodId,
                url = episodeUrl,
                sourceName = sourceNameFromUrl(episodeUrl),
                episodeLabel = episodeLabel
            )
            publishSourceOptions()
            viewModelScope.launch {
                startInitialPlayback()
            }
            preloadEpisodeNavigationForCurrentCandidate()
        } else {
            Log.w(TAG, "init - episodeUrl is blank, not starting playback")
            _playbackUiState.value = PlaybackUiState(
                message = "没有可播放地址",
                isRecovering = false,
                isFailed = true
            )
        }
    }

    fun getPlayer() = playerManager.getPlayer()

    fun togglePlayPause() {
        if (_isPlaying.value) {
            Log.d(TAG, "togglePlayPause - Pausing")
            playerManager.pause()
        } else {
            Log.d(TAG, "togglePlayPause - Resuming")
            playerManager.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        Log.d(TAG, "seekTo - position=${positionMs}ms")
        playerManager.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setSpeed(speed: Float) {
        Log.d(TAG, "setSpeed - speed=${speed}x")
        playerManager.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun stopPlayback() {
        Log.d(TAG, "stopPlayback")
        _isPlaying.value = false
        stopProgressUpdates()
        stopNetworkSpeedUpdates()
        fallbackLoadJob?.cancel()
        episodeNavigationJob?.cancel()
        playerManager.stopAndRelease()
    }

    fun retryPlayback() {
        viewModelScope.launch {
            if (playbackCandidates.isEmpty()) {
                startInitialPlayback()
            } else {
                playCandidate(currentCandidateIndex.coerceIn(0, playbackCandidates.lastIndex))
            }
        }
    }

    fun loadSourceOptions() {
        publishSourceOptions()
        if (fallbackCandidatesLoaded) return

        val loadingMessage = if (title.isNotBlank()) {
            "正在查找《$title》的备用线路"
        } else {
            "正在查找备用线路"
        }
        _playbackUiState.value = PlaybackUiState(
            sourceName = currentSourceName(),
            message = loadingMessage,
            isRecovering = true
        )
        preloadFallbackCandidates()
    }

    fun switchToSource(sourceKey: String) {
        viewModelScope.launch {
            val index = playbackCandidates.indexOfFirst { it.key == sourceKey }
            if (index >= 0) {
                val candidate = playbackCandidates[index]
                Log.d(TAG, "switchToSource - index=$index, source=${candidate.sourceName}")
                playCandidate(index)
            } else {
                Log.w(TAG, "switchToSource - source not found: $sourceKey")
                _playbackUiState.value = PlaybackUiState(
                    sourceName = currentSourceName(),
                    message = "正在查找备用线路",
                    isRecovering = true
                )
                preloadFallbackCandidates()
            }
        }
    }

    fun playPreviousEpisode() {
        playAdjacentEpisode(-1)
    }

    fun playNextEpisode() {
        playAdjacentEpisode(1)
    }

    private suspend fun startInitialPlayback() {
        val candidate = playbackCandidates.firstOrNull()
        if (candidate == null) {
            _playbackUiState.value = PlaybackUiState(
                message = "没有可播放地址",
                isRecovering = false,
                isFailed = true
            )
            return
        }

        _playbackUiState.value = PlaybackUiState(
            sourceName = candidate.sourceName,
            message = "正在检测 ${candidate.sourceName}",
            isRecovering = true
        )

        if (isPlayableCandidate(candidate)) {
            playCandidate(0)
            return
        }

        Log.w(TAG, "startInitialPlayback - initial URL is not playable, url=${candidate.url}")
        val nextIndex = findNextPlayableCandidate()
        if (nextIndex != null) {
            playCandidate(nextIndex)
        } else {
            _playbackUiState.value = PlaybackUiState(
                sourceName = candidate.sourceName,
                message = "当前影片所有已知线路均不可播放",
                isRecovering = false,
                isFailed = true
            )
        }
    }

    private fun playCandidate(index: Int) {
        val candidate = playbackCandidates.getOrNull(index) ?: return
        currentCandidateIndex = index
        _activeEpisodeUrl.value = candidate.url
        currentEpisodeLabel = candidate.episodeLabel
        _activeEpisodeLabel.value = candidate.episodeLabel
        _currentPosition.value = 0L
        _duration.value = 0L
        resetNetworkSpeedSamples()
        _playbackStats.value = PlaybackStats()
        publishSourceOptions()
        syncEpisodeNavigationWithCandidate(candidate)
        preloadEpisodeNavigationForCurrentCandidate()
        _playbackUiState.value = PlaybackUiState(
            sourceName = candidate.sourceName,
            message = "正在连接 ${candidate.sourceName}",
            isRecovering = index > 0
        )
        Log.d(TAG, "playCandidate - index=$index, source=${candidate.sourceName}, label=${candidate.episodeLabel}, url=${candidate.url}")
        playerManager.play(candidate.url)
    }

    private fun seekToPendingInitialPosition() {
        val targetPosition = pendingInitialSeekMs
        val durationMs = _duration.value
        if (targetPosition <= 0L || durationMs <= 0L) return

        val safePosition = targetPosition.coerceIn(0L, (durationMs - 1_000L).coerceAtLeast(0L))
        if (safePosition <= 0L) {
            pendingInitialSeekMs = 0L
            return
        }

        Log.d(TAG, "seekToPendingInitialPosition - position=${safePosition}ms")
        pendingInitialSeekMs = 0L
        playerManager.seekTo(safePosition)
        _currentPosition.value = safePosition
    }

    private fun recoverFromPlaybackError(error: PlaybackException) {
        if (isRecoveringPlayback) return
        isRecoveringPlayback = true
        viewModelScope.launch {
            val failedSource = playbackCandidates.getOrNull(currentCandidateIndex)?.sourceName ?: "当前线路"
            _playbackUiState.value = PlaybackUiState(
                sourceName = failedSource,
                message = "$failedSource 播放失败，正在切换备用线路",
                isRecovering = true
            )

            val nextIndex = findNextPlayableCandidate()
            if (nextIndex != null) {
                playCandidate(nextIndex)
            } else {
                _playbackUiState.value = PlaybackUiState(
                    sourceName = failedSource,
                    message = "所有已知线路均不可播放：${error.message ?: "资源失效"}",
                    isRecovering = false,
                    isFailed = true
                )
            }
            isRecoveringPlayback = false
        }
    }

    private suspend fun findNextPlayableCandidate(): Int? {
        var nextIndex = currentCandidateIndex + 1
        while (nextIndex < playbackCandidates.size) {
            if (isPlayableCandidate(playbackCandidates[nextIndex])) return nextIndex
            nextIndex++
        }

        if (!fallbackCandidatesLoaded) {
            fallbackLoadJob?.join()
            if (!fallbackCandidatesLoaded) {
                mergePlaybackCandidates(loadFallbackCandidates(updateUi = true))
                fallbackCandidatesLoaded = true
            }
        }

        nextIndex = currentCandidateIndex + 1
        while (nextIndex < playbackCandidates.size) {
            if (isPlayableCandidate(playbackCandidates[nextIndex])) return nextIndex
            nextIndex++
        }
        return null
    }

    private suspend fun ensureFallbackCandidatesLoaded() {
        if (!fallbackCandidatesLoaded) {
            fallbackLoadJob?.join()
            if (!fallbackCandidatesLoaded) {
                mergePlaybackCandidates(loadFallbackCandidates(updateUi = true))
                fallbackCandidatesLoaded = true
            }
        }
        publishSourceOptions()
    }

    private fun preloadFallbackCandidates() {
        if (fallbackCandidatesLoaded || fallbackLoadJob?.isActive == true) return
        fallbackLoadJob = viewModelScope.launch {
            mergePlaybackCandidates(loadFallbackCandidates(updateUi = false))
            fallbackCandidatesLoaded = true
        }
    }

    private suspend fun loadFallbackCandidates(updateUi: Boolean): List<PlaybackCandidate> {
        val targetEpisodeLabel = currentEpisodeLabel
        val targetTitle = title
        if (targetTitle.isBlank() || targetEpisodeLabel.isBlank()) return emptyList()

        if (updateUi) {
            _playbackUiState.value = PlaybackUiState(
                sourceName = currentSourceName(),
                message = "正在查找《$targetTitle》的备用线路",
                isRecovering = true
            )
        }

        val candidates = withContext(Dispatchers.IO) {
            val collected = mutableListOf<PlaybackCandidate>()
            val enabledSites = siteRepository.getEnabledSites()
            val normalizedTargetTitle = normalizeTitle(targetTitle)
            enabledSites.forEach { site ->
                val detailItems = if (site.id == siteId && vodId.isNotBlank()) {
                    vodRepository.getVodDetail(
                        site.apiUrl,
                        vodId,
                        timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
                    ).getOrNull()
                        ?.list
                        .orEmpty()
                } else {
                    val searchResults = vodRepository.getVodList(
                        baseUrl = site.apiUrl,
                        page = 1,
                        keyword = targetTitle,
                        timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
                    ).getOrNull()?.list.orEmpty()
                    val matched = withContext(Dispatchers.Default) {
                        searchResults.firstOrNull {
                            normalizeTitle(it.vod_name) == normalizedTargetTitle
                        } ?: searchResults.firstOrNull {
                            val normalizedName = normalizeTitle(it.vod_name)
                            normalizedName.contains(normalizedTargetTitle) ||
                                normalizedTargetTitle.contains(normalizedName)
                        } ?: searchResults.firstOrNull()
                    }
                    matched?.let { item ->
                        vodRepository.getVodDetail(
                            site.apiUrl,
                            item.vod_id.toString(),
                            timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
                        ).getOrNull()?.list.orEmpty()
                    }.orEmpty()
                }

                detailItems.firstOrNull()?.let { vod ->
                    val sourceCandidates = withContext(Dispatchers.Default) {
                        val groups = VodPlayUrlParser.parseGroups(vod.vod_play_from, vod.vod_play_url)
                        buildCandidatesForEpisode(
                            siteId = site.id,
                            vodId = vod.vod_id.toString(),
                            sourceName = site.name,
                            groups = groups,
                            label = targetEpisodeLabel
                        )
                    }
                    collected += sourceCandidates
                }
            }
            collected
        }

        Log.d(TAG, "loadFallbackCandidates - loaded ${candidates.size} candidates")
        return candidates.distinctBy { it.url }
    }

    private fun buildCandidatesForEpisode(
        siteId: Long,
        vodId: String,
        sourceName: String,
        groups: List<EpisodeGroup>,
        label: String
    ): List<PlaybackCandidate> {
        val exactMatches = groups.flatMap { group ->
            group.episodes
                .filter { labelsMatch(it.label, label) }
                .map { group to it }
        }
        val fallbackMatches = if (exactMatches.isEmpty()) {
            groups.mapNotNull { group -> group.episodes.firstOrNull()?.let { group to it } }
        } else {
            exactMatches
        }

        return fallbackMatches
            .sortedWith(
                compareByDescending<Pair<EpisodeGroup, EpisodeItem>> {
                    it.second.url.contains(".m3u8", ignoreCase = true)
                }.thenByDescending {
                    it.first.name.contains("m3u8", ignoreCase = true) ||
                        it.first.name.contains("hls", ignoreCase = true)
                }
            )
            .map { (group, episode) ->
                PlaybackCandidate(
                    siteId = siteId,
                    vodId = vodId,
                    url = episode.url,
                    sourceName = "$sourceName/${group.name}",
                    episodeLabel = episode.label
                )
            }
    }

    private suspend fun isPlayableCandidate(candidate: PlaybackCandidate): Boolean = withContext(Dispatchers.IO) {
        val url = candidate.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext false

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .header("Referer", "https://www.baidu.com/")
            .header("Range", "bytes=0-4095")
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).apply {
                timeout().timeout(
                    networkSettingsRepository.currentSettings().videoSourceTimeoutMs,
                    TimeUnit.MILLISECONDS
                )
            }.execute().use { response ->
                val bodySample = response.peekBody(4096).string()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val playable = response.isSuccessful && (
                    bodySample.contains("#EXTM3U", ignoreCase = true) ||
                        contentType.contains("mpegurl") ||
                        contentType.contains("video")
                    )
                Log.d(
                    TAG,
                    "isPlayableCandidate - source=${candidate.sourceName}, code=${response.code}, type=$contentType, playable=$playable, url=$url"
                )
                playable
            }
        }.getOrElse { error ->
            Log.w(TAG, "isPlayableCandidate - probe failed: ${error.message}, url=$url")
            false
        }
    }

    private fun mergePlaybackCandidates(candidates: List<PlaybackCandidate>) {
        candidates.forEach { candidate ->
            val existingIndex = playbackCandidates.indexOfFirst { it.url == candidate.url }
            if (existingIndex >= 0) {
                playbackCandidates[existingIndex] = candidate
            } else {
                playbackCandidates += candidate
            }
        }
        publishSourceOptions()
    }

    private fun publishSourceOptions() {
        _sourceOptions.value = playbackCandidates.mapIndexed { index, candidate ->
            PlayerSourceOption(
                key = candidate.key,
                sourceName = candidate.sourceName,
                episodeLabel = candidate.episodeLabel,
                isCurrent = index == currentCandidateIndex
            )
        }
    }

    private fun preloadEpisodeNavigationForCurrentCandidate() {
        val candidate = playbackCandidates.getOrNull(currentCandidateIndex) ?: return
        if (candidate.siteId <= 0L || candidate.vodId.isBlank() || candidate.vodId == "online") return
        val requestKey = episodeNavigationRequestKey(candidate)
        if (
            episodeNavigationItems.isNotEmpty() &&
            episodeNavigationSiteId == candidate.siteId &&
            episodeNavigationVodId == candidate.vodId
        ) {
            val candidateGroupName = episodeNavigationGroupName(candidate)
            val belongsToCurrentGroup = episodeNavigationItems.any { it.url == candidate.url } ||
                candidateGroupName.isBlank() ||
                candidateGroupName == episodeNavigationGroupName
            if (belongsToCurrentGroup) {
                syncEpisodeNavigationWithCandidate(candidate)
                return
            }
            episodeNavigationItems = emptyList()
            episodeNavigationIndex = -1
            _episodeNavigation.value = EpisodeNavigationState(currentLabel = candidate.episodeLabel)
        }
        if (episodeNavigationJob?.isActive == true && episodeNavigationRequestKey == requestKey) return
        episodeNavigationJob?.cancel()
        episodeNavigationRequestKey = requestKey

        episodeNavigationJob = viewModelScope.launch {
            loadEpisodeNavigation(candidate)
        }
    }

    private suspend fun loadEpisodeNavigation(candidate: PlaybackCandidate) {
        val site = siteRepository.getAllSites().firstOrNull { it.id == candidate.siteId } ?: return
        val vod = vodRepository.getVodDetail(
            site.apiUrl,
            candidate.vodId,
            timeoutMs = networkSettingsRepository.currentSettings().videoSourceTimeoutMs
        ).getOrNull()?.list?.firstOrNull() ?: return

        val navigationResult = withContext(Dispatchers.Default) {
            val groups = VodPlayUrlParser.parseGroups(vod.vod_play_from, vod.vod_play_url)
            val selectedGroup = selectEpisodeNavigationGroup(groups, candidate) ?: return@withContext null
            val items = selectedGroup.episodes.map { episode ->
                EpisodeNavigationItem(
                    label = episode.label,
                    url = episode.url,
                    groupName = selectedGroup.name
                )
            }
            selectedGroup.name to items
        } ?: return
        val selectedGroupName = navigationResult.first
        val items = navigationResult.second
        if (items.isEmpty()) return

        val activeCandidate = playbackCandidates.getOrNull(currentCandidateIndex) ?: return
        if (episodeNavigationRequestKey(candidate) != episodeNavigationRequestKey) return
        if (activeCandidate.siteId != candidate.siteId || activeCandidate.vodId != candidate.vodId) return
        if (activeCandidate.url != candidate.url && !labelsMatch(activeCandidate.episodeLabel, candidate.episodeLabel)) return

        episodeNavigationSiteId = candidate.siteId
        episodeNavigationVodId = candidate.vodId
        episodeNavigationGroupName = selectedGroupName
        episodeNavigationItems = items
        episodeNavigationIndex = findEpisodeIndex(items, candidate)
        publishEpisodeNavigation()
    }

    private fun selectEpisodeNavigationGroup(
        groups: List<EpisodeGroup>,
        candidate: PlaybackCandidate
    ): EpisodeGroup? {
        return groups.firstOrNull { group ->
            group.episodes.any { it.url == candidate.url }
        } ?: groups.firstOrNull { group ->
            val candidateLineName = episodeNavigationGroupName(candidate)
            candidateLineName.isNotBlank() && group.name == candidateLineName
        } ?: groups.firstOrNull { group ->
            group.episodes.any { labelsMatch(it.label, candidate.episodeLabel) }
        } ?: groups.firstOrNull()
    }

    private fun findEpisodeIndex(
        items: List<EpisodeNavigationItem>,
        candidate: PlaybackCandidate
    ): Int {
        return items.indexOfFirst { it.url == candidate.url }
            .takeIf { it >= 0 }
            ?: items.indexOfFirst { labelsMatch(it.label, candidate.episodeLabel) }
                .takeIf { it >= 0 }
            ?: 0
    }

    private fun syncEpisodeNavigationWithCandidate(candidate: PlaybackCandidate) {
        if (episodeNavigationItems.isEmpty()) {
            _episodeNavigation.value = EpisodeNavigationState(currentLabel = candidate.episodeLabel)
            return
        }
        if (episodeNavigationSiteId != candidate.siteId || episodeNavigationVodId != candidate.vodId) {
            episodeNavigationItems = emptyList()
            episodeNavigationIndex = -1
            _episodeNavigation.value = EpisodeNavigationState(currentLabel = candidate.episodeLabel)
            return
        }
        episodeNavigationIndex = findEpisodeIndex(episodeNavigationItems, candidate)
        publishEpisodeNavigation()
    }

    private fun publishEpisodeNavigation() {
        val index = episodeNavigationIndex
        val currentItem = episodeNavigationItems.getOrNull(index)
        _episodeNavigation.value = EpisodeNavigationState(
            currentLabel = currentItem?.label ?: currentEpisodeLabel,
            previousLabel = episodeNavigationItems.getOrNull(index - 1)?.label,
            nextLabel = episodeNavigationItems.getOrNull(index + 1)?.label,
            hasPrevious = index > 0,
            hasNext = index >= 0 && index < episodeNavigationItems.lastIndex
        )
    }

    private fun playAdjacentEpisode(delta: Int) {
        val targetIndex = episodeNavigationIndex + delta
        val target = episodeNavigationItems.getOrNull(targetIndex) ?: return
        val currentCandidate = playbackCandidates.getOrNull(currentCandidateIndex)
        val candidate = PlaybackCandidate(
            siteId = currentCandidate?.siteId ?: episodeNavigationSiteId,
            vodId = currentCandidate?.vodId ?: episodeNavigationVodId,
            url = target.url,
            sourceName = buildAdjacentEpisodeSourceName(currentCandidate?.sourceName, target.groupName),
            episodeLabel = target.label
        )

        fallbackLoadJob?.cancel()
        fallbackCandidatesLoaded = false
        playbackCandidates.clear()
        playbackCandidates += candidate
        currentCandidateIndex = 0
        episodeNavigationIndex = targetIndex
        currentEpisodeLabel = target.label
        _activeEpisodeLabel.value = target.label
        publishSourceOptions()
        publishEpisodeNavigation()
        playCandidate(0)
    }

    private fun buildAdjacentEpisodeSourceName(currentSourceName: String?, groupName: String): String {
        if (currentSourceName.isNullOrBlank()) {
            return groupName.ifBlank { "当前线路" }
        }
        if (groupName.isBlank()) return currentSourceName
        val siteName = currentSourceName.substringBefore('/', missingDelimiterValue = "")
        return if (siteName.isNotBlank() && siteName != currentSourceName) {
            "$siteName/$groupName"
        } else {
            currentSourceName
        }
    }

    private fun episodeNavigationRequestKey(candidate: PlaybackCandidate): String {
        return "${candidate.siteId}|${candidate.vodId}|${candidate.url}|${candidate.episodeLabel}"
    }

    private fun episodeNavigationGroupName(candidate: PlaybackCandidate): String {
        return candidate.sourceName.substringAfterLast('/', missingDelimiterValue = "")
    }

    private fun currentSourceName(): String {
        return playbackCandidates.getOrNull(currentCandidateIndex)?.sourceName ?: "当前线路"
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                _currentPosition.value = playerManager.getCurrentPosition()
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    private fun startNetworkSpeedUpdates() {
        networkSpeedUpdateJob?.cancel()
        networkSpeedUpdateJob = viewModelScope.launch {
            while (isActive) {
                publishMeasuredNetworkSpeed()
                delay(NETWORK_SPEED_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopNetworkSpeedUpdates() {
        networkSpeedUpdateJob?.cancel()
        networkSpeedUpdateJob = null
    }

    private fun recordTransferredBytes(bytes: Int) {
        if (bytes <= 0) return
        val now = SystemClock.elapsedRealtime()
        synchronized(transferSamplesLock) {
            transferSamples += TransferSample(now, bytes.toLong())
            pruneTransferSamplesLocked(now)
        }
    }

    private fun resetNetworkSpeedSamples() {
        synchronized(transferSamplesLock) {
            transferSamples.clear()
        }
    }

    private fun publishMeasuredNetworkSpeed() {
        val now = SystemClock.elapsedRealtime()
        val speedBitsPerSecond = synchronized(transferSamplesLock) {
            pruneTransferSamplesLocked(now)
            val totalBytes = transferSamples.sumOf { it.bytes }
            if (totalBytes <= 0L) {
                0L
            } else {
                val firstSampleTime = transferSamples.first().timestampMs
                val windowMs = (now - firstSampleTime).coerceAtLeast(NETWORK_SPEED_UPDATE_INTERVAL_MS)
                totalBytes.coerceAtMost(Long.MAX_VALUE / 8_000L) * 8_000L / windowMs
            }
        }

        _playbackStats.value = _playbackStats.value.copy(
            networkSpeedBitsPerSecond = speedBitsPerSecond
        )
    }

    private fun pruneTransferSamplesLocked(now: Long) {
        while (transferSamples.isNotEmpty() && now - transferSamples.first().timestampMs > NETWORK_SPEED_WINDOW_MS) {
            transferSamples.removeFirst()
        }
    }

    private fun updateVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _playbackStats.value = _playbackStats.value.copy(
            resolutionWidth = width,
            resolutionHeight = height
        )
    }

    private fun updateVideoFormat(format: Format?) {
        if (format == null) return

        val nextWidth = format.width.takeIf { it > 0 } ?: _playbackStats.value.resolutionWidth
        val nextHeight = format.height.takeIf { it > 0 } ?: _playbackStats.value.resolutionHeight
        val nextBitrate = firstReasonableVideoBitrate(
            format.bitrate,
            format.averageBitrate,
            format.peakBitrate
        )?.toLong() ?: _playbackStats.value.videoBitrateBitsPerSecond

        _playbackStats.value = _playbackStats.value.copy(
            resolutionWidth = nextWidth,
            resolutionHeight = nextHeight,
            videoBitrateBitsPerSecond = nextBitrate
        )
    }

    private fun selectCurrentVideoFormat(tracks: Tracks): Format? {
        tracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                for (index in 0 until group.length) {
                    if (group.isTrackSelected(index)) {
                        return group.getTrackFormat(index)
                    }
                }
            }
        }
        return null
    }

    private fun firstReasonableVideoBitrate(vararg values: Int): Int? {
        return values.firstOrNull {
            it != Format.NO_VALUE && it >= MIN_VIDEO_BITRATE_BITS_PER_SECOND
        }
    }

    fun savePlaybackPosition(vodName: String, vodPic: String, episodeLabel: String) {
        viewModelScope.launch {
            Log.d(TAG, "savePlaybackPosition - vodName=$vodName, position=${_currentPosition.value}ms, duration=${_duration.value}ms")
            val activeCandidate = playbackCandidates.getOrNull(currentCandidateIndex)
            historyRepository.recordPlayback(
                siteId = activeCandidate?.siteId ?: siteId,
                vodId = activeCandidate?.vodId ?: vodId,
                vodName = vodName,
                vodPic = vodPic,
                episodeLabel = episodeLabel,
                episodeUrl = _activeEpisodeUrl.value,
                positionMs = _currentPosition.value,
                durationMs = _duration.value
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared - Cleaning up")
        stopProgressUpdates()
        stopNetworkSpeedUpdates()
        fallbackLoadJob?.cancel()
        episodeNavigationJob?.cancel()
        playerManager.stopAndRelease()
        playerManager.removeAnalyticsListener(analyticsListener)
        playerManager.removeTransferByteListener(transferByteListener)
        playerManager.removeListener(playerListener)
    }

    private fun labelsMatch(left: String, right: String): Boolean {
        if (left == right) return true
        val leftNumber = left.filter { it.isDigit() }.trimStart('0')
        val rightNumber = right.filter { it.isDigit() }.trimStart('0')
        return leftNumber.isNotBlank() && leftNumber == rightNumber
    }

    private fun normalizeTitle(value: String): String {
        return value
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
            .replace(":", "")
            .replace("：", "")
    }

    private fun sourceNameFromUrl(url: String): String {
        val host = android.net.Uri.parse(url).host.orEmpty()
        return host
            .split('.')
            .firstOrNull { it.length > 2 && it !in setOf("www", "vip", "vod") }
            ?: "当前线路"
    }
}

@HiltViewModel
class LivePlayerViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val liveRepository: LiveRepository,
    private val networkSettingsRepository: NetworkSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "LivePlayerViewModel"
        private const val MIN_VIDEO_BITRATE_BITS_PER_SECOND = 50_000
        private const val NETWORK_SPEED_UPDATE_INTERVAL_MS = 1_000L
        private const val NETWORK_SPEED_WINDOW_MS = 3_000L
    }

    private val liveUrl: String = savedStateHandle.get<String>("url") ?: ""
    private val channelTitle: String = savedStateHandle.get<String>("title").orEmpty()
    private val channelGroup: String = savedStateHandle.get<String>("group").orEmpty()
    private val channelFormat: String = savedStateHandle.get<String>("format").orEmpty()
    private val sourceId: Long = savedStateHandle.get<Long>("sourceId") ?: 0L

    private data class LivePlaybackCandidate(
        val url: String,
        val sourceName: String,
        val channelName: String,
        val group: String,
        val format: String
    ) {
        val key: String = url
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _activeLiveUrl = MutableStateFlow(liveUrl)
    val activeLiveUrl: StateFlow<String> = _activeLiveUrl.asStateFlow()

    private val _playbackUiState = MutableStateFlow(
        PlaybackUiState(
            sourceName = "IPTV直播",
            message = "正在连接直播流",
            isRecovering = true
        )
    )
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _playbackStats = MutableStateFlow(PlaybackStats())
    val playbackStats: StateFlow<PlaybackStats> = _playbackStats.asStateFlow()

    private val _lineOptions = MutableStateFlow<List<PlayerSourceOption>>(emptyList())
    val lineOptions: StateFlow<List<PlayerSourceOption>> = _lineOptions.asStateFlow()

    private var progressUpdateJob: Job? = null
    private var networkSpeedUpdateJob: Job? = null
    private val playbackCandidates = mutableListOf<LivePlaybackCandidate>()
    private val failedLineUrls = mutableSetOf<String>()
    private var currentCandidateIndex = 0
    private var sameNameLinesLoaded = false
    private var sameNameLinesLoading = false
    private var sameNameLinesJob: Job? = null
    private var isSwitchingLine = false
    private val transferSamples = ArrayDeque<TransferSample>()
    private val transferSamplesLock = Any()

    private data class TransferSample(
        val timestampMs: Long,
        val bytes: Long
    )

    private val transferByteListener: (Int) -> Unit = { bytes ->
        recordTransferredBytes(bytes)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged - isPlaying=$isPlaying")
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val sourceName = currentLineLabel()
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playbackUiState.value = PlaybackUiState(
                        sourceName = sourceName,
                        message = "$sourceName 正在缓冲",
                        isRecovering = true
                    )
                }
                Player.STATE_READY -> {
                    _duration.value = playerManager.getDuration().coerceAtLeast(0L)
                    playbackCandidates.getOrNull(currentCandidateIndex)?.let { candidate ->
                        failedLineUrls -= candidate.url
                    }
                    _playbackUiState.value = PlaybackUiState(
                        sourceName = sourceName,
                        message = "$sourceName 已接入直播流",
                        isRecovering = false
                    )
                }
                Player.STATE_ENDED -> {
                    recoverFromLivePlaybackError("直播已结束或中断")
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateVideoFormat(selectCurrentVideoFormat(tracks))
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoSize(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError - errorCode=${error.errorCode}, message=${error.message}", error)
            _isPlaying.value = false
            stopProgressUpdates()
            recoverFromLivePlaybackError(error.message ?: "网络不可达")
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            mediaLoadData: MediaLoadData
        ) {
            if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) {
                updateVideoFormat(mediaLoadData.trackFormat)
            }
        }
    }

    init {
        Log.d(TAG, "init - liveUrl=$liveUrl, sourceId=$sourceId, title=$channelTitle")
        playerManager.addListener(playerListener)
        playerManager.addAnalyticsListener(analyticsListener)
        playerManager.addTransferByteListener(transferByteListener)
        startNetworkSpeedUpdates()
        if (liveUrl.isNotBlank()) {
            playbackCandidates += LivePlaybackCandidate(
                url = liveUrl,
                sourceName = "当前直播源",
                channelName = channelTitle.ifBlank { "直播频道" },
                group = channelGroup,
                format = channelFormat.ifBlank { inferLiveFormat(liveUrl) }
            )
            publishLineOptions()
            playLiveCandidate(0)
            scheduleSameNameLinesPreload()
        } else {
            _playbackUiState.value = PlaybackUiState(
                sourceName = "IPTV直播",
                message = "没有可播放的直播地址",
                isRecovering = false,
                isFailed = true
            )
        }
    }

    fun getPlayer() = playerManager.getPlayer()

    fun togglePlayPause() {
        if (_isPlaying.value) {
            playerManager.pause()
        } else {
            playerManager.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        if (_duration.value <= 0L) return
        playerManager.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun retryPlayback() {
        if (playbackCandidates.isEmpty()) return
        failedLineUrls.clear()
        playLiveCandidate(currentCandidateIndex.coerceIn(0, playbackCandidates.lastIndex))
    }

    fun loadLiveLineOptions() {
        viewModelScope.launch {
            if (!sameNameLinesLoading) {
                sameNameLinesJob?.cancel()
            }
            ensureSameNameLinesLoaded()
        }
    }

    fun switchToLiveLine(sourceKey: String) {
        viewModelScope.launch {
            ensureSameNameLinesLoaded()
            val index = playbackCandidates.indexOfFirst { it.key == sourceKey }
            if (index >= 0) {
                val candidate = playbackCandidates[index]
                failedLineUrls -= candidate.url
                playLiveCandidate(index)
            } else {
                Log.w(TAG, "switchToLiveLine - source not found: $sourceKey")
            }
        }
    }

    fun stopPlayback() {
        Log.d(TAG, "stopPlayback")
        _isPlaying.value = false
        stopProgressUpdates()
        stopNetworkSpeedUpdates()
        sameNameLinesJob?.cancel()
        playerManager.stopAndRelease()
    }

    private fun playLiveCandidate(index: Int) {
        val candidate = playbackCandidates.getOrNull(index) ?: return
        currentCandidateIndex = index
        _activeLiveUrl.value = candidate.url
        _currentPosition.value = 0L
        _duration.value = 0L
        resetNetworkSpeedSamples()
        _playbackStats.value = PlaybackStats()
        publishLineOptions()
        _playbackUiState.value = PlaybackUiState(
            sourceName = currentLineLabel(),
            message = "正在连接 ${currentLineLabel()}",
            isRecovering = true
        )
        Log.d(TAG, "playLiveCandidate - index=$index, url=${candidate.url}")
        playerManager.play(candidate.url)
    }

    private fun recoverFromLivePlaybackError(reason: String) {
        if (isSwitchingLine) return
        isSwitchingLine = true
        viewModelScope.launch {
            val failedCandidate = playbackCandidates.getOrNull(currentCandidateIndex)
            failedCandidate?.let { failedLineUrls += it.url }
            val failedLine = currentLineLabel()
            _playbackUiState.value = PlaybackUiState(
                sourceName = failedLine,
                message = "$failedLine 不可用，正在切换同名备用线路",
                isRecovering = true
            )

            ensureSameNameLinesLoaded()
            val nextIndex = findNextLiveLineIndex()
            if (nextIndex != null) {
                playLiveCandidate(nextIndex)
            } else {
                _playbackUiState.value = PlaybackUiState(
                    sourceName = failedLine,
                    message = "同名频道暂无可用备用线路：$reason",
                    isRecovering = false,
                    isFailed = true
                )
                publishLineOptions()
            }
            isSwitchingLine = false
        }
    }

    private fun findNextLiveLineIndex(): Int? {
        if (playbackCandidates.size <= 1) return null
        val candidateIndices = ((currentCandidateIndex + 1)..playbackCandidates.lastIndex) +
            (0 until currentCandidateIndex)
        return candidateIndices.firstOrNull { index ->
            playbackCandidates[index].url !in failedLineUrls
        }
    }

    private fun scheduleSameNameLinesPreload() {
        if (sameNameLinesLoaded || sameNameLinesLoading || sameNameLinesJob?.isActive == true) return
        sameNameLinesJob = viewModelScope.launch {
            delay(2_500L)
            ensureSameNameLinesLoaded()
        }
    }

    private suspend fun ensureSameNameLinesLoaded() {
        if (sameNameLinesLoaded) {
            publishLineOptions()
            return
        }
        if (sameNameLinesLoading) return
        sameNameLinesLoading = true

        try {
            if (channelTitle.isBlank()) {
                sameNameLinesLoaded = true
                publishLineOptions()
                return
            }

            val enabledSources = liveRepository.getEnabledSources()
            if (enabledSources.isEmpty()) {
                sameNameLinesLoaded = true
                publishLineOptions()
                return
            }

            val candidates = withContext(Dispatchers.IO) {
                val targetName = normalizeLiveChannelName(channelTitle)
                val orderedSources = enabledSources.sortedBy { source ->
                    if (source.id == sourceId) 0 else 1
                }
                orderedSources.flatMap { source ->
                    liveRepository.fetchAndParseChannels(
                        source.url,
                        timeoutMs = networkSettingsRepository.currentSettings().liveSourceTimeoutMs
                    )
                        .getOrNull()
                        .orEmpty()
                        .filter { normalizeLiveChannelName(it.name) == targetName }
                        .map { channel ->
                            LivePlaybackCandidate(
                                url = channel.url,
                                sourceName = source.name,
                                channelName = channel.name,
                                group = channel.group,
                                format = channel.format
                            )
                        }
                }.distinctBy { it.url }
            }

            mergeLivePlaybackCandidates(candidates)
            sameNameLinesLoaded = true
        } finally {
            sameNameLinesLoading = false
        }
    }

    private fun mergeLivePlaybackCandidates(candidates: List<LivePlaybackCandidate>) {
        candidates.forEach { candidate ->
            val existingIndex = playbackCandidates.indexOfFirst { it.url == candidate.url }
            if (existingIndex >= 0) {
                playbackCandidates[existingIndex] = candidate
            } else {
                playbackCandidates += candidate
            }
        }
        val activeIndex = playbackCandidates.indexOfFirst { it.url == _activeLiveUrl.value }
        currentCandidateIndex = activeIndex.takeIf { it >= 0 } ?: currentCandidateIndex.coerceIn(
            0,
            playbackCandidates.lastIndex.coerceAtLeast(0)
        )
        publishLineOptions()
    }

    private fun publishLineOptions() {
        _lineOptions.value = playbackCandidates.mapIndexed { index, candidate ->
            PlayerSourceOption(
                key = candidate.key,
                sourceName = "线路 ${index + 1}",
                episodeLabel = liveLineMeta(candidate),
                isCurrent = index == currentCandidateIndex
            )
        }
    }

    private fun currentLineLabel(): String {
        val lineNumber = currentCandidateIndex + 1
        return if (playbackCandidates.size > 1) {
            "${channelTitle.ifBlank { "直播频道" }} · 线路 $lineNumber"
        } else {
            channelTitle.ifBlank { "IPTV直播" }
        }
    }

    private fun liveLineMeta(candidate: LivePlaybackCandidate): String {
        return listOf(
            candidate.sourceName,
            candidate.group,
            candidate.format,
            liveSourceNameFromUrl(candidate.url)
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun normalizeLiveChannelName(value: String): String {
        return value
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("-", "")
            .replace("_", "")
            .replace(":", "")
            .replace("：", "")
            .replace("频道", "")
            .replace("高清", "")
            .replace("超清", "")
    }

    private fun inferLiveFormat(url: String): String {
        return when {
            url.contains(".m3u8", ignoreCase = true) -> "m3u8"
            url.contains(".flv", ignoreCase = true) -> "flv"
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            else -> "IPTV"
        }
    }

    private fun liveSourceNameFromUrl(url: String): String {
        val host = android.net.Uri.parse(url).host.orEmpty()
        return host
            .split('.')
            .firstOrNull { it.length > 2 && it !in setOf("www", "m", "v", "live") }
            ?: "直播源"
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                _currentPosition.value = playerManager.getCurrentPosition()
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
    }

    private fun startNetworkSpeedUpdates() {
        networkSpeedUpdateJob?.cancel()
        networkSpeedUpdateJob = viewModelScope.launch {
            while (isActive) {
                publishMeasuredNetworkSpeed()
                delay(NETWORK_SPEED_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopNetworkSpeedUpdates() {
        networkSpeedUpdateJob?.cancel()
        networkSpeedUpdateJob = null
    }

    private fun recordTransferredBytes(bytes: Int) {
        if (bytes <= 0) return
        val now = SystemClock.elapsedRealtime()
        synchronized(transferSamplesLock) {
            transferSamples += TransferSample(now, bytes.toLong())
            pruneTransferSamplesLocked(now)
        }
    }

    private fun resetNetworkSpeedSamples() {
        synchronized(transferSamplesLock) {
            transferSamples.clear()
        }
    }

    private fun publishMeasuredNetworkSpeed() {
        val now = SystemClock.elapsedRealtime()
        val speedBitsPerSecond = synchronized(transferSamplesLock) {
            pruneTransferSamplesLocked(now)
            val totalBytes = transferSamples.sumOf { it.bytes }
            if (totalBytes <= 0L) {
                0L
            } else {
                val firstSampleTime = transferSamples.first().timestampMs
                val windowMs = (now - firstSampleTime).coerceAtLeast(NETWORK_SPEED_UPDATE_INTERVAL_MS)
                totalBytes.coerceAtMost(Long.MAX_VALUE / 8_000L) * 8_000L / windowMs
            }
        }

        _playbackStats.value = _playbackStats.value.copy(
            networkSpeedBitsPerSecond = speedBitsPerSecond
        )
    }

    private fun pruneTransferSamplesLocked(now: Long) {
        while (transferSamples.isNotEmpty() && now - transferSamples.first().timestampMs > NETWORK_SPEED_WINDOW_MS) {
            transferSamples.removeFirst()
        }
    }

    private fun updateVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        _playbackStats.value = _playbackStats.value.copy(
            resolutionWidth = width,
            resolutionHeight = height
        )
    }

    private fun updateVideoFormat(format: Format?) {
        if (format == null) return

        val nextWidth = format.width.takeIf { it > 0 } ?: _playbackStats.value.resolutionWidth
        val nextHeight = format.height.takeIf { it > 0 } ?: _playbackStats.value.resolutionHeight
        val nextBitrate = firstReasonableVideoBitrate(
            format.bitrate,
            format.averageBitrate,
            format.peakBitrate
        )?.toLong() ?: _playbackStats.value.videoBitrateBitsPerSecond

        _playbackStats.value = _playbackStats.value.copy(
            resolutionWidth = nextWidth,
            resolutionHeight = nextHeight,
            videoBitrateBitsPerSecond = nextBitrate
        )
    }

    private fun selectCurrentVideoFormat(tracks: Tracks): Format? {
        tracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                for (index in 0 until group.length) {
                    if (group.isTrackSelected(index)) {
                        return group.getTrackFormat(index)
                    }
                }
            }
        }
        return null
    }

    private fun firstReasonableVideoBitrate(vararg values: Int): Int? {
        return values.firstOrNull {
            it != Format.NO_VALUE && it >= MIN_VIDEO_BITRATE_BITS_PER_SECOND
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared - Cleaning up")
        stopProgressUpdates()
        stopNetworkSpeedUpdates()
        sameNameLinesJob?.cancel()
        playerManager.stopAndRelease()
        playerManager.removeAnalyticsListener(analyticsListener)
        playerManager.removeTransferByteListener(transferByteListener)
        playerManager.removeListener(playerListener)
    }
}

@HiltViewModel
class RadioPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "RadioPlayerViewModel"
    }

    private val radioUrl: String = savedStateHandle.get<String>("url") ?: ""
    private val radioTitle: String = savedStateHandle.get<String>("title").orEmpty()
    private val radioGroup: String = savedStateHandle.get<String>("group").orEmpty()
    private val radioCodec: String = savedStateHandle.get<String>("codec").orEmpty()
    private val radioBitrate: Int = savedStateHandle.get<Int>("bitrate") ?: 0
    private val radioLogo: String = savedStateHandle.get<String>("logo").orEmpty()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _activeRadioUrl = MutableStateFlow(radioUrl)
    val activeRadioUrl: StateFlow<String> = _activeRadioUrl.asStateFlow()

    private val _playbackUiState = MutableStateFlow(
        PlaybackUiState(
            sourceName = radioTitle.ifBlank { "网络电台" },
            message = "正在连接电台",
            isRecovering = true
        )
    )
    val playbackUiState: StateFlow<PlaybackUiState> = _playbackUiState.asStateFlow()

    private val _playbackStats = MutableStateFlow(PlaybackStats())
    val playbackStats: StateFlow<PlaybackStats> = _playbackStats.asStateFlow()

    private var stateCollectJob: Job? = null

    init {
        Log.d(TAG, "init - radioUrl=$radioUrl, title=$radioTitle")
        observeAudioPlaybackState()
        if (radioUrl.isNotBlank()) {
            playRadio()
        } else {
            _playbackUiState.value = PlaybackUiState(
                sourceName = "网络电台",
                message = "没有可播放的电台地址",
                isRecovering = false,
                isFailed = true
            )
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            AudioPlaybackService.pause(context)
        } else {
            AudioPlaybackService.resume(context)
        }
    }

    fun retryPlayback() {
        if (radioUrl.isBlank()) return
        playRadio()
    }

    fun stopPlayback() {
        Log.d(TAG, "stopPlayback")
        _isPlaying.value = false
        AudioPlaybackService.stop(context)
    }

    private fun playRadio() {
        val item = AudioPlaybackQueueStore.activeItemOrFallback(
            url = radioUrl,
            title = radioTitle,
            group = radioGroup,
            codec = radioCodec,
            bitrate = radioBitrate,
            artworkUrl = radioLogo
        )
        _activeRadioUrl.value = item.url
        _currentPosition.value = 0L
        _playbackStats.value = PlaybackStats()
        _playbackUiState.value = PlaybackUiState(
            sourceName = item.title.ifBlank { "网络电台" },
            message = "正在连接 ${item.title.ifBlank { "网络电台" }}",
            isRecovering = true
        )
        AudioPlaybackService.play(context, item)
    }

    private fun observeAudioPlaybackState() {
        stateCollectJob?.cancel()
        stateCollectJob = viewModelScope.launch {
            AudioPlaybackQueueStore.state.collect { state ->
                val activeItem = state.activeItem
                _isPlaying.value = state.isPlaying
                _currentPosition.value = state.currentPosition
                _activeRadioUrl.value = state.activeUrl.ifBlank { radioUrl }
                _playbackStats.value = _playbackStats.value.copy(
                    networkSpeedBitsPerSecond = state.networkSpeedBitsPerSecond
                )
                _playbackUiState.value = PlaybackUiState(
                    sourceName = activeItem?.title?.ifBlank { "网络电台" } ?: radioTitle.ifBlank { "网络电台" },
                    message = state.message,
                    isRecovering = state.isRecovering,
                    isFailed = state.isFailed
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stateCollectJob?.cancel()
    }
}
