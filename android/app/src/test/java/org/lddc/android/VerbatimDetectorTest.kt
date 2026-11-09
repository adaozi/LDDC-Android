// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lddc.android.data.parser.VerbatimDetector

class VerbatimDetectorTest {

    @Test
    fun `逐字 LRC 判定为 true`() {
        val text = """
            [ti:夜曲]
            [ar:周杰伦]
            [00:00.000]一[00:00.320]群[00:00.640]嗜[00:00.960]血[00:01.280]的[00:01.600]蚂[00:01.920]蚁
            [00:12.000]被[00:12.300]梦[00:12.600]想[00:12.900]豢[00:13.200]养[00:13.500]的[00:13.800]人
            [00:15.000]无[00:15.300]法[00:15.600]逃[00:15.900]离[00:16.200]这[00:16.500]一切
        """.trimIndent()
        assertTrue(VerbatimDetector.isVerbatim(text))
    }

    @Test
    fun `逐行 LRC 判定为 false`() {
        val text = """
            [ti:夜曲]
            [00:00.00]一群嗜血的蚂蚁 被腐肉所吸引
            [00:12.00]我面无表情 看孤独的风景
            [00:22.00]失去你 爱恨开始分明
        """.trimIndent()
        assertFalse(VerbatimDetector.isVerbatim(text))
    }

    @Test
    fun `一句多时间标签的逐行 LRC 不会误判`() {
        val text = """
            [00:10.00][01:20.00]这是一句很长的歌词内容用来占位
            [00:20.00][01:30.00]第二句同样比较长不会被认为是逐字
            [00:30.00][01:40.00]第三句也超过四个字所以不算片段
            [00:40.00][01:50.00]第四句依然是普通的整句歌词文本
        """.trimIndent()
        assertFalse(VerbatimDetector.isVerbatim(text))
    }

    @Test
    fun `增强型 LRC 判定为 true`() {
        val text = """
            [00:12.00]<00:12.00>故事<00:12.40>的<00:12.60>小<00:12.80>黄花
            [00:15.00]<00:15.00>从<00:15.20>出生<00:15.60>那<00:15.80>年<00:16.00>就<00:16.20>种
        """.trimIndent()
        assertTrue(VerbatimDetector.isVerbatim(text))
    }

    @Test
    fun `纯文本与空内容判定为 false`() {
        assertFalse(VerbatimDetector.isVerbatim("一群嗜血的蚂蚁 被腐肉所吸引\n我面无表情 看孤独的风景"))
        assertFalse(VerbatimDetector.isVerbatim(""))
        assertFalse(VerbatimDetector.isVerbatim(null))
    }

    @Test
    fun `只有一两行行内标签时不判为逐字`() {
        val text = """
            [00:00.000]一[00:00.320]群[00:00.640]嗜[00:00.960]血[00:01.280]的
            [00:12.00]我面无表情 看孤独的风景
        """.trimIndent()
        assertFalse(VerbatimDetector.isVerbatim(text))
    }
}
