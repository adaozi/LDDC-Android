// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.model.LyricsType
import org.lddc.android.data.model.LyricsWord
import org.lddc.android.data.model.Source
import org.lddc.android.data.util.time2ms
import java.util.Base64

/**
 * 歌词解析器集合，逐一对应上游 LDDC/core/parser/ 下的实现：
 * lrc.py / yrc.py / qrc.py / krc.py / utils.py
 */
object LyricsParsers {

    private const val QRC_HEADER = "<Lyric_1 LyricType=\"1\" LyricContent=\""
    private const val KRC_MAGIC = "krc18"

    // ---------------- LRC ----------------

    private val TAG_PATTERN = Regex("""^\[(\w+):([^\]]*)\]$""")
    private val LINE_PATTERN = Regex("""^\[(\d+):(\d+)\.(\d+)\](.*)$""")
    private val ENHANCED_WORD_PATTERN =
        Regex("""<(\d+):(\d+)\.(\d+)>((?:(?!<\d+:\d+\.\d+>).)*)(?:<(\d+):(\d+)\.(\d+)>$)?""")
    private val WORD_PATTERN = Regex("""((?:(?!\[\d+:\d+\.\d+\]).)*)(?:\[(\d+):(\d+)\.(\d+)\])?""")
    private val MULTI_LINE_PATTERN = Regex("""^((?:\[\d+:\d+\.\d+\]){2,})(.*)$""")
    private val TIMESTAMPS_PATTERN = Regex("""\[(\d+):(\d+)\.(\d+)\]""")

    /**
     * 将 LRC / 增强 LRC / 逐字 LRC / 网易云非标准 LRC 解析为多份歌词数据。
     * 返回 (标签, 按出现顺序切分出的多组歌词行)。
     */
    fun lrc2ListData(lrc: String, source: Source? = null): Pair<Map<String, String>, List<List<LyricsLine>>> {
        val lines = mutableListOf<MutableList<LyricsLine>>(mutableListOf())
        val startTimes = mutableListOf<MutableList<Int?>>(mutableListOf())
        val tags = LinkedHashMap<String, String>()

        fun addLine(line: LyricsLine) {
            for (i in lines.indices) {
                if (line.start !in startTimes[i]) {
                    if (line.start != null) {
                        lines[i].add(line)
                        startTimes[i].add(line.start)
                    }
                    return
                }
            }
            if (line.words.isNotEmpty()) {
                lines.add(mutableListOf(line))
                startTimes.add(mutableListOf(line.start))
            }
        }

        lrc.splitToSequence("\n").forEach { rawLine ->
            val line = rawLine.trim('\r', ' ', '\t')
            if (line.isEmpty() || !line.startsWith("[")) return@forEach

            val lineMatch = LINE_PATTERN.find(line)
            if (lineMatch != null) {
                val (m, s, ms, contentRaw) = lineMatch.destructured
                var start = time2ms(m, s, ms)
                var end: Int? = null
                var content = contentRaw

                if (source == Source.NE) {
                    val multi = MULTI_LINE_PATTERN.find(line)
                    if (multi != null) {
                        val timestamps = multi.groupValues[1]
                        content = multi.groupValues[2]
                        TIMESTAMPS_PATTERN.findAll(timestamps).forEach { ts ->
                            val tsStart = time2ms(ts.groupValues[1], ts.groupValues[2], ts.groupValues[3])
                            addLine(LyricsLine(tsStart, null, listOf(LyricsWord(tsStart, null, content))))
                        }
                        return@forEach
                    }
                }

                val words = mutableListOf<LyricsWord>()
                if (content.contains("<") && content.contains(">")) {
                    ENHANCED_WORD_PATTERN.findAll(content).forEach { match ->
                        val g = match.groupValues
                        val wordStart = time2ms(g[1], g[2], g[3])
                        val wordEnd = if (g[5].isNotEmpty() && g[6].isNotEmpty() && g[7].isNotEmpty()) {
                            time2ms(g[5], g[6], g[7])
                        } else {
                            null
                        }
                        if (wordEnd != null) end = wordEnd
                        if (words.isNotEmpty()) {
                            val prev = words.last()
                            words[words.size - 1] = prev.copy(end = wordStart)
                        }
                        if (g[4].isNotEmpty()) {
                            words.add(LyricsWord(wordStart, wordEnd, g[4]))
                        }
                    }
                } else {
                    val parts = WORD_PATTERN.findAll(content).map { it.groupValues }.toList()
                    if (parts.isNotEmpty()) {
                        parts.forEachIndexed { index, g ->
                            val wordStart = if (words.isEmpty()) start else words.last().end
                            val wordEnd = if (g[2].isNotEmpty() && g[3].isNotEmpty() && g[4].isNotEmpty()) {
                                time2ms(g[2], g[3], g[4])
                            } else {
                                null
                            }
                            if (index == parts.size - 1 && wordEnd != null) end = wordEnd
                            if (g[1].isNotEmpty()) {
                                words.add(LyricsWord(wordStart, wordEnd, g[1]))
                            }
                        }
                    }
                }

                addLine(LyricsLine(start, end, words))
                return@forEach
            }

            val tagMatch = TAG_PATTERN.find(line)
            if (tagMatch != null) {
                tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
            }
        }

        val result = lines.map { list ->
            list.filter { it.start != null }.sortedBy { it.start }
                .toMutableList()
                .also { sorted ->
                    for (i in 1 until sorted.size) {
                        if (sorted[i - 1].end == null && sorted[i].start != null) {
                            sorted[i - 1] = sorted[i - 1].copy(end = sorted[i].start)
                        }
                    }
                }
                .filter { it.words.isNotEmpty() }
        }
        return tags to result
    }

    /** LRC → multi-language data（多个时间戳序列分开的歌词） */
    fun lrc2mdata(lrc: String, source: Source? = null): Pair<Map<String, String>, Map<String, List<LyricsLine>>> {
        val (tags, lists) = lrc2ListData(lrc, source)
        if (lists.isEmpty()) return tags to emptyMap()
        if (lists.size == 1) return tags to linkedMapOf("orig" to lists[0])
        if (lists.size == 2) {
            return if (Lyrics.judgeLyricsType(lists[0]) == LyricsType.VERBATIM &&
                Lyrics.judgeLyricsType(lists[1]) == LyricsType.VERBATIM
            ) {
                tags to linkedMapOf("roma" to lists[0], "orig" to lists[1])
            } else {
                tags to linkedMapOf("orig" to lists[0], "ts" to lists[1])
            }
        }
        return tags to linkedMapOf("roma" to lists[0], "orig" to lists[1], "ts" to lists[2])
    }

    /** LRC → 单份歌词（多语言按时间戳交错合并） */
    fun lrc2data(lrc: String, source: Source? = null): Pair<Map<String, String>, List<LyricsLine>> {
        val (tags, lists) = lrc2ListData(lrc, source)
        if (lists.isEmpty()) return tags to emptyList()
        val merged = lists[0].toMutableList()
        for (i in 1 until lists.size) {
            for (line in lists[i]) {
                val idx = merged.indexOfLast { it.start == line.start }
                if (idx >= 0) merged.add(idx + 1, line)
            }
        }
        return tags to merged
    }

    // ---------------- YRC（网易逐字） ----------------

    private val YRC_LINE = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val YRC_WORD = Regex("""(?:\[\d+,\d+\])?\((\d+),(\d+),\d+\)((?:.(?!\d+,\d+,\d+\)))*)""")

    fun yrc2data(yrc: String): List<LyricsLine> {
        val out = ArrayList<LyricsLine>()
        yrc.splitToSequence("\n").forEach { raw ->
            val line = raw.trim('\r', ' ', '\t')
            if (!line.startsWith("[")) return@forEach
            val match = YRC_LINE.find(line) ?: return@forEach
            val lineStart = match.groupValues[1].toInt()
            val lineEnd = lineStart + match.groupValues[2].toInt()
            val content = match.groupValues[3]

            val words = YRC_WORD.findAll(content).map { m ->
                val start = m.groupValues[1].toInt()
                LyricsWord(start, start + m.groupValues[2].toInt(), m.groupValues[3])
            }.toList()

            out.add(LyricsLine(lineStart, lineEnd, words.ifEmpty { listOf(LyricsWord(lineStart, lineEnd, content)) }))
        }
        return out
    }

    // ---------------- QRC（QQ 逐字） ----------------

    private val QRC_CONTENT = Regex("""<Lyric_1 LyricType="1" LyricContent="(.*?)"/>""", RegexOption.DOT_MATCHES_ALL)
    private val QRC_LINE = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val QRC_WORD = Regex("""(?:\[\d+,\d+\])?((?:(?!\(\d+,\d+\)).)*)\((\d+),(\d+)\)""")
    private val QRC_TIMESTAMP_ONLY = Regex("""^\(\d+,\d+\)$""")

    fun qrc2data(qrc: String): Pair<Map<String, String>, List<LyricsLine>> {
        val contentMatch = QRC_CONTENT.find(qrc) ?: return emptyMap<String, String>() to emptyList()
        val tags = LinkedHashMap<String, String>()
        val out = ArrayList<LyricsLine>()

        contentMatch.groupValues[1].splitToSequence("\n").forEach { raw ->
            val line = raw.trim('\r', ' ', '\t')
            val lineMatch = QRC_LINE.find(line)
            if (lineMatch != null) {
                val lineStart = lineMatch.groupValues[1].toInt()
                val lineEnd = lineStart + lineMatch.groupValues[2].toInt()
                val content = lineMatch.groupValues[3]
                if (content.startsWith("(") && content.endsWith(")") && QRC_TIMESTAMP_ONLY.matches(content)) {
                    out.add(LyricsLine(lineStart, lineEnd, emptyList()))
                    return@forEach
                }
                val words = QRC_WORD.findAll(content).filter { it.groupValues[1] != "\r" }.map { m ->
                    val start = m.groupValues[2].toInt()
                    LyricsWord(start, start + m.groupValues[3].toInt(), m.groupValues[1])
                }.toList()
                out.add(LyricsLine(lineStart, lineEnd, words.ifEmpty { listOf(LyricsWord(lineStart, lineEnd, content)) }))
            } else {
                val tagMatch = TAG_PATTERN.find(line)
                if (tagMatch != null) tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
            }
        }
        return tags to out
    }

    /** QQ 返回的歌词可能是 QRC，也可能是普通 LRC / 纯文本 */
    fun qrcStrParse(lyric: String): Pair<Map<String, String>, List<LyricsLine>> {
        if (QRC_CONTENT.containsMatchIn(lyric)) return qrc2data(lyric)
        if (lyric.contains("[") && lyric.contains("]")) {
            val (tags, data) = lrc2data(lyric)
            if (data.isNotEmpty()) return tags to data
        }
        return emptyMap<String, String>() to plaintext2data(lyric)
    }

    // ---------------- KRC（酷狗逐字） ----------------

    private val KRC_LINE = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val KRC_WORD = Regex("""(?:\[\d+,\d+\])?<(\d+),(\d+),\d+>((?:.(?!\d+,\d+,\d+>))*)""")
    private val json = Json { ignoreUnknownKeys = true }

    fun krc2mdata(krc: String): Pair<Map<String, String>, Map<String, List<LyricsLine>>> {
        val tags = LinkedHashMap<String, String>()
        val orig = ArrayList<LyricsLine>()
        val roma = ArrayList<LyricsLine>()
        val ts = ArrayList<LyricsLine>()

        krc.splitToSequence("\n").forEach { raw ->
            val line = raw.trim('\r', ' ', '\t')
            if (!line.startsWith("[")) return@forEach

            val tagMatch = TAG_PATTERN.find(line)
            if (tagMatch != null) {
                tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
                return@forEach
            }

            val lineMatch = KRC_LINE.find(line) ?: return@forEach
            val lineStart = lineMatch.groupValues[1].toInt()
            val lineEnd = lineStart + lineMatch.groupValues[2].toInt()
            val content = lineMatch.groupValues[3]
            val words = KRC_WORD.findAll(content).map { m ->
                val start = lineStart + m.groupValues[1].toInt()
                LyricsWord(start, start + m.groupValues[2].toInt(), m.groupValues[3])
            }.toList()
            orig.add(LyricsLine(lineStart, lineEnd, words.ifEmpty { listOf(LyricsWord(lineStart, lineEnd, content)) }))
        }

        val languageTag = tags["language"]?.trim().orEmpty()
        if (languageTag.isNotEmpty()) {
            runCatching {
                val decoded = String(Base64.getDecoder().decode(languageTag), Charsets.UTF_8)
                val root = json.parseToJsonElement(decoded) as JsonObject
                val content = root["content"]?.jsonArray ?: JsonArray(emptyList())
                content.forEach { element ->
                    val item = element as JsonObject
                    val type = item["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                    val lyricContent = item["lyricContent"]?.jsonArray ?: return@forEach
                    if (type == 0) {
                        var offset = 0
                        orig.forEachIndexed { i, line ->
                            if (line.words.all { it.text.isEmpty() }) {
                                offset++
                                return@forEachIndexed
                            }
                            val row = lyricContent.getOrNull(i - offset)?.jsonArray ?: return@forEachIndexed
                            val mappedWords = line.words.mapIndexed { j, word ->
                                LyricsWord(word.start, word.end, row.getOrNull(j)?.jsonPrimitive?.content ?: "")
                            }
                            roma.add(LyricsLine(line.start, line.end, mappedWords))
                        }
                    } else if (type == 1) {
                        orig.forEachIndexed { i, line ->
                            val row = lyricContent.getOrNull(i)?.jsonArray ?: return@forEachIndexed
                            val text = row.firstOrNull()?.jsonPrimitive?.content ?: ""
                            ts.add(LyricsLine(line.start, line.end, listOf(LyricsWord(line.start, line.end, text))))
                        }
                    }
                }
            }
        }

        val result = LinkedHashMap<String, List<LyricsLine>>()
        if (orig.isNotEmpty()) result["orig"] = orig
        if (roma.isNotEmpty()) result["roma"] = roma
        if (ts.isNotEmpty()) result["ts"] = ts
        return tags to result
    }

    // ---------------- 纯文本 ----------------

    fun plaintext2data(text: String): List<LyricsLine> =
        text.splitToSequence("\n").map { raw ->
            val line = raw.trimEnd('\r')
            LyricsLine(null, null, listOf(LyricsWord(null, null, line)))
        }.toList()

    /**
     * 统一入口：按内容特征自动识别格式。
     * 对应上游各 API 里 judge_lyrics_type + xxx2data 的组合调用。
     */
    fun parse(raw: String, source: Source? = null): Pair<Map<String, String>, Map<String, List<LyricsLine>>> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyMap<String, String>() to emptyMap()

        if (text.contains(KRC_MAGIC)) {
            return krc2mdata(text.substringAfter(KRC_MAGIC))
        }
        if (text.startsWith(QRC_HEADER) || QRC_CONTENT.containsMatchIn(text)) {
            val (tags, lines) = qrc2data(text)
            return tags to (if (lines.isEmpty()) emptyMap() else mapOf("orig" to lines))
        }
        // YRC：形如 [start,duration](start,duration,0)字...
        if (text.contains("](") && text.lineSequence().any { YRC_LINE.containsMatchIn(it.trim()) }) {
            val lines = yrc2data(text)
            if (lines.isNotEmpty()) return emptyMap<String, String>() to mapOf("orig" to lines)
        }
        // LRC / 增强 LRC / 逐字 LRC
        if (text.lineSequence().any { LINE_PATTERN.matches(it.trim()) }) {
            return lrc2mdata(text, source)
        }
        return emptyMap<String, String>() to mapOf("orig" to plaintext2data(text))
    }
}
