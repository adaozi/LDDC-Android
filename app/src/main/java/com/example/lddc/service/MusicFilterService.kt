package com.example.lddc.service

import com.example.lddc.model.Music
import com.example.lddc.model.SearchFilters

/**
 * 音乐筛选服务
 *
 * 负责根据用户设置的筛选条件过滤音乐列表
 * 支持按歌曲名、歌手、专辑、时长、平台等条件筛选
 */
class MusicFilterService {

    /**
     * 根据筛选条件过滤音乐列表
     *
     * 筛选逻辑（所有条件为 AND 关系）：
     * - 歌曲名：模糊匹配，不区分大小写
     * - 歌手：模糊匹配，不区分大小写
     * - 专辑：模糊匹配，不区分大小写
     * - 时长：包含匹配
     * - 平台：精确匹配（支持多选）
     *
     * @param musicList 原始音乐列表
     * @param searchFilters 筛选条件
     * @return 筛选后的音乐列表
     */
    fun filterMusic(musicList: List<Music>, searchFilters: SearchFilters): List<Music> {
        return musicList.filter { music ->
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
                // 将 Source 枚举名称转换为对应的显示名称进行比较
                val displayName = when (platformName) {
                    "NE" -> "网易云音乐"
                    "QM" -> "QQ音乐"
                    "KG" -> "酷狗音乐"
                    else -> platformName
                }
                music.platform.equals(displayName, ignoreCase = true)
            })
        }
    }

    /**
     * 将秒数转换为可读的时间格式
     *
     * 转换规则：
     * - 输入："300"（秒）
     * - 输出："5:00"
     *
     * @param duration 时长（秒）
     * @return 格式化后的时长字符串
     */
    fun formatDuration(duration: String): String {
        return try {
            val seconds = duration.toInt()
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "%d:%02d".format(minutes, remainingSeconds)
        } catch (_: NumberFormatException) {
            // 如果无法转换为数字，返回原值
            duration
        }
    }
}
