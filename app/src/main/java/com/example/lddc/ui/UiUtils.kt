package com.example.lddc.ui

/**
 * 获取平台显示名称
 */
fun getPlatformDisplayName(platform: String): String {
    return when (platform) {
        "QQ_MUSIC", "QM" -> "QQ音乐"
        "NET_EASE", "NE" -> "网易云音乐"
        "KUGOU", "KG" -> "酷狗音乐"
        "LRCLIB" -> "LyricLib"
        "QQ音乐" -> "QQ音乐"
        "网易云音乐" -> "网易云音乐"
        "酷狗音乐" -> "酷狗音乐"
        else -> platform
    }
}

/**
 * 获取歌词类型显示名称
 */
fun getLyricsTypeDisplay(type: String): String {
    return when (type) {
        "QRC" -> "逐字歌词 (QRC)"
        "KRC" -> "逐字歌词 (KRC)"
        "YRC" -> "逐字歌词 (YRC)"
        "LRC" -> "逐行歌词 (LRC)"
        else -> type
    }
}
