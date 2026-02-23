package com.example.lddc.service.api

import android.util.Log
import com.example.lddc.model.APIResultList
import com.example.lddc.model.Artist
import com.example.lddc.model.Language
import com.example.lddc.model.LyricInfo
import com.example.lddc.model.Lyrics
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source
import com.example.lddc.service.decryptor.KrcDecoder
import com.example.lddc.service.network.ApiException
import com.example.lddc.service.network.NetworkModule
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 酷狗音乐API实现
 *
 * 提供酷狗音乐平台的歌曲搜索和歌词获取功能
 * 使用酷狗音乐移动端API接口
 * 支持KRC格式歌词（加密）的解密
 *
 * 主要功能：
 * - 搜索歌曲（主API + 备用API）
 * - 获取歌词列表（多版本候选）
 * - 下载歌词（支持KRC和纯文本格式）
 * - 请求签名验证
 */
class KugouApi(private val httpClient: HttpClient) {

    companion object {
        /** 搜索API地址 */
        private val SEARCH_URL = "http://complexsearch.kugou.com/v2/search/song"

        /** 搜索模块标识 */
        private val SEARCH_MODULE = "SearchSong"

        /** 语言名称映射表：酷狗语言名 -> Language枚举 */
        private val LANGUAGE_MAPPING = mapOf(
            "伴奏" to Language.INSTRUMENTAL,
            "纯音乐" to Language.INSTRUMENTAL,
            "英语" to Language.ENGLISH,
            "韩语" to Language.KOREAN,
            "日语" to Language.JAPANESE,
            "粤语" to Language.CHINESE,
            "国语" to Language.CHINESE
        )

        /** 酷狗API域名列表（负载均衡） */
        private val KG_DOMAINS = listOf(
            "mobiles.kugou.com",
            "msearchcdn.kugou.com",
            "mobilecdnbj.kugou.com",
            "msearch.kugou.com"
        )
    }

    /**
     * 生成API签名
     * 参考 PC 端实现：使用 json.dumps 处理字典类型参数
     */
    private fun generateSignature(params: Map<String, Any>, data: String = ""): String {
        val sortedParams = params.toSortedMap()
        val paramStr = sortedParams.entries.joinToString("") { (key, value) ->
            val valueStr = when (value) {
                is Map<*, *> -> Json.encodeToString(
                    JsonObject.serializer(),
                    value.entries.fold(JsonObject(emptyMap())) { acc, entry ->
                        JsonObject(acc + (entry.key.toString() to JsonPrimitive(entry.value.toString())))
                    }
                )
                else -> value.toString()
            }
            "${key}=${valueStr}"
        }
        val content = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA$paramStr$data" + "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        Log.d("KugouApi", "Signature content: $content")
        return NetworkModule.md5(content)
    }
    
    /**
     * 构建请求参数
     * 参考 PC 端实现，Lyric 模块只需要 appid 和 clientver
     */
    private fun buildParams(module: String, extraParams: Map<String, Any> = emptyMap(), mid: String): Map<String, Any> {
        val params = mutableMapOf<String, Any>()

        when (module) {
            "Lyric" -> {
                params["appid"] = "3116"
                params["clientver"] = "11070"
            }
            "album_song_list" -> {
                params["dfid"] = "-"
                params["appid"] = "3116"
                params["mid"] = mid
                params["clientver"] = "11070"
                params["clienttime"] = NetworkModule.getCurrentTimestamp()
                params["uuid"] = "-"
            }
            else -> {
                params["userid"] = "0"
                params["appid"] = "3116"
                params["token"] = ""
                params["clienttime"] = NetworkModule.getCurrentTimestamp()
                params["iscorrection"] = "1"
                params["uuid"] = "-"
                params["mid"] = mid
                params["dfid"] = "-"
                params["clientver"] = "11070"
                params["platform"] = "AndroidFilter"
            }
        }

        params.putAll(extraParams)
        params["signature"] = generateSignature(params)

        return params
    }

    /**
     * 构建请求头部
     * 参考 PC 端实现，在 headers 中添加 mid
     */
    private fun buildHeaders(module: String, mid: String): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to "Android14-1070-11070-201-0-$module-wifi",
            "Connection" to "Keep-Alive",
            "KG-Rec" to "1",
            "KG-RC" to "1",
            "KG-CLIENTTIMEMS" to NetworkModule.getCurrentTimestampMillis().toString(),
            "mid" to mid
        )
        // album_song_list 模块需要添加 KG-TID 头
        if (module == "album_song_list") {
            headers["KG-TID"] = "221"
        }
        return headers
    }
    
    /**
     * 发送API请求
     */
    private suspend fun request(
        url: String,
        params: Map<String, Any>,
        module: String,
        method: HttpMethod = HttpMethod.Get,
        data: String = ""
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        // 生成 mid 并构建参数和请求头
        val mid = NetworkModule.md5(System.currentTimeMillis().toString())
        val finalParams = buildParams(module, params, mid)
        val headers = buildHeaders(module, mid)

        Log.d("KugouApi", "Request URL: $url")
        Log.d("KugouApi", "Request module: $module")
        Log.d("KugouApi", "Request params: $finalParams")

        val response = if (method == HttpMethod.Get) {
            httpClient.get(url) {
                headers {
                    headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                finalParams.forEach { (key, value) ->
                    parameter(key, value.toString())
                }
            }
        } else {
            httpClient.post(url) {
                headers {
                    headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                finalParams.forEach { (key, value) ->
                    parameter(key, value.toString())
                }
                setBody(data)
            }
        }
        
        val responseData = response.bodyAsText()
        val jsonObject = Json.parseToJsonElement(responseData).jsonObject
        val result = mutableMapOf<String, Any>()
        
        jsonObject.forEach {(key, value) ->
            result[key] = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toMap()
                is JsonArray -> value.toList()
            }
        }
        
        val errorCode = (result["error_code"] as? Number)?.toInt() ?: 0
        
        if (errorCode != 0 && errorCode != 200) {
            val errorMsg = result["error_msg"] as? String ?: "未知错误"
            throw ApiException("酷狗音乐API错误: $errorCode, $errorMsg")
        }
        
        return@withContext result
    }
    
    /**
     * 将JsonObject转换为Map
     */
    private fun JsonObject.toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        this.forEach {(key, value) ->
            result[key] = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toMap()
                is JsonArray -> value.toList()
            }
        }
        return result
    }
    
    /**
     * 将JsonArray转换为List
     */
    private fun JsonArray.toList(): List<Any> {
        val result = mutableListOf<Any>()
        this.forEach {value ->
            result.add(when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toMap()
                is JsonArray -> value.toList()
            })
        }
        return result
    }
    
    /**
     * 搜索歌曲
     */
    suspend fun search(
        keyword: String,
        page: Int = 1
    ): APIResultList<SongInfo> = withContext(Dispatchers.IO) {
        val pagesize = 20

        // 直接传递额外参数，request 函数会调用 buildParams
        val extraParams = mapOf(
            "sorttype" to "0",
            "keyword" to keyword,
            "pagesize" to pagesize,
            "page" to page
        )

        try {
            val data = request(SEARCH_URL, extraParams, SEARCH_MODULE)
            return@withContext processSearchResponse(data, page, pagesize)
        } catch (e: Exception) {
            Log.d("KugouApi", "Search failed, using old search: ${e.message}")
            // 使用备用API
            return@withContext oldSearch(keyword, page)
        }
    }
    
    /**
     * 处理搜索响应
     */
    private fun processSearchResponse(
        data: Map<*, *>,
        page: Int,
        pagesize: Int
    ): APIResultList<SongInfo> {
        val startIndex = (page - 1) * pagesize
        val dataMap = data["data"] as? Map<*, *> ?: emptyMap<String, Any>()
        val lists = dataMap["lists"] as? List<*> ?: emptyList<Any>()
        
        val formattedSongs = formatSongInfos(lists)
        if (formattedSongs.size == pagesize) {
            (dataMap["total"] as? Number)?.toInt() ?: formattedSongs.size
        } else {
            startIndex + formattedSongs.size
        }
        
        return APIResultList(
            results = formattedSongs
        )
    }
    
    /**
     * 备用搜索API
     */
    private suspend fun oldSearch(
        keyword: String,
        page: Int = 1
    ): APIResultList<SongInfo> = withContext(Dispatchers.IO) {
        val domain = KG_DOMAINS.random()
        val pagesize = 20
        (page - 1) * pagesize
        
        val params = mapOf(
            "showtype" to "14",
            "highlight" to "",
            "pagesize" to "30",
            "tag_aggr" to "1",
            "plat" to "0",
            "sver" to "5",
            "keyword" to keyword,
            "correct" to "1",
            "api_ver" to "1",
            "version" to "9108",
            "page" to page.toString()
        )
        
        val response = httpClient.get("http://$domain/api/v3/search/song") {
            parameters {
                params.forEach { (key, value) ->
                    append(key, value)
                }
            }
        }
        
        val responseData = response.bodyAsText()
        val jsonObject = Json.parseToJsonElement(responseData).jsonObject
        val dataMap = (jsonObject["data"] as? JsonObject)?.toMap()?.mapValues { it.value } ?: emptyMap<String, Any>()
        val infoList = (dataMap["info"] as? List<*>) ?: emptyList<Any>()
        
        val formattedSongs = formatOldSongInfos(infoList)
        (dataMap["total"] as? Number)?.toInt() ?: formattedSongs.size
        
        APIResultList(
            results = formattedSongs
        )
    }

    /**
     * 获取歌词
     */
    suspend fun getLyrics(songInfo: SongInfo): Lyrics = withContext(Dispatchers.IO) {
        // 首先获取歌词列表
        val lyricsList = getLyricsList(songInfo)
        if (lyricsList.isEmpty()) {
            throw ApiException("未找到歌词")
        }

        val lyricInfo = lyricsList[0]
        val accesskey = lyricInfo.accesskey ?: throw ApiException("未获取到歌词accesskey")

        Log.d("KugouApi", "Getting lyrics for song: ${songInfo.title}, lyric id: ${lyricInfo.id}, accesskey: $accesskey")

        // 直接传递额外参数，request 函数会调用 buildParams
        val extraParams = mapOf(
            "accesskey" to accesskey,
            "charset" to "utf8",
            "client" to "mobi",
            "fmt" to "krc",
            "id" to lyricInfo.id,
            "ver" to "1"
        )

        val response = request(
            "http://lyrics.kugou.com/download",
            extraParams,
            "Lyric"
        )
        
        val contenttype = (response["contenttype"] as? Number)?.toInt() ?: 0
        val content = response["content"] as? String ?: ""
        val info = response["info"] as? String ?: ""
        val errorCode = (response["error_code"] as? Number)?.toInt() ?: 0
        Log.d("KugouApi", "Lyrics download response - contenttype: $contenttype, content length: ${content.length}, error_code: $errorCode")
        Log.d("KugouApi", "Response info: $info")
        Log.d("KugouApi", "Response keys: ${response.keys}")
        
        val lyricsContent: String
        if (contenttype == 2) {
            // 纯文本歌词
            lyricsContent = String(android.util.Base64.decode(content, android.util.Base64.NO_WRAP))
            Log.d("KugouApi", "Plain text lyrics, length: ${lyricsContent.length}")
        } else {
            // KRC格式歌词，需要解密
            lyricsContent = KrcDecoder.decrypt(content)
            Log.d("KugouApi", "KRC lyrics decrypted, length: ${lyricsContent.length}")
            Log.d("KugouApi", "KRC lyrics first 200 chars: ${lyricsContent.take(200)}")
        }

        return@withContext Lyrics(
            title = songInfo.title,
            artist = songInfo.artist.name,
            album = songInfo.album,
            content = lyricsContent,
            orig = lyricsContent,  // 设置 orig 字段用于解析
            source = Source.KG,
            duration = songInfo.duration
        )
    }
    
    /**
     * 获取歌词列表
     */
    suspend fun getLyricsList(songInfo: SongInfo): List<LyricInfo> = withContext(Dispatchers.IO) {
        // 直接传递额外参数，request 函数会调用 buildParams
        val extraParams = mapOf(
            "album_audio_id" to songInfo.id,
            "duration" to songInfo.duration,
            "hash" to (songInfo.hash ?: ""),
            "keyword" to "${songInfo.artist.name} - ${songInfo.title}",
            "lrctxt" to "1",
            "man" to "no"
        )

        val response = request(
            "https://lyrics.kugou.com/v1/search",
            extraParams,
            "Lyric"
        )
        
        val candidates = (response["candidates"] as? List<Any>) ?: emptyList<Any>()
        Log.d("KugouApi", "Found ${candidates.size} lyric candidates")
        if (candidates.isNotEmpty()) {
            Log.d("KugouApi", "First candidate keys: ${(candidates[0] as? Map<*, *>)?.keys}")
        }
        return@withContext candidates.mapNotNull {
            val lyricMap = it as? Map<*, *> ?: return@mapNotNull null

            val id = when (val idValue = lyricMap["id"]) {
                is Number -> idValue.toString()
                is String -> idValue
                else -> ""
            }
            Log.d("KugouApi", "Lyric candidate - id: $id, accesskey: ${lyricMap["accesskey"]}, nickname: ${lyricMap["nickname"]}")

            LyricInfo(
                id = id,
                title = songInfo.title,
                artist = songInfo.artist.name,
                album = songInfo.album,
                source = Source.KG,
                accesskey = lyricMap["accesskey"] as? String,
                creator = lyricMap["nickname"] as? String,
                score = (lyricMap["score"] as? Number)?.toInt() ?: 0,
                songinfo = songInfo
            )
        }
    }
    
    /**
     * 格式化歌曲信息
     */
    private fun formatSongInfos(songInfos: List<*>): List<SongInfo> {
        return songInfos.mapNotNull { info ->
            val songMap = info as? Map<String, Any> ?: return@mapNotNull null
            
            val singers = (songMap["Singers"] as? List<Any>)?.mapNotNull {
                val singerMap = it as? Map<String, Any> ?: return@mapNotNull null
                Artist(singerMap["name"] as? String ?: "")
            } ?: emptyList()
            
            val artistName = singers.joinToString("/") { it.name }
            val album = songMap["AlbumName"] as? String ?: ""
            // Duration 可能是 String 或 Number 类型
            val duration = when (val d = songMap["Duration"]) {
                is Number -> d.toLong()
                is String -> d.toLongOrNull() ?: 0
                else -> 0
            }
            val transParam = (songMap["trans_param"] as? Map<String, Any>) ?: emptyMap<String, Any>()
            val language = LANGUAGE_MAPPING[transParam["language"] as? String ?: ""] ?: Language.OTHER
            val image = songMap["Image"] as? String ?: ""
            
            // ID 可能是 String 或 Number 类型
            val songId = when (val id = songMap["ID"]) {
                is Number -> id.toString()
                is String -> id
                else -> ""
            }
            // 专辑ID 可能是 String 或 Number 类型
            val albumId = when (val aid = songMap["AlbumID"]) {
                is Number -> aid.toString()
                is String -> aid
                else -> null
            }

            SongInfo(
                id = songId,
                title = songMap["SongName"] as? String ?: "",
                artist = Artist(artistName),
                album = album,
                duration = duration * 1000, // 转换为毫秒
                source = Source.KG,
                hash = songMap["FileHash"] as? String,
                albumId = albumId,
                subtitle = songMap["Auxiliary"] as? String ?: "",
                language = language,
                singers = singers,
                imageUrl = image.replace("{size}", "800")
            )
        }
    }

    /**
     * 格式化旧版API歌曲信息
     */
    private fun formatOldSongInfos(songInfos: List<*>): List<SongInfo> {
        return songInfos.mapNotNull { info ->
            val songMap = info as? Map<String, Any> ?: return@mapNotNull null
            
            val artistName = songMap["singername"] as? String ?: ""
            val album = songMap["album_name"] as? String ?: ""
            val duration = (songMap["duration"] as? Number)?.toLong() ?: 0
            val transParam = (songMap["trans_param"] as? Map<String, Any>) ?: emptyMap<String, Any>()
            val language = LANGUAGE_MAPPING[transParam["language"] as? String ?: ""] ?: Language.OTHER
            val image = songMap["Image"] as? String ?: ""
            
            SongInfo(
                id = (songMap["album_audio_id"] as? Number)?.toString() ?: "",
                title = songMap["songname"] as? String ?: "",
                artist = Artist(artistName),
                album = album,
                duration = duration * 1000, // 转换为毫秒
                source = Source.KG,
                hash = songMap["hash"] as? String,
                subtitle = songMap["topic"] as? String ?: "",
                language = language,
                imageUrl = image.replace("{size}", "800")
            )
        }
    }
}
