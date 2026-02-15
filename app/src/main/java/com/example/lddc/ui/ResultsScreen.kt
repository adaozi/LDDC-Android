package com.example.lddc.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 搜索结果界面
 *
 * 显示歌曲搜索结果列表，支持筛选和分页加载
 *
 * 功能：
 * - 显示搜索结果网格/列表
 * - 顶部搜索栏（可修改关键词重新搜索）
 * - 筛选按钮（打开筛选对话框）
 * - 下拉刷新和上拉加载更多
 * - 横竖屏自适应布局（横屏网格，竖屏列表）
 *
 * @param musicSearchUseCase 音乐搜索业务逻辑接口
 * @param onBack 返回回调
 * @param onMusicSelected 歌曲选中回调
 */
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lddc.model.Music
import com.example.lddc.service.MusicFilterService
import com.example.lddc.viewmodel.MusicSearchUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    musicSearchUseCase: MusicSearchUseCase,
    onBack: () -> Unit,
    onMusicSelected: (Music) -> Unit
) {
    val searchResults by musicSearchUseCase.searchResults.collectAsState()
    val searchFilters by musicSearchUseCase.searchFilters.collectAsState()
    val isLoading by musicSearchUseCase.isLoading.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    val filterService = remember { MusicFilterService() }
    val filteredResults = filterService.filterMusic(searchResults, searchFilters)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = searchFilters.keyword.ifEmpty { "搜索结果" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val totalCount = searchResults.size
                        val filteredCount = filteredResults.size
                        val subtitle = if (filteredCount < totalCount) {
                            "共 $filteredCount 首歌曲（筛选自 $totalCount 首）"
                        } else {
                            "共 $totalCount 首歌曲"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                if (filteredResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchFilters.keyword.isEmpty()) "暂无搜索结果" else "筛选后无结果",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val isLoadingMore by musicSearchUseCase.isLoadingMore.collectAsState()
                    val hasMoreData by musicSearchUseCase.hasMoreData.collectAsState()

                    // 筛选后如果没有结果，不显示加载更多
                    // 只要有筛选结果且还有更多数据，就允许加载更多
                    val effectiveHasMoreData = hasMoreData && filteredResults.isNotEmpty()

                    if (isLandscape) {
                        // 横屏：网格布局
                        val gridState = rememberLazyGridState()
                        // 用于检测滑动方向
                        var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
                        var lastFirstVisibleScrollOffset by remember { mutableIntStateOf(0) }

                        // 监听滚动到底部
                        LaunchedEffect(gridState, effectiveHasMoreData) {
                            snapshotFlow {
                                Triple(
                                    gridState.layoutInfo.visibleItemsInfo,
                                    gridState.firstVisibleItemIndex,
                                    gridState.firstVisibleItemScrollOffset
                                )
                            }
                                .collect { (visibleItems, firstIndex, scrollOffset) ->
                                    if (!effectiveHasMoreData || isLoadingMore) return@collect

                                    val totalItems = gridState.layoutInfo.totalItemsCount
                                    val lastVisibleItem = visibleItems.lastOrNull()?.index ?: 0

                                    // 判断是否滑到底部
                                    val isNearBottom = lastVisibleItem >= totalItems - 2

                                    // 判断是否在滑动（第一个可见项或偏移量发生变化）
                                    val isScrolling = firstIndex != lastFirstVisibleItem ||
                                        scrollOffset != lastFirstVisibleScrollOffset

                                    // 更新上次状态
                                    lastFirstVisibleItem = firstIndex
                                    lastFirstVisibleScrollOffset = scrollOffset

                                    // 同时满足：滑到底部 + 正在滑动
                                    if (isNearBottom && isScrolling) {
                                        musicSearchUseCase.loadMore()
                                    }
                                }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredResults) { music ->
                                MusicCard(
                                    music = music,
                                    onClick = { onMusicSelected(music) }
                                )
                            }

                            // 加载更多指示器
                            if (isLoadingMore || effectiveHasMoreData) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    LoadMoreIndicator(
                                        isLoading = isLoadingMore,
                                        hasMoreData = effectiveHasMoreData,
                                        onClick = { musicSearchUseCase.loadMore() }
                                    )
                                }
                            }
                        }
                    } else {
                        // 竖屏：列表布局
                        val listState = rememberLazyListState()
                        // 用于检测滑动方向
                        var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
                        var lastFirstVisibleScrollOffset by remember { mutableIntStateOf(0) }

                        // 监听滚动到底部
                        LaunchedEffect(listState, effectiveHasMoreData) {
                            snapshotFlow {
                                Triple(
                                    listState.layoutInfo.visibleItemsInfo,
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset
                                )
                            }
                                .collect { (visibleItems, firstIndex, scrollOffset) ->
                                    if (!effectiveHasMoreData || isLoadingMore) return@collect

                                    val totalItems = listState.layoutInfo.totalItemsCount
                                    val lastVisibleItem = visibleItems.lastOrNull()?.index ?: 0

                                    // 判断是否滑到底部
                                    val isNearBottom = lastVisibleItem >= totalItems - 2

                                    // 判断是否在滑动（第一个可见项或偏移量发生变化）
                                    val isScrolling = firstIndex != lastFirstVisibleItem ||
                                        scrollOffset != lastFirstVisibleScrollOffset

                                    // 更新上次状态
                                    lastFirstVisibleItem = firstIndex
                                    lastFirstVisibleScrollOffset = scrollOffset

                                    // 同时满足：滑到底部 + 正在滑动
                                    if (isNearBottom && isScrolling) {
                                        musicSearchUseCase.loadMore()
                                    }
                                }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredResults) { music ->
                                MusicCard(
                                    music = music,
                                    onClick = { onMusicSelected(music) }
                                )
                            }

                            // 加载更多指示器
                            if (isLoadingMore || effectiveHasMoreData) {
                                item {
                                    LoadMoreIndicator(
                                        isLoading = isLoadingMore,
                                        hasMoreData = effectiveHasMoreData,
                                        onClick = { musicSearchUseCase.loadMore() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFilterDialog) {
            FilterDialog(
                searchFilters = searchFilters,
                onFiltersChanged = { newFilters ->
                    musicSearchUseCase.updateSearchFilters(newFilters)
                },
                onDismiss = { showFilterDialog = false }
            )
        }
    }
}

@Composable
fun MusicCard(
    music: Music,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面 - 高度与文字区域匹配
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (music.imageUrl.isNotEmpty() && music.imageUrl != "default") {
                    AsyncImage(
                        model = music.imageUrl,
                        contentDescription = "专辑封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(id = android.R.drawable.ic_media_play),
                        error = painterResource(id = android.R.drawable.ic_media_play)
                    )
                } else {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_media_play),
                        contentDescription = "专辑封面",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = music.artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 专辑名
                Text(
                    text = music.album,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 平台标签
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = getPlatformDisplayName(music.platform),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formatDuration(music.duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun formatDuration(duration: String): String {
    return try {
        val seconds = duration.toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        String.format("%d:%02d", minutes, remainingSeconds)
    } catch (_: NumberFormatException) {
        duration
    }
}

/**
 * 加载更多指示器组件
 */
@Composable
fun LoadMoreIndicator(
    isLoading: Boolean,
    hasMoreData: Boolean,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(enabled = hasMoreData && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            hasMoreData -> {
                Text(
                    text = "点击加载更多",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {
                Text(
                    text = "已加载全部",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    searchFilters: com.example.lddc.model.SearchFilters,
    onFiltersChanged: (com.example.lddc.model.SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFilters by remember { mutableStateOf(searchFilters) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选条件") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 歌曲名筛选
                OutlinedTextField(
                    value = tempFilters.songName,
                    onValueChange = { tempFilters = tempFilters.copy(songName = it) },
                    label = { Text("歌曲名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 作者筛选
                OutlinedTextField(
                    value = tempFilters.artist,
                    onValueChange = { tempFilters = tempFilters.copy(artist = it) },
                    label = { Text("作者") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 专辑筛选
                OutlinedTextField(
                    value = tempFilters.album,
                    onValueChange = { tempFilters = tempFilters.copy(album = it) },
                    label = { Text("专辑") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 平台筛选（下拉菜单）
                var platformExpanded by remember { mutableStateOf(false) }
                val platformOptions = listOf(
                    "全部平台" to emptySet<String>(),
                    "QQ音乐" to setOf("QM"),
                    "网易云音乐" to setOf("NE"),
                    "酷狗音乐" to setOf("KG")
                )
                val selectedPlatformLabel = platformOptions.find { it.second == tempFilters.platforms }?.first ?: "全部平台"

                ExposedDropdownMenuBox(
                    expanded = platformExpanded,
                    onExpandedChange = { platformExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedPlatformLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("平台筛选") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = platformExpanded,
                        onDismissRequest = { platformExpanded = false }
                    ) {
                        platformOptions.forEach { (label, platforms) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    tempFilters = tempFilters.copy(platforms = platforms)
                                    platformExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onFiltersChanged(tempFilters)
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}


