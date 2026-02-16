package com.example.lddc.model

/**
 * 音频元数据信息
 *
 * 用于存储从音频文件中读取的完整元数据
 * 参考 PC 端 mutagen 返回的数据结构
 */
data class AudioMetadata(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0,
    val path: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val hasLyrics: Boolean = false,
    val year: String? = null,
    val trackNumber: Int? = null,
    val genre: String? = null,
    val comment: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val discNumber: Int? = null,
    val lyrics: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: String? = null
) {
    /**
     * 转换为 LocalMusicInfo
     */
    fun toLocalMusicInfo(id: Long = 0): LocalMusicInfo {
        return LocalMusicInfo(
            id = id,
            filePath = path,
            fileName = fileName,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            fileSize = fileSize,
            hasLyrics = hasLyrics || !lyrics.isNullOrBlank(),
            year = year?.toIntOrNull(),
            genre = genre,
            bitrate = bitrate,
            sampleRate = sampleRate,
            trackNumber = trackNumber,
            composer = composer
        )
    }
}
