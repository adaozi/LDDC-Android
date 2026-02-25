package com.example.lddc.domain.usecase

import android.util.Log
import com.example.lddc.data.repository.LyricsRepository
import com.example.lddc.model.Music
import com.example.lddc.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * 搜索歌曲用例
 *
 * 封装多平台并行搜索逻辑，供多个ViewModel复用
 */
class SearchSongsUseCase(
    private val lyricsRepository: LyricsRepository
) {
    companion object {
        private const val TAG = "SearchSongsUseCase"
    }

    /**
     * 执行多平台并行搜索
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param sources 要搜索的平台列表，默认为所有平台
     * @param interleaveResults 是否按平台交叉排序结果
     * @return 搜索结果列表
     */
    suspend operator fun invoke(
        keyword: String,
        page: Int = 1,
        sources: List<Source> = listOf(Source.QM, Source.NE, Source.KG),
        interleaveResults: Boolean = true
    ): Result<List<Music>> = withContext(Dispatchers.IO) {
        try {
            // 为每个平台创建异步搜索任务，实现并行搜索
            val deferredResults = sources.map { source ->
                async {
                    try {
                        val result = lyricsRepository.searchSongs(keyword, source, page)
                        result.getOrNull()?.let { songs ->
                            source to songs
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Search failed for source $source: ${e.message}", e)
                        null
                    }
                }
            }

            // 等待所有平台搜索完成
            val resultsBySource = deferredResults.awaitAll()
                .filterNotNull()
                .toMap()

            // 合并结果
            val mergedResults = if (interleaveResults) {
                interleaveResultsBySource(resultsBySource)
            } else {
                resultsBySource.values.flatten()
            }

            Log.d(
                TAG,
                "Multi-search results: ${mergedResults.size} items from ${resultsBySource.size} sources"
            )
            Result.success(mergedResults)
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败", e)
            Result.failure(e)
        }
    }

    /**
     * 按平台交叉排序结果
     * 顺序：QQ音乐、酷狗音乐、网易云音乐 轮流
     */
    private fun interleaveResultsBySource(resultsBySource: Map<Source, List<Music>>): List<Music> {
        val sourceOrder = listOf(Source.QM, Source.KG, Source.NE)
        val validSources = sourceOrder.filter { it in resultsBySource.keys }

        if (validSources.isEmpty()) return emptyList()

        val interleaved = mutableListOf<Music>()
        var index = 0
        var hasMore = true

        // 轮询每个平台，依次取第 index 个结果
        while (hasMore) {
            hasMore = false
            for (source in validSources) {
                val sourceList = resultsBySource[source]!!
                if (index < sourceList.size) {
                    interleaved.add(sourceList[index])
                    hasMore = true
                }
            }
            index++
        }

        return interleaved
    }
}
