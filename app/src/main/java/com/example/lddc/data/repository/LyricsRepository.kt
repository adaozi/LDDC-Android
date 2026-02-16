package com.example.lddc.data.repository

import com.example.lddc.model.Artist
import com.example.lddc.model.Lyrics
import com.example.lddc.model.Music
import com.example.lddc.model.SearchType
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsApiService
import com.example.lddc.service.LyricsService
import com.example.lddc.service.LyricsOutputFormat
import com.example.lddc.service.LyricsConvertOptions

/**
 * 歌词仓库接口
 *
 * 定义歌词数据的访问接口，包括搜索、获取、转换等功能
 */
interface LyricsRepository {

    /**
     * 搜索歌曲
     */
    suspend fun searchSongs(
        keyword: String,
        source: Source,
        page: Int = 1
    ): Result<List<Music>>

    /**
     * 获取歌曲歌词
     */
    suspend fun getLyrics(
        info: Any
    ): Result<Lyrics>

    /**
     * 转换歌词格式
     */
    fun convertLyrics(
        lyrics: Lyrics,
        format: LyricsOutputFormat,
        options: LyricsConvertOptions = LyricsConvertOptions()
    ): String
}

/**
 * 歌词仓库实现
 */
class LyricsRepositoryImpl(
    private val lyricsApiService: LyricsApiService,
    private val lyricsService: LyricsService
) : LyricsRepository {

    override suspend fun searchSongs(
        keyword: String,
        source: Source,
        page: Int
    ): Result<List<Music>> {
        return try {
            val result = lyricsApiService.search(
                source = source,
                keyword = keyword,
                searchType = SearchType.SONG,
                page = page
            )
            // 转换 SongInfo 列表为 Music 列表
            val musicList = result.map { songInfo ->
                // 将毫秒转换为秒
                val durationInSeconds = songInfo.duration / 1000
                Music(
                    id = songInfo.id,
                    title = songInfo.title,
                    artist = songInfo.artist.name,
                    album = songInfo.album,
                    duration = durationInSeconds.toString(),
                    platform = songInfo.source.name,
                    lyrics = "",
                    lyricsType = "",
                    imageUrl = songInfo.imageUrl
                )
            }
            Result.success(musicList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLyrics(info: Any): Result<Lyrics> {
        return try {
            // 将 Music 对象转换为 SongInfo
            val songInfo = when (info) {
                is Music -> musicToSongInfo(info)
                else -> info
            }
            val lyrics = lyricsApiService.getLyrics(songInfo)
            Result.success(lyrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将 Music 对象转换为 SongInfo 对象
     */
    private fun musicToSongInfo(music: Music): SongInfo {
        // 根据平台名称确定 Source
        val source = when (music.platform) {
            "QQ音乐" -> Source.QM
            "网易云音乐" -> Source.NE
            "酷狗音乐" -> Source.KG
            else -> Source.QM
        }

        // Music.duration 是秒数，转换为毫秒
        val durationInSeconds = music.duration.toLongOrNull() ?: 0L
        val durationInMillis = durationInSeconds * 1000

        return SongInfo(
            id = music.id,
            title = music.title,
            artist = Artist(music.artist),
            album = music.album,
            duration = durationInMillis,
            source = source,
            imageUrl = music.imageUrl
        )
    }

    override fun convertLyrics(
        lyrics: Lyrics,
        format: LyricsOutputFormat,
        options: LyricsConvertOptions
    ): String {
        return lyricsService.convertLyrics(lyrics, format, options)
    }
}
