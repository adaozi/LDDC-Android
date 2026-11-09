// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.lddc.android.data.crypto.QrcCrypto
import org.lddc.android.data.model.Language
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers
import java.net.URLEncoder

/**
 * QQ 音乐。
 *
 * 走 web 端接口，不再用 musicu.fcg（那条路对无登录态客户端稳定返回 code=2001）：
 *   搜索：GET  https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp → 数字 songid
 *   歌词：POST https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg (lrctype=4)
 *
 * 歌词响应是 XML 注释包裹的伪 XML，含三个 CDATA：
 *   content     加密 QRC（hex）→ QQ 专有 3DES + inflate
 *   contentts   明文 LRC（翻译）
 *   contentroma 加密 QRC（罗马音）
 *
 * 参考：ANYNC/TaskbarLyrics 的《QQ 音乐歌词获取文档》、luren-dc/QQMusicApi。
 * 注意 musicid 必须是**数字 songid**，传字母 songmid 会拿不到歌词。
 */
object QmApi : LyricsSource {

    private const val SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/search_for_qq_cp"
    private const val LYRIC_URL = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg"
    private const val PAGE_SIZE = 20

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override val source: Source = Source.QM
    override val supportedSearchTypes: Set<SearchType> = setOf(SearchType.SONG)

    private fun baseHeaders(): List<Pair<String, String>> = listOf(
        "User-Agent" to UA,
        "Referer" to "https://y.qq.com/",
    )

    // ---------- 搜索 ----------

    override suspend fun searchSongs(keyword: String, page: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        val url = "$SEARCH_URL?w=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&format=json&n=$PAGE_SIZE&p=$page&t=0&remoteplace=txt.yqq.song&platform=yqq.json"
        val response = Http.get(url, baseHeaders())
        Http.requireOk(response, url)

        val root = Json.parseToJsonElement(response.text).jsonObject
        val list = root.obj("data")?.obj("song")?.arr("list")?.objects().orEmpty()

        list.mapNotNull { item ->
            val id = item.str("songid") ?: return@mapNotNull null
            SongInfo(
                source = source,
                id = id,
                mid = item.str("songmid"),
                title = item.str("songname"),
                artist = item.arr("singer")?.objects().orEmpty()
                    .mapNotNull { it.str("name")?.takeIf { name -> name.isNotBlank() } },
                album = item.str("albumname"),
                // interval 单位是秒
                duration = item.int("interval")?.times(1000),
                language = Language.OTHER,
            )
        }
    }

    // ---------- 歌词 ----------

    override suspend fun getLyrics(info: LyricInfo): Lyrics = withContext(Dispatchers.IO) {
        val song = info.songInfo
        val musicId = (info.id ?: song.id)?.toLongOrNull()
            ?: throw LyricsSourceException("QQ 音乐需要数字歌曲 id（songmid 无效）")

        var xml = requestLyric(musicId, lrcType = 4)
        var content = cdata(xml, "content")
        var preferPlain = false

        // 主歌词为空时退回普通 LRC（lrctype=1）
        if (content.isBlank()) {
            xml = requestLyric(musicId, lrcType = 1)
            content = cdata(xml, "content")
            preferPlain = true
        }
        if (content.isBlank()) throw LyricsNotFoundException("QQ 音乐没有歌词")

        val decoded = if (looksLikeHex(content)) {
            runCatching { QrcCrypto.decryptHex(content) }
                .getOrElse { throw LyricsSourceException("QQ 音乐歌词解密失败：${it.message}") }
        } else {
            content
        }
        if (decoded.isBlank()) throw LyricsSourceException("QQ 音乐歌词解密后为空")

        val lyrics = Lyrics(info)
        val (tags, qrcLines) = LyricsParsers.qrcStrParse(decoded)
        lyrics.tags.putAll(tags)
        if (!preferPlain && qrcLines.isNotEmpty()) {
            lyrics["orig"] = qrcLines
        } else {
            lyrics["orig"] = LyricsParsers.lrc2data(decoded, Source.QM).second
                .ifEmpty { LyricsParsers.plaintext2data(decoded) }
        }

        // 翻译（明文 LRC）
        val ts = cdata(xml, "contentts")
        if (ts.isNotBlank()) {
            val lines = LyricsParsers.lrc2data(ts, Source.QM).second
            if (lines.isNotEmpty()) lyrics["ts"] = lines
        }

        // 罗马音（加密，格式与主歌词相同）
        val roma = cdata(xml, "contentroma")
        if (roma.isNotBlank()) {
            val plain = runCatching { if (looksLikeHex(roma)) QrcCrypto.decryptHex(roma) else roma }.getOrNull()
            if (!plain.isNullOrBlank()) {
                val lines = LyricsParsers.qrcStrParse(plain).second
                if (lines.isNotEmpty()) lyrics["roma"] = lines
            }
        }

        lyrics.tags.putIfAbsent("ti", song.title.orEmpty())
        if (song.artist.isNotEmpty()) lyrics.tags.putIfAbsent("ar", song.strArtist)
        song.album?.let { lyrics.tags.putIfAbsent("al", it) }

        if (lyrics.get("orig").isEmpty()) throw LyricsNotFoundException("QQ 音乐歌词内容为空")
        lyrics
    }

    private fun requestLyric(musicId: Long, lrcType: Int): String {
        val body = "version=15&miniversion=100&lrctype=$lrcType&musicid=$musicId"
        val response = Http.post(
            url = LYRIC_URL,
            body = body.toByteArray(Charsets.UTF_8),
            contentType = "application/x-www-form-urlencoded",
            headers = baseHeaders(),
        )
        Http.requireOk(response, LYRIC_URL)
        return response.text
    }

    /** 取 <tag ...><![CDATA[...]]></tag> 的内容 */
    private fun cdata(xml: String, tag: String): String {
        val regex = Regex("<$tag[^>]*>\\s*<!\\[CDATA\\[(.*?)]]>\\s*</$tag>", RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun looksLikeHex(text: String): Boolean =
        text.length >= 32 && text.length % 2 == 0 &&
            text.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}
