// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.model

/** 歌词中的一个字/词 */
data class LyricsWord(
    val start: Int? = null,
    val end: Int? = null,
    val text: String = "",
)

/** 歌词中的一行 */
data class LyricsLine(
    val start: Int? = null,
    val end: Int? = null,
    val words: List<LyricsWord> = emptyList(),
) {
    val text: String get() = words.joinToString("") { it.text }
}

/** 歌词类型，对应上游 LyricsType */
enum class LyricsType { PLAIN_TEXT, VERBATIM, LINE_BY_LINE }

/** 完整时间戳（无 null）的字 */
data class FsLyricsWord(val start: Int, val end: Int, val text: String)

/** 完整时间戳（无 null）的行 */
data class FsLyricsLine(val start: Int, val end: Int, val words: List<FsLyricsWord>) {
    val text: String get() = words.joinToString("") { it.text }
}

/**
 * 歌词对象：data 中按语言存放（orig / ts / roma），与上游 LyricsBase 对齐。
 */
class Lyrics(val info: LyricInfo) {

    val data: MutableMap<String, List<LyricsLine>> = LinkedHashMap()
    val types: MutableMap<String, LyricsType> = LinkedHashMap()
    val tags: MutableMap<String, String> = LinkedHashMap()

    operator fun get(lang: String): List<LyricsLine> = data[lang] ?: emptyList()

    operator fun set(lang: String, lines: List<LyricsLine>) {
        data[lang] = lines
        types[lang] = judgeLyricsType(lines)
    }

    fun has(lang: String): Boolean = get(lang).isNotEmpty()

    val original: List<LyricsLine> get() = get("orig")

    val isEmpty: Boolean get() = data.values.all { it.isEmpty() }

    val title: String? get() = info.songInfo.title ?: tags["ti"]
    val album: String? get() = info.songInfo.album ?: tags["al"]

    /**
     * 上游转换器使用的时长：只取 LyricInfo.duration（秒）×1000，
     * 不做"从歌词内容推算"的回退（否则末行时间戳会与 PC 版行为不一致）。
     */
    fun infoDurationMs(): Int? = info.duration?.times(1000)

    /** 歌词覆盖到的时长（毫秒） */
    fun durationMs(): Int {
        info.duration?.let { return it * 1000 }
        val orig = get("orig")
        if (orig.isNotEmpty()) {
            val last = orig.last()
            last.end?.let { return it }
            last.words.lastOrNull()?.let { w -> w.end?.let { return it } ?: w.start?.let { return it } }
            last.start?.let { return it }
        } else if (!isEmpty) {
            val last = data.values.firstOrNull { it.isNotEmpty() }?.last() ?: return 0
            last.end?.let { return it }
            last.words.lastOrNull()?.let { w -> w.end?.let { return it } ?: w.start?.let { return it } }
            last.start?.let { return it }
        }
        return 0
    }

    fun isInstrumental(): Boolean {
        if (info.songInfo.language == Language.INSTRUMENTAL) return true
        val orig = get("orig")
        if (orig.size in 1..9) {
            val text = orig.last().words.joinToString("") { it.text }.trim()
            if (text == "此歌曲为没有填词的纯音乐，请您欣赏" || text == "纯音乐，请欣赏") return true
        }
        return false
    }

    /**
     * 获取"完整时间戳"歌词：把缺失的行/字时间按上下文推算补齐，并丢弃无法推算的行。
     * 对应上游 get_full_timestamps_lyrics_data(data, duration, only_line=False, skip_none=True)
     */
    fun fsLyrics(durationMsOverride: Int? = null): Map<String, List<FsLyricsLine>> {
        val duration = durationMsOverride?.takeIf { it > 0 } ?: durationMs().takeIf { it > 0 }
        val result = LinkedHashMap<String, List<FsLyricsLine>>()
        data.forEach { (lang, lines) ->
            result[lang] = toFsLines(lines, duration)
        }
        return result
    }

    companion object {
        fun judgeLyricsType(lines: List<LyricsLine>): LyricsType {
            var type = LyricsType.PLAIN_TEXT
            for (line in lines) {
                if (line.words.size > 1) return LyricsType.VERBATIM
                if (line.start != null) type = LyricsType.LINE_BY_LINE
            }
            return type
        }

        fun toFsLines(lines: List<LyricsLine>, duration: Int?): List<FsLyricsLine> {
            val out = ArrayList<FsLyricsLine>(lines.size)
            lines.forEachIndexed { i, line ->
                var lineStart = line.start ?: line.words.firstOrNull()?.start
                var lineEnd = line.end ?: line.words.lastOrNull()?.end

                if (lineStart == null) lineStart = if (i == 0) 0 else lines[i - 1].end
                if (lineEnd == null) {
                    lineEnd = if (i == lines.size - 1) {
                        duration?.takeIf { lineStart != null && it >= lineStart!! }
                    } else {
                        lines[i + 1].start
                    }
                }

                if (lineStart == null || lineEnd == null) return@forEachIndexed

                val fsWords = ArrayList<FsLyricsWord>(line.words.size)
                line.words.forEachIndexed { j, word ->
                    val wordStart = word.start ?: if (j == 0) lineStart else line.words[j - 1].end
                    val wordEnd = word.end ?: if (j == line.words.size - 1) lineEnd else line.words[j + 1].start
                    if (wordStart != null && wordEnd != null) {
                        fsWords.add(FsLyricsWord(wordStart, wordEnd, word.text))
                    }
                }
                out.add(FsLyricsLine(lineStart, lineEnd, fsWords))
            }
            return out
        }

        fun instrumental(info: LyricInfo): Lyrics {
            val lyrics = Lyrics(info)
            lyrics["orig"] = listOf(LyricsLine(null, null, listOf(LyricsWord(null, null, "纯音乐，请欣赏"))))
            return lyrics
        }
    }
}

enum class Language {
    INSTRUMENTAL,
    OTHER,
    CHINESE,
    ENGLISH,
    JAPANESE,
    KOREAN,
}
