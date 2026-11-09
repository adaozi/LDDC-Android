// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.converter

import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.util.formatTimeSub1
import org.lddc.android.data.util.ms2formatTime
import org.lddc.android.data.util.ms2roundedTime

/**
 * LRC 输出，对应上游 LDDC/core/converter/lrc.py + share.py。
 */
object LrcConverter {

    private val TAG_WHITELIST = setOf("al", "ar", "au", "by", "offset", "ti")

    /** 同一句歌词所有语言版本的行，(行, 是否需要行末减1时间戳) */
    fun getLyricsLines(
        dict: Map<String, List<LyricsLine>>,
        langsOrder: List<String>,
        origIndex: Int,
        origLine: LyricsLine,
        langsMapping: Map<String, Map<Int, Int>>,
        lastRefLineTimeSty: Int = 0,
    ): List<Pair<LyricsLine, Boolean>> {
        val result = ArrayList<Pair<LyricsLine, Boolean>>(langsOrder.size)
        langsOrder.forEachIndexed { i, lang ->
            val line = if (lang == "orig") {
                origLine
            } else {
                val index = langsMapping[lang]?.get(origIndex) ?: return@forEachIndexed
                dict[lang]?.getOrNull(index) ?: return@forEachIndexed
            }
            val content = line.words.joinToString("") { it.text }.let { if (it.isEmpty()) "" else it }
            val visible = line.words.filter { it.text != "" }.joinToString("") { it.text }
            if (LangAlign.hasContent(visible.ifEmpty { content })) {
                val lastSub1 = lastRefLineTimeSty == 1 && i == langsOrder.size - 1 && line.words.size == 1
                result.add(line to lastSub1)
            }
        }
        return result
    }

    fun lyricsLine2Str(
        line: LyricsLine,
        format: LyricsFormat,
        lineStartTime: Int? = null,
        lineEndTime: Int? = null,
        msConverter: (Int) -> String = ::ms2formatTime,
    ): String {
        val start = lineStartTime ?: line.start
        val end = if (line.end == null) lineEndTime else line.end

        var text = if (start != null) "[${msConverter(start)}]" else ""

        if (format == LyricsFormat.LINE_BY_LINE_LRC) {
            return text + line.words.filter { it.text.isNotEmpty() }.joinToString("") { it.text }
        }

        val open = if (format == LyricsFormat.VERBATIM_LRC) "[" else "<"
        val close = if (format == LyricsFormat.VERBATIM_LRC) "]" else ">"

        var lastEnd: Int? = if (format == LyricsFormat.VERBATIM_LRC) line.start else null
        for (word in line.words) {
            val wordStart = word.start
            if (wordStart != null && wordStart != lastEnd) {
                text += open + msConverter(maxOf(wordStart, start ?: wordStart)) + close
            }
            text += word.text
            if (word.end != null) {
                text += open + msConverter(word.end) + close
            }
            lastEnd = word.end
        }
        if (end != null && !text.endsWith(close)) {
            text += open + msConverter(end) + close
        }
        return text
    }

    /**
     * @param msDigitCount 时间戳毫秒位数：3=毫秒，2=厘秒（对应上游 lrc_ms_digit_count）
     * @param addEndTimestampLine 逐行 LRC 是否在句末追加空时间戳行
     * @param lastRefLineTimeSty 0=与当前原文起始时间相同，1=与下一行原文起始时间接近
     */
    fun convert(
        tags: Map<String, String>,
        dict: Map<String, List<LyricsLine>>,
        format: LyricsFormat,
        langsMapping: Map<String, Map<Int, Int>>,
        langsOrder: List<String>,
        msDigitCount: Int = 3,
        addEndTimestampLine: Boolean = false,
        lastRefLineTimeSty: Int = 0,
        tool: String? = null,
    ): String {
        val msConverter: (Int) -> String = if (msDigitCount == 2) ::ms2roundedTime else ::ms2formatTime

        val header = buildString {
            if (tags.isNotEmpty()) {
                val tagLines = tags.entries
                    .filter { it.key in TAG_WHITELIST && it.value.isNotEmpty() }
                    .joinToString("\n") { "[${it.key}:${it.value}]" }
                if (tagLines.isNotEmpty()) append(tagLines).append("\n")
                if (tool != null) append("[tool:$tool]\n")
                append("\n")
            }
        }

        val orig = dict["orig"] ?: return header
        val sb = StringBuilder(header)

        orig.forEachIndexed { origIndex, origLine ->
            val lineStartTime = origLine.words.firstOrNull()?.start ?: origLine.start
            val lineEndTime = origLine.words.lastOrNull()?.end ?: origLine.end

            for ((lyricsLine, lastSub1) in getLyricsLines(dict, langsOrder, origIndex, origLine, langsMapping, lastRefLineTimeSty)) {
                if (!lastSub1) {
                    sb.append(lyricsLine2Str(lyricsLine, format, lineStartTime, lineEndTime, msConverter)).append('\n')
                } else {
                    val nextStart = when {
                        origIndex + 1 < orig.size -> orig[origIndex + 1].words.firstOrNull()?.start ?: orig[origIndex + 1].start
                        lineEndTime != null -> lineEndTime + 10
                        lineStartTime != null -> lineStartTime + 10
                        else -> null
                    }
                    val formatted = nextStart?.let { formatTimeSub1(msConverter(it)) } ?: ""
                    sb.append(if (formatted.isEmpty()) "" else "[$formatted]")
                    sb.append(lyricsLine.words.filter { it.text.isNotEmpty() }.joinToString("") { it.text })
                    when {
                        formatted.isNotEmpty() && format == LyricsFormat.VERBATIM_LRC -> sb.append("[$formatted]")
                        formatted.isNotEmpty() && format == LyricsFormat.ENHANCED_LRC -> sb.append("<$formatted>")
                    }
                    sb.append('\n')
                }
            }

            if (format == LyricsFormat.LINE_BY_LINE_LRC && addEndTimestampLine && lineEndTime != null &&
                (origIndex == orig.size - 1 || orig[origIndex + 1].start != lineEndTime)
            ) {
                sb.append("[${msConverter(lineEndTime)}]\n")
            }
        }
        return sb.toString()
    }
}
