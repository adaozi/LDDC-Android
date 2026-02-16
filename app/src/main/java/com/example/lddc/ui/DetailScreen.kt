package com.example.lddc.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lddc.model.Lyrics
import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsConvertOptions
import com.example.lddc.service.LyricsOutputFormat
import com.example.lddc.service.LyricsService
import com.example.lddc.service.PlatformService
import com.example.lddc.ui.components.InfoChip
import com.example.lddc.ui.components.LyricsCard
import com.example.lddc.ui.components.SongInfoCard
import com.example.lddc.utils.FormatUtils
import com.example.lddc.utils.LyricsUtils
import com.example.lddc.utils.PlatformUtils
import com.example.lddc.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val selectedMusic by viewModel.selectedMusic.collectAsState()

    selectedMusic?.let { music ->
        val context = LocalContext.current
        val platformService = remember { PlatformService(context) }
        val lyricsService = remember { LyricsService(context) }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val displayLyrics = remember(music.lyrics, music.lyricsType) {
            if (music.lyrics.isNotEmpty()) {
                try {
                    val lyrics = Lyrics(
                        title = music.title,
                        artist = music.artist,
                        album = music.album,
                        content = music.lyrics,
                        orig = music.lyrics,
                        source = when (music.lyricsType) {
                            "QRC" -> Source.QM
                            "KRC" -> Source.KG
                            "LRC" -> Source.NE
                            else -> Source.QM
                        }
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
                    android.util.Log.e("DetailScreen", "Failed to convert lyrics for display: ${e.message}", e)
                    music.lyrics
                }
            } else {
                "暂无歌词"
            }
        }

        val formattedDuration = FormatUtils.formatDuration(music.duration)
        val showSaveDialog = remember { mutableStateOf(false) }

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
                if (isLandscape) {
                    // 横屏布局
                    LandscapeDetailLayout(
                        music = music,
                        displayLyrics = displayLyrics,
                        formattedDuration = formattedDuration,
                        platformService = platformService
                    )
                } else {
                    // 竖屏布局
                    PortraitDetailLayout(
                        music = music,
                        displayLyrics = displayLyrics,
                        formattedDuration = formattedDuration,
                        platformService = platformService
                    )
                }
            }

            // 保存图片对话框
            if (showSaveDialog.value) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSaveDialog.value = false },
                    title = { Text("保存图片") },
                    text = { Text("是否要保存这张图片到相册？") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                val success = platformService.saveImageToGallery("${music.title}_${music.artist}")
                                showSaveDialog.value = false
                                if (success) {
                                    platformService.showToast("图片已保存到相册")
                                } else {
                                    platformService.showToast("保存图片失败")
                                }
                            }
                        ) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showSaveDialog.value = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    } ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("未选择歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LandscapeDetailLayout(
    music: com.example.lddc.model.Music,
    displayLyrics: String,
    formattedDuration: String,
    platformService: PlatformService
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 左侧：歌曲信息卡片
        SongInfoCard(
            title = music.title,
            artist = music.artist,
            album = music.album,
            imageUrl = music.imageUrl,
            platformService = platformService,
            chips = {
                InfoChip(text = formattedDuration)
                InfoChip(text = PlatformUtils.getDisplayName(music.platform))
                InfoChip(text = LyricsUtils.getTypeDisplay(music.lyricsType))
            },
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        )

        // 右侧：歌词卡片
        LyricsCard(
            lyrics = displayLyrics,
            platformService = platformService,
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        )
    }
}

@Composable
fun PortraitDetailLayout(
    music: com.example.lddc.model.Music,
    displayLyrics: String,
    formattedDuration: String,
    platformService: PlatformService
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 歌曲信息卡片
        SongInfoCard(
            title = music.title,
            artist = music.artist,
            album = music.album,
            imageUrl = music.imageUrl,
            platformService = platformService,
            chips = {
                InfoChip(text = formattedDuration)
                InfoChip(text = PlatformUtils.getDisplayName(music.platform))
                InfoChip(text = LyricsUtils.getTypeDisplay(music.lyricsType))
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 歌词卡片
        LyricsCard(
            lyrics = displayLyrics,
            platformService = platformService,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
