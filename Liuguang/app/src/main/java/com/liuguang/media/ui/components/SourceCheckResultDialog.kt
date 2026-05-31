package com.liuguang.media.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liuguang.media.ui.theme.AppColors

data class SourceCheckResultDialogState(
    val title: String,
    val sourceName: String,
    val success: Boolean,
    val message: String,
    val summary: List<SourceCheckSummaryItem> = emptyList(),
    val returnedContent: String? = null
)

data class SourceCheckSummaryItem(
    val label: String,
    val value: String
)

@Composable
fun SourceCheckResultDialog(
    state: SourceCheckResultDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        titleContentColor = AppColors.TextPrimary,
        textContentColor = AppColors.TextSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.title,
                    color = if (state.success) AppColors.Primary else AppColors.Error,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = state.sourceName,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (state.success) {
                        AppColors.Primary.copy(alpha = 0.10f)
                    } else {
                        AppColors.Error.copy(alpha = 0.10f)
                    },
                    contentColor = if (state.success) AppColors.Primary else AppColors.Error,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.success) {
                            AppColors.Primary.copy(alpha = 0.28f)
                        } else {
                            AppColors.Error.copy(alpha = 0.28f)
                        }
                    )
                ) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (state.summary.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.summary.forEach { item ->
                            SourceCheckSummaryRow(item = item)
                        }
                    }
                }

                if (!state.returnedContent.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            text = "接口返回内容",
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        SelectionContainer {
                            Text(
                                text = state.returnedContent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = AppColors.SurfaceAlt,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
                                    .padding(12.dp),
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun SourceCheckSummaryRow(item: SourceCheckSummaryItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceAlt, RoundedCornerShape(4.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(4.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = item.label,
            color = AppColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = item.value,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(0.64f)
        )
    }
}
