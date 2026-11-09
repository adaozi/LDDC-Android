// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.util

/** 时间转换工具，对应上游 LDDC/common/time.py */

/** 时间转毫秒，支持两位/三位毫秒 */
fun time2ms(m: String, s: String, ms: String): Int {
    val msValue = if (ms.length == 2) (ms + "0").toInt() else ms.toInt()
    return (m.toInt() * 60 + s.toInt()) * 1000 + msValue
}

private fun divModTime(ms: Int): IntArray {
    val totalSeconds = ms / 1000
    val milliseconds = ms % 1000
    val h = totalSeconds / 3600
    val remainder = totalSeconds % 3600
    return intArrayOf(h, remainder / 60, remainder % 60, milliseconds)
}

/** 格式化为 MM:SS.mmm */
fun ms2formatTime(ms: Int): String {
    val (_, m, s, milli) = divModTime(ms).toList()
    return "%02d:%02d.%03d".format(m, s, milli)
}

/** 格式化为 MM:SS.cc（四舍五入到 10ms，两位毫秒） */
fun ms2roundedTime(ms: Int): String {
    val rounded = kotlin.math.round(ms / 10.0).toInt() * 10
    val (h, m, s, milli) = divModTime(rounded).toList()
    val totalMinutes = h * 60 + m
    return "%02d:%02d.%02d".format(totalMinutes, s, milli / 10)
}

/** 将时间戳减 1（用于多语言上行末时间戳避免与下一行冲突） */
fun formatTimeSub1(formatTime: String): String {
    val idx = formatTime.indexOf(':')
    val m = formatTime.substring(0, idx)
    val sMs = formatTime.substring(idx + 1)
    var s = sMs.substringBefore('.')
    var ms = sMs.substringAfter('.')
    val msLen = ms.length
    when {
        ms != "0".repeat(msLen) -> ms = "%0${msLen}d".format(ms.toInt() - 1)
        s != "00" -> s = (s.toInt() - 1).toString()
        m != "00" -> return "%s:%s.%s".format((m.toInt() - 1).toString(), s, ms)
    }
    return "$m:$s.$ms"
}
