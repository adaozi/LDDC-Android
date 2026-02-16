package com.example.lddc.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lddc.di.AppModule
import com.example.lddc.domain.usecase.FilterAndSortMusicUseCase
import com.example.lddc.model.Music
import com.example.lddc.model.SearchFilters
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsApiServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音乐搜索 ViewModel
 *
 * 管理音乐搜索相关的所有状态和逻辑
 * - 使用 StateFlow 实现响应式数据流
 * - 自动处理配置变更（屏幕旋转等）
 * - 支持多平台并行搜索
 * - 遵循MVVM架构，通过UseCase层执行业务逻辑
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PAGE_SIZE = 20
        private const val TAG = "MusicViewModel"
    }

    private val filterAndSortMusicUseCase: FilterAndSortMusicUseCase = AppModule.provideFilterAndSortMusicUseCase()

    // 直接使用 LyricsApiServiceImpl 获取歌词（保留原始 SongInfo）
    private val lyricsApiService = LyricsApiServiceImpl()

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

    /** 是否还有更多数据 */
    private val _hasMoreData = MutableStateFlow(true)
    val hasMoreData: StateFlow<Boolean> = _hasMoreData.asStateFlow()

    /** 错误信息 */
    private val _errorMessage = MutableStateFlow<String?>(null)

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
     * 1. 重置分页状态和筛选条件
     * 2. 清空之前的搜索结果和SongInfo映射
     * 3. 通过UseCase执行多平台并行搜索
     * 4. 更新搜索结果
     */
    fun searchMusic() {
        viewModelScope.launch {
            // 保存当前关键词，然后清除所有其他筛选条件
            val currentKeyword = _searchFilters.value.keyword
            _searchFilters.value = SearchFilters(keyword = currentKeyword)

            _isLoading.value = true
            _currentPage.value = 1
            _hasMoreData.value = true
            _errorMessage.value = null
            _searchResults.value = emptyList()  // 清空之前的搜索结果
            songInfoMap = emptyMap()  // 清空之前的SongInfo映射
            _selectedMusic.value = null  // 清空选中的歌曲

            Log.d(TAG, "开始搜索，关键词: '$currentKeyword'")

            try {
                val keyword = currentKeyword.takeIf { it.isNotEmpty() } ?: run {
                    Log.w(TAG, "搜索关键词为空，取消搜索")
                    return@launch
                }

                // 直接使用 LyricsApiServiceImpl 进行多平台搜索，保留原始 SongInfo
                Log.d(TAG, "调用multiSearch开始搜索...")
                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = com.example.lddc.model.SearchType.SONG,
                    sources = listOf(Source.QM, Source.NE, Source.KG),
                    page = 1
                )
                Log.d(TAG, "搜索完成，返回结果数: ${results.size}")

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

                Log.d(TAG, "搜索成功，转换后歌曲数: ${musicResults.size}, hasMoreData: ${_hasMoreData.value}")

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

                // 直接使用 LyricsApiServiceImpl 进行多平台搜索
                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = com.example.lddc.model.SearchType.SONG,
                    sources = listOf(Source.QM, Source.NE, Source.KG),
                    page = nextPage
                )

                val songInfoMapBuilder = mutableMapOf<String, SongInfo>()

                val newMusicResults = results.map { item ->
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

                if (newMusicResults.isNotEmpty()) {
                    songInfoMap = songInfoMap + songInfoMapBuilder
                    _searchResults.value += newMusicResults
                    _currentPage.value = nextPage
                    _hasMoreData.value = newMusicResults.size >= PAGE_SIZE
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
     *
     * 流程：
     * 1. 立即更新选中状态（显示基本信息）
     * 2. 使用原始 SongInfo 异步获取歌词
     * 3. 更新选中状态（包含歌词）
     */
    fun selectMusic(music: Music) {
        _selectedMusic.value = music

        val songInfo = songInfoMap[music.id]
        if (songInfo != null) {
            viewModelScope.launch {
                try {
                    // 使用 LyricsApiServiceImpl 的 getSongDetails 方法获取歌词
                    val (lyrics, lyricsType) = lyricsApiService.getSongDetails(songInfo)
                    _selectedMusic.value = music.copy(
                        lyrics = lyrics.content,
                        lyricsType = lyricsType
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "获取歌词失败", e)
                    // 获取歌词失败，保持已选中的基本信息
                    _errorMessage.value = e.message
                }
            }
        }
    }

    /**
     * 根据筛选条件过滤搜索结果
     *
     * @return 过滤后的结果
     */
    fun getFilteredResults(): List<Music> {
        return filterAndSortMusicUseCase.filterMusic(_searchResults.value, _searchFilters.value)
    }
}
