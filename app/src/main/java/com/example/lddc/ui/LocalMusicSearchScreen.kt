package com.example.lddc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lddc.model.Music
import com.example.lddc.model.SearchFilters
import com.example.lddc.ui.components.FilterDialog
import com.example.lddc.ui.components.LoadMoreIndicator
import com.example.lddc.ui.theme.UiConstants
import com.example.lddc.utils.FormatUtils
import com.example.lddc.utils.PlatformUtils
import com.example.lddc.viewmodel.LocalMatchViewModel

/**
 * 本地音乐歌词搜索页面
 *
 * 为本地音乐搜索在线歌词，使用与网络搜索相同的UI和筛选逻辑
 * - 自动根据歌曲信息搜索
 * - 支持按歌曲名、作者、专辑筛选
 * - 支持按平台筛选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicSearchScreen(
    viewModel: LocalMatchViewModel = viewModel(),
    onBack: () -> Unit,
    onMusicSelected: () -> Unit
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedMusic by viewModel.selectedLocalMusic.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchFilters by viewModel.searchFilters.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    // 使用 remember 和 derivedStateOf 实现响应式筛选
    val filteredResults: List<Music> by remember(searchResults, searchFilters) {
        derivedStateOf<List<Music>> {
            viewModel.getFilteredResults()
        }
    }

    // 搜索状态跟踪，防止重复搜索（使用 rememberSaveable 保持状态）
    var hasSearched by rememberSaveable { mutableStateOf(false) }

    // 横竖屏检测
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 进入页面时自动搜索（只在首次进入时搜索）
    LaunchedEffect(selectedMusic) {
        selectedMusic?.let { music ->
            // 如果已经搜索过，不再重复搜索
            if (hasSearched) return@let

            // 构建搜索关键词：只使用歌曲名，不带演唱者
            val keyword = when {
                music.title.isNotBlank() -> music.title
                else -> music.fileNameWithoutExtension()
            }

            // 设置初始筛选条件（根据本地音乐信息）
            viewModel.updateSearchFilters(
                SearchFilters(
                    keyword = keyword,
                    songName = music.title,
                    artist = if (music.artist != "未知艺术家") music.artist else "",
                    album = music.album
                )
            )

            hasSearched = true
            viewModel.searchSongsWithFilter(keyword, music)
        }
    }

    // 获取搜索关键词用于标题
    val searchKeyword = searchFilters.keyword.takeIf { it.isNotBlank() }
        ?: selectedMusic?.title
        ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = searchKeyword,
                            fontSize = UiConstants.FontSize.XLarge,
                            fontWeight = FontWeight.Bold
                        )
                        selectedMusic?.let { music ->
                            // 副标题只显示艺术家，不重复显示歌曲名
                            if (music.artist.isNotBlank() && music.artist != "未知艺术家") {
                                Text(
                                    text = music.artist,
                                    fontSize = UiConstants.FontSize.Small,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
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
            if (isSearching) {
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
                                text = if (searchResults.isEmpty()) "暂无搜索结果" else "筛选后无结果",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                    val hasMoreData by viewModel.hasMoreData.collectAsState()

                    // 搜索结果列表 - 横竖屏适配
                    if (isLandscape) {
                        // 横屏：双列网格布局
                        LocalSearchLandscapeResults(
                            searchResults = searchResults,
                            filteredResults = filteredResults,
                            isLoadingMore = isLoadingMore,
                            hasMoreData = hasMoreData,
                            onMusicClick = { music ->
                                viewModel.selectSearchResult(music)
                                onMusicSelected()
                            },
                            onLoadMore = { viewModel.loadMore() }
                        )
                    } else {
                        // 竖屏：单列列表布局
                        LocalSearchPortraitResults(
                            searchResults = searchResults,
                            filteredResults = filteredResults,
                            isLoadingMore = isLoadingMore,
                            hasMoreData = hasMoreData,
                            onMusicClick = { music ->
                                viewModel.selectSearchResult(music)
                                onMusicSelected()
                            },
                            onLoadMore = { viewModel.loadMore() }
                        )
                    }
                }
            }
        }

        if (showFilterDialog) {
            FilterDialog(
                searchFilters = searchFilters,
                onFiltersChanged = { newFilters ->
                    viewModel.updateSearchFilters(newFilters)
                },
                onDismiss = { showFilterDialog = false }
            )
        }
    }
}

/**
 * 竖屏搜索结果列表
 */
@Composable
private fun LocalSearchPortraitResults(
    searchResults: List<Music>,
    filteredResults: List<Music>,
    isLoadingMore: Boolean,
    hasMoreData: Boolean,
    onMusicClick: (Music) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    // 用于检测滑动方向
    var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
    var lastFirstVisibleScrollOffset by remember { mutableIntStateOf(0) }

    // 监听滚动到底部
    LaunchedEffect(listState, hasMoreData) {
        snapshotFlow {
            Triple(
                listState.layoutInfo.visibleItemsInfo,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
            .collect { (visibleItems, firstIndex, scrollOffset) ->
                if (!hasMoreData || isLoadingMore) return@collect

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
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiConstants.Padding.Large),
        verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Small)
    ) {
        item {
            val totalCount = searchResults.size
            val filteredCount = filteredResults.size
            val subtitle = if (filteredCount < totalCount) {
                "找到 $filteredCount 首歌曲（筛选自 $totalCount 首）"
            } else {
                "找到 $totalCount 首歌曲"
            }
            Text(
                text = subtitle,
                fontSize = UiConstants.FontSize.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = UiConstants.Spacing.Small)
            )
        }

        items(filteredResults) { music ->
            LocalSearchMusicCard(
                music = music,
                onClick = { onMusicClick(music) },
                isLandscape = false
            )
        }

        // 加载更多指示器
        if (isLoadingMore || hasMoreData) {
            item {
                LoadMoreIndicator(
                    isLoading = isLoadingMore,
                    hasMoreData = hasMoreData,
                    onClick = onLoadMore
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(UiConstants.Spacing.Large))
            Text(
                text = "点击歌曲选择该歌词",
                fontSize = UiConstants.FontSize.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 横屏搜索结果列表 - 双列网格
 */
@Composable
private fun LocalSearchLandscapeResults(
    searchResults: List<Music>,
    filteredResults: List<Music>,
    isLoadingMore: Boolean,
    hasMoreData: Boolean,
    onMusicClick: (Music) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    // 用于检测滑动方向
    var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
    var lastFirstVisibleScrollOffset by remember { mutableIntStateOf(0) }

    // 监听滚动到底部
    LaunchedEffect(listState, hasMoreData) {
        snapshotFlow {
            Triple(
                listState.layoutInfo.visibleItemsInfo,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
            .collect { (visibleItems, firstIndex, scrollOffset) ->
                if (!hasMoreData || isLoadingMore) return@collect

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
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = UiConstants.Padding.XLarge),
        verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium)
    ) {
        item {
            val totalCount = searchResults.size
            val filteredCount = filteredResults.size
            val subtitle = if (filteredCount < totalCount) {
                "找到 $filteredCount 首歌曲（筛选自 $totalCount 首）"
            } else {
                "找到 $totalCount 首歌曲"
            }
            Text(
                text = subtitle,
                fontSize = UiConstants.FontSize.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = UiConstants.Spacing.Medium)
            )
        }

        // 双列布局
        val rows = filteredResults.chunked(2)
        items(rows) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium)
            ) {
                rowItems.forEach { music ->
                    LocalSearchMusicCard(
                        music = music,
                        onClick = { onMusicClick(music) },
                        modifier = Modifier.weight(1f),
                        isLandscape = true
                    )
                }
                // 如果只有一项，填充空白
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 加载更多指示器
        if (isLoadingMore || hasMoreData) {
            item {
                LoadMoreIndicator(
                    isLoading = isLoadingMore,
                    hasMoreData = hasMoreData,
                    onClick = onLoadMore
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(UiConstants.Spacing.XXLarge))
            Text(
                text = "点击歌曲选择该歌词",
                fontSize = UiConstants.FontSize.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * 本地搜索音乐卡片
 * 使用与网络搜索相同的样式
 */
@Composable
private fun LocalSearchMusicCard(
    music: Music,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = UiConstants.Elevation.Low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(if (isLandscape) UiConstants.Padding.Medium else UiConstants.Padding.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(UiConstants.CornerRadius.Small))
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

            Spacer(modifier = Modifier.width(if (isLandscape) UiConstants.Spacing.Medium else UiConstants.Spacing.Small))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.title,
                    fontSize = if (isLandscape) UiConstants.FontSize.Medium else UiConstants.FontSize.Normal,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(UiConstants.Spacing.XS))

                Text(
                    text = music.artist,
                    fontSize = if (isLandscape) UiConstants.FontSize.Normal else UiConstants.FontSize.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(UiConstants.Spacing.XS))

                // 专辑名
                Text(
                    text = music.album,
                    fontSize = UiConstants.FontSize.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(UiConstants.Spacing.XS))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 平台标签（简短形式）
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(UiConstants.Spacing.XS)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = PlatformUtils.getShortName(music.platform),
                            fontSize = UiConstants.FontSize.Small,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(UiConstants.Spacing.Small))

                    Text(
                        text = FormatUtils.formatDuration(music.duration),
                        fontSize = UiConstants.FontSize.Small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
