// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import org.lddc.android.data.converter.LyricsConverter
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.settings.AppSettings
import org.lddc.android.data.tag.AudioTagRepository
import org.lddc.android.data.tag.WriteResult
import java.io.File

/**
 * 本地批量匹配：扫描 → 自动匹配 → 写标签 / 存 .lrc。
 */
object LocalMatchEngine {

    enum class Status { PENDING, MATCHING, SUCCESS, SKIPPED, NOT_FOUND, FAILED }

    data class Item(
        val file: File,
        val status: Status = Status.PENDING,
        val detail: String = "",
    )

    /** 扫描目录下的音频文件 */
    fun scan(root: File, recursive: Boolean = true): List<File> {
        if (!root.isDirectory) return emptyList()
        val result = ArrayList<File>()
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    if (recursive) stack.add(child)
                } else if (child.extension.lowercase() in AudioTagRepository.SUPPORTED_EXTENSIONS) {
                    result.add(child)
                }
            }
        }
        return result.sortedBy { it.absolutePath }
    }

    /** 读取本地文件标签，构造用于匹配的 SongInfo */
    fun readSongInfo(file: File): SongInfo {
        val info = AudioTagRepository.readInfo(file)
        return SongInfo(
            source = Source.LOCAL,
            title = info?.title?.takeIf { it.isNotBlank() },
            artist = info?.artist?.split("/")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            album = info?.album?.takeIf { it.isNotBlank() },
            duration = info?.duration?.takeIf { it > 0 },
            path = file.absolutePath,
        )
    }

    data class MatchOutcome(val status: Status, val detail: String)

    suspend fun matchOne(file: File, settings: AppSettings, sources: List<Source>): MatchOutcome {
        if (settings.skipExistingLyrics && AudioTagRepository.hasLyrics(file)) {
            return MatchOutcome(Status.SKIPPED, "已有歌词")
        }

        val localInfo = readSongInfo(file)
        val match = AutoFetch.fetch(localInfo, sources, settings.minScore.toDouble())
            ?: return MatchOutcome(Status.NOT_FOUND, "没有匹配到")

        val lyrics = match.lyrics
        if (settings.skipInstrumental && lyrics.isInstrumental()) {
            return MatchOutcome(Status.SKIPPED, "纯音乐")
        }

        val langs = settings.langsOrder.filter { lyrics.has(it) }.ifEmpty { listOf("orig") }
        val text = LyricsConverter.convert(
            lyrics = lyrics,
            format = settings.lyricsFormat,
            langs = langs,
            msDigitCount = 3,
            tool = "LDDC Android",
        )
        if (text.isBlank()) return MatchOutcome(Status.FAILED, "歌词内容为空")

        // 写标签
        if (settings.saveMode != org.lddc.android.data.model.SaveMode.FILE_ONLY) {
            val sylt = LyricsSaver.buildSyltPayload(lyrics.fsLyrics().get("orig").orEmpty())
            val result = AudioTagRepository.writeLyrics(file, text, sylt, settings.id3Version.flag)
            if (result != WriteResult.OK) {
                return MatchOutcome(Status.FAILED, "写入失败($result)")
            }
        }

        // 另存 .lrc
        if (settings.saveMode != org.lddc.android.data.model.SaveMode.TAG_ONLY || settings.alsoSaveLrcFile) {
            runCatching {
                val lrc = File(file.parentFile, "${file.nameWithoutExtension}.lrc")
                lrc.writeText(text, Charsets.UTF_8)
            }
        }

        return MatchOutcome(Status.SUCCESS, "${match.song.source.displayName} · ${match.song.artistTitle()}")
    }
}
