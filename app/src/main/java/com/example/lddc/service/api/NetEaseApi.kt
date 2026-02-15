package com.example.lddc.service.api

import com.example.lddc.model.*
import com.example.lddc.service.crypto.CryptoModule
import com.example.lddc.service.network.NetworkModule
import com.example.lddc.service.network.ApiException
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * 网易云音乐API实现
 *
 * 提供网易云音乐平台的歌曲搜索和歌词获取功能
 * 使用网易云音乐PC客户端API（eapi）
 * 支持匿名登录和加密通信
 *
 * 主要功能：
 * - 匿名登录（生成设备ID和会话）
 * - 搜索歌曲
 * - 获取歌词（支持YRC、LRC、翻译、罗马音）
 */
class NetEaseApi(private val httpClient: HttpClient) {

    companion object;

    /** 认证信息（包含cookies和过期时间） */
    private var auth: NetEaseAuth? = null

    /** 是否已初始化 */
    private var isInited = false

    /**
     * 初始化网易云音乐API
     *
     * 使用匿名登录方式：
     * 1. 生成随机设备ID
     * 2. 构建设备信息（MAC地址、型号等）
     * 3. 调用注册接口获取用户ID和cookies
     * 4. 认证信息有效期10天
     */
    suspend fun init() = withContext(Dispatchers.IO) {
        if (isInited && (auth?.expire ?: 0) > NetworkModule.getCurrentTimestamp()) {
            return@withContext
        }

        // 使用预定义的设备ID（与Python一致）
        val deviceId = NetEaseDeviceIds.getRandomDeviceId()
        
        // 生成客户端签名
        val mac = NetworkModule.generateRandomMac()
        val randomStr = NetworkModule.generateRandomString(8)
        val hashPart = NetworkModule.md5(NetworkModule.generateRandomString(32))
        val clientSign = "$mac@@@$randomStr@@@@@@$hashPart"
        
        // 生成设备型号
        val deviceModels = listOf(
            "MS-iCraft B760M WIFI",
            "ASUS ROG STRIX Z790",
            "MSI MAG B550 TOMAHAWK",
            "ASRock X670E Taichi"
        )
        val deviceModel = deviceModels.random()
        
        // 构建预请求cookies
        val preCookies = mapOf(
            "os" to "pc",
            "deviceId" to deviceId,
            "osver" to "Microsoft-Windows-10--build-${(200..300).random()}00-64bit",
            "clientSign" to clientSign,
            "channel" to "netease",
            "mode" to deviceModel,
            "appver" to "3.1.3.203419"
        )
        
        // 构建请求参数
        val path = "/eapi/register/anonimous"
        val params = mapOf(
            "username" to CryptoModule.getAnonimousUsername(deviceId),
            "e_r" to true,
            "header" to buildParamsHeader(preCookies)
        )
        
        // 加密参数
        val encryptedParams = CryptoModule.eapiParamsEncrypt(path.replace("eapi", "api"), params)
        
        val response = httpClient.post("https://interface.music.163.com$path") {
            headers {
                buildHeaders(preCookies).forEach { (key, value) ->
                    append(key, value)
                }
            }
            setBody(TextContent(encryptedParams, ContentType.Application.FormUrlEncoded))
        }
        
        // 解密响应 - 直接读取原始字节数据
        val responseData = response.readBytes()

        if (responseData.isEmpty()) {
            throw ApiException("网易云音乐API返回空响应")
        }

        val decryptedResponse = CryptoModule.eapiResponseDecrypt(responseData)

        if (decryptedResponse.isBlank()) {
            throw ApiException("网易云音乐API解密后响应为空")
        }

        val jsonResponse = Json.parseToJsonElement(decryptedResponse).jsonObject

        // 检查响应状态
        val code = jsonResponse["code"]?.jsonPrimitive?.intOrNull ?: -1
        val message = jsonResponse["message"]?.jsonPrimitive?.content ?: "未知错误"
        if (code != 200) {
            throw ApiException("网易云音乐登录失败: $code, $message")
        }
        
        // 提取用户ID
        val userId = jsonResponse["userId"]?.jsonPrimitive?.content ?: ""
        if (userId.isEmpty()) {
            throw ApiException("网易云音乐登录失败: 未获取到用户ID")
        }
        
        // 构建cookies
        val cookies = mutableMapOf(
            "WEVNSM" to "1.0.0",
            "os" to preCookies["os"]!!,
            "deviceId" to preCookies["deviceId"]!!,
            "osver" to preCookies["osver"]!!,
            "clientSign" to preCookies["clientSign"]!!,
            "channel" to preCookies["channel"]!!,
            "mode" to preCookies["mode"]!!,
            "appver" to preCookies["appver"]!!,
            "WNMCID" to "${NetworkModule.generateRandomString(6)}.${System.currentTimeMillis() - (1000..10000).random()}.01.0"
        )
        
        // 添加响应cookies
        response.setCookie().forEach {cookie ->
            cookies[cookie.name] = cookie.value
        }
        
        // 设置认证信息
        auth = NetEaseAuth(
            userId = userId,
            cookies = cookies,
            expire = NetworkModule.getCurrentTimestamp() + 864000 // 10天过期
        )
        
        isInited = true
    }
    
    /**
     * 构建参数头部 - 与Python保持一致，使用紧凑JSON格式
     */
    private fun buildParamsHeader(cookies: Map<String, String>): String {
        // 手动构建JSON字符串以确保与Python格式完全一致（无空格）
        return "{\"clientSign\":\"${cookies["clientSign"]!!}\",\"os\":\"${cookies["os"]!!}\",\"appver\":\"${cookies["appver"]!!}\",\"deviceId\":\"${cookies["deviceId"]!!}\",\"requestId\":0,\"osver\":\"${cookies["osver"]!!}\"}"
    }
    
    /**
     * 构建请求头部
     */
    private fun buildHeaders(cookies: Map<String, String>): Map<String, String> {
        val headers = mutableMapOf(
            "accept" to "*/*",
            "content-type" to "application/x-www-form-urlencoded",
            "mconfig-info" to "{\"IuRPVVmc3WWul9fT\":{\"version\":733184,\"appver\":\"3.1.3.203419\"}}",
            "origin" to "orpheus://orpheus",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.3.203419",
            "sec-ch-ua" to "\"Chromium\";v=\"91\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-fetch-site" to "cross-site",
            "sec-fetch-mode" to "cors",
            "sec-fetch-dest" to "empty",
            "accept-language" to "en-US,en;q=0.9"
        )
        
        // 添加cookies
        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        headers["cookie"] = cookieString
        
        return headers
    }
    
    /**
     * 发送API请求
     */
    private suspend fun request(path: String, params: Map<String, Any>): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!isInited || (auth?.expire ?: 0) < NetworkModule.getCurrentTimestamp()) {
            init()
        }
        
        // 构建请求参数
        val requestParams = params.toMutableMap()
        requestParams["e_r"] = true
        requestParams["header"] = buildParamsHeader(auth?.cookies ?: emptyMap())
        
        // 加密参数
        val encryptedParams = CryptoModule.eapiParamsEncrypt(path.replace("eapi", "api"), requestParams)

        // 构建 cache_key
        val cacheKey = if (params.containsKey("cache_key")) {
            params["cache_key"] as String
        } else {
            null
        }

        // 发送请求
        val response = httpClient.post("https://interface.music.163.com$path") {
            headers {
                buildHeaders(auth?.cookies ?: emptyMap()).forEach { (key, value) ->
                    append(key, value)
                }
            }
            if (cacheKey != null) {
                parameter("cache_key", cacheKey)
            }
            setBody(TextContent(encryptedParams, ContentType.Application.FormUrlEncoded))
        }

        // 解密响应 - 直接读取原始字节数据
        val responseData = response.readBytes()
        val decryptedResponse = CryptoModule.eapiResponseDecrypt(responseData)
        val jsonResponse = Json.parseToJsonElement(decryptedResponse).jsonObject
        
        // 检查响应状态
        val code = jsonResponse["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 200) {
            val message = jsonResponse["message"]?.jsonPrimitive?.content ?: "未知错误"
            throw ApiException("网易云音乐API错误: $code, $message")
        }
        
        return@withContext jsonResponse.toMap()
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
        val offset = (page - 1) * pagesize
        
        val params = mapOf(
            "keyword" to keyword,
            "scene" to "NORMAL",
            "needCorrect" to "true",
            "limit" to pagesize.toString(),
            "offset" to offset.toString()
        )
        
        val data = request("/eapi/search/song/list/page", params)
        val jsonData = data as? Map<String, Any> ?: emptyMap<String, Any>()

        offset
        
        val resources = (jsonData["data"] as? Map<String, Any>)?.get("resources") as? List<Any> ?: emptyList<Any>()
        val formattedSongs = formatSongInfos(resources)
        (jsonData["data"] as? Map<String, Any>)?.get("totalCount") as? Number ?: formattedSongs.size
        
        APIResultList(
            results = formattedSongs
        )
    }

    /**
     * 获取歌词列表
     */
    suspend fun getLyricsList(songInfo: SongInfo): List<LyricInfo> = withContext(Dispatchers.IO) {
        // 网易云音乐API没有直接获取歌词列表的接口，这里返回一个默认的LyricInfo
        listOf(
            LyricInfo(
                id = songInfo.id,
                title = songInfo.title,
                artist = songInfo.artist.name,
                album = songInfo.album,
                source = Source.NE,
                songinfo = songInfo
            )
        )
    }
    
    /**
     * 获取歌词
     */
    suspend fun getLyrics(songInfo: SongInfo): Lyrics = withContext(Dispatchers.IO) {
        val params = mapOf(
            "id" to songInfo.id,
            "lv" to "-1",
            "tv" to "-1",
            "rv" to "-1",
            "yv" to "-1"
        )
        
        val data = request("/eapi/song/lyric/v1", params)
        val jsonData = data as? Map<String, Any> ?: emptyMap<String, Any>()
        
        val lyrics = Lyrics(
            title = songInfo.title,
            artist = songInfo.artist.name,
            album = songInfo.album,
            content = "",
            source = Source.NE,
            duration = songInfo.duration
        )
        
        // 处理歌词标签
        val tags = mutableMapOf<String, String>()
        tags["ar"] = songInfo.artist.name
        tags["al"] = songInfo.album
        tags["ti"] = songInfo.title
        
        // 处理歌词贡献者
        (jsonData["lyricUser"] as? Map<String, Any>)?.let {
            tags["by"] = it["nickname"] as? String ?: ""
        }
        
        (jsonData["transUser"] as? Map<String, Any>)?.let {
            val transUser = it["nickname"] as? String ?: ""
            if (tags.containsKey("by")) {
                tags["by"] = "${tags["by"]} & $transUser"
            } else {
                tags["by"] = transUser
            }
        }
        
        // 处理歌词内容
        var content = ""
        var orig = ""
        var ts = ""
        var roma = ""
        
        // 检查是否有YRC歌词
        if (jsonData.containsKey("yrc")) {
            val yrc = (jsonData["yrc"] as? Map<String, Any>)?.get("lyric") as? String ?: ""
            if (yrc.isNotEmpty()) {
                orig = yrc
                content = yrc
            }
        } else {
            // 处理普通LRC歌词
            val lrc = (jsonData["lrc"] as? Map<String, Any>)?.get("lyric") as? String ?: ""
            if (lrc.isNotEmpty()) {
                orig = lrc
                content = lrc
            }
        }
        
        // 处理翻译歌词
        val tlyric = (jsonData["tlyric"] as? Map<String, Any>)?.get("lyric") as? String ?: ""
        if (tlyric.isNotEmpty()) {
            ts = tlyric
        }
        
        // 处理罗马音歌词
        val romalrc = (jsonData["romalrc"] as? Map<String, Any>)?.get("lyric") as? String ?: ""
        if (romalrc.isNotEmpty()) {
            roma = romalrc
        }
        
        return@withContext lyrics.copy(
            content = content,
            tags = tags,
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
            
            // 处理嵌套的歌曲数据
            val simpleSongData = (songMap["baseInfo"] as? Map<String, Any>)?.get("simpleSongData") as? Map<String, Any> ?: songMap
            
            val artists = (simpleSongData["ar"] as? List<Any>)?.mapNotNull {
                val artistMap = it as? Map<String, Any> ?: return@mapNotNull null
                Artist(artistMap["name"] as? String ?: "")
            } ?: emptyList()
            
            val artistName = artists.joinToString("/") { it.name }
            val albumMap = simpleSongData["al"] as? Map<String, Any>
            val album = albumMap?.get("name") as? String ?: ""
            val albumImage = albumMap?.get("picUrl") as? String ?: ""
            // 专辑ID 可能是 String 或 Number 类型
            val albumId = when (val aid = albumMap?.get("id")) {
                is Number -> aid.toString()
                is String -> aid
                else -> null
            }
            // dt 可能是 String 或 Number 类型
            val duration = when (val dt = simpleSongData["dt"]) {
                is Number -> dt.toLong()
                is String -> dt.toLongOrNull() ?: 0
                else -> 0
            }
            val title = simpleSongData["name"] as? String ?: ""
            val subtitle = (simpleSongData["alia"] as? List<Any>)?.firstOrNull()?.toString() ?: ""
            // ID 可能是 String 或 Number 类型
            val songId = when (val id = simpleSongData["id"]) {
                is Number -> id.toString()
                is String -> id
                else -> ""
            }

            SongInfo(
                id = songId,
                title = title,
                artist = Artist(artistName),
                album = album,
                duration = duration,
                source = Source.NE,
                albumId = albumId,
                subtitle = subtitle,
                singers = artists,
                imageUrl = albumImage
            )
        }
    }
    
}
