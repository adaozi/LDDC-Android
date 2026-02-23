package com.example.lddc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.lddc.navigation.Screen
import com.example.lddc.service.PlatformService
import com.example.lddc.ui.DetailScreen
import com.example.lddc.ui.LocalMusicDetailScreen
import com.example.lddc.ui.LocalMusicListScreen
import com.example.lddc.ui.LocalMusicSearchScreen
import com.example.lddc.ui.LocalMusicSearchDetailScreen
import com.example.lddc.ui.ResultsScreen
import com.example.lddc.ui.SearchScreen
import com.example.lddc.ui.theme.LDDCTheme
import com.example.lddc.viewmodel.LocalMatchViewModel
import com.example.lddc.viewmodel.MusicViewModel
import org.jaudiotagger.tag.TagOptionSingleton
import java.nio.charset.Charset

/**
 * 主 Activity
 *
 * 实现 ImageLoaderFactory 接口以自定义 Coil 图片加载配置
 * 限制图片缓存大小：内存缓存 20MB，磁盘缓存 5MB
 */
class MainActivity : ComponentActivity(), ImageLoaderFactory {

    init {
        // 初始化 JAudioTagger 编码设置
        initJAudioTagger()
    }

    companion object {
        /**
         * 初始化 JAudioTagger 编码设置
         * 确保正确处理 UTF-8 编码的歌词
         */
        private fun initJAudioTagger() {
            try {
                val tagOptions = TagOptionSingleton.getInstance()
                tagOptions.isAndroid = true
                tagOptions.isWriteMp3GenresAsText = true
                // 设置 ID3 标签使用 UTF-8 编码
                tagOptions.id3v23DefaultTextEncoding = 1 // UTF-8
                tagOptions.id3v24DefaultTextEncoding = 1 // UTF-8
                android.util.Log.d("MainActivity", "JAudioTagger 编码设置完成")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "JAudioTagger 初始化失败", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 切换到正常主题
        setTheme(R.style.Theme_LDDC)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LDDCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicApp()
                }
            }
        }
    }

    /**
     * 创建自定义 ImageLoader
     *
     * 配置图片缓存限制：
     * - 内存缓存：最大 200MB
     * - 磁盘缓存：最大 5MB
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(200 * 1024 * 1024) // 200MB 内存缓存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache")) // 缓存目录：/cache/image_cache/
                    .maxSizeBytes(5 * 1024 * 1024) // 5MB 磁盘缓存
                    .build()
            }
            .build()
    }
}

@Composable
fun MusicApp(
    musicViewModel: MusicViewModel = viewModel(),
    localMatchViewModel: LocalMatchViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.SearchScreen.route
    ) {
        // 网络搜索相关页面
        composable(Screen.SearchScreen.route) {
            SearchScreen(
                viewModel = musicViewModel,
                onSearch = {
                    navController.navigate(Screen.ResultsScreen.route)
                },
                onLocalMusicClick = {
                    navController.navigate(Screen.LocalMusicListScreen.route)
                }
            )
        }

        composable(Screen.ResultsScreen.route) {
            ResultsScreen(
                viewModel = musicViewModel,
                onBack = { navController.popBackStack() },
                onMusicSelected = { music ->
                    musicViewModel.selectMusic(music)
                    navController.navigate(Screen.DetailScreen.route)
                }
            )
        }

        composable(Screen.DetailScreen.route) {
            DetailScreen(
                viewModel = musicViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // 本地音乐相关页面
        composable(Screen.LocalMusicListScreen.route) {
            LocalMusicListScreen(
                viewModel = localMatchViewModel,
                onBack = { navController.popBackStack() },
                onMusicSelected = { music ->
                    localMatchViewModel.selectLocalMusic(music)
                    navController.navigate(Screen.LocalMusicDetailScreen.route)
                }
            )
        }

        composable(Screen.LocalMusicDetailScreen.route) {
            LocalMusicDetailScreen(
                viewModel = localMatchViewModel,
                onBack = { navController.popBackStack() },
                onSearchLyrics = {
                    navController.navigate(Screen.LocalMusicSearchScreen.route)
                }
            )
        }

        composable(Screen.LocalMusicSearchScreen.route) {
            LocalMusicSearchScreen(
                viewModel = localMatchViewModel,
                onBack = { navController.popBackStack() },
                onMusicSelected = {
                    // 进入搜索结果歌曲详情页
                    navController.navigate(Screen.LocalMusicSearchDetailScreen.route)
                }
            )
        }

        composable(Screen.LocalMusicSearchDetailScreen.route) {
            val context = LocalContext.current
            val platformService = remember { PlatformService(context) }
            LocalMusicSearchDetailScreen(
                viewModel = localMatchViewModel,
                onBack = { navController.popBackStack() },
                onUseLyrics = { lyrics, mode ->
                    android.util.Log.d("MainActivity", "点击使用此歌词，歌词长度: ${lyrics.length}, 模式: $mode")
                    // 写入歌词到本地音乐（使用转换后的歌词和选择的保存模式）
                    localMatchViewModel.writeLyricsToLocalMusic(lyrics, mode) { success, errorMessage ->
                        android.util.Log.d("MainActivity", "歌词写入结果: $success, 错误: $errorMessage")
                        if (success) {
                            // 显示成功提示
                            val modeText = when (mode) {
                                com.example.lddc.model.LyricsWriteMode.EMBEDDED -> "已写入音频文件"
                                com.example.lddc.model.LyricsWriteMode.SEPARATE_FILE -> "已保存为歌词文件"
                                com.example.lddc.model.LyricsWriteMode.BOTH -> "已同时保存到两者"
                                else -> "歌词已保存"
                            }
                            platformService.showToast(modeText)
                            // 返回本地歌曲详情页
                            navController.popBackStack(Screen.LocalMusicDetailScreen.route, false)
                        } else {
                            // 显示失败提示
                            if (errorMessage?.contains("权限") == true ||
                                errorMessage?.contains("Permission") == true ||
                                errorMessage?.contains("NoWritePermissionsException") == true) {
                                platformService.showToast("需要「管理所有文件」权限才能保存歌词")
                                // 引导用户去系统设置开启权限（Android 11+）
                                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                } else {
                                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                }
                                context.startActivity(intent)
                            } else {
                                platformService.showToast("歌词保存失败: ${errorMessage ?: "未知错误"}")
                            }
                        }
                    }
                }
            )
        }
    }
}
