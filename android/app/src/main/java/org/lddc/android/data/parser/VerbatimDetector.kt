// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.parser

/**
 * 判断一份歌词文本是不是「逐字」级别。
 *
 * 兼容三种逐字写法：
 * 1. 逐字 LRC：一行内多个时间标签，标签之间只夹 1~4 个字，如 `[00:00.000]夜[00:00.356]曲`；
 * 2. 增强型 LRC：词级标签写在尖括号里，如 `[00:12.00]<00:12.00>故<00:12.40>事`；
 * 3. 上面的混合形式。
 *
 * 逐行 LRC（每行只有一个时间标签）与纯文本都会判为 false。
 * 注意：逐行 LRC 里常见的 `[00:10.00][01:20.00]歌词` 不会误判 —— 相邻标签之间没有文本，
 * 唯一的文本片段在行尾且长度通常超过 4 个字。
 */
object VerbatimDetector {

    private val TIME_TAG = Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]""")
    private val ENHANCED_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    /** 判定为逐字所需的：至少这么多行带行内标签 */
    private const val MIN_INLINE_TAG_LINES = 3

    /** 判定为逐字所需的：至少这么多 1~4 字的文本片段 */
    private const val MIN_SHORT_SEGMENTS = 6

    /** 单个「字/词」片段的长度上限（按字符数） */
    private const val MAX_WORD_LENGTH = 4

    fun isVerbatim(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (ENHANCED_TAG.containsMatchIn(text)) return true

        var inlineTagLines = 0
        var shortSegments = 0

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.size < 2) continue
            inlineTagLines++
            for (index in tags.indices) {
                val start = tags[index].range.last + 1
                val end = if (index + 1 < tags.size) tags[index + 1].range.first else line.length
                if (end <= start) continue
                val segment = line.substring(start, end).trim()
                if (segment.isNotEmpty() && segment.length <= MAX_WORD_LENGTH) shortSegments++
            }
        }

        return inlineTagLines >= MIN_INLINE_TAG_LINES &&
            shortSegments >= MIN_SHORT_SEGMENTS &&
            shortSegments >= inlineTagLines
    }
}
