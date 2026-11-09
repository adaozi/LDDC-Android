// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.converter

import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.model.LyricsType

/**
 * 歌词格式转换统一入口，对应上游 LDDC/core/converter/__init__.py 的 convert2。
 */
object LyricsConverter {

    /** 行级时间戳（可能为 null），对应 get_full_timestamps_lyrics_data(only_line=True, skip_none=False) */
    fun lineTimestamps(lines: List<LyricsLine>, durationMs: Int?): List<Pair<Int?, Int?>> {
        val result = ArrayList<Pair<Int?, Int?>>(lines.size)
        lines.forEachIndexed { i, line ->
            var start = line.start ?: line.words.firstOrNull()?.start
            var end = line.end ?: line.words.lastOrNull()?.end
            if (start == null) start = if (i == 0) 0 else lines[i - 1].end
            if (end == null) {
                end = if (i == lines.size - 1) {
                    durationMs?.takeIf { start != null && it >= start!! }
                } else {
                    lines[i + 1].start
                }
            }
            result.add(start to end)
        }
        return result
    }

    fun convert(
        lyrics: Lyrics,
        format: LyricsFormat,
        langs: List<String>? = null,
        offsetMs: Int = 0,
        msDigitCount: Int = 3,
        addEndTimestampLine: Boolean = false,
        lastRefLineTimeSty: Int = 0,
        tool: String? = null,
    ): String {
        if (langs.isNullOrEmpty()) return ""

        val dict = applyOffset(lyrics.data, offsetMs)
        val requested = langs.filter { it in dict }
        if (requested.isEmpty()) return ""

        val langsOrder = requested.toMutableList()
        val langsMapping = HashMap<String, Map<Int, Int>>()
        val orig = dict["orig"] ?: emptyList()
        langsOrder.filter { it != "orig" && it != "orig_lrc" }.forEach { lang ->
            langsMapping[lang] = LangAlign.findClosestMatch(
                data1 = orig,
                data2 = dict[lang] ?: emptyList(),
                data3 = dict["orig_lrc"],
                source = lyrics.info.source,
            )
        }

        var effectiveFormat = format
        if (lyrics.types["orig"] == LyricsType.PLAIN_TEXT && effectiveFormat.isLrc) {
            effectiveFormat = LyricsFormat.LINE_BY_LINE_LRC
        }

        return when (effectiveFormat) {
            LyricsFormat.VERBATIM_LRC, LyricsFormat.LINE_BY_LINE_LRC, LyricsFormat.ENHANCED_LRC ->
                LrcConverter.convert(
                    tags = lyrics.tags,
                    dict = dict,
                    format = effectiveFormat,
                    langsMapping = langsMapping,
                    langsOrder = langsOrder,
                    msDigitCount = msDigitCount,
                    addEndTimestampLine = addEndTimestampLine,
                    lastRefLineTimeSty = lastRefLineTimeSty,
                    tool = tool,
                )

            LyricsFormat.SRT -> SrtConverter.convert(dict, langsMapping, langsOrder, lyrics.infoDurationMs())
            LyricsFormat.ASS -> AssConverter.convert(lyrics, dict, langsMapping, langsOrder)
        }
    }

    private fun applyOffset(data: Map<String, List<LyricsLine>>, offsetMs: Int): Map<String, List<LyricsLine>> {
        if (offsetMs == 0) return data
        fun adjust(t: Int?): Int? = t?.let { maxOf(it + offsetMs, 0) }
        return data.mapValues { (_, lines) ->
            lines.map { line ->
                LyricsLine(
                    start = adjust(line.start),
                    end = adjust(line.end),
                    words = line.words.map { word -> word.copy(start = adjust(word.start), end = adjust(word.end)) },
                )
            }
        }
    }
}
