// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.model

/** 歌曲信息，对应上游 SongInfo */
data class SongInfo(
    val source: Source,
    val title: String? = null,
    val subtitle: String? = null,
    val artist: List<String> = emptyList(),
    val album: String? = null,
    /** 毫秒 */
    val duration: Int? = null,
    /** 云端歌曲 id；本地来自 CUE 时为音轨号 */
    val id: String? = null,
    val mid: String? = null,
    val hash: String? = null,
    val path: String? = null,
    val fromCue: Boolean = false,
    val language: Language? = null,
) {
    val fullTitle: String get() = if (title != null) {
        if (subtitle != null) "$title($subtitle)" else title
    } else {
        ""
    }

    val strArtist: String get() = artist.distinct().joinToString("/")

    val formatDuration: String
        get() = duration?.let { "%02d:%02d".format(it / 1000 / 60, it / 1000 % 60) } ?: ""

    fun artistTitle(full: Boolean = false, replace: Boolean = false): String {
        val t = (if (full) fullTitle else title) ?: if (replace) "?" else ""
        val a = strArtist.ifEmpty { if (replace) "?" else "" }
        return when {
            a.isNotEmpty() && t.isNotEmpty() -> "$a - $t"
            else -> a + t
        }
    }
}

/** 歌单/专辑信息，对应上游 SongListInfo */
data class SongListInfo(
    val source: Source,
    val type: SongListType,
    val id: String,
    val title: String,
    val imgUrl: String = "",
    val songCount: Int? = null,
    /** 秒 */
    val publishTime: Long? = null,
    val author: String = "",
    val mid: String? = null,
)

enum class SongListType { ALBUM, SONGLIST }

/** 歌词信息（某首歌在某个源下的一份歌词） */
data class LyricInfo(
    val source: Source,
    val songInfo: SongInfo,
    val id: String? = null,
    val accesskey: String? = null,
    /** 毫秒 */
    val duration: Int? = null,
    val creator: String? = null,
    val score: Int? = null,
    val path: String? = null,
    val data: ByteArray? = null,
    val cached: Boolean = false,
) {
    val formatDuration: String
        get() = duration?.let { "%02d:%02d".format(it / 1000 / 60, it / 1000 % 60) } ?: ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LyricInfo) return false
        return source == other.source && id == other.id && songInfo == other.songInfo &&
            accesskey == other.accesskey && duration == other.duration && creator == other.creator
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + songInfo.hashCode()
        result = 31 * result + (accesskey?.hashCode() ?: 0)
        return result
    }
}
