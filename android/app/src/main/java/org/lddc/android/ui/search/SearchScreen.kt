// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.search

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.Source
import org.lddc.android.ui.components.ChoiceChips
import org.lddc.android.ui.components.EmptyState
import org.lddc.android.ui.components.IconBubble
import org.lddc.android.ui.components.ScreenHeader
import org.lddc.android.ui.components.SegmentedControl
import org.lddc.android.ui.components.SkeletonList
import org.lddc.android.ui.components.SourceBadge
import org.lddc.android.ui.components.StatusPill
import org.lddc.android.ui.components.lddc
import org.lddc.android.ui.components.tint
import org.lddc.android.ui.theme.Space

private val ExampleKeywords = listOf("夜曲 周杰伦", "Lemon 米津玄師", "Shape of You")

@Composable
fun SearchScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = "搜索歌词",
            subtitle = "四个源并发匹配，按优先级交叉排列",
        )

        Column(Modifier.padding(horizontal = Space.lg)) {
            SearchBar(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                onSearch = {
                    focus.clearFocus()
                    viewModel.search()
                },
                onClear = { viewModel.onKeywordChange("") },
            )
            Spacer(Modifier.height(Space.md))
            SegmentedControl(
                options = SearchTypes.map { it.second },
                selectedIndex = SearchTypes.indexOfFirst { it.first == state.searchType }.coerceAtLeast(0),
                onSelect = { index -> viewModel.setSearchType(SearchTypes[index].first) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            ChoiceChips(
                items = listOf(Source.QM, Source.KG, Source.NE, Source.LRCLIB),
                label = { it.displayName },
                isSelected = { it in state.selectedSources },
                onSelect = viewModel::toggleSource,
            )
        }

        Spacer(Modifier.height(Space.md))

        if (state.sourceErrors.isNotEmpty() && (state.results.isNotEmpty() || state.songLists.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = lddc.warning,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "部分源不可用：" + state.sourceErrors.entries.joinToString("；") { "${it.key.displayName} ${it.value}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = lddc.warning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        state.downloading?.let { target ->
            DownloadProgressCard(
                title = target.title,
                done = state.downloadDone,
                total = state.downloadTotal,
            )
        }

        if (state.loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        }

        val isEmpty = state.results.isEmpty() && state.songLists.isEmpty()
        when {
            state.loading && isEmpty -> SkeletonList(count = 7, modifier = Modifier.fillMaxSize())

            isEmpty && state.error != null -> EmptyState(
                icon = Icons.Rounded.ErrorOutline,
                title = "没有拿到结果",
                description = state.error,
                modifier = Modifier.fillMaxWidth().padding(top = Space.xl),
                action = {
                    Button(onClick = viewModel::search) { Text("重试") }
                },
            )

            isEmpty -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "搜你想找的歌",
                description = "输入「歌名 歌手」命中率最高。结果会按源优先级交叉列出，点任意一条即可预览并写入。",
                modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
                action = {
                    ChoiceChips(
                        items = ExampleKeywords,
                        label = { it },
                        isSelected = { false },
                        onSelect = {
                            viewModel.onKeywordChange(it)
                            viewModel.search()
                        },
                    )
                },
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.searchType == SearchType.SONG) "${state.results.size} 条结果" else "${state.songLists.size} 个列表",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "点击预览",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                if (state.searchType == SearchType.SONG) {
                    items(state.results, key = { "${it.source}-${it.id}-${it.title}" }) { song ->
                        SongCard(song = song, onClick = { viewModel.openPreview(song) })
                    }
                } else {
                    items(state.songLists, key = { "${it.source}-${it.type}-${it.id}" }) { songList ->
                        SongListCard(
                            songList = songList,
                            enabled = state.downloading == null,
                            onClick = { viewModel.downloadSongList(songList) },
                        )
                    }
                }

                if (state.hasMore || state.loadingMore) {
                    item(key = "load_more") {
                        LoadMoreFooter(loading = state.loadingMore, onLoadMore = viewModel::loadMore)
                    }
                }
                item(key = "bottom_space") { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }

    state.preview?.let { preview ->
        PreviewSheet(
            preview = preview,
            saving = state.saving,
            onDismiss = viewModel::closePreview,
            onPickFile = { uri, name -> viewModel.saveToFile(uri, name) },
            onTranslate = viewModel::translatePreview,
            onLoadCandidates = viewModel::loadCandidates,
            onSelectCandidate = viewModel::selectCandidate,
            onHideCandidates = viewModel::hideCandidates,
            onCopy = { scope.launch { snackbarHostState.showSnackbar("歌词已复制") } },
        )
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, lddc.hairline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "歌名 歌手，例如：夜曲 周杰伦",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onSearch,
                shape = CircleShape16,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowForward, contentDescription = "搜索", modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

private val CircleShape16 = RoundedCornerShape(13.dp)

@Composable
private fun SongCard(song: SongInfo, onClick: () -> Unit) {
    ResultCard(
        icon = Icons.Rounded.MusicNote,
        tint = song.source.tint(),
        title = song.fullTitle.ifEmpty { song.path.orEmpty() },
        subtitle = listOfNotNull(
            song.strArtist.ifEmpty { null },
            song.album?.ifEmpty { null },
            song.formatDuration.ifEmpty { null },
        ).joinToString(" · "),
        badge = { SourceBadge(song.source) },
        onClick = onClick,
    )
}

@Composable
private fun SongListCard(songList: SongListInfo, enabled: Boolean, onClick: () -> Unit) {
    ResultCard(
        icon = if (songList.type == org.lddc.android.data.model.SongListType.ALBUM) Icons.Rounded.Album else Icons.Rounded.QueueMusic,
        tint = songList.source.tint(),
        title = songList.title,
        subtitle = listOfNotNull(
            songList.author.ifEmpty { null },
            songList.songCount?.let { "$it 首" },
            if (songList.type == org.lddc.android.data.model.SongListType.ALBUM) "专辑" else "歌单",
        ).joinToString(" · "),
        badge = { SourceBadge(songList.source) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun ResultCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    badge: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, lddc.hairline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(icon = icon, tint = tint)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Space.sm))
            badge()
        }
    }
}

@Composable
private fun LoadMoreFooter(loading: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = "继续上滑加载更多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadProgressCard(title: String, done: Int, total: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = "正在下载「$title」",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$done / ${total.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Space.sm))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        }
    }
}

// ---------------- 预览抽屉 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(
    preview: PreviewState,
    saving: Boolean,
    onDismiss: () -> Unit,
    onPickFile: (Uri, String) -> Unit,
    onTranslate: () -> Unit,
    onLoadCandidates: () -> Unit,
    onSelectCandidate: (org.lddc.android.data.model.LyricInfo) -> Unit,
    onHideCandidates: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    // 先播完收起动画再销毁，避免「啪」一下消失
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickFile(uri, queryDisplayName(context, uri) ?: "song")
        }
    }

    ModalBottomSheet(
        onDismissRequest = closeSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = Space.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = preview.song.fullTitle.ifEmpty { preview.song.title.orEmpty() },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            preview.song.strArtist.ifEmpty { null },
                            preview.song.album?.ifEmpty { null },
                            preview.song.formatDuration.ifEmpty { null },
                        ).joinToString(" · ").ifEmpty { preview.song.source.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(preview.lyricsText))
                        onCopy()
                    },
                    enabled = preview.lyricsText.isNotBlank(),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "复制歌词",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = closeSheet) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(preview.song.source)
                if (preview.lyricsText.isNotBlank()) {
                    StatusPill(
                        text = "${preview.lyricsText.lineSequence().count { it.isNotBlank() }} 行",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.lyrics?.has("ts") == true) {
                    StatusPill("含译文", lddc.success)
                }
                if (preview.lyrics?.has("roma") == true) {
                    StatusPill("含罗马音", lddc.warning)
                }
            }

            Spacer(Modifier.height(Space.md))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = lddc.surfaceSunken,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = Space.md, vertical = Space.md)) {
                    when {
                        preview.loading -> CircularProgressIndicator(
                            modifier = Modifier.size(26.dp).align(Alignment.Center),
                            strokeWidth = 2.5.dp,
                        )

                        preview.error != null && preview.lyricsText.isBlank() -> Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                text = preview.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }

                        else -> Text(
                            text = preview.lyricsText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetSecondaryAction(
                    icon = Icons.Rounded.SwapHoriz,
                    label = if (preview.loadingCandidates) "查询中" else "换版本",
                    enabled = !preview.loading && !preview.loadingCandidates,
                    onClick = onLoadCandidates,
                )
                SheetSecondaryAction(
                    icon = Icons.Rounded.Translate,
                    label = if (preview.translating) "翻译中" else "翻译",
                    enabled = !preview.loading && preview.lyrics != null && !preview.translating,
                    onClick = onTranslate,
                )
                Button(
                    onClick = { picker.launch(arrayOf("audio/*")) },
                    enabled = !preview.loading && preview.lyricsText.isNotBlank() && !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.md),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(Space.sm))
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(if (saving) "写入中…" else "写入歌曲文件", maxLines = 1)
                }
            }
            Text(
                text = "写入前会先选择目标歌曲文件；支持 FLAC / MP3 / M4A / APE / DSD 等格式。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = Space.sm, bottom = Space.lg + 8.dp),
            )
        }
    }

    if (preview.showCandidates && preview.candidates.isNotEmpty()) {
        CandidateDialog(
            candidates = preview.candidates,
            onSelect = onSelectCandidate,
            onDismiss = onHideCandidates,
        )
    }
}

@Composable
private fun SheetSecondaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, lddc.hairline),
    ) {
        Column(
            modifier = Modifier.width(62.dp).height(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun CandidateDialog(
    candidates: List<org.lddc.android.data.model.LyricInfo>,
    onSelect: (org.lddc.android.data.model.LyricInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, lddc.hairline),
        ) {
            Column(Modifier.padding(Space.lg)) {
                Text("选择歌词版本", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "同一首歌的不同上传版本，逐字条目通常更完整。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp).fillMaxWidth()) {
                    items(candidates, key = { it.id ?: it.creator.orEmpty() + it.duration }) { candidate ->
                        Surface(
                            onClick = { onSelect(candidate) },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.creator?.takeIf { it.isNotBlank() } ?: "未知上传者",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            candidate.score?.let { "评分 $it" },
                                            candidate.duration?.let { formatMs(it) },
                                        ).joinToString(" · ").ifEmpty { "—" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = lddc.hairline)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("取消") }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
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
