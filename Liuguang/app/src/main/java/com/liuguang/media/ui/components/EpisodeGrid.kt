package com.liuguang.media.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.domain.model.EpisodeItem
import com.liuguang.media.ui.theme.Dimens

@Composable
fun EpisodeGrid(
    episodes: List<EpisodeItem>,
    currentEpisodeUrl: String?,
    onEpisodeClick: (EpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(Dimens.paddingMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.paddingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.paddingSmall)
    ) {
        items(episodes) { episode ->
            val isSelected = episode.url == currentEpisodeUrl
            Box(
                modifier = Modifier
                    .aspectRatio(2f)
                    .clip(RoundedCornerShape(Dimens.radiusSmall))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onEpisodeClick(episode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.label,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
