package com.example.lddc.service.converter

import com.example.lddc.model.LyricsFormat
import com.example.lddc.service.parser.LyricsData
import com.example.lddc.service.parser.LyricsLine
import com.example.lddc.service.parser.LyricsWord
import com.example.lddc.service.parser.MultiLyricsData
import com.example.lddc.service.parser.createLyricsData
import com.example.lddc.service.parser.lyricsLine2str
import com.example.lddc.service.parser.ms2formattime
import com.example.lddc.service.parser.ms2roundedtime

/**
 * LRC 歌词转换器
 *
 * 将解析后的歌词数据转换为标准LRC格式字符串
 * 支持多种LRC格式：
 * - VERBATIMLRC: 逐字LRC（精确到每个字的时间戳）
 * - LINEBYLINELRC: 逐行LRC（标准格式）
 * - ENHANCEDLRC: 增强型LRC（支持额外元数据）
 *
 * 对应 Python 中的 core/converter/lrc.py
 */

/**
 * 将时间戳减1毫秒（用于某些特殊格式处理）
 *
 * @param formattime 格式化时间字符串 (mm:ss.xx)
 * @return 减1后的时间字符串
 */
fun formatTimeSub1(formattime: String): String {
    val parts = formattime.split(":", ".")
    if (parts.size != 3) return formattime

    var m = parts[0]
    var s = parts[1]
    val ms = parts[2]
    val msLen = ms.length

    return when {
        ms != "00" && ms != "000" -> {
            val newMs = (ms.toInt() - 1).toString().padStart(msLen, '0')
            "$m:$s.$newMs"
        }

        s != "00" -> {
            s = (s.toInt() - 1).toString().padStart(2, '0')
            "$m:$s.${"0".repeat(msLen)}"
        }

        m != "00" -> {
            m = (m.toInt() - 1).toString().padStart(2, '0')
            "$m:59.${"0".repeat(msLen)}"
        }

        else -> formattime
    }
}

/**
 * 将多语言歌词数据转换为LRC格式字符串
 *
 * @param tags 标签字典（ar:艺术家, ti:标题, al:专辑等）
 * @param lyricsDict 多语言歌词数据（orig原文, ts翻译, roma罗马音）
 * @param lyricsFormat 输出格式（逐字/逐行/增强）
 * @param langsMapping 语言行号映射（处理原文和翻译行数不一致的情况）
 * @param langsOrder 语言输出顺序
 * @param version 版本号
 * @param lrcMsDigitCount 毫秒位数 (2或3)
 * @param addEndTimestampLine 是否添加结束时间戳行
 * @param lastRefLineTimeSty 最后参考行时间样式
 * @return LRC格式字符串
 */
fun lrcConverter(
    tags: Map<String, String>,
    lyricsDict: MultiLyricsData,
    lyricsFormat: LyricsFormat,
    langsMapping: Map<String, Map<Int, Int>>,
    langsOrder: List<String>,
    version: String = "1.0",
    lrcMsDigitCount: Int = 3,
    addEndTimestampLine: Boolean = false,
    lastRefLineTimeSty: Int = 0
): String {
    val msConverter = if (lrcMsDigitCount == 2) ::ms2roundedtime else ::ms2formattime

    // 构建LRC文本
    val lrcText = StringBuilder()

    // 添加标准标签
    val validTags = listOf("al", "ar", "au", "by", "offset", "ti")
    tags.filter { it.key in validTags && it.value.isNotEmpty() }
        .forEach { (k, v) ->
            lrcText.appendLine("[$k:$v]")
        }
    lrcText.appendLine("[tool:LDDC $version https://github.com/chenmozhijin/LDDC]")
    lrcText.appendLine()

    val origLyrics = lyricsDict["orig"] ?: return lrcText.toString()

    // 遍历每一行原文歌词
    for (origIndex in origLyrics.indices) {
        val origLine = origLyrics[origIndex]

        // 确定行开始时间（优先使用第一个词的开始时间）
        val lineStartTime = if (origLine.words.isNotEmpty() && origLine.words[0].start != null) {
            origLine.words[0].start
        } else {
            origLine.start
        }
        // 确定行结束时间（优先使用最后一个词的结束时间）
        val lineEndTime = if (origLine.words.isNotEmpty() && origLine.words.last().end != null) {
            origLine.words.last().end
        } else {
            origLine.end
        }

        // 获取各语言的歌词行
        val lyricsLines = getLyricsLines(
            lyricsDict,
            langsOrder,
            origIndex,
            origLine,
            langsMapping,
            lastRefLineTimeSty
        )

        for ((lyricsLine, lastSub1) in lyricsLines) {
            if (!lastSub1) {
                // 普通行：转换为标准LRC格式
                val line = lyricsLine2str(
                    lyricsLine,
                    lyricsFormat,
                    lineStartTime,
                    lineEndTime,
                    msConverter
                )
                lrcText.appendLine(line)
            } else {
                // lastSub1 特殊处理（用于翻译行的特殊格式）
                val tsSub1StartTime = when {
                    origIndex + 1 < origLyrics.size -> {
                        val nextLine = origLyrics[origIndex + 1]
                        if (nextLine.words.isNotEmpty() && nextLine.words[0].start != null) {
                            nextLine.words[0].start
                        } else {
                            nextLine.start
                        }
                    }

                    lineEndTime != null -> lineEndTime + 10
                    lineStartTime != null -> lineStartTime + 10
                    else -> null
                }
                val tsSub1FormatTime =
                    tsSub1StartTime?.let { formatTimeSub1(msConverter(it)) } ?: ""

                val text = if (tsSub1FormatTime.isNotEmpty()) "[$tsSub1FormatTime]" else ""
                lrcText.append(text)
                lrcText.append(lyricsLine.words.joinToString("") { it.text })
                if (tsSub1FormatTime.isNotEmpty()) {
                    when (lyricsFormat) {
                        LyricsFormat.VERBATIMLRC -> lrcText.append("[$tsSub1FormatTime]")
                        LyricsFormat.ENHANCEDLRC -> lrcText.append("<$tsSub1FormatTime>")
                        else -> {}
                    }
                }
                lrcText.appendLine()
            }
        }

        // 添加结束时间戳行（仅逐行LRC格式）
        if (lyricsFormat == LyricsFormat.LINEBYLINELRC
            && addEndTimestampLine
            && lineEndTime != null
            && (origIndex == origLyrics.size - 1 || origLyrics[origIndex + 1].start != lineEndTime)
        ) {
            lrcText.appendLine("[${msConverter(lineEndTime)}]")
        }
    }

    return lrcText.toString().trim()
}

/**
 * 获取歌词行（按语言顺序）
 *
 * 根据语言顺序获取对应的歌词行，处理原文和翻译行数不一致的情况
 *
 * @param lyricsDict 多语言歌词数据
 * @param langsOrder 语言顺序列表
 * @param origIndex 原文行索引
 * @param origLine 原文行
 * @param langsMapping 语言行号映射
 * @param lastRefLineTimeSty 最后参考行时间样式
 * @return List of Pair<LyricsLine, lastSub1> 其中 lastSub1 表示是否是最后一行参考行时间样式
 */
fun getLyricsLines(
    lyricsDict: MultiLyricsData,
    langsOrder: List<String>,
    origIndex: Int,
    origLine: LyricsLine,
    langsMapping: Map<String, Map<Int, Int>>,
    lastRefLineTimeSty: Int = 0
): List<Pair<LyricsLine, Boolean>> {
    val result = mutableListOf<Pair<LyricsLine, Boolean>>()

    for ((i, lang) in langsOrder.withIndex()) {
        val lyricsLine = when {
            lang == "orig" -> origLine
            else -> {
                val lyricsList = lyricsDict[lang]
                if (lyricsList == null || lyricsList.isEmpty()) continue

                // 从映射中获取索引，如果没有映射则跳过
                val mapping = langsMapping[lang]
                val index = mapping?.get(origIndex)
                if (index == null) continue

                if (index < lyricsList.size) lyricsList[index] else null
            }
        } ?: continue

        // 检查是否有内容（过滤空文本）
        val hasContent = lyricsLine.words.any { it.text.isNotEmpty() }
        if (!hasContent) continue

        // 判断是否是 lastSub1 行（最后一行参考行时间样式）
        val isLastSub1 =
            lastRefLineTimeSty == 1 && i == langsOrder.size - 1 && lyricsLine.words.size == 1

        result.add(Pair(lyricsLine, isLastSub1))
    }

    return result
}

/**
 * 获取完整时间戳的歌词数据
 *
 * 为没有结束时间的行添加结束时间（根据下一行开始时间或歌曲时长）
 *
 * @param lyricsData 歌词数据
 * @param duration 歌曲时长（毫秒）
 * @param onlyLine 是否只处理行级别（不处理逐字）
 * @return 补充完整时间戳的歌词数据
 */
fun getFullTimestampsLyricsData(
    lyricsData: LyricsData,
    duration: Long? = null,
    onlyLine: Boolean = false
): LyricsData {
    val result = createLyricsData()

    for (i in lyricsData.indices) {
        val line = lyricsData[i]
        val nextLine = if (i + 1 < lyricsData.size) lyricsData[i + 1] else null

        // 确定行结束时间
        val lineEnd = line.end ?: nextLine?.start ?: duration

        if (onlyLine || line.words.size <= 1) {
            // 整行处理
            result.add(LyricsLine(line.start, lineEnd, line.words))
        } else {
            // 逐字处理
            val newWords = mutableListOf<LyricsWord>()
            for (j in line.words.indices) {
                val word = line.words[j]
                val nextWord = if (j + 1 < line.words.size) line.words[j + 1] else null
                val wordEnd = word.end ?: nextWord?.start ?: lineEnd
                newWords.add(LyricsWord(word.start, wordEnd, word.text))
            }
            result.add(LyricsLine(line.start, lineEnd, newWords))
        }
    }

    return result
}
