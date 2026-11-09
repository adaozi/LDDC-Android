// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.ui.components.ChoiceChips
import org.lddc.android.ui.components.EmptyState
import org.lddc.android.ui.components.PathField
import org.lddc.android.ui.components.ScreenHeader
import org.lddc.android.ui.components.SectionCard
import org.lddc.android.ui.components.ThinProgressBar
import org.lddc.android.ui.components.lddc
import org.lddc.android.ui.theme.Space
import org.lddc.android.ui.util.StoragePath

@Composable
fun ToolsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: ToolsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { StoragePath.fromTreeUri(context, it)?.let(viewModel::onSourceChange) }
    }
    val outPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { StoragePath.fromTreeUri(context, it)?.let(viewModel::onOutChange) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            ScreenHeader(
                title = "工具箱",
                subtitle = "不联网也能用的本地小工具",
            )
        }

        item(key = "convert") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "批量格式转换",
                icon = Icons.Rounded.AutoFixHigh,
            ) {
                Text(
                    text = "扫描目录下的 .lrc，按目标格式重新生成字幕/歌词文件。原文件不会被修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                PathField(
                    label = "源目录",
                    value = state.sourceDir,
                    onValueChange = viewModel::onSourceChange,
                    onPick = { sourcePicker.launch(null) },
                    placeholder = "含 .lrc 的目录",
                )
                Spacer(Modifier.height(Space.sm))
                PathField(
                    label = "输出目录（留空 = 源目录/converted_格式）",
                    value = state.outDir,
                    onValueChange = viewModel::onOutChange,
                    onPick = { outPicker.launch(null) },
                    placeholder = "可选",
                )
                Spacer(Modifier.height(Space.md))
                Text("目标格式", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(Space.sm))
                ChoiceChips(
                    items = LyricsFormat.entries,
                    label = { it.displayName },
                    isSelected = { it == state.format },
                    onSelect = viewModel::onFormatChange,
                )
                Spacer(Modifier.height(Space.md))
                Button(
                    onClick = viewModel::start,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state.running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.running) "转换中…" else "开始转换")
                }
            }
        }

        if (state.total > 0) {
            item(key = "progress") {
                SectionCard(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "进度",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.done} / ${state.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    ThinProgressBar(progress = if (state.total == 0) 0f else state.done.toFloat() / state.total)
                }
            }
        }

        if (state.files.isEmpty() && state.total == 0 && !state.running) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "选个目录开始",
                    description = "支持 LRC ↔ SRT / ASS 互转，以及逐字、逐行、增强型 LRC 之间的转换。",
                    modifier = Modifier.padding(top = Space.md),
                )
            }
        }

        items(state.files, key = { it.first }) { (name, ok) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (ok) lddc.success else lddc.warning,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (ok) "完成" else "跳过",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ok) lddc.success else lddc.warning,
                )
            }
        }

        item(key = "bottom_space") { Spacer(Modifier.height(Space.xl)) }
    }
}
