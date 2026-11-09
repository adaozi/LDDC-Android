// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import org.lddc.android.data.converter.LyricsConverter
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers
import java.io.File

/**
 * 批量格式转换：把目录里已有的 .lrc 转成其它输出格式（SRT / ASS / 增强 LRC …）。
 * 不改动原文件，结果写到输出目录。
 */
object LyricsFileConverter {

    data class Result(val ok: Int, val failed: Int, val outDir: String)

    fun scanLrc(dir: File, recursive: Boolean = true): List<File> {
        if (!dir.isDirectory) return emptyList()
        val result = ArrayList<File>()
        val stack = ArrayDeque<File>().apply { add(dir) }
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            current.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    if (recursive) stack.add(child)
                } else if (child.extension.equals("lrc", ignoreCase = true) || child.extension.equals("txt", ignoreCase = true)) {
                    result.add(child)
                }
            }
        }
        return result.sortedBy { it.name }
    }

    /** 解析一个歌词文件：LRC / QRC / 纯文本都能认 */
    fun parse(text: String): Pair<Map<String, String>, List<LyricsLine>> {
        val trimmed = text.trim()
        return when {
            trimmed.contains("(\\d+,\\d+)".toRegex()) && trimmed.contains("LyricContent") -> LyricsParsers.qrcStrParse(trimmed)
            trimmed.contains("[") && trimmed.contains("]") -> LyricsParsers.lrc2data(trimmed, Source.LOCAL)
            else -> emptyMap<String, String>() to LyricsParsers.plaintext2data(trimmed)
        }
    }

    fun convert(file: File, format: LyricsFormat, outDir: File): Boolean {
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        if (text.isBlank()) return false

        val (tags, lines) = parse(text)
        if (lines.isEmpty()) return false

        val info = LyricInfo(
            source = Source.LOCAL,
            songInfo = SongInfo(
                source = Source.LOCAL,
                title = tags["ti"] ?: file.nameWithoutExtension,
                artist = listOfNotNull(tags["ar"]?.takeIf { it.isNotBlank() }),
                album = tags["al"],
                path = file.absolutePath,
            ),
            path = file.absolutePath,
        )
        val lyrics = Lyrics(info)
        lyrics.tags.putAll(tags)
        lyrics["orig"] = lines

        val output = runCatching {
            LyricsConverter.convert(
                lyrics = lyrics,
                format = format,
                langs = listOf("orig"),
                msDigitCount = 3,
                tool = "LDDC Android",
            )
        }.getOrNull()
        if (output.isNullOrBlank()) return false

        if (!outDir.isDirectory) outDir.mkdirs()
        val target = File(outDir, file.nameWithoutExtension + format.ext)
        return runCatching { target.writeText(output, Charsets.UTF_8) }.isSuccess
    }
}
