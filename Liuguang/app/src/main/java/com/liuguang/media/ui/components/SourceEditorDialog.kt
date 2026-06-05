package com.liuguang.media.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    icon: ImageVector = Icons.Default.Link,
    confirmText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var nameTouched by remember(initialName) { mutableStateOf(false) }
    var urlTouched by remember(initialUrl) { mutableStateOf(false) }
    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val canConfirm = trimmedName.isNotBlank() && trimmedUrl.isNotBlank()
    val nameError = if (nameTouched && trimmedName.isBlank()) "请输入源名称" else null
    val urlError = if (urlTouched && trimmedUrl.isBlank()) "请输入源地址" else null

    SourceEditorFrame(
        title = title,
        description = description,
        icon = icon,
        confirmText = confirmText ?: if (title.contains("添加")) "添加" else "保存",
        canConfirm = canConfirm,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(trimmedName, trimmedUrl) }
    ) {
        SourceTextField(
            value = name,
            onValueChange = {
                nameTouched = true
                name = it
            },
            label = nameLabel,
            placeholder = "例如：默认源",
            leadingIcon = Icons.Default.DriveFileRenameOutline,
            singleLine = true,
            helperText = "用于在列表中快速识别这个源。",
            errorText = nameError
        )

        SourceTextField(
            value = url,
            onValueChange = {
                urlTouched = true
                url = it
            },
            label = urlLabel,
            placeholder = urlPlaceholder,
            leadingIcon = Icons.Default.Link,
            minLines = 1,
            maxLines = 3,
            keyboardType = KeyboardType.Uri,
            helperText = "支持粘贴长链接，可多行查看和编辑。",
            errorText = urlError
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
    helperText: String = "支持 RSS 或 Atom 订阅地址，长链接可直接粘贴。",
    icon: ImageVector = Icons.Default.RssFeed,
    confirmText: String? = null,
    isConfirming: Boolean = false,
    dismissEnabled: Boolean = true,
    topContent: (@Composable ColumnScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var urlTouched by remember(initialUrl) { mutableStateOf(false) }
    val trimmedUrl = url.trim()
    val urlError = if (urlTouched && trimmedUrl.isBlank()) "请输入源地址" else null

    SourceEditorFrame(
        title = title,
        description = description,
        icon = icon,
        confirmText = confirmText ?: if (title.contains("添加")) "添加" else "保存",
        canConfirm = trimmedUrl.isNotBlank() && !isConfirming,
        isConfirming = isConfirming,
        dismissEnabled = dismissEnabled,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(trimmedUrl) }
    ) {
        topContent?.invoke(this)
        SourceTextField(
            value = url,
            onValueChange = {
                urlTouched = true
                url = it
            },
            label = urlLabel,
            placeholder = urlPlaceholder,
            leadingIcon = icon,
            minLines = 3,
            maxLines = 5,
            keyboardType = KeyboardType.Uri,
            helperText = helperText,
            errorText = urlError
        )
        bottomContent?.invoke(this)
    }
}

@Composable
fun SourceBulkImportDialog(
    title: String,
    description: String,
    initialValue: String = "",
    placeholder: String,
    helperText: String,
    icon: ImageVector,
    isImporting: Boolean,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    SourceUrlEditorDialog(
        title = title,
        initialUrl = initialValue,
        description = description,
        urlLabel = "导入内容",
        urlPlaceholder = placeholder,
        helperText = helperText,
        icon = icon,
        confirmText = "导入",
        isConfirming = isImporting,
        dismissEnabled = !isImporting,
        bottomContent = bottomContent,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

@Composable
private fun SourceEditorFrame(
    title: String,
    description: String,
    icon: ImageVector,
    confirmText: String,
    canConfirm: Boolean,
    isConfirming: Boolean = false,
    dismissEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (dismissEnabled && !isConfirming) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .heightIn(max = 560.dp),
            color = AppColors.Surface,
            shape = RectangleShape,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.16f))
        ) {
            Column {
                SourceEditorHeader(
                    title = title,
                    description = description,
                    icon = icon,
                    dismissEnabled = dismissEnabled && !isConfirming,
                    onDismiss = onDismiss
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )

                SourceEditorActions(
                    confirmText = confirmText,
                    canConfirm = canConfirm,
                    isConfirming = isConfirming,
                    dismissEnabled = dismissEnabled,
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
    icon: ImageVector,
    dismissEnabled: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = AppColors.PrimaryLight,
                contentColor = AppColors.OnPrimary,
                shape = RectangleShape,
                border = BorderStroke(1.dp, AppColors.Primary.copy(alpha = 0.20f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AppColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = AppColors.PrimaryLight,
                        contentColor = AppColors.Primary,
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "必填",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 9.5.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = description,
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = if (dismissEnabled) AppColors.TextSecondary else AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
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
    helperText: String? = null,
    errorText: String? = null
) {
    val fieldHeight = when {
        singleLine -> 32.dp
        minLines <= 1 -> 52.dp
        minLines == 2 -> 68.dp
        else -> 84.dp
    }
    val supportText = errorText ?: helperText
    val shape = RectangleShape
    val borderColor = if (errorText != null) AppColors.Error else AppColors.Divider
    val textStyle = LocalTextStyle.current.copy(
        color = AppColors.TextPrimary,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "*",
                color = AppColors.Error,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight)
                .background(AppColors.SurfaceAlt, shape)
                .border(BorderStroke(1.dp, borderColor), shape),
            textStyle = textStyle,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = if (singleLine) 6.dp else 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
                ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = AppColors.TextTertiary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = if (singleLine) 1 else minLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )
        if (supportText != null) {
            Text(
                text = supportText,
                modifier = Modifier.padding(start = 42.dp),
                color = if (errorText != null) AppColors.Error else AppColors.TextTertiary,
                fontSize = 9.5.sp,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun SourceEditorActions(
    confirmText: String,
    canConfirm: Boolean,
    isConfirming: Boolean,
    dismissEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.SurfaceSoft,
        border = BorderStroke(1.dp, AppColors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = dismissEnabled && !isConfirming,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, AppColors.DividerStrong),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.TextSecondary,
                    disabledContentColor = AppColors.TextTertiary
                )
            ) {
                Text("取消", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !isConfirming,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                    disabledContainerColor = AppColors.SurfaceRaised,
                    disabledContentColor = AppColors.TextTertiary
                )
            ) {
                if (isConfirming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AppColors.OnPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(confirmText, fontSize = 13.sp, fontWeight = FontWeight.Black)
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
    errorContainerColor = AppColors.SurfaceAlt,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Divider,
    errorBorderColor = AppColors.Error,
    cursorColor = AppColors.Primary,
    focusedLabelColor = AppColors.Primary,
    unfocusedLabelColor = AppColors.TextSecondary,
    focusedLeadingIconColor = AppColors.Primary,
    unfocusedLeadingIconColor = AppColors.TextTertiary,
    errorLeadingIconColor = AppColors.Error,
    errorSupportingTextColor = AppColors.Error
)
