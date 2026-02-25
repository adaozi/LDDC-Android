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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LocalMusicMatchResult
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.service.PermissionManager
import com.example.lddc.ui.components.EmptyState
import com.example.lddc.ui.components.LocalMusicCard
import com.example.lddc.ui.theme.UiConstants
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

    // 横竖屏检测
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
                            fontSize = UiConstants.FontSize.XLarge,
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
                                fontSize = UiConstants.FontSize.Small,
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
                        displayedMusicList.isNotEmpty()
                    ) {
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
                                    onStartMatch = { viewModel.showSaveModeDialog(displayedMusicList) },
                                    isLandscape = isLandscape
                                )
                            }
                        }

                        ViewMode.FOLDER -> {
                            // 文件夹视图 - 横竖屏适配
                            if (isLandscape && selectedFolder == null) {
                                // 横屏：左右分栏布局
                                FolderMusicSplitView(
                                    folderList = folderList,
                                    musicByFolder = viewModel.musicByFolder.collectAsState().value,
                                    selectedFolder = selectedFolder,
                                    displayedMusicList = displayedMusicList,
                                    matchResults = matchResults,
                                    matchProgress = matchProgress,
                                    onFolderClick = { folder ->
                                        viewModel.selectFolder(folder)
                                    },
                                    onMusicClick = onMusicSelected,
                                    onStartMatch = { viewModel.showSaveModeDialog(displayedMusicList) }
                                )
                            } else if (selectedFolder == null) {
                                // 竖屏：显示文件夹列表
                                FolderListContent(
                                    folderList = folderList,
                                    musicByFolder = viewModel.musicByFolder.collectAsState().value,
                                    onFolderClick = { folder ->
                                        viewModel.selectFolder(folder)
                                    },
                                    isLandscape = isLandscape
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
                                        onStartMatch = {
                                            viewModel.showSaveModeDialog(
                                                displayedMusicList
                                            )
                                        },
                                        isLandscape = isLandscape
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
            verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Large),
            modifier = Modifier.padding(UiConstants.Padding.XXLarge)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = "需要存储权限",
                fontSize = UiConstants.FontSize.XLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "请授予读取存储权限以扫描本地音乐文件",
                fontSize = UiConstants.FontSize.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(UiConstants.Spacing.Large))

            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

/**
 * 横屏分栏布局 - 左侧文件夹列表，右侧音乐列表
 */
@Composable
private fun FolderMusicSplitView(
    folderList: List<String>,
    musicByFolder: Map<String, List<LocalMusicInfo>>,
    selectedFolder: String?,
    displayedMusicList: List<LocalMusicInfo>,
    matchResults: List<LocalMusicMatchResult>,
    matchProgress: com.example.lddc.model.MatchProgress,
    onFolderClick: (String) -> Unit,
    onMusicClick: (LocalMusicInfo) -> Unit,
    onStartMatch: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧文件夹列表
        LazyColumn(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(UiConstants.Padding.Large),
            verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Small)
        ) {
            items(folderList) { folderPath ->
                val musicCount = musicByFolder[folderPath]?.size ?: 0
                val folderName = File(folderPath).name
                val isSelected = folderPath == selectedFolder

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderClick(folderPath) },
                    shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSelected) UiConstants.Elevation.Medium else UiConstants.Elevation.Low
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiConstants.Padding.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(UiConstants.CornerRadius.Small)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folderName,
                                fontSize = UiConstants.FontSize.Normal,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "$musicCount 首",
                                fontSize = UiConstants.FontSize.Small,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 右侧音乐列表
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            if (selectedFolder == null) {
                // 未选择文件夹时显示提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(UiConstants.Size.IconLarge),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "请选择一个文件夹",
                            fontSize = UiConstants.FontSize.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (displayedMusicList.isEmpty()) {
                EmptyState(message = "该文件夹没有音乐文件")
            } else {
                MusicListContent(
                    musicList = displayedMusicList,
                    matchResults = matchResults,
                    matchProgress = matchProgress,
                    onMusicClick = onMusicClick,
                    onStartMatch = onStartMatch,
                    isLandscape = true
                )
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
    onFolderClick: (String) -> Unit,
    isLandscape: Boolean = false
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            if (isLandscape) UiConstants.Padding.XLarge else UiConstants.Padding.Large
        ),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) UiConstants.Spacing.Medium else UiConstants.Spacing.Small)
    ) {
        items(folderList) { folderPath ->
            val musicCount = musicByFolder[folderPath]?.size ?: 0
            val folderName = File(folderPath).name

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folderPath) },
                shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = UiConstants.Elevation.Low)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiConstants.Padding.Large),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Large)
                ) {
                    // 文件夹图标
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(UiConstants.CornerRadius.Small)
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
                            fontSize = UiConstants.FontSize.Medium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "$musicCount 首音乐",
                            fontSize = UiConstants.FontSize.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = folderPath,
                            fontSize = UiConstants.FontSize.Small,
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
            verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Large)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.height(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "扫描本地音乐",
                fontSize = UiConstants.FontSize.XLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "点击开始扫描设备上的音乐文件",
                fontSize = UiConstants.FontSize.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(UiConstants.Spacing.Large))

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
            verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Large),
            modifier = Modifier.padding(UiConstants.Padding.XXLarge)
        ) {
            CircularProgressIndicator()

            Text(
                text = "正在扫描本地音乐...",
                fontSize = UiConstants.FontSize.Large,
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
                    fontSize = UiConstants.FontSize.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text(
                text = "已发现 ${progress.foundMusic} 首音乐",
                fontSize = UiConstants.FontSize.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 显示线程数信息
            Text(
                text = if (progress.threadCount > 1) {
                    "使用 ${progress.threadCount} 线程并行扫描"
                } else {
                    "单线程扫描"
                },
                fontSize = UiConstants.FontSize.Small,
                color = if (progress.threadCount > 1) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )

            if (progress.currentPath.isNotEmpty()) {
                Text(
                    text = progress.currentPath.takeLast(50),
                    fontSize = UiConstants.FontSize.Small,
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
    onStartMatch: () -> Unit,
    isLandscape: Boolean = false
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 匹配进度显示（仅在匹配中时显示）
        if (matchProgress.total > 0) {
            if (matchProgress.current < matchProgress.total) {
                // 显示匹配进度
                MatchingProgressBar(progress = matchProgress, isLandscape = isLandscape)
            } else {
                // 显示匹配完成统计（简洁版）
                MatchStatusBar(
                    text = "匹配完成: ${matchProgress.successCount} 首成功",
                    isLandscape = isLandscape
                )
            }
        }

        // 音乐列表 - 横屏使用网格，竖屏使用列表
        if (isLandscape) {
            // 横屏：自适应网格布局
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = UiConstants.Padding.XLarge,
                    vertical = UiConstants.Spacing.Medium
                ),
                horizontalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium)
            ) {
                items(musicList) { music ->
                    val matchResult = matchResults.find { it.localMusic.id == music.id }
                    LocalMusicGridItem(
                        music = music,
                        matchResult = matchResult,
                        onClick = { onMusicClick(music) }
                    )
                }
            }
        } else {
            // 竖屏：单列列表
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    UiConstants.Padding.Large
                ),
                verticalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Small)
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
}

/**
 * 匹配状态栏（仅显示状态，无按钮）
 */
@Composable
private fun MatchStatusBar(
    text: String,
    isLandscape: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) UiConstants.Padding.XLarge else UiConstants.Padding.Large,
                vertical = UiConstants.Spacing.Small
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = UiConstants.FontSize.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 匹配进度条
 */
@Composable
private fun MatchingProgressBar(
    progress: com.example.lddc.model.MatchProgress,
    isLandscape: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) UiConstants.Padding.XLarge else UiConstants.Padding.Large,
                vertical = UiConstants.Padding.Medium
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "正在匹配: ${progress.currentMusic}",
                fontSize = UiConstants.FontSize.Normal,
                maxLines = 1
            )
            Text(
                text = "${progress.current}/${progress.total}",
                fontSize = UiConstants.FontSize.Normal
            )
        }

        Spacer(modifier = Modifier.height(UiConstants.Spacing.XS))

        LinearProgressIndicator(
            progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(UiConstants.Spacing.XS))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "成功: ${progress.successCount}",
                fontSize = UiConstants.FontSize.Small,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "失败: ${progress.failedCount}",
                fontSize = UiConstants.FontSize.Small,
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

/**
 * 本地音乐网格项（用于横屏网格布局）
 */
@Composable
private fun LocalMusicGridItem(
    music: LocalMusicInfo,
    matchResult: LocalMusicMatchResult?,
    onClick: () -> Unit
) {
    LocalMusicCard(
        music = music,
        onClick = onClick
    )
}
