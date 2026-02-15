package com.example.lddc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lddc.model.Lyrics
import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsConvertOptions
import com.example.lddc.service.LyricsOutputFormat
import com.example.lddc.service.LyricsService
import com.example.lddc.service.MusicFilterService
import com.example.lddc.service.PlatformService
import com.example.lddc.viewmodel.MusicSearchUseCase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    musicSearchUseCase: MusicSearchUseCase,
    onBack: () -> Unit
) {
    val selectedMusic by musicSearchUseCase.selectedMusic.collectAsState()

    selectedMusic?.let { music ->
        val context = LocalContext.current
        val platformService = remember { PlatformService(context) }
        val filterService = remember { MusicFilterService() }
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

        val formattedDuration = filterService.formatDuration(music.duration)
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
        Card(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 专辑封面 - 接近卡片大小，长按保存图片
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (music.imageUrl.isNotEmpty() && music.imageUrl != "default") {
                                        scope.launch {
                                            val success = platformService.saveImageFromUrl(
                                                music.imageUrl,
                                                "${music.title}_${music.artist}"
                                            )
                                            if (success) {
                                                platformService.showToast("图片已保存到相册")
                                            } else {
                                                platformService.showToast("保存图片失败")
                                            }
                                        }
                                    }
                                }
                            )
                        }
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

                Spacer(modifier = Modifier.height(24.dp))

                // 歌曲标题 - 长按复制
                Text(
                    text = music.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("歌曲名", music.title)
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 歌手 - 长按复制
                Text(
                    text = music.artist,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("歌手", music.artist)
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 专辑名 - 长按复制
                Text(
                    text = music.album,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("专辑", music.album)
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

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

        // 右侧：歌词卡片
        Card(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "歌词",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Button(
                        onClick = {
                            platformService.copyToClipboard("歌词", displayLyrics)
                            platformService.showToast("歌词已复制")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("复制歌词")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 歌词内容
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    BasicTextField(
                        value = displayLyrics,
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 26.sp,
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

@Composable
fun PortraitDetailLayout(
    music: com.example.lddc.model.Music,
    displayLyrics: String,
    formattedDuration: String,
    platformService: PlatformService
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 歌曲信息卡片
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
                // 专辑封面 - 接近卡片宽度，长按保存图片
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (music.imageUrl.isNotEmpty() && music.imageUrl != "default") {
                                        scope.launch {
                                            val success = platformService.saveImageFromUrl(
                                                music.imageUrl,
                                                "${music.title}_${music.artist}"
                                            )
                                            if (success) {
                                                platformService.showToast("图片已保存到相册")
                                            } else {
                                                platformService.showToast("保存图片失败")
                                            }
                                        }
                                    }
                                }
                            )
                        }
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

                // 歌曲标题 - 长按复制
                Text(
                    text = music.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("歌曲名", music.title)
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 歌手 - 长按复制
                Text(
                    text = music.artist,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("歌手", music.artist)
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 专辑名 - 长按复制
                Text(
                    text = music.album,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                platformService.copyToClipboard("专辑", music.album)
                            }
                        )
                    }
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

        Spacer(modifier = Modifier.height(16.dp))

        // 歌词卡片
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

                    Button(
                        onClick = {
                            platformService.copyToClipboard("歌词", displayLyrics)
                            platformService.showToast("歌词已复制")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("复制")
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
                    BasicTextField(
                        value = displayLyrics,
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun InfoChip(text: String, isAlbum: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                color = if (isAlbum) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isAlbum) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}
