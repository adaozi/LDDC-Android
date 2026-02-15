package com.example.lddc.service.parser

/**
 * 歌词数据结构
 * 对应Python中的 LyricsData, LyricsLine, LyricsWord
 */

/**
 * 歌词字（逐字歌词）
 */
data class LyricsWord(
    val start: Long?,  // 开始时间（毫秒）
    val end: Long?,    // 结束时间（毫秒）
    val text: String   // 歌词文本
)

/**
 * 歌词行
 */
data class LyricsLine(
    val start: Long?,              // 开始时间（毫秒）
    val end: Long?,                // 结束时间（毫秒）
    val words: List<LyricsWord>    // 歌词字列表
) {
    /**
     * 获取行文本
     */
    fun getText(): String = words.joinToString("") { it.text }
}

/**
 * 歌词数据列表
 */
typealias LyricsData = MutableList<LyricsLine>

/**
 * 多语言歌词数据
 */
typealias MultiLyricsData = MutableMap<String, LyricsData>

/**
 * 创建空的 LyricsData
 */
fun createLyricsData(): LyricsData = mutableListOf()

/**
 * 创建空的 MultiLyricsData
 */
fun createMultiLyricsData(): MultiLyricsData = mutableMapOf()

// 注意：LyricsType 和 LyricsFormat 枚举定义在 model/LyricsModel.kt 中

/**
 * 将纯文本转换为歌词数据
 */
fun plaintext2data(text: String): LyricsData {
    val data = createLyricsData()
    text.lines().forEach { line ->
        if (line.isNotBlank()) {
            data.add(LyricsLine(null, null, listOf(LyricsWord(null, null, line))))
        }
    }
    return data
}
