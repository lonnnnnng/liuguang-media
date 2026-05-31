package com.liuguang.media.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var currentListener: Player.Listener? = null
    private var currentAnalyticsListener: AnalyticsListener? = null
    private val transferByteListeners = CopyOnWriteArraySet<(Int) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

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
            transferByteListeners.forEach { listener ->
                listener(bytesTransferred)
            }
        }

        override fun onTransferEnd(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean
        ) = Unit
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                .setDefaultRequestProperties(mapOf(
                    "Referer" to "https://www.baidu.com/"
                ))
                .setTransferListener(transferListener)

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context)
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
                }
        }
        return checkNotNull(exoPlayer) { "ExoPlayer initialization failed" }
    }

    fun play(url: String) {
        android.util.Log.d("PlayerManager", "play - URL: $url")
        val player = getPlayer()
        player.stop()
        player.clearMediaItems()
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        android.util.Log.d("PlayerManager", "play - Player prepared and started")
    }

    fun stopAndClear() {
        android.util.Log.d("PlayerManager", "stopAndClear")
        exoPlayer?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
            clearVideoSurface()
        }
    }

    fun stopAndRelease() {
        android.util.Log.d("PlayerManager", "stopAndRelease")
        val player = exoPlayer ?: return
        exoPlayer = null

        currentListener?.let { player.removeListener(it) }
        currentAnalyticsListener?.let { player.removeAnalyticsListener(it) }
        currentListener = null
        currentAnalyticsListener = null
        transferByteListeners.clear()

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        player.clearVideoSurface()

        mainHandler.postDelayed({
            runCatching {
                player.release()
            }
        }, RELEASE_DELAY_MS)
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun addListener(listener: Player.Listener) {
        currentListener?.takeIf { it != listener }?.let { exoPlayer?.removeListener(it) }
        currentListener = listener
        getPlayer().addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        exoPlayer?.removeListener(listener)
        if (currentListener == listener) {
            currentListener = null
        }
    }

    fun addAnalyticsListener(listener: AnalyticsListener) {
        currentAnalyticsListener?.takeIf { it != listener }?.let { exoPlayer?.removeAnalyticsListener(it) }
        currentAnalyticsListener = listener
        getPlayer().addAnalyticsListener(listener)
    }

    fun removeAnalyticsListener(listener: AnalyticsListener) {
        exoPlayer?.removeAnalyticsListener(listener)
        if (currentAnalyticsListener == listener) {
            currentAnalyticsListener = null
        }
    }

    fun addTransferByteListener(listener: (Int) -> Unit) {
        transferByteListeners.clear()
        transferByteListeners += listener
    }

    fun removeTransferByteListener(listener: (Int) -> Unit) {
        transferByteListeners -= listener
    }

    fun release() {
        currentListener?.let { exoPlayer?.removeListener(it) }
        currentAnalyticsListener?.let { exoPlayer?.removeAnalyticsListener(it) }
        exoPlayer?.release()
        exoPlayer = null
        currentListener = null
        currentAnalyticsListener = null
        transferByteListeners.clear()
    }

    private companion object {
        const val RELEASE_DELAY_MS = 250L
    }
}
