// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.lddc.android.BuildConfig
import org.lddc.android.data.model.Language
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers

/**
 * Lrclib：纯 REST，无需登录/签名。
 * 对应上游 LDDC/core/api/lyrics/lrclib.py
 */
object LrclibApi : LyricsSource {

    private const val BASE = "https://lrclib.net/api"
    private const val PAGE_SIZE = 20

    override val source: Source = Source.LRCLIB
    override val supportedSearchTypes: Set<SearchType> = setOf(SearchType.SONG)

    private val headers = listOf(
        "User-Agent" to "LDDC/${BuildConfig.VERSION_NAME} (https://github.com/chenmozhijin/LDDC)",
        "Accept" to "application/json",
    )

    private fun getText(url: String): String {
        val response = Http.get(url, headers)
        Http.requireOk(response, url)
        return response.text
    }

    override suspend fun searchSongs(keyword: String, page: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", keyword)
            .build()
        val body = getText(url.toString())
        val array = Json.parseToJsonElement(body).jsonArray
        array.mapNotNull { runCatching { parseSongInfo(it.jsonObject) }.getOrNull() }
            .drop((page - 1) * PAGE_SIZE)
            .take(PAGE_SIZE)
    }

    override suspend fun getLyrics(info: LyricInfo): Lyrics = withContext(Dispatchers.IO) {
        val song = info.songInfo
        val title = song.title ?: throw LyricsSourceException("Lrclib 需要歌曲标题")
        val artist = song.strArtist.ifEmpty { throw LyricsSourceException("Lrclib 需要歌手名") }
        val album = song.album.orEmpty()
        val durationSeconds = (song.duration ?: 0) / 1000.0

        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("album_name", album)
            .addQueryParameter("duration", durationSeconds.toString())
            .build()

        val obj = Json.parseToJsonElement(getText(url.toString())) as? JsonObject
            ?: throw LyricsSourceException("Lrclib 响应格式异常")
        obj.str("error")?.let { throw LyricsNotFoundException("Lrclib: $it") }

        val lyrics = Lyrics(info)
        obj.str("trackName")?.let { lyrics.tags["ti"] = it }
        obj.str("artistName")?.let { lyrics.tags["ar"] = it }
        obj.str("albumName")?.let { lyrics.tags["al"] = it }

        val synced = obj.str("syncedLyrics")
        val plain = obj.str("plainLyrics")

        when {
            !synced.isNullOrBlank() -> {
                val (tags, data) = LyricsParsers.lrc2data(synced)
                lyrics.tags.putAll(tags)
                lyrics["orig"] = data
            }

            !plain.isNullOrBlank() -> lyrics["orig"] = LyricsParsers.plaintext2data(plain)

            else -> throw LyricsNotFoundException("Lrclib 未提供歌词")
        }
        if (lyrics.get("orig").isEmpty()) throw LyricsNotFoundException("Lrclib 歌词为空")
        lyrics
    }

    private fun parseSongInfo(obj: JsonObject): SongInfo {
        val durationSeconds = obj.str("duration")?.toDoubleOrNull() ?: 0.0
        return SongInfo(
            source = Source.LRCLIB,
            title = obj.str("trackName"),
            artist = listOfNotNull(obj.str("artistName")),
            album = obj.str("albumName"),
            duration = (durationSeconds * 1000).toInt().takeIf { it > 0 },
            id = obj.str("id"),
            language = if (obj.str("instrumental") == "true") Language.INSTRUMENTAL else Language.OTHER,
        )
    }

    /** 安全读取字符串字段：JSON null / 缺失 / 字面量 "null" 都返回 null */
    private fun JsonObject.str(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.content?.takeIf { it != "null" }
    }
}
