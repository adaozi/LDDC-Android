package com.example.lddc.service.parser

/**
 * YRC 歌词解析器（网易云音乐）
 *
 * YRC格式特点：
 * - 网易云音乐PC客户端使用的逐字歌词格式
 * - 行格式：[开始时间,持续时间]内容
 * - 逐字格式：(相对开始时间,持续时间,0)单词
 * - 时间单位：毫秒（行时间是绝对时间，字时间是相对行开始）
 * - 与QRC类似，但使用圆括号而非尖括号
 *
 * 对应 Python 中的 core/parser/yrc.py
 */

/** 行时间戳匹配模式：[开始时间,持续时间] */
private val LINE_SPLIT_PATTERN = Regex("^\\[(\\d+),(\\d+)](.*)$")

/** 逐字时间戳匹配模式：(相对开始时间,持续时间,0)单词 */
private val WORD_SPLIT_PATTERN = Regex("(?:\\[\\d+,\\d+])?\\((?<start>\\d+),(?<duration>\\d+),\\d+\\)(?<content>(?:.(?!\\d+,\\d+,\\d+\\)))*)")

/**
 * 将YRC文本解析为结构化歌词数据
 *
 * @param yrc YRC格式字符串
 * @return 歌词数据（逐行/逐字的歌词结构）
 */
fun yrc2data(yrc: String): LyricsData {
    val lyricsData = createLyricsData()

    yrc.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (!line.startsWith("[")) return@forEach

        // 解析行时间戳
        val lineMatch = LINE_SPLIT_PATTERN.find(line) ?: return@forEach

        val lineStart = lineMatch.groupValues[1].toLong()
        val lineDuration = lineMatch.groupValues[2].toLong()
        val lineEnd = lineStart + lineDuration
        val lineContent = lineMatch.groupValues[3]

        // 解析逐字时间戳
        val words = WORD_SPLIT_PATTERN.findAll(lineContent)
            .map { wordMatch ->
                val wordStart = wordMatch.groupValues[1].toLong()
                val wordDuration = wordMatch.groupValues[2].toLong()
                val wordContent = wordMatch.groupValues[3]

                // 字时间是相对于行开始时间的偏移
                LyricsWord(
                    lineStart + wordStart,
                    lineStart + wordStart + wordDuration,
                    wordContent
                )
            }
            .toList()

        if (words.isEmpty()) {
            // 无逐字信息，整行作为一个单词
            lyricsData.add(LyricsLine(lineStart, lineEnd, listOf(LyricsWord(lineStart, lineEnd, lineContent))))
        } else {
            lyricsData.add(LyricsLine(lineStart, lineEnd, words))
        }
    }

    return lyricsData
}
