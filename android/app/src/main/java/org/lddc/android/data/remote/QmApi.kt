// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.lddc.android.data.crypto.QrcCrypto
import org.lddc.android.data.model.Language
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers
import java.util.Base64
import kotlin.random.Random

/**
 * QQ 音乐。对应上游 LDDC/core/api/lyrics/qm.py
 *
 * 关键点：
 *  - 统一走 musicu.fcg 网关，请求体 {comm, request:{method,module,param}}
 *  - 首次需 GetSession 拿 uid/sid/userip 填进 comm
 *  - 歌词是加密 QRC（hex → 3DES → zlib），解密后是逐字格式
 */
object QmApi : LyricsSource {

    private const val URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    override val source: Source = Source.QM
    override val supportedSearchTypes: Set<SearchType> = setOf(SearchType.SONG)

    private val mutex = Mutex()
    private val json = Json

    @Volatile
    private var inited = false

    @Volatile
    private var comm: JsonObject = baseComm()

    private val headers = listOf(
        "cookie" to "tmeLoginType=-1;",
        // 注意：不要手写 accept-encoding，否则 OkHttp 不会透明解压 gzip
        "user-agent" to "okhttp/3.14.9",
    )

    private fun baseComm(): JsonObject = buildJsonObject {
        put("ct", 11)
        put("cv", "1003006")
        put("v", "1003006")
        put("os_ver", "15")
        put("phonetype", "24122RKC7C")
        put("rom", "Redmi/miro/miro:15/AE3A.240806.005/OS2.0.105.0.VOMCNXM:user/release-keys")
        put("tmeAppID", "qqmusiclight")
        put("nettype", "NETWORK_WIFI")
        put("udid", "0")
    }

    private fun request(method: String, module: String, param: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("comm", comm)
            put(
                "request",
                buildJsonObject {
                    put("method", method)
                    put("module", module)
                    put("param", param)
                },
            )
        }.toString().toByteArray(Charsets.UTF_8)

        val response = Http.post(URL, body, "application/json", headers)
        Http.requireOk(response, URL)

        val root = json.parseToJsonElement(response.text).jsonObject
        val request = root.obj("request")
        val code = root.int("code") ?: 0
        val requestCode = request?.int("code") ?: 0
        if (code != 0 || requestCode != 0) {
            throw LyricsSourceException(
                "QQ音乐接口错误: code=$code/${requestCode} ${root.str("msg").orEmpty()}",
            )
        }
        return request?.obj("data") ?: JsonObject(emptyMap())
    }

    private fun ensureSession() {
        if (inited) return
        val data = request(
            "GetSession",
            "music.getSession.session",
            buildJsonObject {
                put("caller", 0)
                put("uid", "0")
                put("vkey", 0)
            },
        )
        val session = data.obj("session") ?: return
        comm = JsonObject(
            comm + mapOf(
                "uid" to JsonPrimitive(session.str("uid") ?: "0"),
                "sid" to JsonPrimitive(session.str("sid") ?: ""),
                "userip" to JsonPrimitive(session.str("userip") ?: ""),
            ),
        )
        inited = true
    }

    // ---------- 搜索 ----------

    override suspend fun searchSongs(keyword: String, page: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { ensureSession() }

        val param = buildJsonObject {
            put("search_id", buildSearchId())
            put("remoteplace", "search.android.keyboard")
            put("query", keyword)
            put("search_type", 0)
            put("num_per_page", PAGE_SIZE)
            put("page_num", page)
            put("highlight", 0)
            put("nqc_flag", 0)
            put("page_id", 1)
            put("grp", 1)
        }
        val data = request("DoSearchForQQMusicLite", "music.search.SearchCgiService", param)
        data.obj("body")?.arr("item_song")?.objects().orEmpty().mapNotNull(::formatSongInfo)
    }

    /** 上游用一串随机数拼 search_id，避免被识别为固定请求 */
    private fun buildSearchId(): String {
        val part = Random.nextInt(1, 21) * 18_014_398_509_481_984L
        val mid = Random.nextInt(0, 4_194_305).toLong() * 4_294_967_296L
        val time = (System.currentTimeMillis() % 86_400_000L)
        return (part + mid + time).toString()
    }

    private fun formatSongInfo(song: JsonObject): SongInfo? {
        val id = song.str("id") ?: return null
        val singers = song.arr("singer")?.objects().orEmpty()
            .mapNotNull { it.str("name")?.takeIf { name -> name.isNotEmpty() } }
        val language = when (song.int("language")) {
            9 -> Language.INSTRUMENTAL
            5 -> Language.ENGLISH
            4 -> Language.KOREAN
            3 -> Language.JAPANESE
            0, 1 -> Language.CHINESE
            else -> Language.OTHER
        }
        return SongInfo(
            source = source,
            id = id,
            mid = song.str("mid"),
            title = song.str("title"),
            subtitle = song.str("subtitle"),
            artist = singers,
            album = song.obj("album")?.str("name"),
            duration = song.int("interval")?.times(1000),
            language = language,
        )
    }

    // ---------- 歌词 ----------

    override suspend fun getLyrics(info: LyricInfo): Lyrics = withContext(Dispatchers.IO) {
        mutex.withLock { ensureSession() }

        val song = info.songInfo
        val title = song.title ?: throw LyricsSourceException("QQ音乐需要歌曲标题")
        val album = song.album ?: throw LyricsSourceException("QQ音乐需要专辑名")
        val id = (info.id ?: song.id) ?: throw LyricsSourceException("QQ音乐需要歌曲 id")
        val duration = song.duration ?: throw LyricsSourceException("QQ音乐需要歌曲时长")

        val b64 = fun(value: String): String =
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

        val param = buildJsonObject {
            put("albumName", b64(album))
            put("crypt", 1)
            put("ct", 19)
            put("cv", 2111)
            put("interval", duration / 1000)
            put("lrc_t", 0)
            put("qrc", 1)
            put("qrc_t", 0)
            put("roma", 1)
            put("roma_t", 0)
            put("singerName", b64(song.strArtist))
            put("songID", id.toLongOrNull() ?: throw LyricsSourceException("QQ音乐歌曲 id 非法: $id"))
            put("songName", b64(title))
            put("trans", 1)
            put("trans_t", 0)
            put("type", 0)
        }

        val data = request("GetPlayLyricInfo", "music.musichallSong.PlayLyricInfo", param)

        val lyrics = Lyrics(info)
        val qrcTimestamp = data.int("qrc_t") ?: 0
        val lrcTimestamp = data.int("lrc_t") ?: 0

        val entries = listOf(
            Triple("orig", "lyric", if (qrcTimestamp != 0) qrcTimestamp else lrcTimestamp),
            Triple("ts", "trans", data.int("trans_t") ?: 0),
            Triple("roma", "roma", data.int("roma_t") ?: 0),
        )

        for ((lang, field, timestamp) in entries) {
            val raw = data.str(field).orEmpty()
            if (raw.isEmpty() || timestamp == 0) continue
            val decrypted = runCatching { QrcCrypto.decryptHex(raw) }.getOrNull() ?: continue
            if (decrypted.isBlank()) continue
            val (tags, lines) = LyricsParsers.qrcStrParse(decrypted)
            if (lang == "orig") {
                lyrics.tags.putAll(tags)
                song.title?.let { lyrics.tags.putIfAbsent("ti", it) }
                song.album?.let { lyrics.tags.putIfAbsent("al", it) }
                if (song.artist.isNotEmpty()) lyrics.tags.putIfAbsent("ar", song.strArtist)
            }
            if (lines.isNotEmpty()) lyrics[lang] = lines
        }

        if (lyrics.get("orig").isEmpty()) throw LyricsNotFoundException("QQ音乐未返回歌词")
        lyrics
    }

    private const val PAGE_SIZE = 20

    /** 调试用：拉取原始歌词响应 */
    internal suspend fun debugRawLyric(song: SongInfo): JsonObject = withContext(Dispatchers.IO) {
        mutex.withLock { ensureSession() }
        val title = song.title ?: throw LyricsSourceException("需要标题")
        val album = song.album ?: throw LyricsSourceException("需要专辑")
        val id = song.id ?: throw LyricsSourceException("需要 id")
        val duration = song.duration ?: throw LyricsSourceException("需要时长")
        val b64 = fun(value: String): String =
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
        request(
            "GetPlayLyricInfo",
            "music.musichallSong.PlayLyricInfo",
            buildJsonObject {
                put("albumName", b64(album))
                put("crypt", 1)
                put("ct", 19)
                put("cv", 2111)
                put("interval", duration / 1000)
                put("lrc_t", 0)
                put("qrc", 1)
                put("qrc_t", 0)
                put("roma", 1)
                put("roma_t", 0)
                put("singerName", b64(song.strArtist))
                put("songID", id.toLongOrNull() ?: 0L)
                put("songName", b64(title))
                put("trans", 1)
                put("trans_t", 0)
                put("type", 0)
            },
        )
    }
}
