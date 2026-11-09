// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.tag

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 音频文件的标签信息（读取时用） */
@Serializable
data class AudioTagInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int = 0,
)

/** SYLT 字级时间戳载荷 */
data class SyltPayload(
    val timesMs: IntArray,
    val words: List<String>,
) {
    init {
        require(timesMs.size == words.size) { "timesMs 与 words 长度必须一致" }
    }
}

enum class WriteResult {
    OK,
    UNSUPPORTED_FORMAT,
    IO_ERROR,
    BAD_ARGUMENT,
    ;

    companion object {
        fun fromCode(code: Int): WriteResult = when (code) {
            0 -> OK
            1 -> UNSUPPORTED_FORMAT
            2 -> IO_ERROR
            else -> BAD_ARGUMENT
        }
    }
}

/**
 * 标签读写仓库：上层只依赖这个类，native 实现可替换（TagLib / 其他）。
 */
object AudioTagRepository {

    /** 与上游 LDDC/core/song_info.py 的 AUDIO_FORMATS 对齐（TagLib 能写的部分） */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "mp3", "mp2", "aac",
        "flac",
        "m4a", "m4b", "mp4",
        "ogg", "oga", "opus", "spx",
        "wav", "aif", "aiff", "aifc",
        "wma", "asf",
        "ape",
        "wv",
        "tta",
        "mpc",
        "dsf", "dff", "dsdiff",
    )

    /** 扫描到的、TagLib 能识别但我们暂不写标签的格式（明确提示，不静默失败） */
    val UNSUPPORTED_BUT_KNOWN: Set<String> = setOf("mid", "midi", "ofr", "ofs", "3g2", "3ga")

    private val json = Json { ignoreUnknownKeys = true }

    fun isSupported(file: File): Boolean = file.extension.lowercase() in SUPPORTED_EXTENSIONS

    fun readInfo(file: File): AudioTagInfo? {
        val raw = TagLibBridge.readAudioInfo(file.absolutePath.toByteArray(Charsets.UTF_8)) ?: return null
        return runCatching { json.decodeFromString<AudioTagInfo>(String(raw, Charsets.UTF_8)) }.getOrNull()
    }

    fun hasLyrics(file: File): Boolean =
        TagLibBridge.hasLyrics(file.absolutePath.toByteArray(Charsets.UTF_8))

    fun readLyrics(file: File): String? =
        TagLibBridge.readLyrics(file.absolutePath.toByteArray(Charsets.UTF_8))?.let { String(it, Charsets.UTF_8) }

    fun writeLyrics(
        file: File,
        text: String,
        sylt: SyltPayload? = null,
        id3Version: Int = 3,
    ): WriteResult {
        if (text.isBlank()) return WriteResult.BAD_ARGUMENT
        if (!isSupported(file)) return WriteResult.UNSUPPORTED_FORMAT
        val code = TagLibBridge.writeLyrics(
            path = file.absolutePath.toByteArray(Charsets.UTF_8),
            text = text.toByteArray(Charsets.UTF_8),
            syltTimes = sylt?.timesMs ?: IntArray(0),
            syltWords = sylt?.words?.map { it.toByteArray(Charsets.UTF_8) }?.toTypedArray(),
            id3Version = id3Version,
        )
        return WriteResult.fromCode(code)
    }

    fun nativeVersion(): String = runCatching { TagLibBridge.nativeVersion() }.getOrDefault("unavailable")
}
