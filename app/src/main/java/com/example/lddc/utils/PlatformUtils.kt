package com.example.lddc.utils

/**
 * 平台相关工具类
 *
 * 提供平台名称转换等功能
 */
object PlatformUtils {

    /**
     * 获取平台显示名称
     *
     * @param platform 平台标识（枚举名称或显示名称）
     * @return 平台显示名称
     */
    fun getDisplayName(platform: String): String {
        return when (platform) {
            "QQ_MUSIC", "QM" -> "QQ音乐"
            "NET_EASE", "NE", "网易云音乐" -> "网易云音乐"
            "KUGOU", "KG", "酷狗音乐" -> "酷狗音乐"
            "LRCLIB" -> "LyricLib"
            "QQ音乐" -> "QQ音乐"
            else -> platform
        }
    }

    /**
     * 获取平台简短名称
     *
     * @param platform 平台标识
     * @return 平台简短名称（如：QQ音乐, 网易云, 酷狗）
     */
    fun getShortName(platform: String): String {
        return when (platform) {
            "QQ音乐", "QM", "QQ_MUSIC" -> "QQ音乐"
            "网易云音乐", "NE", "NET_EASE" -> "网易云"
            "酷狗音乐", "KG", "KUGOU" -> "酷狗"
            else -> platform
        }
    }

}
