// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lddc.android.R
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.domain.BatchController
import org.lddc.android.ui.components.HairlineDivider
import org.lddc.android.ui.components.lddc
import org.lddc.android.ui.local.LocalScreen
import org.lddc.android.ui.search.SearchScreen
import org.lddc.android.ui.settings.SettingsScreen
import org.lddc.android.ui.theme.Space
import org.lddc.android.ui.tools.ToolsScreen

private data class TabItem(val labelRes: Int, val icon: ImageVector)

private val Tabs = listOf(
    TabItem(R.string.tab_search, Icons.Rounded.Search),
    TabItem(R.string.tab_local, Icons.Rounded.Folder),
    TabItem(R.string.tab_tools, Icons.Rounded.AutoFixHigh),
    TabItem(R.string.tab_settings, Icons.Rounded.Settings),
)

@Composable
fun AppScaffold(settingsRepository: SettingsRepository) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val batch by BatchController.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 任务条保留到用户点「查看」或关闭为止（新任务开始时重新出现）
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(batch.running) { if (batch.running) dismissed = false }
    val showBanner = !dismissed && batch.total > 0 && (batch.running || batch.message != null)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = showBanner,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
                ) {
                    TaskBanner(
                        running = batch.running,
                        title = batch.title,
                        done = batch.done,
                        total = batch.total,
                        message = batch.message,
                        onOpen = {
                            selected = 1
                            dismissed = true
                        },
                        onDismiss = { dismissed = true },
                    )
                }
                BottomNav(selected = selected, onSelect = { selected = it })
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selected) {
                0 -> SearchScreen(snackbarHostState = snackbarHostState)
                1 -> LocalScreen(snackbarHostState = snackbarHostState)
                2 -> ToolsScreen(snackbarHostState = snackbarHostState)
                3 -> SettingsScreen(repository = settingsRepository)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            HairlineDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tabs.forEachIndexed { index, item ->
                    NavItem(
                        item = item,
                        active = selected == index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    item: TabItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(200),
        label = "navContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "navContent",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

/** 全局任务条：任务在后台跑的时候，任何页面都能看到进度并一键跳转 */
@Composable
private fun TaskBanner(
    running: Boolean,
    title: String,
    done: Int,
    total: Int,
    message: String?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = if (running) MaterialTheme.colorScheme.primary else lddc.success
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                if (running) {
                    CircularProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f),
                    )
                } else {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (running) "正在处理「$title」" else (message ?: "任务结束"),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (total > 0) {
                    Text(
                        text = if (running) "$done / $total" else "已完成 $done / $total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onOpen,
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.14f),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("查看", style = MaterialTheme.typography.labelMedium, color = accent)
                    Icon(Icons.Rounded.ChevronRight, null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭任务条",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
