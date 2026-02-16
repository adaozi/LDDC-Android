package com.example.lddc.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LocalMusicMatchResult
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.service.PermissionManager
import com.example.lddc.ui.components.EmptyState
import com.example.lddc.ui.components.LocalMusicCard
import com.example.lddc.viewmodel.LocalMatchViewModel
import com.example.lddc.viewmodel.MatchState
import com.example.lddc.viewmodel.ScanState
import com.example.lddc.viewmodel.ViewMode
import java.io.File

/**
 * 本地音乐列表页面
 *
 * 显示扫描到的本地音乐列表，支持：
 * - 扫描本地音乐
 * - 自动匹配歌词
 * - 点击音乐进入详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicListScreen(
    viewModel: LocalMatchViewModel = viewModel(),
    onBack: () -> Unit,
    onMusicSelected: (LocalMusicInfo) -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val localMusicList by viewModel.localMusicList.collectAsState()
    val matchState by viewModel.matchState.collectAsState()
    val matchProgress by viewModel.matchProgress.collectAsState()
    val matchResults by viewModel.matchResults.collectAsState()

    // 视图模式
    val viewMode by viewModel.viewMode.collectAsState()
    val folderList by viewModel.folderList.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val displayedMusicList by viewModel.displayedMusicList.collectAsState()

    // 保存方式选择对话框状态
    val showSaveModeDialog by viewModel.showSaveModeDialog.collectAsState()
    val defaultSaveMode by viewModel.defaultSaveMode.collectAsState()

    // 权限状态
    var hasPermission by remember {
        mutableStateOf(PermissionManager.hasAudioPermission(context))
    }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.startScan()
        }
    }

    // 检查权限并自动开始扫描（仅在列表为空时）
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(PermissionManager.getAudioPermission())
        } else if (localMusicList.isEmpty() && scanState is ScanState.Idle) {
            // 只在列表为空且处于空闲状态时才扫描
            viewModel.startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (viewMode) {
                                ViewMode.LIST -> "本地音乐"
                                ViewMode.FOLDER -> if (selectedFolder != null) {
                                    File(selectedFolder).name
                                } else "文件夹"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // 显示数量信息
                        val subtitle = when {
                            scanState is ScanState.Scanning -> "扫描中... ${scanProgress.foundMusic} 首"
                            viewMode == ViewMode.FOLDER && selectedFolder == null -> "${folderList.size} 个文件夹"
                            else -> "共 ${displayedMusicList.size} 首音乐"
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewMode == ViewMode.FOLDER && selectedFolder != null) {
                            viewModel.selectFolder(null)
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 批量匹配按钮（仅在有音乐时显示）
                    if (scanState !is ScanState.Scanning &&
                        hasPermission &&
                        displayedMusicList.isNotEmpty()) {
                        if (matchState is MatchState.Matching) {
                            // 匹配中：显示停止按钮（正方形），使用特殊颜色
                            IconButton(onClick = { viewModel.cancelMatch() }) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "停止匹配",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            // 未开始：显示播放按钮（三角形）
                            IconButton(onClick = { viewModel.showSaveModeDialog(displayedMusicList) }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "开始批量匹配"
                                )
                            }
                        }
                    }

                    // 视图模式切换按钮
                    if (scanState !is ScanState.Scanning && hasPermission && localMusicList.isNotEmpty()) {
                        // 文件夹视图按钮
                        IconButton(onClick = {
                            if (viewMode == ViewMode.LIST) {
                                viewModel.setViewMode(ViewMode.FOLDER)
                            } else {
                                viewModel.setViewMode(ViewMode.LIST)
                                viewModel.selectFolder(null)
                            }
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) 
                                    Icons.Default.Folder else Icons.Default.List,
                                contentDescription = if (viewMode == ViewMode.LIST) 
                                    "文件夹视图" else "列表视图"
                            )
                        }
                    }
                    
                    // 扫描按钮
                    if (scanState !is ScanState.Scanning && hasPermission) {
                        IconButton(onClick = { viewModel.startScan() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重新扫描"
                            )
                        }
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
        ) {
            when {
                // 没有权限时显示权限请求界面
                !hasPermission -> {
                    PermissionDeniedState(
                        onRequestPermission = {
                            permissionLauncher.launch(PermissionManager.getAudioPermission())
                        }
                    )
                }

                scanState is ScanState.Idle -> {
                    // 初始状态，显示扫描按钮
                    IdleState(
                        onStartScan = { viewModel.startScan() }
                    )
                }

                scanState is ScanState.Scanning -> {
                    // 扫描中状态
                    ScanningState(progress = scanProgress)
                }

                scanState is ScanState.Completed ||
                scanState is ScanState.Cancelled ||
                scanState is ScanState.Error -> {
                    // 根据视图模式显示不同内容
                    when (viewMode) {
                        ViewMode.LIST -> {
                            // 列表视图
                            if (displayedMusicList.isEmpty()) {
                                EmptyState(message = "未找到本地音乐文件")
                            } else {
                                MusicListContent(
                                    musicList = displayedMusicList,
                                    matchResults = matchResults,
                                    matchProgress = matchProgress,
                                    onMusicClick = onMusicSelected,
                                    onStartMatch = { viewModel.showSaveModeDialog(displayedMusicList) }
                                )
                            }
                        }
                        ViewMode.FOLDER -> {
                            // 文件夹视图
                            if (selectedFolder == null) {
                                // 显示文件夹列表
                                FolderListContent(
                                    folderList = folderList,
                                    musicByFolder = viewModel.musicByFolder.collectAsState().value,
                                    onFolderClick = { folder ->
                                        viewModel.selectFolder(folder)
                                    }
                                )
                            } else {
                                // 显示选中文件夹的音乐
                                if (displayedMusicList.isEmpty()) {
                                    EmptyState(message = "该文件夹没有音乐文件")
                                } else {
                                    MusicListContent(
                                        musicList = displayedMusicList,
                                        matchResults = matchResults,
                                        matchProgress = matchProgress,
                                        onMusicClick = onMusicSelected,
                                        onStartMatch = { viewModel.showSaveModeDialog(displayedMusicList) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 保存方式选择对话框
        if (showSaveModeDialog) {
            SaveModeDialog(
                defaultMode = defaultSaveMode,
                onConfirm = { saveLyrics, writeMode ->
                    viewModel.confirmStartMatch(saveLyrics, writeMode)
                },
                onDismiss = {
                    viewModel.hideSaveModeDialog()
                }
            )
        }
    }
}

/**
 * 保存方式选择对话框
 */
@Composable
private fun SaveModeDialog(
    defaultMode: LyricsWriteMode,
    onConfirm: (Boolean, LyricsWriteMode) -> Unit,
    onDismiss: () -> Unit
) {
    var saveLyrics by remember { mutableStateOf(true) }
    var selectedMode by remember { mutableStateOf(defaultMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量匹配设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 是否保存歌词
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动保存歌词")
                    Switch(
                        checked = saveLyrics,
                        onCheckedChange = { saveLyrics = it }
                    )
                }

                // 保存方式选择
                if (saveLyrics) {
                    Text(
                        "保存方式:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Column {
                        SaveModeOption(
                            text = "嵌入到音乐文件",
                            selected = selectedMode == LyricsWriteMode.EMBEDDED,
                            onClick = { selectedMode = LyricsWriteMode.EMBEDDED }
                        )
                        SaveModeOption(
                            text = "保存为独立歌词文件",
                            selected = selectedMode == LyricsWriteMode.SEPARATE_FILE,
                            onClick = { selectedMode = LyricsWriteMode.SEPARATE_FILE }
                        )
                        SaveModeOption(
                            text = "同时嵌入和保存文件",
                            selected = selectedMode == LyricsWriteMode.BOTH,
                            onClick = { selectedMode = LyricsWriteMode.BOTH }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(saveLyrics, selectedMode) }
            ) {
                Text("开始匹配")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 保存方式选项
 */
@Composable
private fun SaveModeOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * 权限被拒绝状态
 */
@Composable
private fun PermissionDeniedState(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = "需要存储权限",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "请授予读取存储权限以扫描本地音乐文件",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

/**
 * 文件夹列表内容
 */
@Composable
private fun FolderListContent(
    folderList: List<String>,
    musicByFolder: Map<String, List<LocalMusicInfo>>,
    onFolderClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folderList) { folderPath ->
            val musicCount = musicByFolder[folderPath]?.size ?: 0
            val folderName = File(folderPath).name
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folderPath) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 文件夹图标
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folderName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "$musicCount 首音乐",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = folderPath,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // 箭头图标
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(180f)
                    )
                }
            }
        }
    }
}

/**
 * 初始状态 - 显示扫描按钮
 */
@Composable
private fun IdleState(
    onStartScan: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "扫描本地音乐",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "点击开始扫描设备上的音乐文件",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onStartScan) {
                Text("开始扫描")
            }
        }
    }
}

/**
 * 扫描中状态
 */
@Composable
private fun ScanningState(progress: com.example.lddc.model.ScanProgress) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator()

            Text(
                text = "正在扫描本地音乐...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            // 进度条
            if (progress.total > 0) {
                LinearProgressIndicator(
                    progress = { progress.percentage() / 100f },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "${progress.current} / ${progress.total} (${progress.percentage()}%)",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = "已发现 ${progress.foundMusic} 首音乐",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 显示线程数信息
            Text(
                text = if (progress.threadCount > 1) {
                    "使用 ${progress.threadCount} 线程并行扫描"
                } else {
                    "单线程扫描"
                },
                fontSize = 12.sp,
                color = if (progress.threadCount > 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )

            if (progress.currentPath.isNotEmpty()) {
                Text(
                    text = progress.currentPath.takeLast(50),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 音乐列表内容
 */
@Composable
private fun MusicListContent(
    musicList: List<LocalMusicInfo>,
    matchResults: List<LocalMusicMatchResult>,
    matchProgress: com.example.lddc.model.MatchProgress,
    onMusicClick: (LocalMusicInfo) -> Unit,
    onStartMatch: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 匹配进度显示（仅在匹配中时显示）
        if (matchProgress.total > 0) {
            if (matchProgress.current < matchProgress.total) {
                // 显示匹配进度
                MatchingProgressBar(progress = matchProgress)
            } else {
                // 显示匹配完成统计（简洁版）
                MatchStatusBar(
                    text = "匹配完成: ${matchProgress.successCount} 首成功"
                )
            }
        }

        // 音乐列表
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(musicList) { music ->
                val matchResult = matchResults.find { it.localMusic.id == music.id }
                LocalMusicListItem(
                    music = music,
                    matchResult = matchResult,
                    onClick = { onMusicClick(music) }
                )
            }
        }
    }
}

/**
 * 匹配状态栏（仅显示状态，无按钮）
 */
@Composable
private fun MatchStatusBar(
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 匹配进度条
 */
@Composable
private fun MatchingProgressBar(
    progress: com.example.lddc.model.MatchProgress
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "正在匹配: ${progress.currentMusic}",
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = "${progress.current}/${progress.total}",
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "成功: ${progress.successCount}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "失败: ${progress.failedCount}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 本地音乐列表项
 */
@Composable
private fun LocalMusicListItem(
    music: LocalMusicInfo,
    matchResult: LocalMusicMatchResult?,
    onClick: () -> Unit
) {
    LocalMusicCard(
        music = music,
        onClick = onClick
    )
}
