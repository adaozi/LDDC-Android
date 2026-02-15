package com.example.lddc.service.parser

import android.annotation.SuppressLint
import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.Source

/**
 * LRC 歌词解析器
 *
 * 支持标准LRC格式及其扩展格式：
 * - 标准LRC：逐行时间戳 [mm:ss.xx]
 * - 增强型LRC：逐字时间戳 <mm:ss.xx>
 * - 多时间戳行：网易云音乐特殊格式
 *
 * 对应 Python 中的 core/parser/lrc.py
 */

/** 标签匹配模式：[ar:艺术家] */
private val TAG_SPLIT_PATTERN = Regex("^\\[(\\w+):([^]]*)]$")

/** 行时间戳匹配模式：[mm:ss.xx]歌词内容 */
private val LINE_SPLIT_PATTERN = Regex("^\\[(\\d+):(\\d+)\\.(\\d+)](.*)$")

/** 增强型逐字匹配模式：<mm:ss.xx>单词<mm:ss.xx> */
private val ENHANCED_WORD_SPLIT_PATTERN = Regex("<(\\d+):(\\d+)\\.(\\d+)>((?:(?!<\\d+:\\d+\\.\\d+>).)*)(?:<(\\d+):(\\d+)\\.(\\d+)>)?")

/** 单词分割模式 */
private val WORD_SPLIT_PATTERN = Regex("((?:(?!\\[\\d+:\\d+\\.\\d+]).)*)(?:\\[(\\d+):(\\d+)\\.(\\d+)])?")

/** 多时间戳行匹配模式（网易云特殊格式） */
private val MULTI_LINE_SPLIT_PATTERN = Regex("^((?:\\[\\d+:\\d+\\.\\d+]){2,})(.*)$")

/** 时间戳提取模式 */
private val TIMESTAMPS_PATTERN = Regex("\\[(\\d+):(\\d+)\\.(\\d+)]")

/**
 * 将时间字符串转换为毫秒
 *
 * @param minutes 分钟
 * @param seconds 秒
 * @param millis 毫秒（支持1-3位）
 * @return 总毫秒数
 */
private fun time2ms(minutes: String, seconds: String, millis: String): Long {
    val msLength = millis.length
    val msMultiplier = when (msLength) {
        1 -> 100
        2 -> 10
        3 -> 1
        else -> 1
    }
    return minutes.toLong() * 60 * 1000 + seconds.toLong() * 1000 + millis.toLong() * msMultiplier
}

/**
 * 将LRC文本解析为结构化歌词数据
 *
 * @param lrc LRC格式字符串
 * @param source 歌词来源平台（影响特殊格式处理）
 * @return Pair<标签字典, 歌词数据列表>
 *         标签：如[ar:艺术家]、[ti:标题]等
 *         歌词数据：按时间排序的歌词行列表
 */
fun lrc2data(lrc: String, source: Source? = null): Pair<Map<String, String>, List<LyricsData>> {
    val lrcLists = mutableListOf<LyricsData>(createLyricsData())
    val startTimeLists = mutableListOf<MutableList<Long?>>(mutableListOf())
    val tags = mutableMapOf<String, String>()

    /**
     * 添加歌词行到列表
     * 处理同一时间点可能有多个翻译版本的情况
     */
    fun addLine(line: LyricsLine) {
        for (i in lrcLists.indices) {
            if (line.start !in startTimeLists[i]) {
                if (line.start != null) {
                    lrcLists[i].add(line)
                    startTimeLists[i].add(line.start)
                }
                return
            }
        }
        // 如果有内容，创建新的列表（多语言版本）
        if (line.words.isNotEmpty() && line.words.any { it.text.isNotEmpty() }) {
            lrcLists.add(createLyricsData().apply { add(line) })
            startTimeLists.add(mutableListOf(line.start))
        }
    }

    lrc.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || !line.startsWith("[")) return@forEach

        // 处理多时间戳行（网易云音乐特殊格式）
        if (source == Source.NE) {
            val multiMatch = MULTI_LINE_SPLIT_PATTERN.find(line)
            if (multiMatch != null) {
                val timestamps = multiMatch.groupValues[1]
                val lineContent = multiMatch.groupValues[2]
                TIMESTAMPS_PATTERN.findAll(timestamps).forEach { tsMatch ->
                    val start = time2ms(tsMatch.groupValues[1], tsMatch.groupValues[2], tsMatch.groupValues[3])
                    addLine(LyricsLine(start, null, listOf(LyricsWord(start, null, lineContent))))
                }
                return@forEach
            }
        }

        // 处理普通歌词行
        val lineMatch = LINE_SPLIT_PATTERN.find(line)
        if (lineMatch != null) {
            val minutes = lineMatch.groupValues[1]
            val seconds = lineMatch.groupValues[2]
            val millis = lineMatch.groupValues[3]
            val lineContent = lineMatch.groupValues[4]
            val start = time2ms(minutes, seconds, millis)
            var end: Long? = null
            val words = mutableListOf<LyricsWord>()

            // 处理增强型LRC格式（逐字时间戳）
            if ("<" in lineContent && ">" in lineContent) {
                ENHANCED_WORD_SPLIT_PATTERN.findAll(lineContent).forEach { wordParts ->
                    val sMin = wordParts.groupValues[1]
                    val sSec = wordParts.groupValues[2]
                    val sMs = wordParts.groupValues[3]
                    val wordStr = wordParts.groupValues[4]
                    val eMin = wordParts.groupValues[5]
                    val eSec = wordParts.groupValues[6]
                    val eMs = wordParts.groupValues[7]

                    val wordStart = time2ms(sMin, sSec, sMs)
                    val wordEnd = if (eMin.isNotEmpty()) {
                        time2ms(eMin, eSec, eMs)
                    } else null

                    // 更新上一个字的结束时间为当前字的开始时间
                    if (words.isNotEmpty()) {
                        val lastWord = words.last()
                        words[words.size - 1] = LyricsWord(lastWord.start, wordStart, lastWord.text)
                    }

                    words.add(LyricsWord(wordStart, wordEnd, wordStr))
                    if (end == null || (wordEnd != null && wordEnd > end)) {
                        end = wordEnd
                    }
                }
            } else {
                // 标准LRC格式，整行作为一个单词
                words.add(LyricsWord(start, null, lineContent))
            }

            addLine(LyricsLine(start, end, words))
        } else {
            // 处理标签行 [ar:艺术家]
            val tagMatch = TAG_SPLIT_PATTERN.find(line)
            if (tagMatch != null) {
                tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
            }
        }
    }

    return Pair(tags, lrcLists)
}

/**
 * 将毫秒转换为LRC时间戳格式（3位毫秒）
 *
 * 格式：mm:ss.xxx
 *
 * @param ms 毫秒数
 * @return LRC格式时间戳（如"01:23.456"）
 */
@SuppressLint("DefaultLocale")
fun ms2formattime(ms: Long): String {
    val minutes = ms / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
}

/**
 * 将毫秒转换为LRC时间戳格式（2位毫秒，四舍五入）
 *
 * 格式：mm:ss.xx（毫秒四舍五入到百分秒）
 *
 * @param ms 毫秒数
 * @return LRC格式时间戳（如"01:23.46"）
 */
@SuppressLint("DefaultLocale")
fun ms2roundedtime(ms: Long): String {
    val minutes = ms / 60000
    val seconds = (ms % 60000) / 1000
    val millis = ms % 1000
    val rounded = (millis + 5) / 10  // 四舍五入到百分秒
    return String.format("%02d:%02d.%02d", minutes, seconds, rounded)
}

/**
 * 将歌词行转换为LRC格式字符串
 *
 * 根据指定的LRC格式（逐字/逐行/增强）生成对应格式的字符串
 *
 * @param lyricsLine 歌词行
 * @param lyricsFormat LRC格式类型
 * @param lineStartTime 行开始时间（可选，覆盖歌词行内的开始时间）
 * @param lineEndTime 行结束时间（可选，用于某些格式）
 * @param msConverter 毫秒转换函数（ms2formattime 或 ms2roundedtime）
 * @return LRC格式字符串
 */
fun lyricsLine2str(
    lyricsLine: LyricsLine,
    lyricsFormat: LyricsFormat,
    lineStartTime: Long? = null,
    lineEndTime: Long? = null,
    msConverter: (Long) -> String = ::ms2formattime
): String {
    val actualLineStartTime = lineStartTime ?: lyricsLine.start
    val actualLineEndTime = lineEndTime ?: lyricsLine.end
    val words = lyricsLine.words

    val text = StringBuilder()

    // 添加行首时间戳（标准LRC格式）
    if (actualLineStartTime != null) {
        text.append("[${msConverter(actualLineStartTime)}]")
    }

    when (lyricsFormat) {
        LyricsFormat.LINEBYLINELRC -> {
            // 逐行LRC：直接追加歌词文本
            text.append(words.joinToString("") { it.text })
            return text.toString()
        }
        LyricsFormat.VERBATIMLRC -> {
            // 逐字LRC：使用 [ ] 包裹时间戳
            val symbols = Pair("[", "]")
            // VERBATIMLRC 模式下，last_end 初始值为实际行开始时间（考虑覆盖值）
            var lastEnd: Long? = actualLineStartTime ?: lyricsLine.start

            for (word in words) {
                val (start, end, wordText) = word

                // 如果时间戳变化，添加新的时间戳
                if (start != null && start != lastEnd) {
                    val effectiveStart = maxOf(start, actualLineStartTime ?: start)
                    text.append("${symbols.first}${msConverter(effectiveStart)}${symbols.second}")
                }

                text.append(wordText)

                // 添加词的结束时间戳
                if (end != null) {
                    text.append("${symbols.first}${msConverter(end)}${symbols.second}")
                    lastEnd = end
                }
            }

            // 添加行结束时间戳（如果文本不以结束标记结尾）
            if (actualLineEndTime != null && !text.endsWith(symbols.second)) {
                text.append("${symbols.first}${msConverter(actualLineEndTime)}${symbols.second}")
            }
        }
        LyricsFormat.ENHANCEDLRC -> {
            // 增强型LRC：使用 < > 包裹时间戳
            val symbols = Pair("<", ">")
            // ENHANCEDLRC 模式下，last_end 初始值为 null
            var lastEnd: Long? = null

            for (word in words) {
                val (start, end, wordText) = word

                // 如果时间戳变化，添加新的时间戳
                if (start != null && start != lastEnd) {
                    val effectiveStart = maxOf(start, actualLineStartTime ?: start)
                    text.append("${symbols.first}${msConverter(effectiveStart)}${symbols.second}")
                }

                text.append(wordText)

                // 添加词的结束时间戳
                if (end != null) {
                    text.append("${symbols.first}${msConverter(end)}${symbols.second}")
                    lastEnd = end
                }
            }

            // 添加行结束时间戳（如果文本不以结束标记结尾）
            if (actualLineEndTime != null && !text.endsWith(symbols.second)) {
                text.append("${symbols.first}${msConverter(actualLineEndTime)}${symbols.second}")
            }
        }
    }

    return text.toString()
}
