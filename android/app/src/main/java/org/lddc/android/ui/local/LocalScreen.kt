// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lddc.android.domain.BatchController
import org.lddc.android.domain.LocalMatchEngine
import org.lddc.android.ui.components.EmptyState
import org.lddc.android.ui.components.PathField
import org.lddc.android.ui.components.ScreenHeader
import org.lddc.android.ui.components.SectionCard
import org.lddc.android.ui.components.StatTile
import org.lddc.android.ui.components.ThinProgressBar
import org.lddc.android.ui.components.StatusPill
import org.lddc.android.ui.components.lddc
import org.lddc.android.ui.theme.Space
import org.lddc.android.ui.util.StoragePath

@Composable
fun LocalScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: LocalViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<LocalMatchEngine.Status?>(null) }

    // Android 13+ 通知是运行时权限，不申请前台服务的进度通知会被系统丢弃
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            StoragePath.fromTreeUri(context, uri)?.let { path ->
                viewModel.onRootChange(path)
                viewModel.scan()
            }
        }
    }

    val counts = remember(state.items) { state.items.groupingBy { it.status }.eachCount() }
    val failCount = (counts[LocalMatchEngine.Status.NOT_FOUND] ?: 0) + (counts[LocalMatchEngine.Status.FAILED] ?: 0)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "本地批量",
            subtitle = "扫描目录，自动匹配并写入歌曲标签",
        )

        if (state.items.isEmpty()) {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "音乐目录",
                icon = Icons.Rounded.FolderOpen,
            ) {
                PathField(
                    label = "扫描路径",
                    value = state.rootPath,
                    onValueChange = viewModel::onRootChange,
                    onPick = { folderPicker.launch(null) },
                    placeholder = "/storage/emulated/0/Music",
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = "点右侧文件夹图标可直接浏览选择；扫描不会修改任何文件。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        } else {
            // 已有列表时把目录卡收成一行，把屏幕空间留给歌曲
            Surface(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, lddc.hairline),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = StoragePath.shorten(state.rootPath, 40),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "音乐目录 · 点按更换",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.running) {
                Button(
                    onClick = viewModel::stop,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("停止任务")
                }
            } else {
                Button(
                    onClick = viewModel::start,
                    enabled = state.items.isNotEmpty() && !state.scanning,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开始匹配")
                }
            }
            OutlinedButton(
                onClick = viewModel::scan,
                enabled = !state.scanning && !state.running,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (state.scanning) "扫描中" else "扫描")
            }
        }

        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "还没有可处理的歌曲",
                    description = "选好音乐目录后点「扫描」，支持 FLAC / MP3 / M4A / WAV / APE / DSD 等格式。",
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (state.total > 0) {
                item(key = "progress") {
                    ProgressCard(
                        title = state.taskTitle,
                        done = state.done,
                        total = state.total,
                        running = state.running,
                        counts = counts,
                        onReset = viewModel::resetProgress,
                    )
                }
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterPill(
                        label = "全部 ${state.items.size}",
                        selected = filter == null,
                        onClick = { filter = null },
                    )
                    StatusFilters.forEach { status ->
                        val count = counts[status] ?: 0
                        if (count > 0 || filter == status) {
                            FilterPill(
                                label = "${status.label()} $count",
                                selected = filter == status,
                                onClick = { filter = status },
                            )
                        }
                    }
                }
            }

            if (failCount > 0 && !state.running) {
                item(key = "retry") {
                    TextButton(
                        onClick = viewModel::retryFailed,
                        modifier = Modifier.padding(horizontal = Space.md),
                    ) {
                        Text("重试未成功的 $failCount 首", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            val visible = if (filter == null) state.items else state.items.filter { it.status == filter }
            items(visible, key = { it.key }) { item ->
                LocalItemRow(item)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

private val StatusFilters = listOf(
    LocalMatchEngine.Status.SUCCESS,
    LocalMatchEngine.Status.NOT_FOUND,
    LocalMatchEngine.Status.SKIPPED,
    LocalMatchEngine.Status.FAILED,
)

private fun LocalMatchEngine.Status.label(): String = when (this) {
    LocalMatchEngine.Status.PENDING -> "待处理"
    LocalMatchEngine.Status.MATCHING -> "匹配中"
    LocalMatchEngine.Status.SUCCESS -> "成功"
    LocalMatchEngine.Status.SKIPPED -> "跳过"
    LocalMatchEngine.Status.NOT_FOUND -> "未找到"
    LocalMatchEngine.Status.FAILED -> "失败"
}

@Composable
private fun LocalMatchEngine.Status.color(): Color = when (this) {
    LocalMatchEngine.Status.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    LocalMatchEngine.Status.MATCHING -> MaterialTheme.colorScheme.primary
    LocalMatchEngine.Status.SUCCESS -> lddc.success
    LocalMatchEngine.Status.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    LocalMatchEngine.Status.NOT_FOUND -> lddc.warning
    LocalMatchEngine.Status.FAILED -> MaterialTheme.colorScheme.error
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else lddc.hairline,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ProgressCard(
    title: String,
    done: Int,
    total: Int,
    running: Boolean,
    counts: Map<LocalMatchEngine.Status, Int>,
    onReset: () -> Unit,
) {
    SectionCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank {
                        when {
                            running -> "正在匹配"
                            done > 0 -> "上次任务"
                            else -> "已扫描"
                        }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (done == 0) {
                        "共 $total 首 · 点「开始匹配」开始"
                    } else {
                        "$done / $total" + if (total > 0) " · ${done * 100 / total}%" else ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!running && done > 0 && done < total) {
                TextButton(onClick = onReset) { Text("清空断点", style = MaterialTheme.typography.labelMedium) }
            }
        }
        Spacer(Modifier.height(Space.sm))
        ThinProgressBar(progress = if (total == 0) 0f else done.toFloat() / total)
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatTile(
                value = "${counts[LocalMatchEngine.Status.SUCCESS] ?: 0}",
                label = "成功",
                color = lddc.success,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${counts[LocalMatchEngine.Status.NOT_FOUND] ?: 0}",
                label = "未找到",
                color = lddc.warning,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${counts[LocalMatchEngine.Status.SKIPPED] ?: 0}",
                label = "跳过",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = "${counts[LocalMatchEngine.Status.FAILED] ?: 0}",
                label = "失败",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocalItemRow(item: BatchController.Item) {
    val color = item.status.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.detail.isNotEmpty()) {
                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        if (item.status != LocalMatchEngine.Status.PENDING) {
            StatusPill(text = item.status.label(), color = color)
        }
    }
}
