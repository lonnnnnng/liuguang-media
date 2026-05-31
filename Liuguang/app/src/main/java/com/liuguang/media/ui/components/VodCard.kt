package com.liuguang.media.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.ui.theme.AppColors
import com.liuguang.media.ui.theme.Dimens

@Composable
fun VodCard(
    title: String,
    coverUrl: String,
    remark: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceName: String? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Dimens.radiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(Dimens.radiusMedium))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
            ) {
                NetworkImage(
                    url = coverUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = Dimens.radiusMedium, topEnd = Dimens.radiusMedium))
                )

                if (!remark.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.paddingSmall)
                            .background(
                                color = AppColors.Primary,
                                shape = RoundedCornerShape(Dimens.radiusSmall)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = remark,
                            color = AppColors.OnPrimary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.paddingSmall,
                        top = Dimens.paddingSmall,
                        end = Dimens.paddingSmall,
                        bottom = if (sourceName.isNullOrBlank()) Dimens.paddingSmall else 2.dp
                    ),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.TextPrimary
            )

            if (!sourceName.isNullOrBlank()) {
                Text(
                    text = sourceName,
                    modifier = Modifier
                        .padding(
                            start = Dimens.paddingSmall,
                            end = Dimens.paddingSmall,
                            bottom = Dimens.paddingSmall
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.PrimaryLight)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = AppColors.Primary
                )
            }
        }
    }
}
