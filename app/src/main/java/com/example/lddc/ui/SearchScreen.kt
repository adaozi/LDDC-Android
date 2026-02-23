package com.example.lddc.ui

import android.util.Log
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lddc.model.SearchFilters
import com.example.lddc.service.PlatformService
import com.example.lddc.ui.theme.UiConstants
import com.example.lddc.viewmodel.MusicViewModel

/**
 * 搜索界面
 *
 * 提供歌曲搜索的输入界面，支持横竖屏自适应布局
 *
 * 功能：
 * - 关键词输入
 * - 搜索按钮
 * - 横屏时显示品牌区域和搜索区域并排
 * - 竖屏时显示居中搜索卡片
 * - 本地音乐入口
 *
 * @param viewModel 音乐搜索ViewModel
 * @param onSearch 搜索回调，点击搜索或回车时触发
 * @param onLocalMusicClick 本地音乐入口点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    onSearch: () -> Unit,
    onLocalMusicClick: (() -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val platformService = remember { PlatformService(context) }

    // 检测屏幕方向
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // 使用独立的本地状态管理输入，避免Compose状态更新延迟问题
    var keywordInput by remember { mutableStateOf("") }

    // 搜索函数
    fun performSearch() {
        val trimmedInput = keywordInput.trim()
        Log.d("SearchScreen", "执行搜索，输入内容: '$trimmedInput'")
        if (trimmedInput.isEmpty()) {
            platformService.showToast("搜索词不能为空")
            return
        }
        // 更新ViewModel的筛选条件，只保留关键词
        viewModel.updateSearchFilters(
            SearchFilters(keyword = trimmedInput)
        )
        Log.d("SearchScreen", "已更新ViewModel筛选条件，开始调用searchMusic()")
        viewModel.searchMusic()
        keyboardController?.hide()
        onSearch()
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLandscape) {
                // 横屏布局
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(64.dp)
                ) {
                    // 左侧：品牌区域
                    BrandSection(modifier = Modifier.weight(1f))

                    // 右侧：搜索区域
                    SearchSection(
                        keyword = keywordInput,
                        onKeywordChange = { keywordInput = it },
                        onSearch = { performSearch() },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // 竖屏布局
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BrandSection(modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(48.dp))

                    SearchSection(
                        keyword = keywordInput,
                        onKeywordChange = { keywordInput = it },
                        onSearch = { performSearch() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 本地音乐入口
                    if (onLocalMusicClick != null) {
                        Spacer(modifier = Modifier.height(24.dp))

                        LocalMusicEntry(
                            onClick = onLocalMusicClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrandSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo 卡片
        Card(
            shape = RoundedCornerShape(UiConstants.CornerRadius.Logo),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(UiConstants.Size.Logo)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "L",
                    fontSize = UiConstants.FontSize.XXLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(UiConstants.Spacing.XXLarge))

        // 应用名称
        Text(
            text = "LDDC",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(UiConstants.Spacing.Small))

        // 副标题
        Text(
            text = "歌词下载工具",
            fontSize = UiConstants.FontSize.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SearchSection(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(UiConstants.CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = UiConstants.Elevation.Medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiConstants.Padding.XXLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "搜索歌曲",
                fontSize = UiConstants.FontSize.XLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(UiConstants.Spacing.Large))

            // 搜索输入框
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                placeholder = { Text("请输入歌曲名或歌手") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                trailingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onSearch()
                    }
                )
            )

            Spacer(modifier = Modifier.height(UiConstants.Spacing.Large))

            // 搜索按钮
            Button(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "开始搜索",
                    fontSize = UiConstants.FontSize.Medium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 本地音乐入口组件
 */
@Composable
fun LocalMusicEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(UiConstants.CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = UiConstants.Elevation.Low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiConstants.Padding.Large),
            horizontalArrangement = Arrangement.spacedBy(UiConstants.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(UiConstants.Size.IconMedium)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本地音乐",
                    fontSize = UiConstants.FontSize.Medium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Text(
                    text = "为本地音乐文件匹配歌词",
                    fontSize = UiConstants.FontSize.Small,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
