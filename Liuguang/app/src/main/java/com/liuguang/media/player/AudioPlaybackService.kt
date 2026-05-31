package com.liuguang.media.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.liuguang.media.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AudioPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var networkSpeedJob: Job? = null
    private val transferSamples = ArrayDeque<TransferSample>()
    private val transferSamplesLock = Any()

    private data class TransferSample(
        val timestampMs: Long,
        val bytes: Long
    )

    private val transferListener = object : TransferListener {
        override fun onTransferInitializing(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onTransferStart(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int
        ) {
            if (!isNetwork || bytesTransferred <= 0) return
            val now = SystemClock.elapsedRealtime()
            synchronized(transferSamplesLock) {
                transferSamples += TransferSample(now, bytesTransferred.toLong())
                pruneTransferSamplesLocked(now)
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            AudioPlaybackQueueStore.updateFromPlayer(player ?: return)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            AudioPlaybackQueueStore.updateFromPlayer(player ?: return)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resetNetworkSpeedSamples()
            AudioPlaybackQueueStore.updateFromPlayer(player ?: return)
        }

        override fun onPlayerError(error: PlaybackException) {
            AudioPlaybackQueueStore.updatePlayback(
                isPlaying = false,
                playbackState = Player.STATE_IDLE,
                errorMessage = error.message ?: "音频流不可用"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.audio_playback_channel_name)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_launcher_monochrome)
                }
        )
        val exoPlayer = buildPlayer()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId(AUDIO_SESSION_ID)
            .build()
            .also { addSession(it) }
        startPositionUpdates()
        startNetworkSpeedUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_QUEUE -> playQueueFromStore(intent)
            ACTION_PAUSE -> {
                player?.pause()
                updateMediaNotification(startInForegroundRequired = false)
            }
            ACTION_RESUME -> {
                player?.play()
                updateMediaNotification(startInForegroundRequired = true)
            }
            ACTION_STOP -> stopPlayback()
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = player
        if (currentPlayer == null || !currentPlayer.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        positionJob?.cancel()
        networkSpeedJob?.cancel()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        mediaSession?.let { session ->
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            session.release()
        }
        mediaSession = null
        AudioPlaybackQueueStore.markStopped()
        super.onDestroy()
    }

    private fun buildPlayer(): ExoPlayer {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.baidu.com/"))
            .setTransferListener(transferListener)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                addListener(playerListener)
            }
    }

    private fun playQueueFromStore(intent: Intent) {
        val fallback = intent.toQueueItem()
        val (items, startIndex) = AudioPlaybackQueueStore.queueFor(fallback)
        val mediaItems = items.map { it.toMediaItem() }
        val currentPlayer = player ?: return
        resetNetworkSpeedSamples()
        currentPlayer.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        currentPlayer.prepare()
        currentPlayer.play()
        AudioPlaybackQueueStore.updateFromPlayer(currentPlayer)
        updateMediaNotification(startInForegroundRequired = true)
    }

    private fun stopPlayback() {
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        AudioPlaybackQueueStore.markStopped()
        stopSelf()
    }

    private fun updateMediaNotification(startInForegroundRequired: Boolean) {
        mediaSession?.let { session ->
            onUpdateNotification(session, startInForegroundRequired)
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = serviceScope.launch {
            while (isActive) {
                player?.let {
                    AudioPlaybackQueueStore.updatePosition(it.currentPosition)
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun startNetworkSpeedUpdates() {
        networkSpeedJob?.cancel()
        networkSpeedJob = serviceScope.launch {
            while (isActive) {
                publishMeasuredNetworkSpeed()
                delay(NETWORK_SPEED_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun resetNetworkSpeedSamples() {
        synchronized(transferSamplesLock) {
            transferSamples.clear()
        }
        AudioPlaybackQueueStore.updateNetworkSpeed(0L)
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
        AudioPlaybackQueueStore.updateNetworkSpeed(speedBitsPerSecond)
    }

    private fun pruneTransferSamplesLocked(now: Long) {
        while (
            transferSamples.isNotEmpty() &&
            now - transferSamples.first().timestampMs > NETWORK_SPEED_WINDOW_MS
        ) {
            transferSamples.removeFirst()
        }
    }

    private fun AudioQueueItem.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "网络音频" })
            .setArtist(subtitle.ifBlank { group })
            .setAlbumTitle(group)
            .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.toUri())
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun Intent.toQueueItem(): AudioQueueItem {
        val url = getStringExtra(EXTRA_URL).orEmpty()
        val title = getStringExtra(EXTRA_TITLE).orEmpty()
        val group = getStringExtra(EXTRA_GROUP).orEmpty()
        val codec = getStringExtra(EXTRA_CODEC).orEmpty()
        val bitrate = getIntExtra(EXTRA_BITRATE, 0)
        val artworkUrl = getStringExtra(EXTRA_ARTWORK_URL).orEmpty()
        return AudioQueueItem(
            url = url,
            title = title.ifBlank { "网络音频" },
            subtitle = group.ifBlank { codec },
            group = group,
            codec = codec,
            bitrate = bitrate,
            artworkUrl = artworkUrl
        )
    }

    companion object {
        private const val ACTION_PLAY_QUEUE = "com.liuguang.media.player.action.PLAY_AUDIO_QUEUE"
        private const val ACTION_PAUSE = "com.liuguang.media.player.action.PAUSE_AUDIO"
        private const val ACTION_RESUME = "com.liuguang.media.player.action.RESUME_AUDIO"
        private const val ACTION_STOP = "com.liuguang.media.player.action.STOP_AUDIO"
        private const val AUDIO_SESSION_ID = "liuguang-audio-session"
        private const val NOTIFICATION_ID = 3002
        private const val NOTIFICATION_CHANNEL_ID = "audio_media_playback"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_CODEC = "codec"
        private const val EXTRA_BITRATE = "bitrate"
        private const val EXTRA_ARTWORK_URL = "artworkUrl"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val NETWORK_SPEED_UPDATE_INTERVAL_MS = 1_000L
        private const val NETWORK_SPEED_WINDOW_MS = 3_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"

        fun play(context: Context, item: AudioQueueItem) {
            val intent = Intent(context, AudioPlaybackService::class.java)
                .setAction(ACTION_PLAY_QUEUE)
                .putExtra(EXTRA_URL, item.url)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_GROUP, item.group)
                .putExtra(EXTRA_CODEC, item.codec)
                .putExtra(EXTRA_BITRATE, item.bitrate)
                .putExtra(EXTRA_ARTWORK_URL, item.artworkUrl)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AudioPlaybackService::class.java)
                    .setAction(ACTION_STOP)
            )
        }

        fun pause(context: Context) {
            context.startService(
                Intent(context, AudioPlaybackService::class.java)
                    .setAction(ACTION_PAUSE)
            )
        }

        fun resume(context: Context) {
            context.startService(
                Intent(context, AudioPlaybackService::class.java)
                    .setAction(ACTION_RESUME)
            )
        }
    }
}
