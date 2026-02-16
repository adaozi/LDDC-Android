package com.example.lddc.utils

import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.Source

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

    /**
     * 根据平台获取歌词类型
     *
     * @param source 平台来源
     * @return 歌词类型
     */
    fun getTypeFromSource(source: Source): String {
        return when (source) {
            Source.QM -> "QRC"
            Source.KG -> "KRC"
            Source.NE -> "LRC"
        }
    }

    /**
     * 根据歌词格式获取显示名称
     *
     * @param format 歌词格式
     * @return 格式显示名称
     */
    fun getFormatDisplay(format: LyricsFormat): String {
        return when (format) {
            LyricsFormat.VERBATIMLRC -> "逐字LRC"
            LyricsFormat.LINEBYLINELRC -> "逐行LRC"
            LyricsFormat.ENHANCEDLRC -> "增强LRC"
        }
    }
}
