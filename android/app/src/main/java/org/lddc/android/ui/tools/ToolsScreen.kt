// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ToolsScreen(viewModel: ToolsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.sourceDir,
                onValueChange = viewModel::onSourceChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("源目录（扫描 .lrc）") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.outDir,
                onValueChange = viewModel::onOutChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("输出目录（留空 = 源目录/converted_格式）") },
            )

            Spacer(Modifier.height(10.dp))
            Text("目标格式", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                org.lddc.android.data.model.LyricsFormat.entries.take(3).forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                org.lddc.android.data.model.LyricsFormat.entries.drop(3).forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { viewModel.onFormatChange(format) },
                        label = { Text(format.displayName) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = viewModel::start,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.running) "转换中…" else "开始转换")
            }

            if (state.total > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "进度 ${state.done} / ${state.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.files, key = { it.first }) { (name, ok) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (ok) "成功" else "跳过",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}
