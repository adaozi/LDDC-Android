// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.LyricsType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.remote.LyricsRepository

/**
 * 自动匹配，对应上游 LDDC/core/auto_fetch.py。
 *
 * 流程：多关键词 → 多源并发搜索 → 打分筛选 → 取词（失败换下一个候选，最多 3 个）
 * → 取最高分 ±15 分内的结果 → 按"逐字+译文+罗马音"优先、再按源优先级挑一个。
 */
object AutoFetch {

    data class Match(
        val lyrics: Lyrics,
        val song: SongInfo,
        val score: Double,
    )

    private data class Candidate(val score: Double, val song: SongInfo)

    suspend fun fetch(
        info: SongInfo,
        sources: List<Source>,
        minScore: Double = 55.0,
        timeoutMs: Long = 30_000,
    ): Match? = withTimeoutOrNull(timeoutMs) {
        val keywords = buildKeywords(info)
        if (keywords.isEmpty()) return@withTimeoutOrNull null

        val primaryKeyword = keywords.first()

        // 1) 多源并发搜索
        val searchResults = coroutineScope {
            sources.map { source ->
                async {
                    runCatching { LyricsRepository.searchSongs(source, primaryKeyword) }.getOrElse { emptyList() }
                }
            }.awaitAll()
        }

        // 2) 打分
        val candidates = scoreResults(info, primaryKeyword, searchResults, minScore)
        if (candidates.isEmpty()) {
            // 首轮无结果时，退回用更短的关键词（歌名）再试一次
            val secondary = keywords.getOrNull(1) ?: return@withTimeoutOrNull null
            if (secondary == primaryKeyword) return@withTimeoutOrNull null
            val retry = coroutineScope {
                sources.map { source ->
                    async { runCatching { LyricsRepository.searchSongs(source, secondary) }.getOrElse { emptyList() } }
                }.awaitAll()
            }
            val retryCandidates = scoreResults(info, secondary, retry, minScore)
            return@withTimeoutOrNull fetchFromCandidates(retryCandidates, sources)
        }
        return@withTimeoutOrNull fetchFromCandidates(candidates, sources)
    }

    private suspend fun fetchFromCandidates(candidates: List<Candidate>, sources: List<Source>): Match? {
        val sorted = candidates.sortedByDescending { it.score }
        val collected = ArrayList<Match>()

        // 3) 依次取词，最多尝试 3 个候选
        for (candidate in sorted.take(3)) {
            val lyrics = runCatching { LyricsRepository.getLyrics(candidate.song) }.getOrNull() ?: continue
            if (lyrics.get("orig").isEmpty()) continue
            collected.add(Match(lyrics, candidate.song, candidate.score))
        }
        if (collected.isEmpty()) return null

        // 4) 取最高分 ±15 分内的集合
        val highest = collected.maxOf { it.score }
        val nearBest = collected.filter { kotlin.math.abs(it.score - highest) <= 15.0 }

        // 5) 组合优先级：逐字+译+罗马音 > 逐字+译 > 译+罗马音 > 译 > 逐字+罗马音 > 逐字 > 罗马音
        val preferences: List<(Lyrics) -> Boolean> = listOf(
            { it.types["orig"] == LyricsType.VERBATIM && it.has("ts") && it.has("roma") },
            { it.types["orig"] == LyricsType.VERBATIM && it.has("ts") },
            { it.has("ts") && it.has("roma") },
            { it.has("ts") },
            { it.types["orig"] == LyricsType.VERBATIM && it.has("roma") },
            { it.types["orig"] == LyricsType.VERBATIM },
            { it.has("roma") },
        )
        var pool = nearBest
        for (preference in preferences) {
            val filtered = nearBest.filter { preference(it.lyrics) }
            if (filtered.isNotEmpty()) {
                pool = filtered
                break
            }
        }

        // 6) 按源优先级取
        for (source in sources) {
            pool.firstOrNull { it.lyrics.info.source == source }?.let { return it }
        }
        return pool.firstOrNull()
    }

    private fun scoreResults(
        info: SongInfo,
        keyword: String,
        searchResults: List<List<SongInfo>>,
        minScore: Double,
    ): List<Candidate> {
        val keywords = buildKeywords(info)
        val candidates = ArrayList<Candidate>()

        for (results in searchResults) {
            for (result in results) {
                // 时长相差超过 4 秒直接丢弃
                if (info.duration != null && result.duration != null &&
                    kotlin.math.abs(info.duration - result.duration) > 4000
                ) {
                    continue
                }

                val score = if (keyword == keywords.getOrNull(0) || keyword == keywords.getOrNull(1)) {
                    var titleScore = MatchAlgorithm.calculateTitleScore(info.title.orEmpty(), result.title.orEmpty())
                    val albumScore = if (!info.album.isNullOrEmpty() && !result.album.isNullOrEmpty()) {
                        maxOf(MatchAlgorithm.textDifference(info.album.lowercase(), result.album.lowercase()) * 100, 0.0)
                    } else {
                        null
                    }
                    val artistScore = if (info.artist.isNotEmpty() && result.artist.isNotEmpty()) {
                        MatchAlgorithm.calculateArtistScore(info.strArtist, result.strArtist)
                    } else {
                        null
                    }

                    var value = when {
                        artistScore != null && albumScore != null ->
                            maxOf(titleScore * 0.5 + artistScore * 0.5, titleScore * 0.5 + artistScore * 0.35 + albumScore * 0.15)

                        artistScore != null -> titleScore * 0.5 + artistScore * 0.5
                        albumScore != null -> maxOf(titleScore * 0.7 + albumScore * 0.3, titleScore * 0.8)
                        else -> titleScore
                    }
                    if (titleScore < 30) value = maxOf(0.0, value - 35)
                    value
                } else {
                    // 用文件名匹配
                    val fileName = info.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: info.title.orEmpty()
                    maxOf(
                        MatchAlgorithm.textDifference(fileName, result.title.orEmpty()) * 100,
                        MatchAlgorithm.textDifference(fileName, "${result.strArtist} - ${result.title}") * 100,
                        MatchAlgorithm.textDifference(fileName, "${result.title} - ${result.strArtist}") * 100,
                    )
                }

                if (score > minScore) candidates.add(Candidate(score, result))
            }
        }
        return candidates
    }

    /** 关键词优先级：歌手 - 标题 → 标题 → 文件名 */
    private fun buildKeywords(info: SongInfo): List<String> {
        val list = ArrayList<String>()
        val title = info.title?.trim().orEmpty()
        if (title.isNotEmpty()) {
            if (info.artist.isNotEmpty()) list.add(info.artistTitle())
            list.add(title)
        } else if (!info.path.isNullOrEmpty()) {
            list.add(info.path.substringAfterLast('/').substringBeforeLast('.'))
        }
        return list
    }
}
