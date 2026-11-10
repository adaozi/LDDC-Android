// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import org.lddc.android.data.converter.LyricsConverter
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.VerbatimDetector
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

    /** 断点里「上次已完成」条目的复核结论 */
    enum class CompletedReview {
        /** 已有逐字歌词，可以继续跳过 */
        VERBATIM,

        /** 歌词非逐字或已不存在，需要重新匹配 */
        NEED_REMATCH,

        /** 有标签但读不出内容，保守保留 */
        UNKNOWN,
    }

    /**
     * 复核上次已完成的歌曲：断点只记了「跑过」，不代表现在还满足条件，
     * 用户重新匹配时要把「逐行歌词」这类条目重新拉一遍。
     */
    fun reviewCompleted(file: File): CompletedReview {
        val existing = AudioTagRepository.readLyrics(file)
        if (existing.isNullOrBlank()) {
            return if (AudioTagRepository.hasLyrics(file)) CompletedReview.UNKNOWN else CompletedReview.NEED_REMATCH
        }
        return if (VerbatimDetector.isVerbatim(existing)) CompletedReview.VERBATIM else CompletedReview.NEED_REMATCH
    }

    /** 现有歌词的处理结论 */
    private sealed interface Existing {
        data object None : Existing
        data class Keep(val detail: String) : Existing
        data object Replace : Existing
    }

    /**
     * 检查歌曲里已有的歌词：
     * - 未开启「跳过已有歌词」→ 不管；
     * - 开启了但未开启「非逐字重匹配」→ 有歌词就跳过（与原行为一致）；
     * - 两者都开启 → 逐字才跳过，逐行/纯文本判为覆盖。
     * 读取失败（有歌词但解析不出来）时选择保留，避免异常情况下反复覆盖。
     */
    private fun inspectExisting(file: File, settings: AppSettings): Existing {
        if (!settings.skipExistingLyrics) return Existing.None
        if (!settings.refreshNonVerbatimLyrics) {
            return if (AudioTagRepository.hasLyrics(file)) Existing.Keep("已有歌词") else Existing.None
        }
        val existing = AudioTagRepository.readLyrics(file)
        if (existing.isNullOrBlank()) {
            return if (AudioTagRepository.hasLyrics(file)) Existing.Keep("已有歌词（无法解析）") else Existing.None
        }
        return if (VerbatimDetector.isVerbatim(existing)) {
            Existing.Keep("已有逐字歌词")
        } else {
            Existing.Replace
        }
    }

    suspend fun matchOne(file: File, settings: AppSettings, sources: List<Source>): MatchOutcome {
        val existing = inspectExisting(file, settings)
        if (existing is Existing.Keep) {
            return MatchOutcome(Status.SKIPPED, existing.detail)
        }
        val replacingNonVerbatim = existing is Existing.Replace

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

        val prefix = if (replacingNonVerbatim) "覆盖非逐字 · " else ""
        return MatchOutcome(Status.SUCCESS, prefix + "${match.song.source.displayName} · ${match.song.artistTitle()}")
    }
}
