package com.example.lddc.service.converter

import android.annotation.SuppressLint
import com.example.lddc.service.parser.MultiLyricsData

/**
 * SRT 字幕转换器
 *
 * 将歌词数据转换为SRT（SubRip Subtitle）格式
 * SRT是通用的视频字幕格式，可被大多数视频播放器识别
 *
 * 格式示例：
 * 1
 * 00:00:01,000 --> 00:00:05,000
 * 第一行歌词
 * 第二行翻译
 *
 * 对应 Python 中的 core/converter/srt.py
 */

/**
 * 将毫秒转换为SRT时间戳格式
 *
 * SRT时间戳格式：HH:MM:SS,mmm
 *
 * @param ms 毫秒数
 * @return SRT格式时间戳（如"00:01:23,456"）
 */
@SuppressLint("DefaultLocale")
fun ms2srtTimestamp(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000

    return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}

/**
 * 将歌词数据转换为SRT格式字符串
 *
 * @param lyricsDict 多语言歌词数据（orig原文, ts翻译, roma罗马音）
 * @param langsMapping 语言行号映射（处理原文和翻译行数不一致）
 * @param langsOrder 语言输出顺序
 * @param duration 歌曲时长（毫秒），用于最后一行的结束时间
 * @return SRT格式字符串
 */
fun srtConverter(
    lyricsDict: MultiLyricsData,
    langsMapping: Map<String, Map<Int, Int>>,
    langsOrder: List<String>,
    duration: Long? = null
): String {
    val sb = StringBuilder()

    // 获取原始歌词并补充完整时间戳
    val origLyrics = lyricsDict["orig"] ?: return ""
    val fullTimestampsLyrics = getFullTimestampsLyricsData(
        origLyrics,
        duration = duration,
        onlyLine = true
    )

    // 遍历每一行生成SRT条目
    for (origIndex in fullTimestampsLyrics.indices) {
        val origLine = fullTimestampsLyrics[origIndex]
        if (origLine.start == null || origLine.end == null) continue

        // 添加序号
        sb.appendLine(origIndex + 1)

        // 添加时间戳（格式：00:00:01,000 --> 00:00:05,000）
        val start = ms2srtTimestamp(origLine.start)
        val end = ms2srtTimestamp(origLine.end)
        sb.appendLine("$start --> $end")

        // 添加歌词文本（多语言）
        val lyricsLines = getLyricsLines(lyricsDict, langsOrder, origIndex, origLine, langsMapping)

        for ((lyricsLine, _) in lyricsLines) {
            val text = lyricsLine.getText()
            if (text.isNotEmpty()) {
                sb.appendLine(text)
            }
        }

        sb.appendLine()
    }

    return sb.toString().trim()
}
