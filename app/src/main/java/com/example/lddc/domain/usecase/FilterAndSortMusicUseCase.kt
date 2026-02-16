package com.example.lddc.domain.usecase

import android.util.Log
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.Music
import com.example.lddc.model.SearchFilters

/**
 * 音乐筛选和排序用例
 *
 * 封装筛选和排序逻辑，供多个ViewModel复用
 */
class FilterAndSortMusicUseCase {

    companion object {
        private const val TAG = "FilterAndSortMusicUseCase"
    }

    /**
     * 根据筛选条件过滤音乐列表
     *
     * @param musicList 原始音乐列表
     * @param searchFilters 筛选条件
     * @return 筛选后的音乐列表
     */
    fun filterMusic(musicList: List<Music>, searchFilters: SearchFilters): List<Music> {
        Log.d(TAG, "筛选开始 - 原始歌曲数: ${musicList.size}")
        Log.d(TAG, "筛选条件 - songName: '${searchFilters.songName}', artist: '${searchFilters.artist}', album: '${searchFilters.album}', duration: '${searchFilters.duration}', platforms: ${searchFilters.platforms}")
        
        // 快速路径：如果所有筛选条件都是空的，直接返回原始列表，不做任何筛选
        val hasNoFilters = searchFilters.songName.isBlank() &&
                          searchFilters.artist.isBlank() &&
                          searchFilters.album.isBlank() &&
                          searchFilters.duration.isBlank() &&
                          searchFilters.platforms.isEmpty()
                          
        if (hasNoFilters) {
            Log.d(TAG, "无筛选条件，直接返回原始结果")
            return musicList
        }
        
        val filtered = musicList.filter { music ->
            // 歌曲名筛选（模糊匹配）
            (searchFilters.songName.isBlank() || music.title.contains(searchFilters.songName, ignoreCase = true)) &&
            // 歌手筛选（模糊匹配）
            (searchFilters.artist.isBlank() || music.artist.contains(searchFilters.artist, ignoreCase = true)) &&
            // 专辑筛选（模糊匹配）
            (searchFilters.album.isBlank() || music.album.contains(searchFilters.album, ignoreCase = true)) &&
            // 时长筛选（包含匹配）
            (searchFilters.duration.isBlank() || music.duration.contains(searchFilters.duration)) &&
            // 平台筛选（多选，匹配任一即可）
            (searchFilters.platforms.isEmpty() || searchFilters.platforms.any { platformName ->
                val displayName = when (platformName) {
                    "NE" -> "网易云音乐"
                    "QM" -> "QQ音乐"
                    "KG" -> "酷狗音乐"
                    else -> platformName
                }
                music.platform.equals(displayName, ignoreCase = true)
            })
        }
        
        Log.d(TAG, "筛选结束 - 筛选后歌曲数: ${filtered.size}")
        return filtered
    }

    /**
     * 根据本地音乐信息筛选和排序搜索结果
     *
     * @param songs 搜索结果列表
     * @param localMusic 本地音乐信息
     * @return 排序后的结果
     */
    fun filterAndSortByLocalMusic(songs: List<Music>, localMusic: LocalMusicInfo): List<Music> {
        val localTitle = localMusic.title.lowercase()
        val localArtist = localMusic.artist.lowercase()

        return songs.sortedByDescending { song ->
            var score = 0
            val songTitle = song.title.lowercase()
            val songArtist = song.artist.lowercase()

            // 歌曲名匹配
            when {
                // 完全匹配
                songTitle == localTitle -> score += 100
                // 歌曲名包含本地歌曲名
                songTitle.contains(localTitle) -> score += 50
                // 本地歌曲名包含搜索结果
                localTitle.contains(songTitle) -> score += 30
            }

            // 艺术家匹配
            if (localArtist.isNotBlank() && localArtist != "未知艺术家") {
                when {
                    // 艺术家完全匹配
                    songArtist == localArtist -> score += 40
                    // 艺术家包含
                    songArtist.contains(localArtist) -> score += 20
                    // 本地艺术家包含搜索结果
                    localArtist.contains(songArtist) -> score += 10
                }
            }

            score
        }
    }

    /**
     * 计算字符串相似度
     */
    fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        if (s1.isEmpty() || s2.isEmpty()) return 0f

        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

        return if (longer.contains(shorter)) {
            shorter.length.toFloat() / longer.length
        } else {
            0f
        }
    }
}
