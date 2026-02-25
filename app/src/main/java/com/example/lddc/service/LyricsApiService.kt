package com.example.lddc.service

import com.example.lddc.model.APIResultList
import com.example.lddc.model.LyricInfo
import com.example.lddc.model.Lyrics
import com.example.lddc.model.SearchType
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source

/**
 * 歌词API服务接口，对应Python工程中的LyricsAPI类
 * 提供统一的歌词获取接口，支持多平台歌词搜索和获取
 */
interface LyricsApiService {

    /**
     * 从指定歌词源搜索歌曲
     *
     * @param source 歌词源（如QQ音乐、网易云等）
     * @param keyword 搜索关键词
     * @param searchType 搜索类型
     * @param page 页码，用于分页
     * @return 搜索结果列表
     */
    suspend fun search(
        source: Source,
        keyword: String,
        searchType: SearchType,
        page: Int = 1
    ): APIResultList<SongInfo> // 返回SongInfo列表

    /**
     * 获取歌曲歌词信息
     *
     * @param songInfo 歌曲信息，包含歌曲ID、标题、艺术家等信息
     * @return 歌词信息列表，包含不同版本的歌词
     */
    suspend fun getLyricslist(songInfo: SongInfo): APIResultList<LyricInfo>

    /**
     * 获取歌词
     *
     * @param info 歌曲信息或歌词信息
     * @return 歌词对象，包含歌词内容和元数据
     */
    suspend fun getLyrics(info: Any): Lyrics // info可以是SongInfo或LyricInfo

    /**
     * 多源搜索歌曲
     *
     * @param keyword 搜索关键词
     * @param searchType 搜索类型
     * @param sources 搜索源列表，如果为null则使用默认源
     * @param page 页码
     * @return 合并的搜索结果
     */
    suspend fun multiSearch(
        keyword: String,
        searchType: SearchType,
        sources: List<Source>? = null,
        page: Int = 1
    ): APIResultList<SongInfo>
}
