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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LocalMusicMatchResult
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.service.PermissionManager
import com.example.lddc.service.PlatformService
import com.example.lddc.ui.components.InfoChip
import com.example.lddc.viewmodel.LocalMatchViewModel

/**
 * 本地音乐详情页面
 *
 * 显示本地音乐的详细信息，支持：
 * - 查看音乐元数据
 * - 搜索在线歌词
 * - 查看匹配结果
 * - 写入歌词到文件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicDetailScreen(
    viewModel: LocalMatchViewModel = viewModel(),
    onBack: () -> Unit,
    onSearchLyrics: () -> Unit
) {
    val context = LocalContext.current
    val platformService = remember { PlatformService(context) }

    val selectedMusic by viewModel.selectedLocalMusic.collectAsState()
    val matchResult by viewModel.selectedMatchResult.collectAsState()
    val localLyrics by viewModel.localLyrics.collectAsState()

    // 如果没有选中音乐，返回
    if (selectedMusic == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("未选择音乐")
        }
        return
    }

    val music = selectedMusic!!
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 歌词内容：优先使用匹配结果，其次使用本地歌词，最后显示提示
    val lyricsContent = matchResult?.lyrics
        ?: localLyrics
        ?: "暂无歌词\n\n点击右上角搜索按钮查找歌词"

    // 可编辑的歌词状态
    var editableLyrics by remember { mutableStateOf(lyricsContent) }
    var showManageStorageDialog by remember { mutableStateOf(false) }

    // 当歌词内容变化时更新编辑框
    LaunchedEffect(lyricsContent) {
        editableLyrics = lyricsContent
    }

    // 格式化时长（duration 是毫秒字符串，需要转换为 Long）
    val durationMs = try {
        music.duration
    } catch (_: NumberFormatException) {
        0L
    }
    val formattedDuration = formatDuration(durationMs)

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
                actions = {
                    // 搜索歌词按钮
                    IconButton(onClick = onSearchLyrics) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索歌词"
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
                LandscapeLocalMusicDetail(
                    music = music,
                    matchResult = matchResult,
                    lyrics = editableLyrics,
                    onLyricsChange = { editableLyrics = it },
                    formattedDuration = formattedDuration,
                    platformService = platformService,
                    onWriteLyrics = { mode ->
                        val selectedMusic = viewModel.selectedLocalMusic.value
                        if (selectedMusic != null) {
                            // 检查是否需要管理外部存储权限
                            if (PermissionManager.needsManageExternalStoragePermission() &&
                                !PermissionManager.hasManageExternalStoragePermission()
                            ) {
                                // 显示权限引导
                                showManageStorageDialog = true
                            } else {
                                viewModel.writeLyrics(
                                    selectedMusic,
                                    editableLyrics,
                                    mode
                                ) { success, error ->
                                    if (success) {
                                        platformService.showToast("歌词写入成功")
                                    } else {
                                        platformService.showToast("写入失败: ${error ?: "未知错误"}")
                                    }
                                }
                            }
                        }
                    }
                )
            } else {
                // 竖屏布局
                PortraitLocalMusicDetail(
                    music = music,
                    matchResult = matchResult,
                    lyrics = editableLyrics,
                    onLyricsChange = { editableLyrics = it },
                    formattedDuration = formattedDuration,
                    platformService = platformService,
                    onWriteLyrics = { mode ->
                        val selectedMusic = viewModel.selectedLocalMusic.value
                        if (selectedMusic != null) {
                            // 检查是否需要管理外部存储权限
                            if (PermissionManager.needsManageExternalStoragePermission() &&
                                !PermissionManager.hasManageExternalStoragePermission()
                            ) {
                                // 显示权限引导
                                showManageStorageDialog = true
                            } else {
                                viewModel.writeLyrics(
                                    selectedMusic,
                                    editableLyrics,
                                    mode
                                ) { success, error ->
                                    if (success) {
                                        platformService.showToast("歌词写入成功")
                                    } else {
                                        platformService.showToast("写入失败: ${error ?: "未知错误"}")
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // 管理外部存储权限对话框
            if (showManageStorageDialog) {
                ManageStorageDialog(
                    onDismiss = { showManageStorageDialog = false },
                    onConfirm = {
                        showManageStorageDialog = false
                        // 打开系统设置页面
                        val intent = PermissionManager.getManageExternalStorageIntent()
                        if (intent != null) {
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}

/**
 * 管理外部存储权限对话框
 */
@Composable
private fun ManageStorageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要文件管理权限") },
        text = {
            Text(
                "Android 11+ 需要授予\"所有文件访问权限\"才能修改音频文件中的歌词。\n\n" +
                        "请在设置中找到本应用，开启\"允许访问所有文件\"权限。"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("去设置")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 竖屏本地音乐详情 - 参考网络歌曲详情设计
 */
@Composable
private fun PortraitLocalMusicDetail(
    music: LocalMusicInfo,
    matchResult: LocalMusicMatchResult?,
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    formattedDuration: String,
    platformService: PlatformService,
    onWriteLyrics: (LyricsWriteMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 歌曲信息卡片
        LocalMusicInfoCard(
            music = music,
            matchResult = matchResult,
            formattedDuration = formattedDuration,
            platformService = platformService,
            modifier = Modifier.fillMaxWidth()
        )

        // 歌词卡片
        LocalLyricsCard(
            lyrics = lyrics,
            onLyricsChange = onLyricsChange,
            platformService = platformService,
            onWriteLyrics = onWriteLyrics,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
    }
}

/**
 * 横屏本地音乐详情 - 参考网络歌曲详情设计
 */
@Composable
private fun LandscapeLocalMusicDetail(
    music: LocalMusicInfo,
    matchResult: LocalMusicMatchResult?,
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    formattedDuration: String,
    platformService: PlatformService,
    onWriteLyrics: (LyricsWriteMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 左侧：歌曲信息
        LocalMusicInfoCard(
            music = music,
            matchResult = matchResult,
            formattedDuration = formattedDuration,
            platformService = platformService,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        )

        // 右侧：歌词
        LocalLyricsCard(
            lyrics = lyrics,
            onLyricsChange = onLyricsChange,
            platformService = platformService,
            onWriteLyrics = onWriteLyrics,
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        )
    }
}

/**
 * 本地音乐信息卡片 - 参考网络歌曲详情设计
 */
@Composable
private fun LocalMusicInfoCard(
    music: LocalMusicInfo,
    matchResult: LocalMusicMatchResult?,
    formattedDuration: String,
    platformService: PlatformService,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 专辑封面（支持长按复制文件名）
            AlbumCoverWithLongPress(
                imageUrl = music.albumArtUri?.toString(),
                onLongPress = {
                    platformService.copyToClipboard("文件名", music.fileName)
                    platformService.showToast("文件名已复制")
                },
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 歌曲标题（支持长按复制）
            CopyableText(
                text = music.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                onLongPress = {
                    platformService.copyToClipboard("歌曲名", music.title)
                    platformService.showToast("歌曲名已复制")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 歌手（支持长按复制）
            CopyableText(
                text = music.artist,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onLongPress = {
                    platformService.copyToClipboard("艺术家", music.artist)
                    platformService.showToast("艺术家已复制")
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 专辑名（支持长按复制）
            CopyableText(
                text = music.album,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                onLongPress = {
                    platformService.copyToClipboard("专辑", music.album)
                    platformService.showToast("专辑已复制")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 信息标签
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(text = formattedDuration)
                // 文件格式标签
                val fileExtension = music.fileName.substringAfterLast(".", "").uppercase()
                if (fileExtension.isNotEmpty()) {
                    InfoChip(text = fileExtension)
                }
                InfoChip(text = if (matchResult?.lyrics != null) "已匹配" else "未匹配")
                if (music.hasLyrics) {
                    InfoChip(
                        text = "已有歌词",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // 额外信息标签（比特率、采样率、年份等）
            if (music.bitrate != null || music.sampleRate != null || music.year != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    music.bitrate?.let { bitrate ->
                        InfoChip(text = "${bitrate / 1000}kbps")
                    }
                    music.sampleRate?.let { sampleRate ->
                        InfoChip(text = "${sampleRate / 1000}kHz")
                    }
                    music.year?.let { year ->
                        InfoChip(text = "$year")
                    }
                }
            }

            // 流派和作曲家信息
            if (!music.genre.isNullOrEmpty() || !music.composer.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    music.genre?.let { genre ->
                        InfoChip(
                            text = genre,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    music.composer?.let { composer ->
                        InfoChip(
                            text = composer,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 本地歌词卡片 - 参考网络歌曲详情设计
 */
@Composable
private fun LocalLyricsCard(
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    platformService: PlatformService,
    onWriteLyrics: (LyricsWriteMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 写入歌词按钮（如果有歌词内容）
                    if (lyrics.isNotBlank() && !lyrics.startsWith("暂无歌词")) {
                        WriteLyricsButton(
                            onWriteLyrics = onWriteLyrics
                        )
                    }

                    // 复制歌词按钮
                    Button(
                        onClick = {
                            platformService.copyToClipboard("歌词", lyrics)
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 歌词内容区域 - 使用卡片包裹
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = lyrics,
                    onValueChange = onLyricsChange,
                    readOnly = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/**
 * 支持长按保存的专辑封面
 */
@Composable
private fun AlbumCoverWithLongPress(
    imageUrl: String?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() }
                )
            }
    ) {
        if (!imageUrl.isNullOrEmpty() && imageUrl != "default") {
            AsyncImage(
                model = imageUrl,
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
}

/**
 * 支持长按复制的文本
 */
@Composable
private fun CopyableText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onLongPress: (() -> Unit)? = null
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() }
            )
        }
    )
}

/**
 * 写入歌词按钮
 */
@Composable
private fun WriteLyricsButton(
    onWriteLyrics: (LyricsWriteMode) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("写入歌词")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("写入到音频文件 (ID3标签)") },
                onClick = {
                    onWriteLyrics(LyricsWriteMode.EMBEDDED)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("写入到歌词文件 (.lrc)") },
                onClick = {
                    onWriteLyrics(LyricsWriteMode.SEPARATE_FILE)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("同时写入两者") },
                onClick = {
                    onWriteLyrics(LyricsWriteMode.BOTH)
                    showMenu = false
                }
            )
        }
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 1000 / 60
    val seconds = (durationMs / 1000) % 60
    return "%d:%02d".format(minutes, seconds)
}
