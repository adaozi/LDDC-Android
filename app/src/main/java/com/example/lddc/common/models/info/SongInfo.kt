package com.example.lddc.common.models.info

import com.example.lddc.common.models.enums.Language
import com.example.lddc.common.models.enums.Source
import kotlinx.serialization.Serializable

/**
 * 歌词类型
 */
enum class LyricsType {
    EMBEDDED,    // 内嵌歌词
    EXTERNAL_LRC, // 外部 LRC 文件
    EXTERNAL_TXT, // 外部 TXT 文件
}

@Serializable
data class SongInfo(
    val source: Source,
    val id: String? = null,
    val mid: String? = null,
    val hash: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val artist: Artist? = null,
    val album: String? = null,
    val duration: Int? = null,
    val imageUrl: String? = null,
    val path: String? = null,
    val language: Language? = null,
    val hasLyrics: Boolean = false,
    val lyricsPath: String? = null,
    val lyricsType: LyricsType? = null
) {
    val fullTitle: String
        get() = if (!subtitle.isNullOrBlank()) "$title($subtitle)" else title ?: ""

    val strArtist: String
        get() = artist?.toString() ?: ""

}
