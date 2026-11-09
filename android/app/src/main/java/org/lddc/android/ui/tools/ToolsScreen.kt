// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.runtime.Composable
import org.lddc.android.ui.common.PlaceholderScreen

@Composable
fun ToolsScreen() {
    PlaceholderScreen(
        icon = Icons.Outlined.Build,
        title = "批量格式转换",
        description = "M7 阶段接入：把歌词批量转换为逐字LRC / 逐行LRC / 增强LRC / SRT / ASS。",
    )
}
