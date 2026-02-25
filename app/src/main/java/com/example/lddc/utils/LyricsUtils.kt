package com.example.lddc.utils

/**
 * 歌词相关工具类
 */
object LyricsUtils {

    /**
     * 获取歌词类型显示名称
     *
     * @param type 歌词类型
     * @return 歌词类型显示名称
     */
    fun getTypeDisplay(type: String): String {
        return when (type) {
            "QRC" -> "逐字歌词 (QRC)"
            "KRC" -> "逐字歌词 (KRC)"
            "YRC" -> "逐字歌词 (YRC)"
            "LRC" -> "逐行歌词 (LRC)"
            else -> type
        }
    }

}
