package com.example.lddc.service.parser

import com.example.lddc.service.network.ApiException

/**
 * QRC 歌词解析器（QQ音乐）
 *
 * QRC格式特点：
 * - XML包装：<Lyric_1 LyricType="1" LyricContent="..."/>
 * - 行格式：[开始时间,持续时间]内容
 * - 逐字格式：单词(开始时间,持续时间)
 * - 时间单位：毫秒（绝对时间，从歌曲开始计算）
 *
 * 对应 Python 中的 core/parser/qrc.py
 */

/** QRC内容提取模式 */
private val QRC_PATTERN =
    Regex("<Lyric_1 LyricType=\"1\" LyricContent=\"(.*?)\"/>", RegexOption.DOT_MATCHES_ALL)

/** 标签匹配模式：[ar:艺术家] */
private val TAG_SPLIT_PATTERN = Regex("^\\[(\\w+):([^]]*)]$")

/** 行时间戳匹配模式：[开始时间,持续时间] */
private val LINE_SPLIT_PATTERN = Regex("^\\[(\\d+),(\\d+)](.*)$")

/** 逐字时间戳匹配模式：单词(开始时间,持续时间) */
private val WORD_SPLIT_PATTERN =
    Regex("(?:\\[\\d+,\\d+])?(?<content>(?:(?!\\(\\d+,\\d+\\)).)*?)\\((?<start>\\d+),(?<duration>\\d+)\\)")

/** 空行标记匹配模式：(时间,0) */
private val WORD_TIMESTAMP_PATTERN = Regex("^\\(\\d+,\\d+\\)$")

/**
 * 将QRC文本解析为结构化歌词数据
 *
 * @param sQrc QRC格式字符串（XML包装）
 * @return Pair<标签字典, 歌词数据>
 *         标签：元数据标签
 *         歌词数据：逐行/逐字的歌词结构
 * @throws ApiException 格式不支持时抛出
 */
fun qrc2data(sQrc: String): Pair<Map<String, String>, LyricsData> {
    val qrcMatch = QRC_PATTERN.find(sQrc)
        ?: throw ApiException("不支持的歌词格式")

    val content = qrcMatch.groupValues[1]
    val tags = mutableMapOf<String, String>()
    val lyricsData = createLyricsData()

    content.lines().forEach { rawLine ->
        val line = rawLine.trim()

        // 处理歌词行 [开始时间,持续时间]内容
        val lineMatch = LINE_SPLIT_PATTERN.find(line)
        if (lineMatch != null) {
            val lineStart = lineMatch.groupValues[1].toLong()
            val lineDuration = lineMatch.groupValues[2].toLong()
            val lineEnd = lineStart + lineDuration
            val lineContent = lineMatch.groupValues[3]

            // 检查是否是空行标记 (时间,0)
            if (lineContent.startsWith("(") && lineContent.endsWith(")") &&
                WORD_TIMESTAMP_PATTERN.matches(lineContent)
            ) {
                lyricsData.add(LyricsLine(lineStart, lineEnd, emptyList()))
                return@forEach
            }

            // 处理逐字歌词
            // QRC中的字时间戳是绝对时间（相对于歌曲开始）
            val words = WORD_SPLIT_PATTERN.findAll(lineContent)
                .mapNotNull { wordMatch ->
                    val wordText = wordMatch.groupValues[1]
                    val wordStart = wordMatch.groupValues[2].toLong()
                    val wordDuration = wordMatch.groupValues[3].toLong()

                    if (wordText == "\r") return@mapNotNull null

                    LyricsWord(
                        wordStart,
                        wordStart + wordDuration,
                        wordText
                    )
                }
                .toList()

            if (words.isEmpty()) {
                // 如果没有逐字信息，将整个行作为一个字
                lyricsData.add(
                    LyricsLine(
                        lineStart,
                        lineEnd,
                        listOf(LyricsWord(lineStart, lineEnd, lineContent))
                    )
                )
            } else {
                lyricsData.add(LyricsLine(lineStart, lineEnd, words))
            }
        } else {
            // 处理标签行 [ar:艺术家]
            val tagMatch = TAG_SPLIT_PATTERN.find(line)
            if (tagMatch != null) {
                tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
            }
        }
    }

    return Pair(tags, lyricsData)
}
