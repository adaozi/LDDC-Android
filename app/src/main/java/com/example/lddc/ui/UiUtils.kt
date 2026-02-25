package com.example.lddc.ui

import com.example.lddc.utils.LyricsUtils
import com.example.lddc.utils.PlatformUtils

/**
 * 获取平台显示名称
 *
 * @deprecated 请使用 PlatformUtils.getDisplayName()
 */
@Deprecated(
    "请使用 PlatformUtils.getDisplayName()",
    ReplaceWith("PlatformUtils.getDisplayName(platform)")
)
fun getPlatformDisplayName(platform: String): String {
    return PlatformUtils.getDisplayName(platform)
}

/**
 * 获取歌词类型显示名称
 *
 * @deprecated 请使用 LyricsUtils.getTypeDisplay()
 */
@Deprecated("请使用 LyricsUtils.getTypeDisplay()", ReplaceWith("LyricsUtils.getTypeDisplay(type)"))
fun getLyricsTypeDisplay(type: String): String {
    return LyricsUtils.getTypeDisplay(type)
}
