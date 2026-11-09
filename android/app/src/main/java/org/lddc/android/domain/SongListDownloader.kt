// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import android.os.Environment
import org.lddc.android.data.converter.LyricsConverter
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.settings.AppSettings
import java.io.File

/**
 * 保存路径占位符，对应上游 LDDC/common/path_processor.py。
 * 支持：%<title> %<artist> %<album> %<id> %<langs>
 */
object PathPlaceholder {

    private val ILLEGAL = mapOf(
        '/' to '／', '\\' to '＼', ':' to '：', '*' to '＊', '?' to '？',
        '"' to '＂', '<' to '＜', '>' to '＞', '|' to '｜',
    )

    fun sanitize(text: String): String = buildString {
        text.forEach { ch -> append(ILLEGAL[ch] ?: ch) }
    }

    fun apply(format: String, song: SongInfo, langs: List<String>): String {
        var text = format
        text = text.replace("%<title>", sanitize(song.title ?: "?"))
        text = text.replace("%<artist>", sanitize(song.strArtist.ifEmpty { "?" }))
        text = text.replace("%<album>", sanitize(song.album ?: "?"))
        text = text.replace("%<id>", sanitize(song.id ?: "?"))
        text = text.replace("%<langs>", sanitize(langs.joinToString("-")))
        // 标题里常有尾部空格（如「ひまわり 」），去掉避免生成 "xxx .lrc"
        return sanitize(text).trim()
    }

    fun targetFile(root: File, format: String, song: SongInfo, langs: List<String>, ext: String): File {
        val name = apply(format.ifBlank { "%<artist> - %<title>" }, song, langs)
        if (!root.isDirectory) root.mkdirs()
        return File(root, name + ext)
    }
}

/**
 * 歌单 / 专辑单曲下载：自动匹配 + 按占位符写 .lrc。
 * 由 BatchController 调度，因此具备前台服务、并发与断点续跑能力。
 */
object SongListDownloader {

    fun defaultRoot(): File = File(Environment.getExternalStorageDirectory(), "Music/LDDC")

    suspend fun downloadOne(
        song: SongInfo,
        settings: AppSettings,
        sources: List<Source>,
    ): LocalMatchEngine.MatchOutcome {
        val match = runCatching { AutoFetch.fetch(song, sources, settings.minScore.toDouble()) }
            .getOrNull()
            ?: return LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.NOT_FOUND, "没有匹配到")

        val lyrics = match.lyrics
        val langs = settings.langsOrder.filter { lyrics.has(it) }.ifEmpty { listOf("orig") }
        val text = runCatching {
            LyricsConverter.convert(
                lyrics = lyrics,
                format = if (settings.lyricsFormat.isLrc) settings.lyricsFormat else LyricsFormat.LINE_BY_LINE_LRC,
                langs = langs,
                msDigitCount = 3,
                tool = "LDDC Android",
            )
        }.getOrNull()

        if (text.isNullOrBlank()) {
            return LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.FAILED, "歌词内容为空")
        }

        return runCatching {
            val root = settings.saveRootPath.takeIf { it.isNotBlank() }?.let(::File) ?: defaultRoot()
            root.mkdirs()
            val target = PathPlaceholder.targetFile(
                root = root,
                format = settings.fileNameFormat,
                song = song,
                langs = langs,
                ext = ".lrc",
            )
            target.writeText(text, Charsets.UTF_8)
            LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.SUCCESS, "已保存 ${target.name}")
        }.getOrElse { error ->
            LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.FAILED, error.message ?: "写入失败")
        }
    }
}
