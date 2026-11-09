// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.converter

import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.model.Source

/**
 * 多语言歌词行对齐，对应上游 LDDC/core/algorithm.py 的 is_same_line / find_closest_match。
 */
object LangAlign {

    private val CLEAN_PATTERN = Regex("""[(（][^）)]*[）)]|\s+""")

    private val INSTRUMENTAL_TEXTS = setOf("此歌曲为没有填词的纯音乐，请您欣赏", "纯音乐，请欣赏")

    fun isSameLine(line1: LyricsLine, line2: LyricsLine): Boolean {
        val s1 = line1.text
        val s2 = line2.text
        if (s1 == s2) return true
        val c1 = CLEAN_PATTERN.replace(s1, "")
        val c2 = CLEAN_PATTERN.replace(s2, "")
        return c1 == c2 && c1.isNotEmpty()
    }

    fun findClosestMatch(
        data1: List<LyricsLine>,
        data2: List<LyricsLine>,
        data3: List<LyricsLine>? = null,
        source: Source? = null,
    ): Map<Int, Int> {
        if (source == Source.NE && data3 != null && data3.isNotEmpty()) {
            val matched3 = findClosestMatch(data3, data2, null, Source.NE).toMutableMap()
            val matched = LinkedHashMap<Int, Int>()
            data1.forEachIndexed { i1, line1 ->
                val hit = matched3.entries.firstOrNull { (i3, _) ->
                    i3 < data3.size && isSameLine(line1, data3[i3])
                }
                if (hit != null) {
                    matched[i1] = hit.value
                    matched3.remove(hit.key)
                }
            }
            if (matched.isNotEmpty()) return matched
        }

        if (source == Source.QM || source == Source.KG) {
            var d1 = data1
            var d2 = data2
            repeat(2) { iteration ->
                if (iteration == 1) {
                    d1 = d1.filter { it.words.isNotEmpty() }
                    d2 = d2.filter { it.words.isNotEmpty() }
                }
                if (d1.size == d2.size) {
                    return d1.indices.associateWith { it }
                }
            }
        }

        data class Candidate(val i1: Int, val i2: Int, val diff: Int)

        val candidates = ArrayList<Candidate>()
        data1.forEachIndexed { i1, line1 ->
            val start1 = line1.start ?: return@forEachIndexed
            data2.forEachIndexed { i2, line2 ->
                val start2 = line2.start ?: return@forEachIndexed
                candidates.add(Candidate(i1, i2, kotlin.math.abs(start1 - start2)))
            }
        }
        candidates.sortBy { it.diff }

        val matched = LinkedHashMap<Int, Int>()
        val usedI1 = HashSet<Int>()
        val usedI2 = HashSet<Int>()
        for (candidate in candidates) {
            if (candidate.i1 !in usedI1 && candidate.i2 !in usedI2) {
                usedI1.add(candidate.i1)
                usedI2.add(candidate.i2)
                matched[candidate.i1] = candidate.i2
                if (usedI1.size == data1.size || usedI2.size == data2.size) break
            }
        }
        return matched
    }

    fun hasContent(line: String): Boolean {
        val content = Regex("""\[\d+:\d+\.\d+\]|\[\d+,\d+\]|<\d+:\d+\.\d+>""").replace(line, "").trim()
        if (content.isEmpty() || content == "//") return false
        return !(content.length == 2 && content[0].isUpperCase() && content[1] == '：')
    }

    fun isInstrumentalText(text: String): Boolean = text.trim() in INSTRUMENTAL_TEXTS
}
