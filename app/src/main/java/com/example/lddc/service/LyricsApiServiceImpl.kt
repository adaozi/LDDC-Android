package com.example.lddc.service

import com.example.lddc.model.*
import com.example.lddc.service.api.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.util.Log

/**
 * 歌词API服务实现类
 * 
 * 提供统一的歌词获取接口，支持多平台（QQ音乐、网易云音乐、酷狗音乐）歌词搜索和获取
 * 所有网络请求都在 IO 线程执行，搜索支持并行处理以提高速度
 */
class LyricsApiServiceImpl : LyricsApiService {

    /**
     * Ktor HTTP客户端配置
     * - 使用 CIO 引擎（Coroutine-based I/O）
     * - 自动解析 JSON 响应
     * - 10秒请求超时
     */
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true  // 忽略未知字段，避免解析失败
                prettyPrint = true
            })
        }
        engine {
            requestTimeout = 10000 // 10秒超时
        }
    }

    /**
     * 平台API映射表
     * Key: 平台枚举 (QM-QQ音乐, NE-网易云, KG-酷狗)
     * Value: 对应平台的API实现类
     */
    private val platformApis: Map<Source, Any> = mapOf(
        Source.QM to QQMusicApi(httpClient),
        Source.NE to NetEaseApi(httpClient),
        Source.KG to KugouApi(httpClient)
    )

    /**
     * 单平台搜索
     *
     * @param source 搜索平台
     * @param keyword 搜索关键词（歌曲名/歌手名）
     * @param searchType 搜索类型（目前仅支持歌曲）
     * @param page 页码，用于分页加载
     * @return 搜索结果列表
     * @throws IllegalArgumentException 不支持的平台
     */
    override suspend fun search(
        source: Source,
        keyword: String,
        searchType: SearchType,
        page: Int
    ): APIResultList<SongInfo> {
        return withContext(Dispatchers.IO) {
            val api = platformApis[source]
                ?: throw IllegalArgumentException("Unsupported source: $source")

            when (api) {
                is QQMusicApi -> api.search(keyword, page)
                is NetEaseApi -> api.search(keyword, page)
                is KugouApi -> api.search(keyword, page)
                else -> throw IllegalArgumentException("Unsupported API implementation")
            }
        }
    }

    /**
     * 获取歌曲的歌词列表
     *
     * @param songInfo 歌曲信息
     * @return 该歌曲的所有可用歌词版本列表（不同格式/翻译）
     */
    override suspend fun getLyricslist(songInfo: SongInfo): APIResultList<LyricInfo> {
        return withContext(Dispatchers.IO) {
            val api = platformApis[songInfo.source]
                ?: throw IllegalArgumentException("Unsupported source: ${songInfo.source}")

            when (api) {
                is QQMusicApi -> APIResultList(api.getLyricsList(songInfo))
                is NetEaseApi -> APIResultList(api.getLyricsList(songInfo))
                is KugouApi -> APIResultList(api.getLyricsList(songInfo))
                else -> throw IllegalArgumentException("Unsupported API implementation")
            }
        }
    }

    /**
     * 获取歌词内容
     *
     * @param info 歌曲信息或歌词信息
     * @return 歌词对象，包含原始歌词、翻译、罗马音等
     * @throws IllegalArgumentException 不支持的信息类型或平台
     */
    override suspend fun getLyrics(info: Any): Lyrics {
        return withContext(Dispatchers.IO) {
            val api = when (info) {
                is SongInfo -> platformApis[info.source]
                is LyricInfo -> platformApis[info.source]
                else -> throw IllegalArgumentException("Invalid info type: ${info::class.simpleName}")
            } ?: throw IllegalArgumentException("Unsupported source")

            when (api) {
                is QQMusicApi -> if (info is SongInfo) api.getLyrics(info) else throw IllegalArgumentException("Invalid info type")
                is NetEaseApi -> if (info is SongInfo) api.getLyrics(info) else throw IllegalArgumentException("Invalid info type")
                is KugouApi -> if (info is SongInfo) api.getLyrics(info) else throw IllegalArgumentException("Invalid info type")
                else -> throw IllegalArgumentException("Unsupported API implementation")
            }
        }
    }

    /**
     * 获取歌曲详情（歌词 + 歌词类型）
     *
     * @param songInfo 歌曲信息
     * @return Pair<歌词对象, 歌词格式类型>
     *         歌词格式: QRC(QQ音乐), LRC(网易云), KRC(酷狗)
     */
    suspend fun getSongDetails(songInfo: SongInfo): Pair<Lyrics, String> = withContext(Dispatchers.IO) {
        val lyricsType = when (songInfo.source) {
            Source.QM -> "QRC"
            Source.NE -> "LRC"
            Source.KG -> "KRC"
        }
        Pair(getLyrics(songInfo), lyricsType)
    }

    /**
     * 多平台并行搜索
     *
     * 同时向多个平台发送搜索请求，使用协程并行处理以提高搜索速度
     * 搜索结果按平台交叉合并，避免同一平台的结果连续出现
     *
     * @param keyword 搜索关键词
     * @param searchType 搜索类型
     * @param sources 要搜索的平台列表，null 表示搜索所有平台
     * @param page 页码
     * @return 合并后的搜索结果列表
     *
     * 搜索顺序优先级: QQ音乐 > 酷狗音乐 > 网易云音乐
     * 合并示例: [QQ-1, 酷狗-1, 网易-1, QQ-2, 酷狗-2, 网易-2, ...]
     */
    override suspend fun multiSearch(
        keyword: String,
        searchType: SearchType,
        sources: List<Source>?,
        page: Int
    ): APIResultList<SongInfo> {
        return withContext(Dispatchers.IO) {
            // 使用默认平台列表（QQ音乐、网易云、酷狗）
            val searchSources = sources ?: listOf(Source.QM, Source.NE, Source.KG)

            // 为每个平台创建异步搜索任务，实现并行搜索
            val deferredResults = searchSources.map { source ->
                async {
                    try {
                        val api = platformApis[source] ?: return@async null
                        val result = when (api) {
                            is QQMusicApi -> api.search(keyword, page)
                            is NetEaseApi -> api.search(keyword, page)
                            is KugouApi -> api.search(keyword, page)
                            else -> return@async null
                        }
                        source to result.results
                    } catch (e: Exception) {
                        // 单个平台搜索失败不影响其他平台
                        Log.e("LyricsApiService", "Search failed for source $source: ${e.message}", e)
                        null
                    }
                }
            }

            // 等待所有平台搜索完成
            val resultsBySource = deferredResults.awaitAll()
                .filterNotNull()
                .toMap()

            // 按平台优先级交叉合并结果
            val interleavedResults = mutableListOf<SongInfo>()
            val sourceOrder = listOf(Source.QM, Source.KG, Source.NE)
            val validSources = sourceOrder.filter { it in resultsBySource.keys }

            if (validSources.isNotEmpty()) {
                var index = 0
                var hasMore = true
                // 轮询每个平台，依次取第 index 个结果
                while (hasMore) {
                    hasMore = false
                    for (source in validSources) {
                        val sourceList = resultsBySource[source]!!
                        if (index < sourceList.size) {
                            interleavedResults.add(sourceList[index])
                            hasMore = true
                        }
                    }
                    index++
                }
            }

            Log.d("LyricsApiService", "Multi-search results: ${interleavedResults.size} items from ${validSources.size} sources")
            APIResultList(interleavedResults)
        }
    }
}
