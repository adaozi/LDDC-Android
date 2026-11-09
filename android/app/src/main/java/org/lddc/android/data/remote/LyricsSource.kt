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
 * 歌词源接口。对应上游 LDDC/core/api/lyrics/models.py 的 BaseAPI / CloudAPI。
 */
interface LyricsSource {

    val source: Source

    /** 该源支持的搜索类型（Lrclib 只有单曲） */
    val supportedSearchTypes: Set<SearchType>

    suspend fun searchSongs(keyword: String, page: Int = 1): List<SongInfo> =
        throw UnsupportedOperationException("$source 不支持单曲搜索")

    suspend fun searchSongLists(keyword: String, type: SearchType, page: Int = 1): List<SongListInfo> =
        throw UnsupportedOperationException("$source 不支持歌单/专辑搜索")

    suspend fun getSongList(songList: SongListInfo): List<SongInfo> =
        throw UnsupportedOperationException("$source 不支持获取歌单内容")

    /** 某首歌在该源下可选的歌词版本（不同上传者/版本） */
    suspend fun getLyricsList(song: SongInfo): List<LyricInfo> =
        throw UnsupportedOperationException("$source 不支持歌词列表")

    suspend fun getLyrics(info: LyricInfo): Lyrics

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

/** 歌词源返回的业务异常（网络层异常直接抛出） */
class LyricsSourceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class LyricsNotFoundException(message: String) : RuntimeException(message)
