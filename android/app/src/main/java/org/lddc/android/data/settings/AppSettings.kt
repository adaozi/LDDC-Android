// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.settings

import org.lddc.android.data.model.Id3Version
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.SaveDirMode
import org.lddc.android.data.model.SaveMode
import org.lddc.android.data.model.Source
import org.lddc.android.data.model.TagLyricsContent
import org.lddc.android.data.model.TranslateBackend

/**
 * 应用设置。默认值与上游 LDDC/common/data/config.py 的 default_cfg 对齐，
 * 只有保存方式默认改为"仅写入歌曲标签"（用户明确要求默认写进歌曲文件）。
 */
data class AppSettings(
    // 歌词源：全部源的有序列表（顺序即优先级）
    val sourcePriority: List<Source> = listOf(Source.QM, Source.KG, Source.NE, Source.LRCLIB),
    // 参与搜索的源集合
    val enabledSources: Set<Source> = setOf(Source.QM, Source.KG, Source.NE, Source.LRCLIB),
    // 匹配
    val minScore: Int = 55,
    val skipExistingLyrics: Boolean = true,
    // 已有歌词不是逐字（逐行/纯文本）时重新匹配并覆盖
    val refreshNonVerbatimLyrics: Boolean = false,
    val skipInstrumental: Boolean = true,
    val concurrency: Int = 4,
    // 保存
    val saveMode: SaveMode = SaveMode.TAG_ONLY,
    val lyricsFormat: LyricsFormat = LyricsFormat.VERBATIM_LRC,
    val alsoSaveLrcFile: Boolean = false,
    val tagLyricsContent: TagLyricsContent = TagLyricsContent.LINE_BY_LINE_LRC,
    val id3Version: Id3Version = Id3Version.V23,
    // 路径（占位符：%<title> %<artist> %<album> %<id> %<langs>）
    val saveDirMode: SaveDirMode = SaveDirMode.SONG,
    val saveRootPath: String = "",
    val fileNameFormat: String = "%<artist> - %<title>",
    // 上次使用的本地扫描目录（记住用户选择，下次启动直接沿用）
    val localMusicPath: String = "",
    // 歌词组合，顺序即渲染顺序（上游默认 roma, orig, ts）
    val langsOrder: List<String> = listOf("orig", "ts", "roma"),
    // 翻译
    val translateBackend: TranslateBackend = TranslateBackend.BING,
    val translateTargetLang: String = "SIMPLIFIED_CHINESE",
    val openAiBaseUrl: String = "",
    val openAiApiKey: String = "",
    val openAiModel: String = "",
)
