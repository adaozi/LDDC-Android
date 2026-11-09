// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.lddc.android.R
import org.lddc.android.data.settings.SettingsRepository
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

    Scaffold(
        bottomBar = {
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
