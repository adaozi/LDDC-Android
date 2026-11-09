// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.search

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.Source

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = viewModel::onKeywordChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("歌名 歌手，例如：夜曲 周杰伦") },
                )
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Source.QM, Source.KG, Source.NE, Source.LRCLIB).forEach { source ->
                    FilterChip(
                        selected = source in state.selectedSources,
                        onClick = { viewModel.toggleSource(source) },
                        label = { Text(source.displayName) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchTypes.forEach { (type, label) ->
                    FilterChip(
                        selected = state.searchType == type,
                        onClick = { viewModel.setSearchType(type) },
                        label = { Text(label) },
                    )
                }
            }

            if (state.sourceErrors.isNotEmpty() && state.results.isNotEmpty()) {
                Text(
                    text = "部分源不可用：" + state.sourceErrors.entries.joinToString("；") {
                        "${it.key.displayName} ${it.value}"
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.results.isEmpty() && !state.loading && state.error == null) {
                Text(
                    text = "输入关键词开始搜索。四个源并发匹配，结果按源优先级交叉排列。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.downloading?.let { target ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        text = "正在下载「${target.title}」 ${state.downloadDone}/${state.downloadTotal.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (state.downloadTotal == 0) 0f
                            else state.downloadDone.toFloat() / state.downloadTotal
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.searchType == SearchType.SONG) {
                    items(state.results, key = { "${it.source}-${it.id}-${it.title}" }) { song ->
                        SongRow(song = song, onClick = { viewModel.openPreview(song) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                } else {
                    items(state.songLists, key = { "${it.source}-${it.type}-${it.id}" }) { songList ->
                        SongListRow(
                            songList = songList,
                            enabled = state.downloading == null,
                            onClick = { viewModel.downloadSongList(songList) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }

    state.preview?.let { preview ->
        PreviewDialog(
            preview = preview,
            saving = state.saving,
            onDismiss = viewModel::closePreview,
            onPickFile = { uri, name -> viewModel.saveToFile(uri, name) },
            onTranslate = viewModel::translatePreview,
        )
    }
}

@Composable
private fun SongRow(song: SongInfo, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.fullTitle.ifEmpty { song.path.orEmpty() },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = song.source.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(song.strArtist.ifEmpty { null }, song.album, song.formatDuration.ifEmpty { null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SongListRow(songList: SongListInfo, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = songList.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = songList.source.shortName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    songList.author.ifEmpty { null },
                    songList.songCount?.let { "$it 首" },
                    if (songList.type == org.lddc.android.data.model.SongListType.ALBUM) "专辑" else "歌单",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewDialog(
    preview: PreviewState,
    saving: Boolean,
    onDismiss: () -> Unit,
    onPickFile: (Uri, String) -> Unit,
    onTranslate: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickFile(uri, queryDisplayName(context, uri) ?: "song")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = preview.song.fullTitle.ifEmpty { preview.song.strArtist },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${preview.song.strArtist} · ${preview.song.source.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.heightIn(min = 120.dp, max = 380.dp).fillMaxWidth()) {
                    when {
                        preview.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                        preview.error != null -> Text(
                            text = preview.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        else -> Text(
                            text = preview.lyricsText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(
                        enabled = !preview.loading && preview.lyrics != null && !preview.translating,
                        onClick = onTranslate,
                    ) { Text(if (preview.translating) "翻译中…" else "翻译") }
                    Button(
                        enabled = !preview.loading && preview.error == null && !saving,
                        onClick = { picker.launch(arrayOf("audio/*")) },
                    ) {
                        Text(if (saving) "写入中…" else "写入歌曲文件")
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private val SearchTypes = listOf(
    SearchType.SONG to "单曲",
    SearchType.ALBUM to "专辑",
    SearchType.SONGLIST to "歌单",
)
