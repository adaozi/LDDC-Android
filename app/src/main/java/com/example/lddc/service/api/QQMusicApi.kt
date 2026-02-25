package com.example.lddc.service.api

import com.example.lddc.model.APIResultList
import com.example.lddc.model.Artist
import com.example.lddc.model.Language
import com.example.lddc.model.LyricInfo
import com.example.lddc.model.Lyrics
import com.example.lddc.model.QQMusicSession
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source
import com.example.lddc.service.crypto.CryptoModule
import com.example.lddc.service.crypto.QrcDecoder
import com.example.lddc.service.network.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * QQ音乐API实现
 *
 * 提供QQ音乐平台的歌曲搜索和歌词获取功能
 * 使用QQ音乐Lite版API接口
 * 支持QRC格式歌词（加密）的解密
 *
 * 主要功能：
 * - 初始化会话（获取uid/sid/userip）
 * - 搜索歌曲
 * - 获取歌词（支持原词、翻译、罗马音）
 */
class QQMusicApi(private val httpClient: HttpClient) {

    companion object {
        /** 语言代码映射表：QQ音乐语言ID -> Language枚举 */
        private val LANGUAGE_MAPPING = mapOf(
            9 to Language.INSTRUMENTAL,
            5 to Language.ENGLISH,
            4 to Language.KOREAN,
            3 to Language.JAPANESE,
            1 to Language.CHINESE,  // 粤语
            0 to Language.CHINESE   // 汉语
        )
    }

    /** 会话信息（uid/sid/userip） */
    private var session: QQMusicSession? = null

    /** 是否已初始化 */
    private var isInited = false

    /**
     * 公共请求参数
     * 模拟QQ音乐Lite版Android客户端
     */
    private val commParams = mutableMapOf<String, Any>(
        "ct" to 11,
        "cv" to "1003006",
        "v" to "1003006",
        "os_ver" to "15",
        "phonetype" to "24122RKC7C",
        "rom" to "Redmi/miro/miro:15/AE3A.240806.005/OS2.0.10${
            listOf(
                "5",
                "4",
                "2"
            ).random()
        }.0.VOMCNXM:user/release-keys",
        "tmeAppID" to "qqmusiclight",
        "nettype" to "NETWORK_WIFI",
        "udid" to "0"
    )

    /**
     * 初始化QQ音乐API会话
     *
     * 调用GetSession接口获取uid、sid、userip
     * 这些信息用于后续的API请求认证
     */
    suspend fun init() = withContext(Dispatchers.IO) {
        if (isInited) return@withContext

        val param = mapOf(
            "caller" to 0,
            "uid" to "0",
            "vkey" to 0
        )

        val data = request("GetSession", "music.getSession.session", param)
        val sessionData = data["session"] as? Map<*, *> ?: throw ApiException("获取会话失败")

        session = QQMusicSession(
            uid = sessionData["uid"] as? String ?: "0",
            sid = sessionData["sid"] as? String ?: "",
            userip = sessionData["userip"] as? String ?: ""
        )

        // 更新公共参数
        commParams["uid"] = session?.uid ?: "0"
        commParams["sid"] = session?.sid ?: ""
        commParams["userip"] = session?.userip ?: ""

        isInited = true
    }

    /**
     * 发送API请求
     */
    private suspend fun request(
        method: String,
        module: String,
        param: Map<String, Any>
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!isInited && method != "GetSession") {
            init()
        }

        // 构建 JSON 对象
        val requestJson = buildJsonObject {
            putJsonObject("comm") {
                commParams.forEach { (key, value) ->
                    when (value) {
                        is Number -> put(key, value)
                        is String -> put(key, value)
                        is Boolean -> put(key, value)
                        else -> put(key, value.toString())
                    }
                }
            }
            putJsonObject("request") {
                put("method", method)
                put("module", module)
                putJsonObject("param") {
                    param.forEach { (key, value) ->
                        when (value) {
                            is Number -> put(key, value)
                            is String -> put(key, value)
                            is Boolean -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                }
            }
        }

        val domains = listOf(
            "u.y.qq.com"
        )
        val domain = domains.random()
        val url = "https://$domain/cgi-bin/musicu.fcg"

        val response = httpClient.post(url) {
            headers {
                append("cookie", "tmeLoginType=-1;")
                append("content-type", "application/json")
                append("user-agent", "okhttp/3.14.9")
            }
            setBody(requestJson.toString())
        }

        val responseData = response.bodyAsText()
        val jsonObject = Json.parseToJsonElement(responseData).jsonObject
        val result = jsonObject.toMap()

        // 检查响应状态
        val code = (result["code"] as? Number)?.toInt() ?: 0
        val requestMap = result["request"] as? Map<*, *> ?: emptyMap<String, Any>()
        val requestCode = (requestMap["code"] as? Number)?.toInt() ?: 0

        if (code != 0 || requestCode != 0) {
            throw ApiException("QQ音乐API错误: $code")
        }

        return@withContext (requestMap["data"] as? Map<String, Any>) ?: emptyMap()
    }

    /**
     * 将JsonObject转换为Map
     */
    private fun JsonObject.toMap(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        this.forEach { (key, value) ->
            result[key] = when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.toMap()
                is JsonArray -> value.toList()
                else -> value
            }
        }
        return result
    }

    /**
     * 将JsonArray转换为List
     */
    private fun JsonArray.toList(): List<Any> {
        val result = mutableListOf<Any>()
        this.forEach { value ->
            result.add(
                when (value) {
                    is JsonPrimitive -> value.content
                    is JsonObject -> value.toMap()
                    is JsonArray -> value.toList()
                    else -> value
                }
            )
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

        val param = mapOf(
            "search_id" to CryptoModule.generateQQMusicSearchId(),
            "remoteplace" to "search.android.keyboard",
            "query" to keyword,
            "search_type" to 0,
            "num_per_page" to pagesize,
            "page_num" to page,
            "highlight" to 0,
            "nqc_flag" to 0,
            "page_id" to 1,
            "grp" to 1
        )

        val data = request("DoSearchForQQMusicLite", "music.search.SearchCgiService", param)

        val startIndex = (page - 1) * pagesize

        val songInfos =
            (data["body"] as? Map<String, Any>)?.get("item_song") as? List<Any> ?: emptyList<Any>()
        val formattedSongs = formatSongInfos(songInfos)
        if (formattedSongs.size == pagesize) {
            (data["meta"] as? Map<String, Any>)?.get("sum") as? Number ?: formattedSongs.size
        } else {
            startIndex + formattedSongs.size
        }

        APIResultList(
            results = formattedSongs
        )
    }

    /**
     * 获取歌词列表
     */
    suspend fun getLyricsList(songInfo: SongInfo): List<LyricInfo> = withContext(Dispatchers.IO) {
        // QQ音乐API没有直接获取歌词列表的接口，这里返回一个默认的LyricInfo
        listOf(
            LyricInfo(
                id = songInfo.id,
                title = songInfo.title,
                artist = songInfo.artist.name,
                album = songInfo.album,
                source = Source.QM,
                songinfo = songInfo
            )
        )
    }

    /**
     * 获取歌词
     */
    suspend fun getLyrics(songInfo: SongInfo): Lyrics = withContext(Dispatchers.IO) {
        val param = mapOf(
            "albumName" to songInfo.album,
            "crypt" to 1,
            "ct" to 19,
            "cv" to 2111,
            "interval" to (songInfo.duration / 1000),
            "lrc_t" to 0,
            "qrc" to 1,
            "qrc_t" to 0,
            "roma" to 1,
            "roma_t" to 0,
            "singerName" to songInfo.artist.name,
            "songID" to songInfo.id.toInt(),
            "songName" to songInfo.title,
            "trans" to 1,
            "trans_t" to 0,
            "type" to 0
        )

        val data = request("GetPlayLyricInfo", "music.musichallSong.PlayLyricInfo", param)

        val lyrics = Lyrics(
            title = songInfo.title,
            artist = songInfo.artist.name,
            album = songInfo.album,
            content = "",
            source = Source.QM,
            duration = songInfo.duration
        )

        // 处理歌词内容
        val lyricContent = (data["lyric"] as? String) ?: ""
        val transContent = (data["trans"] as? String) ?: ""
        val romaContent = (data["roma"] as? String) ?: ""
        val lrcT = (data["lrc_t"] as? Number)?.toLong() ?: 0
        val qrcT = (data["qrc_t"] as? Number)?.toLong() ?: 0

        android.util.Log.d(
            "QQMusicApi",
            "getLyrics: lyricContent.length=${lyricContent.length}, lrcT=$lrcT, qrcT=$qrcT"
        )
        android.util.Log.d("QQMusicApi", "lyricContent first 100 chars: ${lyricContent.take(100)}")

        // 解密歌词 - QQ音乐歌词可能是加密的十六进制字符串，也可能是未加密的XML格式
        val orig = if (lyricContent.isNotEmpty()) {
            // 检查是否是XML格式（未加密）
            if (lyricContent.trimStart().startsWith("<?xml") || lyricContent.contains("<Lyric_1")) {
                android.util.Log.d(
                    "QQMusicApi",
                    "Lyrics is in XML format (not encrypted), using as-is"
                )
                lyricContent
            } else {
                // 尝试解密（十六进制格式）
                try {
                    val decrypted = QrcDecoder.decrypt(lyricContent)
                    android.util.Log.d("QQMusicApi", "Lyrics decrypted: ${decrypted.take(100)}...")
                    decrypted
                } catch (e: Exception) {
                    // 解密失败，返回提示信息
                    android.util.Log.e("QQMusicApi", "Lyrics decrypt failed: ${e.message}")
                    android.util.Log.e("QQMusicApi", "Exception: $e")
                    "[歌词解密失败，请检查QrcDecoder实现]\n\n原始数据长度: ${lyricContent.length}"
                }
            }
        } else {
            "[暂无歌词]"
        }

        val ts = if (transContent.isNotEmpty()) {
            // 检查是否是XML格式（未加密）
            if (transContent.trimStart().startsWith("<?xml") || transContent.contains("<Lyric_1")) {
                transContent
            } else {
                try {
                    QrcDecoder.decrypt(transContent)
                } catch (_: Exception) {
                    // 解密失败，使用原始内容
                    transContent
                }
            }
        } else {
            transContent
        }

        val roma = if (romaContent.isNotEmpty()) {
            // 检查是否是XML格式（未加密）
            if (romaContent.trimStart().startsWith("<?xml") || romaContent.contains("<Lyric_1")) {
                romaContent
            } else {
                try {
                    QrcDecoder.decrypt(romaContent)
                } catch (_: Exception) {
                    // 解密失败，使用原始内容
                    romaContent
                }
            }
        } else {
            romaContent
        }

        lyrics.copy(
            content = orig,
            orig = orig,
            ts = ts,
            roma = roma
        )
    }

    /**
     * 格式化歌曲信息
     */
    private fun formatSongInfos(songInfos: List<*>): List<SongInfo> {
        return songInfos.mapNotNull { info ->
            val songMap = info as? Map<String, Any> ?: return@mapNotNull null

            val singers = (songMap["singer"] as? List<Any>)?.mapNotNull {
                val singerMap = it as? Map<String, Any> ?: return@mapNotNull null
                Artist(singerMap["name"] as? String ?: "")
            } ?: emptyList()

            val artistName = singers.joinToString("/") { it.name }
            val albumMap = songMap["album"] as? Map<String, Any>
            val album = albumMap?.get("name") as? String ?: ""
            // QQ音乐使用专辑的 mid 作为图片标识
            val albumMid = albumMap?.get("mid") as? String
            // interval 可能是 String 或 Number 类型
            val duration = when (val interval = songMap["interval"]) {
                is Number -> interval.toLong()
                is String -> interval.toLongOrNull() ?: 0
                else -> 0
            }
            val language =
                LANGUAGE_MAPPING[(songMap["language"] as? Number)?.toInt() ?: -1] ?: Language.OTHER

            // 构建图片 URL - 使用专辑 mid
            val imageUrl = if (!albumMid.isNullOrEmpty()) {
                "https://y.qq.com/music/photo_new/T002R800x800M000${albumMid}.jpg?max_age=2592000"
            } else {
                ""
            }

            // ID 可能是 String 或 Number 类型
            val songId = when (val id = songMap["id"]) {
                is Number -> id.toString()
                is String -> id
                else -> ""
            }
            // 专辑ID 可能是 String 或 Number 类型
            val albumId = when (val aid = albumMap?.get("id")) {
                is Number -> aid.toString()
                is String -> aid
                else -> null
            }

            SongInfo(
                id = songId,
                title = songMap["title"] as? String ?: "",
                artist = Artist(artistName),
                album = album,
                duration = duration * 1000, // 转换为毫秒
                source = Source.QM,
                mid = songMap["mid"] as? String,
                albumId = albumId,
                subtitle = songMap["subtitle"] as? String ?: "",
                language = language,
                singers = singers,
                imageUrl = imageUrl
            )
        }
    }

}
