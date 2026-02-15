package com.example.lddc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.lddc.navigation.Screen
import com.example.lddc.ui.DetailScreen
import com.example.lddc.ui.ResultsScreen
import com.example.lddc.ui.SearchScreen
import com.example.lddc.ui.theme.LDDCTheme
import com.example.lddc.viewmodel.MusicViewModel
import com.example.lddc.viewmodel.MusicSearchUseCase

/**
 * 主 Activity
 *
 * 实现 ImageLoaderFactory 接口以自定义 Coil 图片加载配置
 * 限制图片缓存大小：内存缓存 20MB，磁盘缓存 5MB
 */
class MainActivity : ComponentActivity(), ImageLoaderFactory {

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

@androidx.compose.runtime.Composable
fun MusicApp(musicSearchUseCase: MusicSearchUseCase = androidx.lifecycle.viewmodel.compose.viewModel<MusicViewModel>()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.SearchScreen.route
    ) {
        composable(Screen.SearchScreen.route) {
            SearchScreen(
                musicSearchUseCase = musicSearchUseCase,
                onSearch = {
                    navController.navigate(Screen.ResultsScreen.route)
                }
            )
        }

        composable(Screen.ResultsScreen.route) {
            ResultsScreen(
                musicSearchUseCase = musicSearchUseCase,
                onBack = { navController.popBackStack() },
                onMusicSelected = { music ->
                    musicSearchUseCase.selectMusic(music)
                    navController.navigate(Screen.DetailScreen.route)
                }
            )
        }

        composable(Screen.DetailScreen.route) {
            DetailScreen(
                musicSearchUseCase = musicSearchUseCase,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
