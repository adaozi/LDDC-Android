// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.model

/** 歌词源，与上游 LDDC/common/models/_enums.py 的 Source 对齐 */
enum class Source(val displayName: String, val shortName: String) {
    QM("QQ音乐", "QM"),
    KG("酷狗音乐", "KG"),
    NE("网易云音乐", "NE"),
    LRCLIB("Lrclib", "LRCLIB"),
    LOCAL("本地", "LOCAL"),
}

/** 搜索类型，对应上游 SearchType */
enum class SearchType { SONG, ALBUM, SONGLIST }

/** 歌词输出格式，对应上游 LyricsFormat（已裁掉 QRC/KRC/YRC/JSON 输出） */
enum class LyricsFormat(val displayName: String, val ext: String) {
    VERBATIM_LRC("逐字 LRC", ".lrc"),
    LINE_BY_LINE_LRC("逐行 LRC", ".lrc"),
    ENHANCED_LRC("增强型 LRC", ".lrc"),
    SRT("SRT 字幕", ".srt"),
    ASS("ASS 字幕", ".ass"),
    ;

    val isLrc: Boolean get() = this == VERBATIM_LRC || this == LINE_BY_LINE_LRC || this == ENHANCED_LRC

    /** 输出里是否含字级时间戳（逐字 LRC / 增强型 LRC） */
    val isWordLevel: Boolean get() = this == VERBATIM_LRC || this == ENHANCED_LRC
}

/** 保存方式，对应上游 LocalMatchSave2TagMode */
enum class SaveMode(val displayName: String) {
    TAG_ONLY("仅写入歌曲标签"),
    FILE_ONLY("仅保存为歌词文件"),
    BOTH("标签与歌词文件都保存"),
}

/** 文件名模式，对应上游 FileNameMode */
enum class FileNameMode(val displayName: String) {
    SONG("使用歌曲文件名"),
    FORMAT_BY_LYRICS("按歌词信息格式化"),
    FORMAT_BY_SONG("按歌曲信息格式化"),
}

/** 保存目录模式，对应上游 SaveMode（path_processor） */
enum class SaveDirMode(val displayName: String) {
    SONG("歌曲所在目录"),
    MIRROR("镜像到指定根目录"),
    SPECIFY("全部放到指定目录"),
}

enum class Id3Version(val displayName: String, val flag: Int) {
    V23("ID3v2.3", 3),
    V24("ID3v2.4", 4),
}

enum class TranslateBackend(val displayName: String) {
    BING("Bing"),
    GOOGLE("Google"),
    OPENAI("OpenAI 兼容"),
}

/** FLAC 等 Vorbis Comment 格式写入的歌词内容（体积/兼容性取舍） */
enum class TagLyricsContent(val displayName: String) {
    VERBATIM_LRC("逐字 LRC（体积大）"),
    LINE_BY_LINE_LRC("逐行 LRC（推荐）"),
    PLAIN_TEXT("纯文本"),
}
