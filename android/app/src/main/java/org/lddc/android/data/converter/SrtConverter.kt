// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.converter

import org.lddc.android.data.model.LyricsLine

/**
 * SRT 输出，对应上游 LDDC/core/converter/srt.py。
 */
object SrtConverter {

    private fun ms2srtTimestamp(ms: Int): String {
        val totalSeconds = ms / 1000
        val milli = ms % 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    fun convert(
        dict: Map<String, List<LyricsLine>>,
        langsMapping: Map<String, Map<Int, Int>>,
        langsOrder: List<String>,
        durationMs: Int?,
    ): String {
        val orig = dict["orig"] ?: return ""
        val stamps = LyricsConverter.lineTimestamps(orig, durationMs)
        val sb = StringBuilder()

        orig.forEachIndexed { index, origLine ->
            val (origStart, origEnd) = stamps[index]
            if (origStart == null || origEnd == null) return@forEachIndexed

            sb.append(index + 1).append('\n')
            sb.append(ms2srtTimestamp(origStart)).append(" --> ").append(ms2srtTimestamp(origEnd)).append('\n')
            LrcConverter.getLyricsLines(dict, langsOrder, index, origLine, langsMapping).forEach { (line, _) ->
                sb.append(line.words.filter { it.text.isNotEmpty() }.joinToString("") { it.text }).append('\n')
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
