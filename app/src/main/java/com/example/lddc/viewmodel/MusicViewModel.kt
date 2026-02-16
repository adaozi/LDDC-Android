package com.example.lddc.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lddc.data.repository.LyricsRepository
import com.example.lddc.di.AppModule
import com.example.lddc.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音乐搜索 ViewModel
 *
 * 管理音乐搜索相关的所有状态和逻辑
 * - 使用 StateFlow 实现响应式数据流
 * - 自动处理配置变更（屏幕旋转等）
 * - 支持多平台并行搜索
 * - 遵循MVVM架构，通过Repository层访问数据
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 20
        private const val TAG = "MusicViewModel"
    }

    // 仓库层依赖（通过DI获取）
    private val lyricsRepository: LyricsRepository = AppModule.provideLyricsRepository(application)

    // ==================== 状态管理 ====================

    /** 搜索筛选条件（关键词、平台等） */
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()

    /** 搜索结果列表（已转换为 Music 对象） */
    private val _searchResults = MutableStateFlow<List<Music>>(emptyList())
    val searchResults: StateFlow<List<Music>> = _searchResults.asStateFlow()

    /** 首次加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 加载更多状态 */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** 当前选中的歌曲（包含歌词详情） */
    private val _selectedMusic = MutableStateFlow<Music?>(null)
    val selectedMusic: StateFlow<Music?> = _selectedMusic.asStateFlow()

    /** 当前页码（用于分页加载） */
    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /** 是否还有更多数据 */
    private val _hasMoreData = MutableStateFlow(true)
    val hasMoreData: StateFlow<Boolean> = _hasMoreData.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * SongInfo 映射表
     * Key: 歌曲ID
     * Value: 原始 SongInfo 对象（用于获取歌词详情）
     */
    private var songInfoMap: Map<String, SongInfo> = emptyMap()

    // ==================== 业务方法 ====================

    /**
     * 更新搜索筛选条件
     */
    fun updateSearchFilters(filters: SearchFilters) {
        _searchFilters.value = filters
    }

    /**
     * 执行搜索
     *
     * 流程：
     * 1. 重置分页状态
     * 2. 并行搜索多个平台（QQ音乐、网易云、酷狗）
     * 3. 转换数据格式
     * 4. 按平台交叉排序结果（QQ、KG、NE 轮流）
     * 5. 更新搜索结果
     */
    fun searchMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            _currentPage.value = 1
            _hasMoreData.value = true
            _errorMessage.value = null

            try {
                val filters = _searchFilters.value
                val keyword = filters.keyword.takeIf { it.isNotEmpty() } ?: return@launch

                // 并行搜索多个平台
                val results = multiSearch(keyword, 1)

                // 保存原始 SongInfo 用于后续获取歌词
                val songInfoMapBuilder = mutableMapOf<String, SongInfo>()

                // 将搜索结果转换为 Music 对象
                val musicResults = results.map { item ->
                    songInfoMapBuilder[item.id] = item

                    // 时长从毫秒转换为秒
                    val durationInSeconds = item.duration / 1000

                    Music(
                        id = item.id,
                        title = item.title,
                        artist = item.artist.name,
                        duration = durationInSeconds.toString(),
                        platform = when (item.source) {
                            Source.QM -> "QQ音乐"
                            Source.NE -> "网易云音乐"
                            Source.KG -> "酷狗音乐"
                        },
                        album = item.album,
                        imageUrl = item.imageUrl,
                        description = "",
                        lyrics = "",      // 点击后异步获取
                        lyricsType = "未知"  // 点击后异步获取
                    )
                }

                songInfoMap = songInfoMapBuilder
                _searchResults.value = musicResults
                _hasMoreData.value = musicResults.size >= PAGE_SIZE
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                _searchResults.value = emptyList()
                _hasMoreData.value = false
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 多平台并行搜索
     *
     * 同时向多个平台发送搜索请求，使用协程并行处理以提高搜索速度
     * 搜索结果按平台交叉合并，避免同一平台的结果连续出现
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @return 合并后的搜索结果列表
     */
    private suspend fun multiSearch(keyword: String, page: Int): List<SongInfo> =
        withContext(Dispatchers.IO) {
            val searchSources = listOf(Source.QM, Source.NE, Source.KG)

            // 为每个平台创建异步搜索任务，实现并行搜索
            val deferredResults = searchSources.map { source ->
                async {
                    try {
                        val result = lyricsRepository.searchSongs(keyword, source, page)
                        result.getOrNull()?.let { songs ->
                            // 将 Music 转换回 SongInfo（简化处理，实际应该修改 Repository 返回 SongInfo）
                            source to songs.map { music ->
                                SongInfo(
                                    id = music.id,
                                    title = music.title,
                                    artist = Artist(music.artist),
                                    album = music.album,
                                    duration = music.duration.toLongOrNull()?.times(1000) ?: 0L,
                                    source = when (music.platform) {
                                        "QQ音乐" -> Source.QM
                                        "网易云音乐" -> Source.NE
                                        "酷狗音乐" -> Source.KG
                                        else -> source
                                    },
                                    imageUrl = music.imageUrl
                                )
                            }
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

            Log.d(TAG, "Multi-search results: ${interleavedResults.size} items from ${validSources.size} sources")
            interleavedResults
        }

    /**
     * 加载更多数据（分页）
     */
    fun loadMore() {
        if (_isLoadingMore.value || !_hasMoreData.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = _currentPage.value + 1

            try {
                val filters = _searchFilters.value
                val keyword = filters.keyword.takeIf { it.isNotEmpty() } ?: return@launch

                // 并行搜索更多数据
                val results = multiSearch(keyword, nextPage)

                // 保存原始 SongInfo
                val songInfoMapBuilder = songInfoMap.toMutableMap()

                // 转换搜索结果
                val musicResults = results.map { item ->
                    songInfoMapBuilder[item.id] = item
                    val durationInSeconds = item.duration / 1000

                    Music(
                        id = item.id,
                        title = item.title,
                        artist = item.artist.name,
                        duration = durationInSeconds.toString(),
                        platform = when (item.source) {
                            Source.QM -> "QQ音乐"
                            Source.NE -> "网易云音乐"
                            Source.KG -> "酷狗音乐"
                        },
                        album = item.album,
                        imageUrl = item.imageUrl,
                        description = "",
                        lyrics = "",
                        lyricsType = "未知"
                    )
                }

                songInfoMap = songInfoMapBuilder

                if (musicResults.isNotEmpty()) {
                    _searchResults.value += musicResults
                    _currentPage.value = nextPage
                    _hasMoreData.value = musicResults.size >= PAGE_SIZE
                } else {
                    _hasMoreData.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载更多失败", e)
                _hasMoreData.value = false
                _errorMessage.value = e.message
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 选中歌曲并获取歌词详情
     */
    fun selectMusic(music: Music) {
        _selectedMusic.value = music

        viewModelScope.launch {
            try {
                // 从映射表中获取原始 SongInfo
                val songInfo = songInfoMap[music.id]
                    ?: throw IllegalStateException("未找到歌曲信息: ${music.id}")

                val result = lyricsRepository.getLyrics(songInfo)
                result.fold(
                    onSuccess = { lyrics ->
                        _selectedMusic.value = music.copy(
                            lyrics = lyrics.orig ?: lyrics.content,
                            lyricsType = when (songInfo.source) {
                                Source.QM -> "QRC"
                                Source.NE -> "LRC"
                                Source.KG -> "KRC"
                            }
                        )
                    },
                    onFailure = {
                        // 获取歌词失败，保持已选中的基本信息
                        _errorMessage.value = it.message
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取歌词失败", e)
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * 清除选中的歌曲
     */
    fun clearSelectedMusic() {
        _selectedMusic.value = null
    }

    /**
     * 清除所有筛选条件
     */
    fun clearFilters() {
        _searchFilters.value = SearchFilters()
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
