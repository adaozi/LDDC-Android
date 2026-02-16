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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        selectedMusic?.let { music ->
                            // 副标题只显示艺术家，不重复显示歌曲名
                            if (music.artist.isNotBlank() && music.artist != "未知艺术家") {
                                Text(
                                    text = music.artist,
                                    fontSize = 12.sp,
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
                    // 搜索结果列表
                    val listState = rememberLazyListState()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(filteredResults) { music ->
                            LocalSearchMusicCard(
                                music = music,
                                onClick = {
                                    viewModel.selectSearchResult(music)
                                    onMusicSelected()
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "点击歌曲选择该歌词",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
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
 * 本地搜索音乐卡片
 * 使用与网络搜索相同的样式
 */
@Composable
private fun LocalSearchMusicCard(
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
            // 专辑封面
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
                    // 平台标签（简短形式）
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = PlatformUtils.getShortName(music.platform),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = FormatUtils.formatDuration(music.duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
