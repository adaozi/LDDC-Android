package com.example.lddc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lddc.model.Lyrics
import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.Music
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsConvertOptions
import com.example.lddc.service.LyricsOutputFormat
import com.example.lddc.service.LyricsService
import com.example.lddc.service.MusicFilterService
import com.example.lddc.service.PlatformService
import com.example.lddc.ui.components.InfoChip
import com.example.lddc.viewmodel.LocalMatchViewModel

/**
 * 本地音乐搜索结果详情页
 *
 * 显示搜索结果的歌曲详情和歌词
 * 完全参考网络搜索详情页的设计风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicSearchDetailScreen(
    viewModel: LocalMatchViewModel = viewModel(),
    onBack: () -> Unit,
    onUseLyrics: (String, LyricsWriteMode) -> Unit
) {
    val selectedSearchResult by viewModel.selectedSearchResult.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val context = LocalContext.current
    val platformService = remember { PlatformService(context) }
    val filterService = remember { MusicFilterService() }
    val lyricsService = remember { LyricsService(context) }
    rememberCoroutineScope()

    // 加载歌词
    LaunchedEffect(selectedSearchResult) {
        selectedSearchResult?.let { music ->
            if (music.lyrics.isBlank()) {
                viewModel.loadLyricsForSearchResult(music)
            }
        }
    }

    // 转换歌词用于显示
    val displayLyrics = selectedSearchResult?.let { music ->
        remember(music.lyrics, music.lyricsType) {
            if (music.lyrics.isNotEmpty()) {
                try {
                    // 根据歌词内容自动检测来源平台
                    val detectedSource = when {
                        music.lyrics.contains("<Lyric_1") -> Source.QM  // QRC格式
                        music.lyrics.contains("[00:") && music.lyrics.contains("<") && 
                            music.lyrics.contains(">") -> Source.KG  // KRC格式
                        music.lyrics.contains("[") && music.lyrics.contains("](") && 
                            music.lyrics.contains(")") -> Source.NE  // YRC格式
                        else -> Source.NE  // 默认LRC格式
                    }

                    android.util.Log.d("LocalMusicSearchDetail", "Lyrics type: ${music.lyricsType}, detected source: $detectedSource")
                    android.util.Log.d("LocalMusicSearchDetail", "Lyrics first 100 chars: ${music.lyrics.take(100)}")

                    val lyrics = Lyrics(
                        title = music.title,
                        artist = music.artist,
                        album = music.album,
                        content = music.lyrics,
                        orig = music.lyrics,
                        source = detectedSource
                    )
                    val options = LyricsConvertOptions(
                        lyricsFormat = LyricsFormat.VERBATIMLRC
                    )
                    lyricsService.convertLyrics(
                        lyrics = lyrics,
                        format = LyricsOutputFormat.LRC,
                        options = options
                    )
                } catch (e: Exception) {
                    android.util.Log.e("LocalMusicSearchDetail", "Failed to convert lyrics: ${e.message}", e)
                    music.lyrics
                }
            } else {
                "暂无歌词"
            }
        }
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "歌曲详情",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            selectedSearchResult?.let { music ->
                val formattedDuration = filterService.formatDuration(music.duration)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 歌曲信息卡片
                    MusicInfoCard(
                        music = music,
                        formattedDuration = formattedDuration
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 歌词卡片（包含使用此歌词按钮）
                    LyricsCard(
                        lyrics = displayLyrics,
                        originalLyrics = music.lyrics,
                        lyricsType = music.lyricsType,
                        isLoading = isLoadingLyrics,
                        platformService = platformService,
                        onUseLyrics = { mode -> onUseLyrics(displayLyrics, mode) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            } ?: run {
                // 没有选中歌曲
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到歌曲信息",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 歌曲信息卡片
 * 完全参考网络搜索详情页的样式
 */
@Composable
private fun MusicInfoCard(
    music: Music,
    formattedDuration: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 专辑封面 - 接近卡片宽度
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                if (music.imageUrl.isNotEmpty() && music.imageUrl != "default") {
                    AsyncImage(
                        model = music.imageUrl,
                        contentDescription = "歌曲封面",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_media_play),
                        contentDescription = "歌曲封面",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(48.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 歌曲标题
            Text(
                text = music.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 歌手
            Text(
                text = music.artist,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 专辑名
            Text(
                text = music.album,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 信息标签行：时长、平台、歌词类型
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(text = formattedDuration)
                InfoChip(text = getPlatformDisplayName(music.platform))
                InfoChip(text = getLyricsTypeDisplay(music.lyricsType))
            }
        }
    }
}

/**
 * 歌词卡片
 * 完全参考网络搜索详情页的样式，包含使用此歌词按钮
 */
@Composable
private fun LyricsCard(
    lyrics: String,
    originalLyrics: String,
    lyricsType: String,
    isLoading: Boolean,
    platformService: PlatformService,
    onUseLyrics: (LyricsWriteMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 调试日志
                android.util.Log.d("LyricsCard", "isLoading: $isLoading, originalLyrics.isNotBlank: ${originalLyrics.isNotBlank()}, originalLyrics.length: ${originalLyrics.length}")

                if (!isLoading && originalLyrics.isNotBlank()) {
                    // 使用下拉菜单让用户选择保存方式
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = {
                                android.util.Log.d("LyricsCard", "点击使用此歌词按钮")
                                showMenu = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("使用此歌词")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("写入到音频文件 (ID3标签)") },
                                onClick = {
                                    android.util.Log.d("LyricsCard", "选择：写入到音频文件")
                                    onUseLyrics(LyricsWriteMode.EMBEDDED)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("写入到歌词文件 (.lrc)") },
                                onClick = {
                                    android.util.Log.d("LyricsCard", "选择：写入到歌词文件")
                                    onUseLyrics(LyricsWriteMode.SEPARATE_FILE)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("同时写入两者") },
                                onClick = {
                                    android.util.Log.d("LyricsCard", "选择：同时写入两者")
                                    onUseLyrics(LyricsWriteMode.BOTH)
                                    showMenu = false
                                }
                            )
                        }
                    }
                } else if (!isLoading) {
                    // 歌词为空时显示提示
                    Text(
                        text = "暂无歌词",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 歌词内容
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        originalLyrics.isBlank() -> {
                            Text(
                                text = "暂无歌词",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            BasicTextField(
                                value = lyrics,
                                onValueChange = { },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                readOnly = true,
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


