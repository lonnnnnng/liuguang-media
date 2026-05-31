package com.liuguang.media.ui.screens.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.liuguang.media.R
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.components.NetworkImage
import com.liuguang.media.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val QUICK_SEEK_MS = 10_000L

private enum class PlayerDragMode {
    Seek,
    Brightness,
    Volume
}

@Composable
fun EpisodePlayerScreen(
    siteId: Long,
    vodId: String,
    episodeUrl: String,
    title: String,
    episodeLabel: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedRouteArgs = Triple(siteId, vodId, episodeUrl)
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val activity = context as? Activity

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionState = viewModel.currentPosition.collectAsState()
    val durationState = viewModel.duration.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val activeEpisodeUrlState = viewModel.activeEpisodeUrl.collectAsState()
    val activeEpisodeLabel by viewModel.activeEpisodeLabel.collectAsState()
    val playbackUiState by viewModel.playbackUiState.collectAsState()
    val sourceOptions by viewModel.sourceOptions.collectAsState()
    val episodeNavigation by viewModel.episodeNavigation.collectAsState()
    val playbackStatsState = viewModel.playbackStats.collectAsState()
    val saveEpisodeLabel = activeEpisodeLabel.ifBlank { episodeLabel }
    val saveEpisodeLabelState = rememberUpdatedState(saveEpisodeLabel)

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    var volumeOverlay by remember { mutableStateOf<Int?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val seekBackward = {
        viewModel.seekTo(quickSeekPosition(currentPositionState.value, durationState.value, -QUICK_SEEK_MS))
    }
    val seekForward = {
        viewModel.seekTo(quickSeekPosition(currentPositionState.value, durationState.value, QUICK_SEEK_MS))
    }
    val leavePlayer = {
        if (!isLeaving) {
            isLeaving = true
            viewModel.savePlaybackPosition(title, "", saveEpisodeLabel)
            viewModel.stopPlayback()
            if (isFullscreen) {
                exitFullscreen(activity)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isLeaving) {
                viewModel.savePlaybackPosition(title, "", saveEpisodeLabelState.value)
                viewModel.stopPlayback()
            }
            if (isFullscreen) {
                exitFullscreen(activity)
            }
        }
    }

    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            enterFullscreen(activity)
        } else {
            exitFullscreen(activity)
        }
        onDispose { }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            showControls = true
        }
    }

    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            withFrameNanos { }
            onNavigateBack()
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            leavePlayer()
        }
    }

    if (isLeaving) {
        CinemaBackground(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val playerViewFactory: @Composable () -> Unit = {
        AndroidView(
            factory = { ctx ->
                createTexturePlayerView(ctx)
            },
            onRelease = { playerView ->
                playerView.player = null
                playerView.onPause()
            },
            update = { playerView ->
                playerView.player = if (isLeaving) null else viewModel.getPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isFullscreen) {
                    PlayerEpisodeTopBar(
                        title = episodeTopBarTitle(title, saveEpisodeLabel),
                        onNavigateBack = leavePlayer
                    )

                    PlayerSurface(
                        isFullscreen = false,
                        showControls = showControls,
                        isPlaying = isPlaying,
                        currentPositionState = currentPositionState,
                        durationState = durationState,
                        playbackUiState = playbackUiState,
                        brightnessOverlay = brightnessOverlay,
                        volumeOverlay = volumeOverlay,
                        maxVolume = maxVolume,
                        onToggleControls = { showControls = !showControls },
                        onTogglePlay = viewModel::togglePlayPause,
                        onRetryPlayback = viewModel::retryPlayback,
                        onSeekTo = viewModel::seekTo,
                        onToggleFullscreen = {
                            showControls = true
                            isFullscreen = true
                        },
                        onRewindClick = seekBackward,
                        onForwardClick = seekForward,
                        onExitFullscreen = { isFullscreen = false },
                        onBrightnessChange = { brightnessOverlay = it },
                        onVolumeChange = { volumeOverlay = it },
                        onGestureEnd = {
                            brightnessOverlay = null
                            volumeOverlay = null
                        },
                        audioManager = audioManager,
                        activity = activity,
                        context = context,
                        playerViewFactory = playerViewFactory
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EpisodePlaybackInfo(
                        episodeUrlState = activeEpisodeUrlState,
                        currentPositionState = currentPositionState,
                        durationState = durationState,
                        playbackUiState = playbackUiState,
                        playbackStatsState = playbackStatsState,
                        onCopyClick = { url ->
                            copyPlayerSourceLink(
                                context = context,
                                clipboardManager = clipboardManager,
                                url = url
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerUtilityButton(
                            icon = Icons.Default.SkipPrevious,
                            label = "上一集",
                            onClick = viewModel::playPreviousEpisode,
                            enabled = episodeNavigation.hasPrevious,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        PlayerUtilityButton(
                            icon = Icons.Default.SkipNext,
                            label = "下一集",
                            onClick = viewModel::playNextEpisode,
                            enabled = episodeNavigation.hasNext,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerUtilityButton(
                            icon = Icons.Default.FastRewind,
                            label = "快退",
                            onClick = seekBackward,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        Surface(
                            onClick = {
                                if (playbackUiState.isFailed) {
                                    viewModel.retryPlayback()
                                } else {
                                    viewModel.togglePlayPause()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            color = AppColors.SurfaceAlt,
                            contentColor = AppColors.TextPrimary,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, AppColors.Divider)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = when {
                                        playbackUiState.isFailed -> "重试"
                                        isPlaying -> "暂停"
                                        else -> "播放"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false
                                )
                            }
                        }

                        PlayerUtilityButton(
                            icon = Icons.Default.FastForward,
                            label = "快进",
                            onClick = seekForward,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerUtilityButton(
                            icon = Icons.Default.SwapHoriz,
                            label = "换源",
                            onClick = {
                                viewModel.loadSourceOptions()
                                showSourceDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        PlayerUtilityButton(
                            icon = Icons.Default.Speed,
                            label = "${playbackSpeed}x",
                            onClick = { showSpeedDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        PlayerUtilityButton(
                            icon = Icons.Default.Cast,
                            label = "投屏",
                            onClick = { showCastDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        PlayerUtilityButton(
                            icon = Icons.Default.Fullscreen,
                            label = "全屏",
                            onClick = {
                                showControls = true
                                isFullscreen = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }

            if (isFullscreen) {
                PlayerSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    isFullscreen = true,
                    showControls = showControls,
                    isPlaying = isPlaying,
                    currentPositionState = currentPositionState,
                    durationState = durationState,
                    playbackUiState = playbackUiState,
                    brightnessOverlay = brightnessOverlay,
                    volumeOverlay = volumeOverlay,
                    maxVolume = maxVolume,
                    onToggleControls = { showControls = !showControls },
                    onTogglePlay = viewModel::togglePlayPause,
                    onRetryPlayback = viewModel::retryPlayback,
                    onSeekTo = viewModel::seekTo,
                    onToggleFullscreen = { isFullscreen = false },
                    onRewindClick = seekBackward,
                    onForwardClick = seekForward,
                    onSourceClick = {
                        viewModel.loadSourceOptions()
                        showSourceDialog = true
                    },
                    onSpeedClick = { showSpeedDialog = true },
                    playbackSpeedLabel = "${playbackSpeed}x",
                    onCastClick = { showCastDialog = true },
                    onExitFullscreen = { isFullscreen = false },
                    onBrightnessChange = { brightnessOverlay = it },
                    onVolumeChange = { volumeOverlay = it },
                    onGestureEnd = {
                        brightnessOverlay = null
                        volumeOverlay = null
                    },
                    audioManager = audioManager,
                    activity = activity,
                    context = context,
                    playerViewFactory = playerViewFactory
                )
            }
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("播放速度") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        0.5f,
                        0.75f,
                        1.0f,
                        1.25f,
                        1.5f,
                        2.0f,
                        2.5f,
                        3.0f,
                        4.0f
                    ).chunked(3).forEach { rowSpeeds ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowSpeeds.forEach { speed ->
                                TextButton(
                                    onClick = {
                                        viewModel.setSpeed(speed)
                                        showSpeedDialog = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = "${speed}x",
                                        color = if (speed == playbackSpeed) AppColors.Primary else AppColors.TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("投屏") },
            text = { Text("当前未发现可用投屏设备，请确认电视或盒子与手机在同一网络。") },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showSourceDialog) {
        EpisodeSourceDialog(
            sourceOptions = sourceOptions,
            currentEpisodeLabel = saveEpisodeLabel,
            onSelect = { key ->
                viewModel.switchToSource(key)
                showSourceDialog = false
            },
            onDismiss = { showSourceDialog = false }
        )
    }

}

@Composable
fun LivePlayerScreen(
    url: String,
    title: String,
    group: String,
    format: String,
    sourceId: Long,
    onNavigateBack: () -> Unit,
    viewModel: LivePlayerViewModel = hiltViewModel()
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedRouteArgs = Triple(url, group, sourceId)
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val activity = context as? Activity

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionState = viewModel.currentPosition.collectAsState()
    val durationState = viewModel.duration.collectAsState()
    val activeLiveUrlState = viewModel.activeLiveUrl.collectAsState()
    val playbackUiState by viewModel.playbackUiState.collectAsState()
    val playbackStatsState = viewModel.playbackStats.collectAsState()
    val lineOptions by viewModel.lineOptions.collectAsState()

    var showCastDialog by remember { mutableStateOf(false) }
    var showLineDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isLeaving by remember { mutableStateOf(false) }
    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    var volumeOverlay by remember { mutableStateOf<Int?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val currentLineOption = lineOptions.firstOrNull { it.isCurrent }
    val lineButtonLabel = if (lineOptions.size > 1) {
        "${currentLineOption?.sourceName ?: "线路"}/${lineOptions.size}"
    } else {
        "线路"
    }
    val leavePlayer = {
        if (!isLeaving) {
            isLeaving = true
            viewModel.stopPlayback()
            if (isFullscreen) {
                exitFullscreen(activity)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isLeaving) {
                viewModel.stopPlayback()
            }
            if (isFullscreen) {
                exitFullscreen(activity)
            }
        }
    }

    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            enterFullscreen(activity)
        } else {
            exitFullscreen(activity)
        }
        onDispose { }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            showControls = true
        }
    }

    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            withFrameNanos { }
            onNavigateBack()
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            leavePlayer()
        }
    }

    if (isLeaving) {
        CinemaBackground(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val playerViewFactory: @Composable () -> Unit = {
        AndroidView(
            factory = { ctx ->
                createTexturePlayerView(ctx)
            },
            onRelease = { playerView ->
                playerView.player = null
                playerView.onPause()
            },
            update = { playerView ->
                playerView.player = if (isLeaving) null else viewModel.getPlayer()
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isFullscreen) {
                    PlayerEpisodeTopBar(
                        title = title.ifBlank { "直播频道" },
                        onNavigateBack = leavePlayer
                    )

                    PlayerSurface(
                        isFullscreen = false,
                        showControls = showControls,
                        isPlaying = isPlaying,
                        currentPositionState = currentPositionState,
                        durationState = durationState,
                        playbackUiState = playbackUiState,
                        recoveringTitle = "正在连接直播流",
                        brightnessOverlay = brightnessOverlay,
                        volumeOverlay = volumeOverlay,
                        maxVolume = maxVolume,
                        onToggleControls = { showControls = !showControls },
                        onTogglePlay = viewModel::togglePlayPause,
                        onRetryPlayback = viewModel::retryPlayback,
                        onSeekTo = viewModel::seekTo,
                        onToggleFullscreen = {
                            showControls = true
                            isFullscreen = true
                        },
                        onExitFullscreen = { isFullscreen = false },
                        onBrightnessChange = { brightnessOverlay = it },
                        onVolumeChange = { volumeOverlay = it },
                        onGestureEnd = {
                            brightnessOverlay = null
                            volumeOverlay = null
                        },
                        audioManager = audioManager,
                        activity = activity,
                        context = context,
                        playerViewFactory = playerViewFactory
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LivePlaybackInfo(
                        group = group,
                        format = format,
                        liveUrlState = activeLiveUrlState,
                        currentPositionState = currentPositionState,
                        durationState = durationState,
                        playbackUiState = playbackUiState,
                        playbackStatsState = playbackStatsState,
                        onCopyClick = { url ->
                            copyPlayerSourceLink(
                                context = context,
                                clipboardManager = clipboardManager,
                                url = url
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerUtilityButton(
                            icon = Icons.Default.SwapHoriz,
                            label = lineButtonLabel,
                            onClick = {
                                viewModel.loadLiveLineOptions()
                                showLineDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        PlayerUtilityButton(
                            icon = Icons.Default.Cast,
                            label = "投屏",
                            onClick = { showCastDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        Surface(
                            onClick = {
                                if (playbackUiState.isFailed) {
                                    viewModel.retryPlayback()
                                } else {
                                    viewModel.togglePlayPause()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            color = AppColors.SurfaceAlt,
                            contentColor = AppColors.TextPrimary,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, AppColors.Divider)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = when {
                                        playbackUiState.isFailed -> "重试直播"
                                        isPlaying -> "暂停"
                                        else -> "播放"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false
                                )
                            }
                        }

                        PlayerUtilityButton(
                            icon = Icons.Default.Fullscreen,
                            label = "全屏",
                            onClick = {
                                showControls = true
                                isFullscreen = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }

            if (isFullscreen) {
                PlayerSurface(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f),
                    isFullscreen = true,
                    showControls = showControls,
                    isPlaying = isPlaying,
                    currentPositionState = currentPositionState,
                    durationState = durationState,
                    playbackUiState = playbackUiState,
                    recoveringTitle = "正在连接直播流",
                    brightnessOverlay = brightnessOverlay,
                    volumeOverlay = volumeOverlay,
                    maxVolume = maxVolume,
                    onToggleControls = { showControls = !showControls },
                    onTogglePlay = viewModel::togglePlayPause,
                    onRetryPlayback = viewModel::retryPlayback,
                    onSeekTo = viewModel::seekTo,
                    onToggleFullscreen = { isFullscreen = false },
                    onSourceClick = {
                        viewModel.loadLiveLineOptions()
                        showLineDialog = true
                    },
                    onCastClick = { showCastDialog = true },
                    onExitFullscreen = { isFullscreen = false },
                    onBrightnessChange = { brightnessOverlay = it },
                    onVolumeChange = { volumeOverlay = it },
                    onGestureEnd = {
                        brightnessOverlay = null
                        volumeOverlay = null
                    },
                    audioManager = audioManager,
                    activity = activity,
                    context = context,
                    playerViewFactory = playerViewFactory
                )
            }
        }
    }

    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("投屏") },
            text = { Text("当前未发现可用投屏设备，请确认电视或盒子与手机在同一网络。") },
            confirmButton = {
                TextButton(onClick = { showCastDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showLineDialog) {
        AlertDialog(
            onDismissRequest = { showLineDialog = false },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            title = { Text("直播线路") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        lineOptions.isEmpty() -> {
                            Text(
                                text = "正在查找同名频道线路...",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                        lineOptions.size == 1 -> {
                            Text(
                                text = "当前频道暂无同名备用线路。",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            LiveLineOptionRow(
                                option = lineOptions.first(),
                                onClick = { showLineDialog = false }
                            )
                        }
                        else -> {
                            lineOptions.forEach { option ->
                                LiveLineOptionRow(
                                    option = option,
                                    onClick = {
                                        viewModel.switchToLiveLine(option.key)
                                        showLineDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLineDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
fun RadioPlayerScreen(
    url: String,
    title: String,
    group: String,
    codec: String,
    bitrate: Int,
    logo: String,
    sourceId: Long,
    onNavigateBack: () -> Unit,
    viewModel: RadioPlayerViewModel = hiltViewModel()
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedRouteArgs = url to sourceId
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionState = viewModel.currentPosition.collectAsState()
    val activeRadioUrlState = viewModel.activeRadioUrl.collectAsState()
    val playbackUiState by viewModel.playbackUiState.collectAsState()
    val playbackStatsState = viewModel.playbackStats.collectAsState()

    var isLeaving by remember { mutableStateOf(false) }
    val leavePlayer = {
        if (!isLeaving) {
            isLeaving = true
            viewModel.stopPlayback()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isLeaving) {
                viewModel.stopPlayback()
            }
        }
    }

    LaunchedEffect(isLeaving) {
        if (isLeaving) {
            withFrameNanos { }
            onNavigateBack()
        }
    }

    BackHandler { leavePlayer() }

    if (isLeaving) {
        CinemaBackground(modifier = Modifier.fillMaxSize()) {}
        return
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlayerEpisodeTopBar(
                title = title.ifBlank { "网络电台" },
                onNavigateBack = leavePlayer
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioHeroPanel(
                    title = title,
                    group = group,
                    codec = codec,
                    bitrate = bitrate,
                    logo = logo,
                    playbackUiState = playbackUiState
                )

                RadioPlaybackInfo(
                    title = title,
                    group = group,
                    codec = codec,
                    bitrate = bitrate,
                    radioUrlState = activeRadioUrlState,
                    currentPositionState = currentPositionState,
                    playbackUiState = playbackUiState,
                    playbackStatsState = playbackStatsState,
                    onCopyClick = { radioUrl ->
                        copyPlayerSourceLink(
                            context = context,
                            clipboardManager = clipboardManager,
                            url = radioUrl
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            if (playbackUiState.isFailed) {
                                viewModel.retryPlayback()
                            } else {
                                viewModel.togglePlayPause()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        color = AppColors.Primary,
                        contentColor = AppColors.OnPrimary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    playbackUiState.isFailed -> "重试"
                                    isPlaying -> "暂停"
                                    else -> "播放"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    PlayerUtilityButton(
                        icon = Icons.Default.Refresh,
                        label = "重连",
                        onClick = viewModel::retryPlayback,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodePlaybackInfo(
    episodeUrlState: State<String>,
    currentPositionState: State<Long>,
    durationState: State<Long>,
    playbackUiState: PlaybackUiState,
    playbackStatsState: State<PlaybackStats>,
    onCopyClick: (String) -> Unit
) {
    val episodeUrl = episodeUrlState.value
    val currentPosition = currentPositionState.value
    val duration = durationState.value
    val sourceLabel = remember(episodeUrl) { playerSourceLabel(episodeUrl) }

    Text(
        text = episodePlayerSubtitle(
            sourceLabel = sourceLabel,
            currentPosition = currentPosition,
            duration = duration,
            playbackUiState = playbackUiState
        ),
        color = AppColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )

    PlayerProgressBar(
        currentPosition = currentPosition,
        duration = duration
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerMetaPill(formatTime(currentPosition))
        PlayerMetaPill(sourceLabel)
        PlayerMetaPill(
            if (duration > 0L) {
                "${((currentPosition * 100f) / duration).toInt()}%"
            } else {
                "准备中"
            }
        )
    }

    PlaybackStatsRow(stats = playbackStatsState.value)

    PlayerSourceLinkRow(
        url = episodeUrl,
        onCopyClick = { onCopyClick(episodeUrl) }
    )
}

@Composable
private fun LivePlaybackInfo(
    group: String,
    format: String,
    liveUrlState: State<String>,
    currentPositionState: State<Long>,
    durationState: State<Long>,
    playbackUiState: PlaybackUiState,
    playbackStatsState: State<PlaybackStats>,
    onCopyClick: (String) -> Unit
) {
    val liveUrl = liveUrlState.value
    val currentPosition = currentPositionState.value
    val duration = durationState.value
    val sourceLabel = remember(liveUrl) { playerSourceLabel(liveUrl) }

    Text(
        text = livePlayerSubtitle(
            group = group,
            format = format,
            sourceLabel = sourceLabel,
            playbackUiState = playbackUiState
        ),
        color = AppColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )

    PlayerProgressBar(
        currentPosition = currentPosition,
        duration = duration
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerMetaPill("直播")
        PlayerMetaPill(sourceLabel)
        PlayerMetaPill(format.ifBlank { "IPTV" })
    }

    PlaybackStatsRow(stats = playbackStatsState.value)

    PlayerSourceLinkRow(
        url = liveUrl,
        onCopyClick = { onCopyClick(liveUrl) }
    )
}

@Composable
private fun RadioHeroPanel(
    title: String,
    group: String,
    codec: String,
    bitrate: Int,
    logo: String,
    playbackUiState: PlaybackUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AppColors.Primary,
                                AppColors.Accent
                            )
                        )
                    )
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (logo.isNotBlank()) {
                    NetworkImage(
                        url = logo,
                        contentDescription = "${title.ifBlank { "网络电台" }}台标",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = title.ifBlank { "网络电台" },
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        group,
                        codec,
                        bitrate.takeIf { it > 0 }?.let { "${it}kbps" }
                    ).filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { "在线音频流" },
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playbackUiState.message,
                    color = if (playbackUiState.isFailed) AppColors.Error else AppColors.Primary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RadioPlaybackInfo(
    title: String,
    group: String,
    codec: String,
    bitrate: Int,
    radioUrlState: State<String>,
    currentPositionState: State<Long>,
    playbackUiState: PlaybackUiState,
    playbackStatsState: State<PlaybackStats>,
    onCopyClick: (String) -> Unit
) {
    val radioUrl = radioUrlState.value
    val currentPosition = currentPositionState.value
    val sourceLabel = remember(radioUrl) { playerSourceLabel(radioUrl) }

    Text(
        text = radioPlayerSubtitle(
            title = title,
            group = group,
            sourceLabel = sourceLabel,
            playbackUiState = playbackUiState
        ),
        color = AppColors.TextSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp
    )

    PlayerProgressBar(
        currentPosition = 0L,
        duration = 0L
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlayerMetaPill(formatTime(currentPosition))
        PlayerMetaPill(codec.ifBlank { "AUDIO" })
        PlayerMetaPill(bitrate.takeIf { it > 0 }?.let { "${it}kbps" } ?: sourceLabel)
    }

    RadioStatsRow(
        codec = codec,
        bitrate = bitrate,
        networkSpeedBitsPerSecond = playbackStatsState.value.networkSpeedBitsPerSecond
    )

    PlayerSourceLinkRow(
        url = radioUrl,
        onCopyClick = { onCopyClick(radioUrl) }
    )
}

@Composable
private fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long
) {
    LinearProgressIndicator(
        progress = {
            if (duration > 0) currentPosition.toFloat() / duration else 0f
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = AppColors.Primary,
        trackColor = AppColors.SurfaceRaised
    )
}

@Composable
private fun PlayerEpisodeTopBar(
    title: String,
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onNavigateBack,
            modifier = Modifier.size(42.dp),
            color = AppColors.SurfaceAlt,
            contentColor = AppColors.TextPrimary,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, AppColors.Divider)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeSourceDialog(
    sourceOptions: List<PlayerSourceOption>,
    currentEpisodeLabel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = { Text("播放换源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (sourceOptions.isEmpty()) {
                        "正在查找同集可用线路..."
                    } else {
                        "${currentEpisodeLabel.ifBlank { "当前集" }} · ${sourceOptions.size}条线路"
                    },
                    color = AppColors.TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (sourceOptions.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sourceOptions.forEach { option ->
                            EpisodeSourcePill(
                                option = option,
                                fallbackEpisodeLabel = currentEpisodeLabel,
                                onClick = { onSelect(option.key) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun EpisodeSourcePill(
    option: PlayerSourceOption,
    fallbackEpisodeLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .widthIn(min = 76.dp, max = 126.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (option.isCurrent) {
                    Brush.linearGradient(listOf(AppColors.Primary, AppColors.Primary))
                } else {
                    Brush.linearGradient(listOf(AppColors.Surface, AppColors.SurfaceAlt))
                }
            )
            .then(
                if (option.isCurrent) {
                    Modifier
                } else {
                    Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = option.sourceName,
                color = if (option.isCurrent) AppColors.OnPrimary else AppColors.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = option.episodeLabel.ifBlank { fallbackEpisodeLabel.ifBlank { "当前集" } },
                color = if (option.isCurrent) AppColors.OnPrimary.copy(alpha = 0.82f) else AppColors.TextTertiary,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (option.isCurrent) {
            Text(
                text = "当前",
                color = AppColors.OnPrimary,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    modifier: Modifier = Modifier,
    isFullscreen: Boolean,
    showControls: Boolean,
    isPlaying: Boolean,
    currentPositionState: State<Long>,
    durationState: State<Long>,
    playbackUiState: PlaybackUiState,
    recoveringTitle: String = "正在切换备用线路",
    brightnessOverlay: Float?,
    volumeOverlay: Int?,
    maxVolume: Int,
    onToggleControls: () -> Unit,
    onTogglePlay: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onRewindClick: (() -> Unit)? = null,
    onForwardClick: (() -> Unit)? = null,
    onSourceClick: (() -> Unit)? = null,
    onSpeedClick: (() -> Unit)? = null,
    playbackSpeedLabel: String? = null,
    onCastClick: (() -> Unit)? = null,
    onExitFullscreen: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onGestureEnd: () -> Unit,
    audioManager: AudioManager,
    activity: Activity?,
    context: Context,
    playerViewFactory: @Composable () -> Unit
) {
    val onSeekToState = rememberUpdatedState(onSeekTo)
    val onToggleControlsState = rememberUpdatedState(onToggleControls)
    val onTogglePlayState = rememberUpdatedState(onTogglePlay)
    val onRewindClickState = rememberUpdatedState(onRewindClick)
    val onForwardClickState = rememberUpdatedState(onForwardClick)
    val onBrightnessChangeState = rememberUpdatedState(onBrightnessChange)
    val onVolumeChangeState = rememberUpdatedState(onVolumeChange)
    val onGestureEndState = rememberUpdatedState(onGestureEnd)

    var seekOverlayPosition by remember { mutableStateOf<Long?>(null) }
    var seekOverlayForward by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .then(
                if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                }
            )
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControlsState.value() },
                    onDoubleTap = { offset ->
                        if (isFullscreen) {
                            when {
                                offset.x < size.width / 3f -> {
                                    onRewindClickState.value?.invoke() ?: onTogglePlayState.value()
                                }

                                offset.x > size.width * 2f / 3f -> {
                                    onForwardClickState.value?.invoke() ?: onTogglePlayState.value()
                                }

                                else -> onTogglePlayState.value()
                            }
                        } else {
                            onTogglePlayState.value()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var dragMode: PlayerDragMode? = null
                var startX = 0f
                var startPosition = 0L
                var startBrightness = 0.5f
                var startVolume = 0
                var totalDragX = 0f
                var totalDragY = 0f
                val directionThreshold = 10.dp.toPx()

                fun clearGestureOverlays() {
                    seekOverlayPosition = null
                    onGestureEndState.value()
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        dragMode = null
                        startX = offset.x
                        startPosition = currentPositionState.value
                        startBrightness = readCurrentBrightness(context, activity)
                        startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val screenWidth = size.width
                        val screenHeight = size.height
                        if (screenWidth <= 0 || screenHeight <= 0) return@detectDragGestures

                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragMode == null) {
                            val horizontalDistance = abs(totalDragX)
                            val verticalDistance = abs(totalDragY)
                            if (horizontalDistance < directionThreshold && verticalDistance < directionThreshold) {
                                return@detectDragGestures
                            }

                            dragMode = when {
                                isFullscreen &&
                                    durationState.value > 0L &&
                                    horizontalDistance > verticalDistance -> PlayerDragMode.Seek

                                verticalDistance >= horizontalDistance -> {
                                    if (startX < screenWidth / 2f) PlayerDragMode.Brightness else PlayerDragMode.Volume
                                }

                                else -> return@detectDragGestures
                            }
                        }

                        when (dragMode) {
                            PlayerDragMode.Seek -> {
                                val durationValue = durationState.value
                                if (durationValue <= 0L) return@detectDragGestures
                                val delta = (totalDragX / screenWidth * durationValue).toLong()
                                val target = quickSeekPosition(startPosition, durationValue, delta)
                                seekOverlayForward = totalDragX >= 0f
                                seekOverlayPosition = target
                                onSeekToState.value(target)
                            }

                            PlayerDragMode.Brightness -> {
                                val newBrightness = (startBrightness - totalDragY / screenHeight).coerceIn(0f, 1f)
                                activity?.window?.let { window ->
                                    window.attributes = window.attributes.apply {
                                        screenBrightness = newBrightness
                                    }
                                }
                                onBrightnessChangeState.value(newBrightness)
                            }

                            PlayerDragMode.Volume -> {
                                val volumeDelta = (-totalDragY / screenHeight * maxVolume).roundToInt()
                                val newVolume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                onVolumeChangeState.value(newVolume)
                            }

                            null -> Unit
                        }
                    },
                    onDragCancel = { clearGestureOverlays() },
                    onDragEnd = { clearGestureOverlays() }
                )
            }
    ) {
        playerViewFactory()

        seekOverlayPosition?.let { position ->
            GestureOverlay(
                icon = if (seekOverlayForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                text = "${formatTime(position)} / ${formatTime(durationState.value)}"
            )
        }

        brightnessOverlay?.let { brightness ->
            GestureOverlay(
                icon = Icons.Default.Brightness6,
                text = "${(brightness * 100).toInt()}%"
            )
        }

        volumeOverlay?.let { volume ->
            GestureOverlay(
                icon = if (volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                text = "${(volume * 100 / maxVolume)}%"
            )
        }

        if (!showControls && (playbackUiState.isRecovering || playbackUiState.isFailed)) {
            PlaybackStatusOverlay(
                state = playbackUiState,
                recoveringTitle = recoveringTitle,
                onRetryPlayback = onRetryPlayback
            )
        }

        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            ) {
                if (isFullscreen) {
                    IconButton(
                        onClick = onExitFullscreen,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(74.dp),
                    color = Color.Black.copy(alpha = 0.54f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentPosition = currentPositionState.value
                        val duration = durationState.value
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { progress -> onSeekTo((progress * duration).toLong()) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = AppColors.Primary,
                                activeTrackColor = AppColors.Primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                            )
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isFullscreen) {
                            IconButton(onClick = onToggleFullscreen) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "全屏",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    if (isFullscreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            onRewindClick?.let { rewindClick ->
                                PlayerFullscreenActionButton(
                                    icon = Icons.Default.FastRewind,
                                    label = "快退",
                                    onClick = rewindClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
                            }
                            onForwardClick?.let { forwardClick ->
                                PlayerFullscreenActionButton(
                                    icon = Icons.Default.FastForward,
                                    label = "快进",
                                    onClick = forwardClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
                            }
                            onSourceClick?.let { sourceClick ->
                                PlayerFullscreenActionButton(
                                    icon = Icons.Default.SwapHoriz,
                                    label = "换源",
                                    onClick = sourceClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
                            }
                            if (onSpeedClick != null && playbackSpeedLabel != null) {
                                PlayerFullscreenActionButton(
                                    icon = Icons.Default.Speed,
                                    label = playbackSpeedLabel,
                                    onClick = onSpeedClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
                            }
                            onCastClick?.let { castClick ->
                                PlayerFullscreenActionButton(
                                    icon = Icons.Default.Cast,
                                    label = "投屏",
                                    onClick = castClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                )
                            }
                            PlayerFullscreenActionButton(
                                icon = Icons.Default.FullscreenExit,
                                label = "退出",
                                onClick = onToggleFullscreen,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            )
                        }
                    }
                }
            }
        } else if (!isPlaying && !playbackUiState.isFailed && !playbackUiState.isRecovering) {
            Surface(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(84.dp),
                color = Color.Black.copy(alpha = 0.52f),
                contentColor = Color.White,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

private fun createTexturePlayerView(context: Context): PlayerView {
    return (LayoutInflater.from(context).inflate(
        R.layout.view_texture_player,
        null,
        false
    ) as PlayerView).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(android.graphics.Color.BLACK)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PlaybackStatusOverlay(
    state: PlaybackUiState,
    recoveringTitle: String,
    onRetryPlayback: () -> Unit
) {
    Surface(
        onClick = {
            if (state.isFailed) onRetryPlayback()
        },
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp),
        color = Color.Black.copy(alpha = 0.70f),
        contentColor = Color.White,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            1.dp,
            if (state.isFailed) AppColors.Error.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.20f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (state.isFailed) "线路不可用" else recoveringTitle,
                color = if (state.isFailed) AppColors.Error else AppColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = state.message,
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            if (state.isFailed) {
                Text(
                    text = "点击重试",
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlayerMetaPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.SurfaceAlt)
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        color = AppColors.TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun PlaybackStatsRow(stats: PlaybackStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerStatsCell(
            label = "分辨率",
            value = formatResolution(stats.resolutionWidth, stats.resolutionHeight),
            modifier = Modifier.weight(1f)
        )
        PlayerStatsCell(
            label = "码率",
            value = formatBitrate(stats.videoBitrateBitsPerSecond),
            modifier = Modifier.weight(1f)
        )
        PlayerStatsCell(
            label = "网速",
            value = formatTransferSpeed(stats.networkSpeedBitsPerSecond),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RadioStatsRow(
    codec: String,
    bitrate: Int,
    networkSpeedBitsPerSecond: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerStatsCell(
            label = "格式",
            value = codec.ifBlank { "AUDIO" },
            modifier = Modifier.weight(1f)
        )
        PlayerStatsCell(
            label = "码率",
            value = bitrate.takeIf { it > 0 }?.let { "${it} Kbps" } ?: "待获取",
            modifier = Modifier.weight(1f)
        )
        PlayerStatsCell(
            label = "网速",
            value = formatTransferSpeed(networkSpeedBitsPerSecond),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerStatsCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(46.dp),
        color = AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = AppColors.TextTertiary,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
            Text(
                text = value,
                color = AppColors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PlayerSourceLinkRow(
    url: String,
    onCopyClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        color = AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "当前源地址",
                    color = AppColors.TextTertiary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = url.ifBlank { "暂无可用地址" },
                    color = AppColors.TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCopyClick,
                enabled = url.isNotBlank(),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "复制当前源地址",
                    tint = if (url.isNotBlank()) AppColors.Primary else AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveLineOptionRow(
    option: PlayerSourceOption,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        color = if (option.isCurrent) AppColors.PrimaryLight else AppColors.SurfaceAlt,
        contentColor = AppColors.TextPrimary,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(
            1.dp,
            if (option.isCurrent) AppColors.Primary.copy(alpha = 0.42f) else AppColors.Divider
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = option.sourceName,
                    color = if (option.isCurrent) AppColors.Primary else AppColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.episodeLabel.ifBlank { "同名频道备用线路" },
                    color = AppColors.TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (option.isCurrent) {
                Text(
                    text = "当前",
                    color = AppColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlayerUtilityButton(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = if (enabled) AppColors.SurfaceAlt else AppColors.Surface,
        contentColor = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary,
        shape = shape,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        val horizontalPadding = if (label == null) 0.dp else 8.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            if (label != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = if (enabled) AppColors.TextPrimary else AppColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun PlayerFullscreenActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.46f),
        contentColor = Color.White,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false
            )
        }
    }
}

@Composable
private fun GestureOverlay(
    icon: ImageVector,
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White,
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = text, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun readCurrentBrightness(context: Context, activity: Activity?): Float {
    val windowBrightness = activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    if (windowBrightness in 0f..1f) {
        return windowBrightness
    }

    return try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        ) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

private fun enterFullscreen(activity: Activity?) {
    activity?.apply {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun exitFullscreen(activity: Activity?) {
    activity?.apply {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = ms / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun formatResolution(width: Int, height: Int): String {
    return if (width > 0 && height > 0) {
        "${width}x$height"
    } else {
        "待获取"
    }
}

private fun formatBitrate(bitsPerSecond: Long): String {
    if (bitsPerSecond <= 0L) return "待获取"
    return if (bitsPerSecond >= 1_000_000L) {
        val mbps = bitsPerSecond / 1_000_000f
        if (mbps >= 10f) {
            String.format("%.0f Mbps", mbps)
        } else {
            String.format("%.1f Mbps", mbps)
        }
    } else {
        "${(bitsPerSecond / 1_000L).coerceAtLeast(1L)} Kbps"
    }
}

private fun formatTransferSpeed(bitsPerSecond: Long): String {
    if (bitsPerSecond <= 0L) return "待获取"
    val bytesPerSecond = bitsPerSecond / 8f
    return if (bytesPerSecond >= 1_048_576f) {
        val mbps = bytesPerSecond / 1_048_576f
        if (mbps >= 10f) {
            String.format("%.0f MB/s", mbps)
        } else {
            String.format("%.1f MB/s", mbps)
        }
    } else {
        "${(bytesPerSecond / 1024f).toLong().coerceAtLeast(1L)} KB/s"
    }
}

private fun episodeTopBarTitle(title: String, episodeLabel: String): String {
    return when {
        title.isNotBlank() && episodeLabel.isNotBlank() -> "$title · $episodeLabel"
        title.isNotBlank() -> title
        episodeLabel.isNotBlank() -> episodeLabel
        else -> "剧集播放"
    }
}

private fun quickSeekPosition(currentPosition: Long, duration: Long, delta: Long): Long {
    val target = currentPosition + delta
    return if (duration > 0L) {
        target.coerceIn(0L, duration)
    } else {
        target.coerceAtLeast(0L)
    }
}

private fun copyPlayerSourceLink(
    context: Context,
    clipboardManager: ClipboardManager,
    url: String
) {
    if (url.isBlank()) return
    clipboardManager.setPrimaryClip(ClipData.newPlainText("流光源地址", url))
    Toast.makeText(context, "源地址已复制", Toast.LENGTH_SHORT).show()
}

private fun episodePlayerSubtitle(
    sourceLabel: String,
    currentPosition: Long,
    duration: Long,
    playbackUiState: PlaybackUiState
): String {
    if (playbackUiState.isRecovering || playbackUiState.isFailed) {
        return playbackUiState.message
    }
    return if (duration > 0L) {
        "已播放 ${formatTime(currentPosition)} / ${formatTime(duration)}"
    } else {
        "$sourceLabel 线路准备中"
    }
}

private fun livePlayerSubtitle(
    group: String,
    format: String,
    sourceLabel: String,
    playbackUiState: PlaybackUiState
): String {
    if (playbackUiState.isRecovering || playbackUiState.isFailed) {
        return playbackUiState.message
    }
    val meta = listOf(group, format, sourceLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    return if (meta.isBlank()) "直播线路已接入" else "$meta · 直播线路已接入"
}

private fun radioPlayerSubtitle(
    title: String,
    group: String,
    sourceLabel: String,
    playbackUiState: PlaybackUiState
): String {
    if (playbackUiState.isRecovering || playbackUiState.isFailed) {
        return playbackUiState.message
    }
    val meta = listOf(title, group, sourceLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    return if (meta.isBlank()) "电台音频流已接入" else "$meta · 电台音频流已接入"
}

private fun playerSourceLabel(episodeUrl: String): String {
    val parsed = Uri.parse(episodeUrl)
    val hostParts = parsed.host
        ?.split('.')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    val preferredHost = hostParts.firstOrNull { part ->
        part.length > 2 && part !in setOf("www", "m", "v")
    }
    val fallbackHost = hostParts.firstOrNull { it.isNotBlank() }
    if (!preferredHost.isNullOrBlank()) return preferredHost
    if (!fallbackHost.isNullOrBlank()) return fallbackHost
    return when {
        episodeUrl.contains("m3u8", ignoreCase = true) -> "m3u8"
        episodeUrl.contains("mp4", ignoreCase = true) -> "mp4"
        else -> "线路"
    }
}
