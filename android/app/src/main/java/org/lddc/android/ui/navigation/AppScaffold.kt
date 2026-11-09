// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lddc.android.R
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.domain.BatchController
import org.lddc.android.ui.local.LocalScreen
import org.lddc.android.ui.search.SearchScreen
import org.lddc.android.ui.settings.SettingsScreen
import org.lddc.android.ui.tools.ToolsScreen

private data class TabItem(val labelRes: Int, val icon: ImageVector)

private val Tabs = listOf(
    TabItem(R.string.tab_search, Icons.Outlined.Search),
    TabItem(R.string.tab_local, Icons.Outlined.Folder),
    TabItem(R.string.tab_tools, Icons.Outlined.Build),
    TabItem(R.string.tab_settings, Icons.Outlined.Settings),
)

@Composable
fun AppScaffold(settingsRepository: SettingsRepository) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val batch by BatchController.state.collectAsState()

    // 任务条保留到用户点「查看」为止（新任务开始时重新出现），避免错过
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(batch.running) { if (batch.running) dismissed = false }
    val showBanner = !dismissed && batch.total > 0 && (batch.running || batch.message != null)

    Scaffold(
        bottomBar = {
            Column {
                if (showBanner) {
                    TaskBanner(
                        running = batch.running,
                        title = batch.title,
                        done = batch.done,
                        total = batch.total,
                        message = batch.message,
                        onClick = {
                            selected = 1
                            dismissed = true
                        },
                    )
                }
                NavigationBar {
                Tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selected) {
                0 -> SearchScreen()
                1 -> LocalScreen()
                2 -> ToolsScreen()
                3 -> SettingsScreen(settingsRepository)
            }
        }
    }
}

/** 运行中/刚结束的批量任务条，点一下跳到本地页看详情 */
@Composable
private fun TaskBanner(
    running: Boolean,
    title: String,
    done: Int,
    total: Int,
    message: String?,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (running) "正在处理「$title」" else (message ?: "任务结束"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (total > 0) "$done / $total" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("查看", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (running) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
