// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.Source

/**
 * 歌词源总入口，对应上游 LDDC/core/api/lyrics/__init__.py 的 LyricsAPI。
 * 负责：源注册 + 结果缓存（上游用 diskcache 4 小时过期，这里用 LRU 内存缓存）。
 */
object LyricsRepository {

    private val sources: Map<Source, LyricsSource> = listOf(
        LrclibApi,
        NeApi,
        QmApi,
        KgApi,
    ).associateBy { it.source }

    private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L

    private class CacheEntry(val value: Any, val expireAt: Long)

    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 200
    }

    fun availableSources(): List<Source> = sources.keys.toList()

    fun isAvailable(source: Source): Boolean = sources.containsKey(source)

    // ---- 源熔断：连续失败 3 次后本次运行内跳过，避免批量匹配时每首歌都白等 ----
    private val failureCounts = LinkedHashMap<Source, Int>()
    private const val FAILURE_THRESHOLD = 3

    fun isCircuitOpen(source: Source): Boolean =
        synchronized(failureCounts) { (failureCounts[source] ?: 0) >= FAILURE_THRESHOLD }

    fun resetCircuits() = synchronized(failureCounts) { failureCounts.clear() }

    private fun recordSuccess(source: Source) = synchronized(failureCounts) {
        failureCounts[source] = 0
    }

    private fun recordFailure(source: Source) = synchronized(failureCounts) {
        failureCounts[source] = (failureCounts[source] ?: 0) + 1
    }

    fun supportedSearchTypes(source: Source): Set<SearchType> =
        sources[source]?.supportedSearchTypes ?: emptySet()

    private suspend fun <T> cached(key: String, block: suspend () -> T): T {        var cachedValue: Any? = null
        var hit = false
        synchronized(cache) {
            val entry = cache[key]
            if (entry != null && entry.expireAt > System.currentTimeMillis()) {
                cachedValue = entry.value
                hit = true
            }
        }
        if (hit) {
            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }
        val value = block()
        synchronized(cache) {
            cache[key] = CacheEntry(value as Any, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return value
    }

    private fun impl(source: Source): LyricsSource =
        sources[source] ?: throw LyricsSourceException("未接入的歌词源: ${source.displayName}")

    suspend fun searchSongs(source: Source, keyword: String, page: Int = 1): List<SongInfo> {
        if (isCircuitOpen(source)) throw LyricsSourceException("${source.displayName} 连续失败，本次已跳过")
        return try {
            cached("songs|$source|$keyword|$page") { impl(source).searchSongs(keyword, page) }
                .also { recordSuccess(source) }
        } catch (e: Exception) {
            recordFailure(source)
            throw e
        }
    }

    suspend fun searchSongLists(
        source: Source,
        keyword: String,
        type: SearchType,
        page: Int = 1,
    ): List<SongListInfo> =
        cached("lists|$source|$keyword|$type|$page") { impl(source).searchSongLists(keyword, type, page) }

    suspend fun getSongList(source: Source, songList: SongListInfo): List<SongInfo> =
        cached("songlist|$source|${songList.id}") { impl(source).getSongList(songList) }

    suspend fun getLyricsList(source: Source, song: SongInfo): List<LyricInfo> =
        cached("llist|$source|${song.id}") { impl(source).getLyricsList(song) }

    suspend fun getLyrics(info: LyricInfo): Lyrics {
        if (isCircuitOpen(info.source)) throw LyricsSourceException("${info.source.displayName} 连续失败，本次已跳过")
        return try {
            cached("lyrics|${info.source}|${info.id}|${info.songInfo.title}|${info.accesskey}") {
                impl(info.source).getLyrics(info)
            }.also { recordSuccess(info.source) }
        } catch (e: Exception) {
            recordFailure(info.source)
            throw e
        }
    }

    suspend fun getLyrics(song: SongInfo): Lyrics =
        getLyrics(
            LyricInfo(
                source = song.source,
                songInfo = song,
                id = song.id,
                duration = song.duration,
            ),
        )
}
