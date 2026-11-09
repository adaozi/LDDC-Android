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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.lddc.android.data.crypto.DeviceIdProvider
import org.lddc.android.data.crypto.EapiCrypto
import org.lddc.android.data.model.Language
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.SongListType
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers
import java.security.SecureRandom

/**
 * 网易云音乐。对应上游 LDDC/core/api/lyrics/ne.py
 *
 * 关键点：
 *  - 全站 eapi 接口：AES-128-ECB 加密参数，body 为 `params=HEX(密文)`
 *  - 首次使用需游客登录（/eapi/register/anonimous），拿 MUSIC_A 等 cookie
 *  - deviceId 从 24641 条的池子里随机取（DeviceIdProvider）
 */
object NeApi : LyricsSource {

    private const val HOST = "https://interface.music.163.com"
    private const val APP_VER = "3.1.3.203419"
    private const val PAGE_SIZE = 20

    override val source: Source = Source.NE
    override val supportedSearchTypes: Set<SearchType> =
        setOf(SearchType.SONG, SearchType.ALBUM, SearchType.SONGLIST)

    private val mutex = Mutex()
    private val random = SecureRandom()

    @Volatile
    private var cookies: MutableMap<String, String> = LinkedHashMap()

    @Volatile
    private var expireAt: Long = 0

    @Volatile
    private var userId: String = ""

    // ---------- 登录与请求 ----------

    private suspend fun ensureInited() {
        mutex.withLock {
            if (cookies.isNotEmpty() && expireAt > System.currentTimeMillis() / 1000) return
            register()
        }
    }

    private fun register() {
        val deviceId = DeviceIdProvider.random()
        val clientSign = "${DeviceIdProvider.randomMac()}@@@${DeviceIdProvider.randomUpperLetters()}@@@@@@${DeviceIdProvider.randomHex64()}"
        val preCookies = linkedMapOf(
            "os" to "pc",
            "deviceId" to deviceId,
            "osver" to "Microsoft-Windows-10--build-${200 + random.nextInt(101)}00-64bit",
            "clientSign" to clientSign,
            "channel" to "netease",
            "mode" to listOf("MS-iCraft B760M WIFI", "ASUS ROG STRIX Z790", "MSI MAG B550 TOMAHAWK", "ASRock X670E Taichi")
                .random(kotlin.random.Random.Default),
            "appver" to APP_VER,
        )

        val params = buildJsonObject {
            put("username", EapiCrypto.getAnonymousUsername(deviceId))
            put("e_r", true)
            put("header", paramsHeader(preCookies))
        }

        val url = "$HOST/eapi/register/anonimous"
        val body = EapiCrypto.eapiParamsEncrypt("/api/register/anonimous", params.toString())
        val response = Http.post(
            url = url,
            body = body,
            contentType = "application/x-www-form-urlencoded",
            headers = headerPairs(preCookies),
        )
        Http.requireOk(response, url)

        val data = Json.parseToJsonElement(EapiCrypto.eapiResponseDecrypt(response.body)).jsonObject
        val code = data.int("code")
        if (code != 200) {
            throw LyricsSourceException("网易云游客登录失败: code=$code ${data.str("message").orEmpty()}")
        }
        userId = data.str("userId").orEmpty()

        val setCookies = response.cookies()
        val merged = linkedMapOf(
            "WEVNSM" to "1.0.0",
            "os" to preCookies.getValue("os"),
            "deviceId" to deviceId,
            "osver" to preCookies.getValue("osver"),
            "clientSign" to clientSign,
            "channel" to "netease",
            "NMTID" to setCookies["NMTID"].orEmpty(),
            "MUSIC_A" to setCookies["MUSIC_A"].orEmpty(),
            "__csrf" to setCookies["__csrf"].orEmpty(),
            "appver" to APP_VER,
            "WNMCID" to buildString {
                append((0 until 6).joinToString("") { ('a'.code + random.nextInt(26)).toChar().toString() })
                append('.')
                append(System.currentTimeMillis() - random.nextInt(9000) - 1000)
                append(".01.0")
            },
        )
        merged.entries.removeAll { it.value.isEmpty() }
        cookies = merged
        expireAt = System.currentTimeMillis() / 1000 + 864_000
    }

    private fun paramsHeader(source: Map<String, String>): String = buildJsonObject {
        put("clientSign", source["clientSign"].orEmpty())
        put("os", source["os"].orEmpty())
        put("appver", source["appver"].orEmpty())
        put("deviceId", source["deviceId"].orEmpty())
        put("requestId", 0)
        put("osver", source["osver"].orEmpty())
    }.toString()

    private fun headerPairs(source: Map<String, String>): List<Pair<String, String>> = buildList {
        add("accept" to "*/*")
        add("content-type" to "application/x-www-form-urlencoded")
        source.forEach { (k, v) -> add("cookie" to "$k=$v") }
        add("mconfig-info" to """{"IuRPVVmc3WWul9fT":{"version":733184,"appver":"$APP_VER"}}""")
        add("origin" to "orpheus://orpheus")
        add(
            "user-agent" to
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER",
        )
        add("sec-ch-ua" to "\"Chromium\";v=\"91\"")
        add("sec-ch-ua-mobile" to "?0")
        add("sec-fetch-site" to "cross-site")
        add("sec-fetch-mode" to "cors")
        add("sec-fetch-dest" to "empty")
        add("accept-language" to "en-US,en;q=0.9")
    }

    /** 发送 eapi 请求并解密响应（阻塞调用，需在 IO 线程执行） */
    private fun request(path: String, params: JsonObject): JsonObject {
        val augmented = JsonObject(
            params + mapOf(
                "e_r" to JsonPrimitive(true),
                "header" to JsonPrimitive(paramsHeader(cookies)),
            ),
        )
        val body = EapiCrypto.eapiParamsEncrypt(path.replace("eapi", "api"), augmented.toString())
        val url = HOST + path
        val response = Http.post(url, body, "application/x-www-form-urlencoded", headerPairs(cookies))
        Http.requireOk(response, url)

        val data = Json.parseToJsonElement(EapiCrypto.eapiResponseDecrypt(response.body)).jsonObject
        val code = data.int("code")
        if (code != 200) {
            throw LyricsSourceException("网易云接口错误: code=$code ${data.str("message").orEmpty()}")
        }
        return data
    }

    // ---------- 搜索 ----------

    override suspend fun searchSongs(keyword: String, page: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        ensureInited()
        val params = buildJsonObject {
            put("keyword", keyword)
            put("scene", "NORMAL")
            put("needCorrect", "true")
            put("limit", "$PAGE_SIZE")
            put("offset", "${(page - 1) * PAGE_SIZE}")
        }
        val data = request("/eapi/search/song/list/page", params)
        data.obj("data")?.arr("resources")?.objects().orEmpty().mapNotNull { resource ->
            resource.obj("baseInfo")?.obj("simpleSongData")?.let(::formatSongInfo)
        }
    }

    override suspend fun searchSongLists(
        keyword: String,
        type: SearchType,
        page: Int,
    ): List<SongListInfo> = withContext(Dispatchers.IO) {
        ensureInited()
        val path = when (type) {
            SearchType.ALBUM -> "/eapi/v1/search/album/get"
            SearchType.SONGLIST -> "/eapi/v1/search/playlist/get"
            else -> throw LyricsSourceException("网易云不支持该搜索类型: $type")
        }
        val params = buildJsonObject {
            put("s", keyword)
            put("queryCorrect", "true")
            put("limit", "$PAGE_SIZE")
            put("offset", "${(page - 1) * PAGE_SIZE}")
        }
        val result = request(path, params).obj("result") ?: return@withContext emptyList()

        when (type) {
            SearchType.ALBUM -> result.arr("albums")?.objects().orEmpty().mapNotNull { album ->
                val id = album.str("id") ?: return@mapNotNull null
                SongListInfo(
                    source = source,
                    type = SongListType.ALBUM,
                    id = id,
                    title = album.str("name").orEmpty(),
                    imgUrl = album.str("picUrl").orEmpty(),
                    songCount = album.int("size"),
                    publishTime = album.long("publishTime")?.div(1000),
                    author = album.arr("artists")?.objects()?.firstOrNull()?.str("name").orEmpty(),
                )
            }

            SearchType.SONGLIST -> result.arr("playlists")?.objects().orEmpty().mapNotNull { playlist ->
                val id = playlist.str("id") ?: return@mapNotNull null
                SongListInfo(
                    source = source,
                    type = SongListType.SONGLIST,
                    id = id,
                    title = playlist.str("name").orEmpty(),
                    imgUrl = playlist.str("coverImgUrl").orEmpty(),
                    songCount = playlist.int("trackCount"),
                    author = playlist.obj("creator")?.str("nickname").orEmpty(),
                )
            }

            else -> emptyList()
        }
    }

    // ---------- 歌单 / 专辑内容 ----------

    override suspend fun getSongList(songList: SongListInfo): List<SongInfo> = withContext(Dispatchers.IO) {
        ensureInited()
        when (songList.type) {
            SongListType.ALBUM -> {
                val params = buildJsonObject {
                    put("id", songList.id)
                    put("cache_key", EapiCrypto.getCacheKey("e_r=true&id=${songList.id}"))
                }
                request("/eapi/album/v3/detail", params).arr("songs")?.objects().orEmpty()
                    .mapNotNull(::formatSongInfo)
            }

            SongListType.SONGLIST -> {
                val detailParams = buildJsonObject {
                    put("id", songList.id)
                    put("n", "0")
                    put("s", "0")
                    put("cache_key", EapiCrypto.getCacheKey("e_r=true&id=${songList.id}&n=0&s=0"))
                }
                val trackIds = request("/eapi/v1/playlist/detail", detailParams)
                    .obj("playlist")?.arr("trackIds")?.objects().orEmpty()

                if (trackIds.isEmpty()) return@withContext emptyList()

                // 每批 500 首，避免请求体过大
                trackIds.chunked(500).flatMap { chunk ->
                    val c = buildJsonArray {
                        chunk.forEach { track ->
                            add(buildJsonObject {
                                put("id", track.str("id").orEmpty())
                                put("v", 0)
                            })
                        }
                    }
                    val params = buildJsonObject {
                        put("c", c.toString())
                        put("trialMode", "-1")
                    }
                    request("/eapi/v3/song/detail", params).arr("songs")?.objects().orEmpty()
                        .mapNotNull(::formatSongInfo)
                }
            }
        }
    }

    // ---------- 歌词 ----------

    override suspend fun getLyrics(info: LyricInfo): Lyrics = withContext(Dispatchers.IO) {
        ensureInited()
        val song = info.songInfo
        val id = info.id ?: song.id ?: throw LyricsSourceException("网易云歌词需要歌曲 id")
        val numericId = id.toLongOrNull() ?: throw LyricsSourceException("网易云歌曲 id 非法: $id")

        val params = buildJsonObject {
            put("id", numericId)
            put("lv", "-1")
            put("tv", "-1")
            put("rv", "-1")
            put("yv", "-1")
        }
        val data = request("/eapi/song/lyric/v1", params)

        val lyrics = Lyrics(info)
        val tags = linkedMapOf<String, String>()
        song.title?.let { tags["ti"] = it }
        if (song.artist.isNotEmpty()) tags["ar"] = song.strArtist
        song.album?.let { tags["al"] = it }
        data.obj("lyricUser")?.str("nickname")?.let { tags["by"] = it }
        data.obj("transUser")?.str("nickname")?.let { translator ->
            val existing = tags["by"]
            tags["by"] = if (existing != null && existing != translator) "$existing & $translator" else translator
        }
        lyrics.tags.putAll(tags)

        val hasYrc = (data.obj("yrc")?.str("lyric")?.length ?: 0) > 0
        val mapping = if (hasYrc) {
            listOf("orig" to "yrc", "ts" to "tlyric", "roma" to "romalrc", "orig_lrc" to "lrc")
        } else {
            listOf("orig" to "lrc", "ts" to "tlyric", "roma" to "romalrc")
        }

        for ((lang, key) in mapping) {
            val text = data.obj(key)?.str("lyric") ?: continue
            if (text.isEmpty()) continue
            val lines = when {
                key == "yrc" -> LyricsParsers.yrc2data(text)
                text.contains("[") && text.contains("]") -> LyricsParsers.lrc2data(text, Source.NE).second
                else -> LyricsParsers.plaintext2data(text)
            }
            if (lines.isNotEmpty()) lyrics[lang] = lines
        }

        if (lyrics.get("orig").isEmpty()) throw LyricsNotFoundException("网易云未返回歌词")
        lyrics
    }

    private fun formatSongInfo(song: JsonObject): SongInfo? {
        val id = song.str("id") ?: return null
        val artists = song.arr("ar")?.objects().orEmpty()
            .mapNotNull { it.str("name")?.takeIf { name -> name.isNotEmpty() } }
        val alias = song.arr("alia")?.firstOrNull()?.let { (it as? JsonPrimitive)?.content }.orEmpty()
        return SongInfo(
            source = source,
            id = id,
            title = song.str("name"),
            subtitle = alias.ifEmpty { null },
            artist = artists,
            album = song.obj("al")?.str("name"),
            duration = song.int("dt"),
            language = Language.OTHER,
        )
    }

    /** 调试用：当前游客账号 id */
    fun currentUserId(): String = userId

    /** 调试用：拉取原始歌词响应，便于排查 yrc/lrc 是否可用 */
    internal suspend fun debugRawLyric(songId: String): JsonObject = withContext(Dispatchers.IO) {
        ensureInited()
        request(
            "/eapi/song/lyric/v1",
            buildJsonObject {
                put("id", songId.toLongOrNull() ?: 0L)
                put("lv", "-1")
                put("tv", "-1")
                put("rv", "-1")
                put("yv", "-1")
            },
        )
    }
}
