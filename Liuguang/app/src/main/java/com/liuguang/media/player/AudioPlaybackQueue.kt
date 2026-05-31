package com.liuguang.media.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.absoluteValue

data class AudioQueueItem(
    val url: String,
    val title: String,
    val subtitle: String,
    val group: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val artworkUrl: String = "",
    val mediaId: String = stableAudioMediaId(url, title)
)

data class AudioPlaybackState(
    val activeItem: AudioQueueItem? = null,
    val activeUrl: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val networkSpeedBitsPerSecond: Long = 0L,
    val message: String = "正在连接音频",
    val isRecovering: Boolean = true,
    val isFailed: Boolean = false,
    val queueIndex: Int = 0,
    val queueSize: Int = 0
) {
    val hasPrevious: Boolean = queueSize > 1 && queueIndex > 0
    val hasNext: Boolean = queueSize > 1 && queueIndex < queueSize - 1
}

object AudioPlaybackQueueStore {
    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    @Volatile
    private var queue: List<AudioQueueItem> = emptyList()

    @Volatile
    private var startIndex: Int = 0

    fun setQueue(items: List<AudioQueueItem>, requestedIndex: Int) {
        val playableItems = items
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
        if (playableItems.isEmpty()) return

        val selectedUrl = items.getOrNull(requestedIndex)?.url
        val normalizedIndex = playableItems
            .indexOfFirst { it.url == selectedUrl }
            .takeIf { it >= 0 }
            ?: requestedIndex.coerceIn(playableItems.indices)

        queue = playableItems
        startIndex = normalizedIndex
        publishActiveItem(normalizedIndex, playableItems[normalizedIndex])
    }

    fun queueFor(fallback: AudioQueueItem): Pair<List<AudioQueueItem>, Int> {
        val currentQueue = queue
        val matchedIndex = currentQueue.indexOfFirst { it.url == fallback.url }
        if (matchedIndex >= 0) {
            startIndex = matchedIndex
            publishActiveItem(matchedIndex, currentQueue[matchedIndex])
            return currentQueue to matchedIndex
        }

        setQueue(listOf(fallback), 0)
        return queue to 0
    }

    fun snapshotQueue(): List<AudioQueueItem> = queue

    fun activeItemOrFallback(
        url: String,
        title: String,
        group: String,
        codec: String,
        bitrate: Int,
        artworkUrl: String
    ): AudioQueueItem {
        return queue.firstOrNull { it.url == url } ?: AudioQueueItem(
            url = url,
            title = title.ifBlank { "网络音频" },
            subtitle = group.ifBlank { codec },
            group = group,
            codec = codec,
            bitrate = bitrate,
            artworkUrl = artworkUrl
        )
    }

    fun updateFromPlayer(player: Player) {
        val currentQueue = queue
        val index = player.currentMediaItemIndex
            .takeIf { it in currentQueue.indices }
            ?: currentQueue.indexOfFirst { it.mediaId == player.currentMediaItem?.mediaId }
                .takeIf { it >= 0 }
            ?: startIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))

        currentQueue.getOrNull(index)?.let { item ->
            startIndex = index
            publishActiveItem(index, item)
        }
        updatePlayback(
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
            errorMessage = null
        )
    }

    fun updatePlayback(
        isPlaying: Boolean,
        playbackState: Int,
        errorMessage: String?
    ) {
        val current = _state.value
        val activeTitle = current.activeItem?.title?.ifBlank { "音频" } ?: "音频"
        val failed = errorMessage != null || playbackState == Player.STATE_IDLE && current.isFailed
        val message = when {
            errorMessage != null -> errorMessage
            playbackState == Player.STATE_BUFFERING -> "$activeTitle 正在缓冲"
            playbackState == Player.STATE_READY -> "$activeTitle 已接入"
            playbackState == Player.STATE_ENDED -> "音频流已结束"
            playbackState == Player.STATE_IDLE && failed -> "音频流不可用"
            else -> current.message
        }
        _state.value = current.copy(
            isPlaying = isPlaying,
            message = message,
            isRecovering = playbackState == Player.STATE_BUFFERING,
            isFailed = failed || playbackState == Player.STATE_ENDED
        )
    }

    fun updatePosition(positionMs: Long) {
        _state.value = _state.value.copy(currentPosition = positionMs.coerceAtLeast(0L))
    }

    fun updateNetworkSpeed(bitsPerSecond: Long) {
        _state.value = _state.value.copy(networkSpeedBitsPerSecond = bitsPerSecond.coerceAtLeast(0L))
    }

    fun markStopped() {
        _state.value = _state.value.copy(
            isPlaying = false,
            currentPosition = 0L,
            networkSpeedBitsPerSecond = 0L,
            isRecovering = false
        )
    }

    private fun publishActiveItem(index: Int, item: AudioQueueItem) {
        _state.value = _state.value.copy(
            activeItem = item,
            activeUrl = item.url,
            queueIndex = index,
            queueSize = queue.size,
            message = "正在连接 ${item.title.ifBlank { "音频" }}",
            isRecovering = true,
            isFailed = false
        )
    }
}

fun stableAudioMediaId(url: String, title: String): String {
    val hash = (url.hashCode() * 31 + title.hashCode()).absoluteValue
    return "audio-$hash"
}
