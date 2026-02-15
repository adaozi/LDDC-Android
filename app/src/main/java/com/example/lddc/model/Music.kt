package com.example.lddc.model

/**
 * 音乐数据类（UI层使用）
 *
 * 用于在UI层展示歌曲信息，是SongInfo的简化版本
 * 包含歌词相关的展示字段
 *
 * @param id 歌曲唯一标识
 * @param title 歌曲标题
 * @param artist 艺术家/歌手名称
 * @param duration 歌曲时长（秒），格式为字符串便于展示
 * @param platform 来源平台名称（"QQ音乐"、"网易云音乐"、"酷狗音乐"）
 * @param album 专辑名称
 * @param imageUrl 专辑封面图片URL
 * @param description 歌曲描述/简介
 * @param lyrics 歌词内容（获取详情后填充）
 * @param lyricsType 歌词格式类型（"LRC"、"QRC"、"KRC"、"YRC"等）
 */
data class Music(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val platform: String,
    val album: String,
    val imageUrl: String,
    val description: String = "",
    val lyrics: String = "",
    val lyricsType: String = "未知"
)

/**
 * 搜索筛选条件
 *
 * 用户在搜索界面设置的筛选参数
 *
 * @param keyword 搜索关键词（歌曲名/歌手名）
 * @param songName 按歌曲名筛选（精确筛选）
 * @param artist 按歌手名筛选（精确筛选）
 * @param album 按专辑名筛选（精确筛选）
 * @param duration 按时长筛选（格式如"3:30"）
 * @param platforms 平台筛选（多选，如["QQ_MUSIC", "NET_EASE"]）
 */
data class SearchFilters(
    val keyword: String = "",
    val songName: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: String = "",
    val platforms: Set<String> = emptySet()
)
