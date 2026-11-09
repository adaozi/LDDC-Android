// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.lddc.android.data.crypto.EapiCrypto
import org.lddc.android.data.crypto.KrcCrypto
import org.lddc.android.data.model.Language
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.SongListType
import org.lddc.android.data.model.Source
import org.lddc.android.data.parser.LyricsParsers
import java.util.Base64

/**
 * 酷狗音乐。对应上游 LDDC/core/api/lyrics/kg.py
 *
 * 关键点：
 *  - 签名：md5(secret + 参数按 key 排序拼 "k=v" + data + secret)
 *  - 需先调 r_register_dev 拿 dfid（缓存 30 分钟）
 *  - 歌词是 KRC：base64 → 去 4 字节头 → XOR → zlib
 */
object KgApi : LyricsSource {

    private const val SECRET = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    private const val PAGE_SIZE = 20

    private const val URL_SEARCH_SONG = "https://complexsearch.kugou.com/v2/search/song"
    private const val URL_SEARCH_ALBUM = "https://complexsearch.kugou.com/v1/search/album"
    private const val URL_SEARCH_SPECIAL = "https://complexsearch.kugou.com/v1/search/special"
    private const val URL_ALBUM_SONGS = "https://openapi.kugou.com/kmr/v1/album_songlist"
    private const val URL_SPECIAL_SONGS = "https://pubsongscdn.kugou.com/v4/get_other_list_file"
    private const val URL_LYRIC_SEARCH = "https://lyrics.kugou.com/v1/search"
    private const val URL_LYRIC_DOWNLOAD = "https://lyrics.kugou.com/download"
    private const val URL_REGISTER = "https://userservice.kugou.com/risk/v1/r_register_dev"

    private val SEARCH_MODULE = mapOf(
        SearchType.SONG to "SearchSong",
        SearchType.ALBUM to "SearchAlbum",
        SearchType.SONGLIST to "SearchSongRecommand",
    )

    private val SEARCH_URL = mapOf(
        SearchType.SONG to URL_SEARCH_SONG,
        SearchType.ALBUM to URL_SEARCH_ALBUM,
        SearchType.SONGLIST to URL_SEARCH_SPECIAL,
    )

    private val LANGUAGE_MAPPING = mapOf(
        "英语" to Language.ENGLISH,
        "韩语" to Language.KOREAN,
        "日语" to Language.JAPANESE,
        "粤语" to Language.CHINESE,
        "国语" to Language.CHINESE,
    )

    override val source: Source = Source.KG
    override val supportedSearchTypes: Set<SearchType> =
        setOf(SearchType.SONG, SearchType.ALBUM, SearchType.SONGLIST)

    private val mutex = Mutex()

    @Volatile
    private var dfid: String? = null

    @Volatile
    private var dfidExpireAt: Long = 0

    private fun md5(text: String): String = EapiCrypto.md5Hex(text.toByteArray(Charsets.UTF_8))

    // ---------- 设备注册 ----------

    private fun ensureDfid() {
        if (dfid != null && System.currentTimeMillis() < dfidExpireAt) return

        val mid = md5(System.currentTimeMillis().toString())
        val registerParams = linkedMapOf("appid" to "1014", "platid" to "4", "mid" to mid)
        val sortedValues = registerParams.values.filter { it.isNotEmpty() }.sorted().joinToString("")
        val signature = md5("1014${sortedValues}1014")

        val url = URL_REGISTER.toHttpUrl().newBuilder().apply {
            registerParams.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("signature", signature)
        }.build()

        val body = Base64.getEncoder().encodeToString("""{"uuid":""}""".toByteArray(Charsets.UTF_8))
        val headers = listOf(
            "User-Agent" to "Android14-1070-11070-201-0-SearchSong-wifi",
            "Content-Type" to "application/x-www-form-urlencoded",
        )
        val response = Http.post(url.toString(), body.toByteArray(Charsets.UTF_8), "application/x-www-form-urlencoded", headers)
        val value = runCatching {
            Json.parseToJsonElement(response.text).jsonObject.obj("data")?.str("dfid")
        }.getOrNull()

        dfid = value ?: "-"
        dfidExpireAt = System.currentTimeMillis() + 30 * 60 * 1000
    }

    // ---------- 请求与签名 ----------

    private fun sign(params: Map<String, String>, data: String?): String {
        val joined = params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        return md5(SECRET + joined + (data ?: "") + SECRET)
    }

    private fun request(
        url: String,
        params: Map<String, String>,
        module: String,
        data: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonObject {
        ensureDfid()
        val now = System.currentTimeMillis()
        val mid = md5(now.toString())

        val headers = linkedMapOf(
            "User-Agent" to "Android14-1070-11070-201-0-$module-wifi",
            "Connection" to "Keep-Alive",
            "KG-Rec" to "1",
            "KG-RC" to "1",
            "KG-CLIENTTIMEMS" to now.toString(),
            "mid" to mid,
        )
        headers.putAll(extraHeaders)

        val finalParams = LinkedHashMap<String, String>()
        if (module == "Lyric") {
            finalParams["appid"] = "3116"
            finalParams["clientver"] = "11070"
        } else if (module == "album_song_list") {
            // 专辑曲目接口用另一套参数（真实 dfid + KG-TID 头），套通用参数会返回 20006
            finalParams["dfid"] = dfid ?: "-"
            finalParams["appid"] = "3116"
            finalParams["mid"] = mid
            finalParams["clientver"] = "11070"
            finalParams["clienttime"] = (now / 1000).toString()
            finalParams["uuid"] = "-"
            headers["KG-TID"] = "221"
        } else {
            finalParams["userid"] = "0"
            finalParams["appid"] = "3116"
            finalParams["token"] = ""
            finalParams["clienttime"] = (now / 1000).toString()
            finalParams["iscorrection"] = "1"
            finalParams["uuid"] = "-"
            finalParams["mid"] = mid
            finalParams["dfid"] = "-"
            finalParams["clientver"] = "11070"
            finalParams["platform"] = "AndroidFilter"
        }
        finalParams.putAll(params)
        finalParams["signature"] = sign(finalParams, data)

        val builder = url.toHttpUrl().newBuilder()
        finalParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val fullUrl = builder.build().toString()

        val response = if (data != null) {
            // 注意：上游用 httpx 的 data= 发 JSON 文本，Content-Type 实际是 form-urlencoded；
            // 改成 application/json 会被服务端拒绝（code=20006）
            Http.post(fullUrl, data.toByteArray(Charsets.UTF_8), "application/x-www-form-urlencoded", headers.toList())
        } else {
            Http.get(fullUrl, headers.toList())
        }
        Http.requireOk(response, url)

        val root = Json.parseToJsonElement(response.text).jsonObject
        val code = root.int("error_code") ?: 0
        if (code != 0 && code != 200) {
            throw LyricsSourceException("酷狗接口错误: code=$code ${root.str("error_msg").orEmpty()}")
        }
        return root
    }

    // ---------- 搜索 ----------

    override suspend fun searchSongs(keyword: String, page: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { ensureDfid() }
        val params = linkedMapOf(
            "sorttype" to "0",
            "keyword" to keyword,
            "pagesize" to PAGE_SIZE.toString(),
            "page" to page.toString(),
        )
        val root = request(
            URL_SEARCH_SONG,
            params,
            SEARCH_MODULE.getValue(SearchType.SONG),
            extraHeaders = mapOf("x-router" to "complexsearch.kugou.com"),
        )
        root.obj("data")?.arr("lists")?.objects().orEmpty().mapNotNull(::formatSong)
    }

    override suspend fun searchSongLists(
        keyword: String,
        type: SearchType,
        page: Int,
    ): List<SongListInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { ensureDfid() }
        val url = SEARCH_URL[type] ?: throw LyricsSourceException("酷狗不支持该搜索类型: $type")
        val params = linkedMapOf(
            "sorttype" to "0",
            "keyword" to keyword,
            "pagesize" to PAGE_SIZE.toString(),
            "page" to page.toString(),
        )
        val root = request(
            url,
            params,
            SEARCH_MODULE.getValue(type),
            extraHeaders = mapOf("x-router" to "complexsearch.kugou.com"),
        )
        val lists = root.obj("data")?.arr("lists")?.objects().orEmpty()

        when (type) {
            SearchType.ALBUM -> lists.mapNotNull { info ->
                val id = info.str("albumid") ?: return@mapNotNull null
                SongListInfo(
                    source = source,
                    type = SongListType.ALBUM,
                    id = id,
                    title = info.str("albumname").orEmpty(),
                    imgUrl = info.str("img").orEmpty(),
                    songCount = info.int("songcount"),
                    publishTime = parseTime(info.str("publish_time"), "yyyy-MM-dd"),
                    author = info.str("singer").orEmpty(),
                )
            }

            SearchType.SONGLIST -> lists.mapNotNull { info ->
                val id = info.str("gid") ?: return@mapNotNull null
                SongListInfo(
                    source = source,
                    type = SongListType.SONGLIST,
                    id = id,
                    title = info.str("specialname").orEmpty(),
                    imgUrl = info.str("img").orEmpty(),
                    songCount = info.int("song_count"),
                    publishTime = parseTime(info.str("publish_time"), "yyyy-MM-dd HH:mm:ss"),
                    author = info.str("nickname").orEmpty(),
                )
            }

            else -> emptyList()
        }
    }

    private fun parseTime(text: String?, pattern: String): Long? {
        if (text.isNullOrEmpty() || text.startsWith("0000-00-00")) return null
        return runCatching {
            val formatter = java.time.format.DateTimeFormatter.ofPattern(pattern)
            java.time.LocalDateTime.parse(text, formatter)
                .atZone(java.time.ZoneOffset.ofHours(8))
                .toEpochSecond()
        }.getOrNull()
    }

    private fun formatSong(song: JsonObject): SongInfo? {
        val id = song.str("ID") ?: return null
        val languageKey = song.obj("trans_param")?.str("language")
        return SongInfo(
            source = source,
            id = id,
            hash = song.str("FileHash"),
            title = song.str("SongName"),
            subtitle = song.str("Auxiliary")?.takeIf { it.isNotEmpty() },
            artist = song.arr("Singers")?.objects().orEmpty()
                .mapNotNull { it.str("name")?.takeIf { name -> name.isNotEmpty() } },
            album = song.str("AlbumName"),
            duration = song.int("Duration")?.times(1000),
            language = languageKey?.let { LANGUAGE_MAPPING[it] } ?: Language.OTHER,
        )
    }

    // ---------- 歌词 ----------

    override suspend fun getSongList(songList: SongListInfo): List<SongInfo> = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (songList.type) {
                SongListType.ALBUM -> fetchAlbumSongs(songList.id)
                SongListType.SONGLIST -> fetchSpecialSongs(songList.id)
            }
        }
    }

    /** 专辑曲目：POST /kmr/v1/album_songlist */
    private fun fetchAlbumSongs(albumId: String): List<SongInfo> {
        val body = """{"pagesize":"2147483647","album_id":"$albumId","page":"1"}"""
        val root = request(URL_ALBUM_SONGS, emptyMap(), "album_song_list", data = body)
        return root.obj("data")?.arr("songs")?.objects().orEmpty().mapNotNull { info ->
            val base = info.obj("base") ?: return@mapNotNull null
            val audio = info.obj("audio_info")
            val trans = info.obj("trans_param")
            val suffix = trans?.str("songname_suffix").orEmpty()
            val rawName = base.str("audio_name").orEmpty()
            SongInfo(
                source = source,
                id = base.str("album_audio_id"),
                hash = audio?.str("hash"),
                title = if (suffix.isNotEmpty() && rawName.endsWith(suffix)) rawName.dropLast(suffix.length) else rawName,
                subtitle = suffix.ifEmpty { null },
                artist = base.str("author_name").orEmpty().split("、").filter { it.isNotBlank() },
                album = info.obj("album_info")?.str("album_name"),
                duration = audio?.int("duration"),
                language = trans?.str("language")?.let { LANGUAGE_MAPPING[it] } ?: Language.OTHER,
            )
        }
    }

    /** 歌单曲目：GET /v4/get_other_list_file */
    private fun fetchSpecialSongs(specialId: String): List<SongInfo> {
        val params = linkedMapOf(
            "specialid" to specialId,
            "need_sort" to "1",
            "module" to "CloudMusic",
            "pagesize" to "-1",
            "global_collection_id" to specialId,
            "page" to "1",
            "type" to "0",
        )
        val root = request(URL_SPECIAL_SONGS, params, "SongList")
        return root.obj("data")?.arr("info")?.objects().orEmpty().mapNotNull { info ->
            val name = info.str("name").orEmpty()
            SongInfo(
                source = source,
                id = info.str("mixsongid"),
                hash = info.str("hash"),
                title = if (name.contains(" - ")) name.substringAfter(" - ") else name,
                subtitle = info.str("remark"),
                // singerinfo 是数组（每项 {name:...}），用 arr 不能用 obj
                artist = info.arr("singerinfo")?.objects().orEmpty()
                    .mapNotNull { it.str("name")?.trim()?.takeIf { n -> n.isNotEmpty() } },
                album = info.obj("albuminfo")?.str("name"),
                duration = info.int("timelen"),
                language = info.obj("trans_param")?.str("language")?.let { LANGUAGE_MAPPING[it] } ?: Language.OTHER,
            )
        }
    }

    override suspend fun getLyricsList(song: SongInfo): List<LyricInfo> = withContext(Dispatchers.IO) {
        mutex.withLock { fetchLyricsListInternal(song) }
    }

    /** 注意：本函数不加锁（getLyrics 已在锁内调用它，Mutex 不可重入） */
    private fun fetchLyricsListInternal(song: SongInfo): List<LyricInfo> {
        val keyword = buildString {
            append(song.strArtist)
            append(" - ")
            append(song.title.orEmpty())
        }
        val params = linkedMapOf(
            "album_audio_id" to (song.id ?: ""),
            "duration" to (song.duration?.toString() ?: ""),
            "hash" to (song.hash ?: ""),
            "keyword" to keyword,
            "lrctxt" to "1",
            "man" to "no",
        )
        val root = request(URL_LYRIC_SEARCH, params, "Lyric")
        return root.arr("candidates")?.objects().orEmpty().mapNotNull { candidate ->
            val id = candidate.str("id") ?: return@mapNotNull null
            LyricInfo(
                source = source,
                songInfo = song,
                id = id,
                accesskey = candidate.str("accesskey"),
                duration = candidate.int("duration"),
                creator = candidate.str("nickname"),
                score = candidate.int("score"),
            )
        }
    }

    override suspend fun getLyrics(info: LyricInfo): Lyrics = withContext(Dispatchers.IO) {
        mutex.withLock { ensureDfid() }

        val target = if (info.accesskey.isNullOrEmpty()) {
            fetchLyricsListInternal(info.songInfo).firstOrNull()
                ?: throw LyricsNotFoundException("酷狗没有找到歌词")
        } else {
            info
        }

        val params = linkedMapOf(
            "accesskey" to (target.accesskey ?: ""),
            "charset" to "utf8",
            "client" to "mobi",
            "fmt" to "krc",
            "id" to (target.id ?: ""),
            "ver" to "1",
        )
        val root = request(URL_LYRIC_DOWNLOAD, params, "Lyric")
        val content = root.str("content") ?: throw LyricsNotFoundException("酷狗歌词内容为空")
        val lyrics = Lyrics(target)

        if (root.int("contenttype") == 2) {
            val plain = String(Base64.getDecoder().decode(content), Charsets.UTF_8)
            lyrics["orig"] = LyricsParsers.plaintext2data(plain)
        } else {
            val decoded = runCatching { KrcCrypto.decrypt(Base64.getDecoder().decode(content)) }
                .getOrElse { throw LyricsSourceException("酷狗 KRC 解密失败: ${it.message}", it) }
            val (tags, data) = LyricsParsers.krc2mdata(decoded)
            lyrics.tags.putAll(tags)
            data.forEach { (lang, lines) -> lyrics[lang] = lines }
        }

        if (lyrics.get("orig").isEmpty()) throw LyricsNotFoundException("酷狗歌词为空")
        lyrics
    }
}
