package com.liuguang.media.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.liuguang.media.ui.theme.AppColors

@Composable
fun SourceEditorDialog(
    title: String,
    initialName: String = "",
    initialUrl: String = "",
    description: String = "填写源名称和地址，保存后会立即更新当前源列表。",
    nameLabel: String = "源名称",
    urlLabel: String = "源地址",
    urlPlaceholder: String = "https://example.com/source",
    confirmText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val canConfirm = trimmedName.isNotBlank() && trimmedUrl.isNotBlank()

    SourceEditorFrame(
        title = title,
        description = description,
        icon = Icons.Default.Link,
        confirmText = confirmText ?: if (title.contains("添加")) "添加" else "保存",
        canConfirm = canConfirm,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(trimmedName, trimmedUrl) }
    ) {
        SourceTextField(
            value = name,
            onValueChange = { name = it },
            label = nameLabel,
            placeholder = "例如：默认源",
            leadingIcon = Icons.Default.DriveFileRenameOutline,
            singleLine = true
        )

        SourceTextField(
            value = url,
            onValueChange = { url = it },
            label = urlLabel,
            placeholder = urlPlaceholder,
            leadingIcon = Icons.Default.Link,
            minLines = 4,
            maxLines = 6,
            keyboardType = KeyboardType.Uri,
            helperText = "支持粘贴长链接，可多行查看和编辑。"
        )
    }
}

@Composable
fun SourceUrlEditorDialog(
    title: String,
    initialUrl: String = "",
    description: String = "填写订阅源地址，保存后会自动刷新源信息。",
    urlLabel: String = "源地址",
    urlPlaceholder: String = "https://example.com/feed.xml",
    confirmText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val trimmedUrl = url.trim()

    SourceEditorFrame(
        title = title,
        description = description,
        icon = Icons.Default.RssFeed,
        confirmText = confirmText ?: if (title.contains("添加")) "添加" else "保存",
        canConfirm = trimmedUrl.isNotBlank(),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(trimmedUrl) }
    ) {
        SourceTextField(
            value = url,
            onValueChange = { url = it },
            label = urlLabel,
            placeholder = urlPlaceholder,
            leadingIcon = Icons.Default.RssFeed,
            minLines = 5,
            maxLines = 7,
            keyboardType = KeyboardType.Uri,
            helperText = "支持 RSS 或 Atom 订阅地址，长链接可直接粘贴。"
        )
    }
}

@Composable
private fun SourceEditorFrame(
    title: String,
    description: String,
    icon: ImageVector,
    confirmText: String,
    canConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp),
            color = AppColors.Surface,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, AppColors.DividerStrong)
        ) {
            Column {
                SourceEditorHeader(
                    title = title,
                    description = description,
                    icon = icon
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )

                SourceEditorActions(
                    confirmText = confirmText,
                    canConfirm = canConfirm,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun SourceEditorHeader(
    title: String,
    description: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Primary.copy(alpha = 0.08f),
        contentColor = AppColors.TextPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = AppColors.Primary,
                contentColor = AppColors.OnPrimary,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = AppColors.TextPrimary,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    helperText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                maxLines = if (singleLine) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = AppColors.TextTertiary
            )
        },
        supportingText = helperText?.let { text ->
            { Text(text, color = AppColors.TextTertiary, fontSize = 11.sp, lineHeight = 14.sp) }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = editorTextFieldColors()
    )
}

@Composable
private fun SourceEditorActions(
    confirmText: String,
    canConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.SurfaceSoft,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AppColors.DividerStrong),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.TextSecondary
                )
            ) {
                Text("取消", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    disabledContainerColor = AppColors.SurfaceRaised,
                    disabledContentColor = AppColors.TextTertiary
                )
            ) {
                Text(confirmText, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun editorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedContainerColor = AppColors.SurfaceAlt,
    unfocusedContainerColor = AppColors.SurfaceAlt,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider,
    cursorColor = AppColors.Primary,
    focusedLabelColor = AppColors.Primary,
    unfocusedLabelColor = AppColors.TextSecondary
)
