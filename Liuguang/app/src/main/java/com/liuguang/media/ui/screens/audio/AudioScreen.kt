package com.liuguang.media.ui.screens.audio

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.domain.model.PodcastEpisode
import com.liuguang.media.domain.model.RadioStation
import com.liuguang.media.ui.components.CinemaBackground
import com.liuguang.media.ui.screens.podcast.PodcastScreen
import com.liuguang.media.ui.screens.podcast.PodcastViewModel
import com.liuguang.media.ui.screens.radio.RadioScreen
import com.liuguang.media.ui.theme.AppColors

private enum class AudioTab(
    val title: String,
    val icon: ImageVector
) {
    Radio("电台", Icons.Default.Radio),
    Podcast("播客", Icons.Default.Podcasts)
}

@Composable
fun AudioScreen(
    onSecondaryPageVisibilityChange: (Boolean) -> Unit = {},
    onNavigateToRadioPlayer: (RadioStation, Long?) -> Unit,
    onNavigateToPodcastPlayer: (PodcastEpisode, String, String) -> Unit,
    podcastViewModel: PodcastViewModel = hiltViewModel()
) {
    var selectedTabName by rememberSaveable { mutableStateOf(AudioTab.Radio.name) }
    val selectedTab = AudioTab.valueOf(selectedTabName)
    val podcastUiState by podcastViewModel.uiState.collectAsState()
    val secondaryPageVisible = selectedTab == AudioTab.Podcast &&
        podcastUiState.selectedSubscriptionId != null

    LaunchedEffect(secondaryPageVisible) {
        onSecondaryPageVisibilityChange(secondaryPageVisible)
    }

    DisposableEffect(Unit) {
        onDispose { onSecondaryPageVisibilityChange(false) }
    }

    CinemaBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AudioHeader(
                selectedTab = selectedTab,
                onTabSelected = { selectedTabName = it.name }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Crossfade(targetState = selectedTab, label = "audio-tab-content") { tab ->
                    when (tab) {
                        AudioTab.Radio -> RadioScreen(
                            onNavigateToPlayer = onNavigateToRadioPlayer,
                            useOuterBackground = false
                        )
                        AudioTab.Podcast -> PodcastScreen(
                            onNavigateBack = {},
                            onNavigateToPlayer = onNavigateToPodcastPlayer,
                            useOuterBackground = false,
                            showBackButton = false,
                            showHeader = false,
                            contentBottomPadding = 18.dp,
                            viewModel = podcastViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioHeader(
    selectedTab: AudioTab,
    onTabSelected: (AudioTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Shell)
            .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.Divider, RectangleShape),
            color = AppColors.Surface,
            shape = RectangleShape,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudioTab.values().forEach { tab ->
                    AudioTabButton(
                        tab = tab,
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTabButton(
    tab: AudioTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RectangleShape)
            .background(if (selected) AppColors.Primary else AppColors.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = if (selected) AppColors.OnPrimary else AppColors.TextSecondary,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(18.dp)
        )
        Text(
            text = tab.title,
            color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}
