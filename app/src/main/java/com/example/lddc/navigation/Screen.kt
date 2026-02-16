package com.example.lddc.navigation

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    // 网络搜索相关
    object SearchScreen : Screen("search")
    object ResultsScreen : Screen("results")
    object DetailScreen : Screen("detail")

    // 本地音乐相关
    object LocalMusicListScreen : Screen("local_music_list")
    object LocalMusicDetailScreen : Screen("local_music_detail")
    object LocalMusicSearchScreen : Screen("local_music_search")
    object LocalMusicSearchDetailScreen : Screen("local_music_search_detail")
}