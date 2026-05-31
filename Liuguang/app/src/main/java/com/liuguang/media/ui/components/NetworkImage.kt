package com.liuguang.media.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.liuguang.media.ui.theme.AppColors

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var fallbackMode by remember(url) { mutableStateOf(ImageFallbackMode.Loading) }
    val imageRequest = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .build()
    }

    Box(modifier = modifier) {
        if (url.isBlank()) {
            ImageFallback(title = contentDescription, mode = ImageFallbackMode.Error)
        } else {
            if (fallbackMode != ImageFallbackMode.Hidden) {
                ImageFallback(title = contentDescription, mode = fallbackMode)
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                onLoading = { fallbackMode = ImageFallbackMode.Loading },
                onSuccess = { fallbackMode = ImageFallbackMode.Hidden },
                onError = { fallbackMode = ImageFallbackMode.Error },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private enum class ImageFallbackMode {
    Loading,
    Error,
    Hidden
}

@Composable
private fun ImageFallback(
    title: String?,
    mode: ImageFallbackMode
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (mode == ImageFallbackMode.Loading) {
                    Modifier.background(AppColors.SurfaceAlt)
                } else {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                AppColors.SurfaceAlt,
                                AppColors.SurfaceRaised,
                                AppColors.Primary.copy(alpha = 0.10f)
                            )
                        )
                    )
                }
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (mode == ImageFallbackMode.Error) {
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: "流光",
                color = AppColors.TextPrimary.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
