package com.example.lddc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lddc.model.*
import com.example.lddc.service.LyricsApiServiceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 音乐搜索用例接口
 *
 * 定义音乐搜索相关的所有业务操作，包括：
 * - 搜索歌曲
 * - 加载更多结果
 * - 筛选结果
 * - 查看歌曲详情
 */
interface MusicSearchUseCase {
    /** 当前搜索筛选条件 */
    val searchFilters: StateFlow<SearchFilters>

    /** 搜索结果列表 */
    val searchResults: StateFlow<List<Music>>

    /** 是否正在首次加载 */
    val isLoading: StateFlow<Boolean>

    /** 是否正在加载更多 */
    val isLoadingMore: StateFlow<Boolean>

    /** 当前选中的歌曲 */
    val selectedMusic: StateFlow<Music?>

    /** 是否还有更多数据可加载 */
    val hasMoreData: StateFlow<Boolean>

    /** 当前页码 */
    val currentPage: StateFlow<Int>

    /** 更新搜索筛选条件 */
    fun updateSearchFilters(filters: SearchFilters)

    /** 执行搜索 */
    fun searchMusic()

    /** 加载下一页 */
    fun loadMore()

    /** 选中歌曲（同时获取歌词详情） */
    fun selectMusic(music: Music)

    /** 清除选中的歌曲 */
    fun clearSelectedMusic()

    /** 清除所有筛选条件 */
    fun clearFilters()
}

/**
 * 音乐搜索 ViewModel
 *
 * 管理音乐搜索相关的所有状态和逻辑
 * - 使用 StateFlow 实现响应式数据流
 * - 自动处理配置变更（屏幕旋转等）
 * - 支持多平台并行搜索
 */
class MusicViewModel : ViewModel(), MusicSearchUseCase {

    /** 歌词API服务，用于搜索和获取歌词 */
    private val lyricsApiService = LyricsApiServiceImpl()

    // ==================== 状态管理 ====================

    /** 搜索筛选条件（关键词、平台等） */
    private val _searchFilters = MutableStateFlow(SearchFilters())
    override val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()

    /** 搜索结果列表（已转换为 Music 对象） */
    private val _searchResults = MutableStateFlow<List<Music>>(emptyList())
    override val searchResults: StateFlow<List<Music>> = _searchResults.asStateFlow()

    /** 首次加载状态 */
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 加载更多状态 */
    private val _isLoadingMore = MutableStateFlow(false)
    override val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    /** 当前选中的歌曲（包含歌词详情） */
    private val _selectedMusic = MutableStateFlow<Music?>(null)
    override val selectedMusic: StateFlow<Music?> = _selectedMusic.asStateFlow()

    /** 当前页码（用于分页加载） */
    private val _currentPage = MutableStateFlow(1)
    override val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    /** 是否还有更多数据 */
    private val _hasMoreData = MutableStateFlow(true)
    override val hasMoreData: StateFlow<Boolean> = _hasMoreData.asStateFlow()

    /**
     * SongInfo 映射表
     * Key: 歌曲ID
     * Value: 原始 SongInfo 对象（用于获取歌词详情）
     */
    private var songInfoMap: Map<String, SongInfo> = emptyMap()

    // ==================== 业务方法 ====================

    override fun updateSearchFilters(filters: SearchFilters) {
        _searchFilters.value = filters
    }

    /**
     * 执行搜索
     *
     * 流程：
     * 1. 重置分页状态
     * 2. 并行搜索多个平台（QQ音乐、网易云、酷狗）
     * 3. 转换数据格式
     * 4. 更新搜索结果
     */
    override fun searchMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            _currentPage.value = 1
            _hasMoreData.value = true

            try {
                val filters = _searchFilters.value
                val keyword = filters.keyword.takeIf { it.isNotEmpty() } ?: "周杰伦"

                // 并行搜索多个平台
                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = SearchType.SONG,
                    sources = listOf(Source.QM, Source.NE, Source.KG),
                    page = 1
                )

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
                _hasMoreData.value = musicResults.size >= 20
            } catch (_: Exception) {
                _searchResults.value = emptyList()
                _hasMoreData.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 加载更多数据（分页）
     *
     * 流程与 searchMusic 类似，但会追加到现有列表
     */
    override fun loadMore() {
        if (_isLoadingMore.value || !_hasMoreData.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = _currentPage.value + 1

            try {
                val filters = _searchFilters.value
                val keyword = filters.keyword.takeIf { it.isNotEmpty() } ?: "周杰伦"

                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = SearchType.SONG,
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
                    _hasMoreData.value = newMusicResults.size >= 20
                } else {
                    _hasMoreData.value = false
                }
            } catch (_: Exception) {
                _hasMoreData.value = false
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
     * 2. 异步获取歌词
     * 3. 更新选中状态（包含歌词）
     */
    override fun selectMusic(music: Music) {
        _selectedMusic.value = music

        val songInfo = songInfoMap[music.id]
        if (songInfo != null) {
            viewModelScope.launch {
                try {
                    val (lyrics, lyricsType) = lyricsApiService.getSongDetails(songInfo)
                    _selectedMusic.value = music.copy(
                        lyrics = lyrics.content,
                        lyricsType = lyricsType
                    )
                } catch (_: Exception) {
                    // 获取歌词失败，保持已选中的基本信息
                }
            }
        }
    }

    override fun clearSelectedMusic() {
        _selectedMusic.value = null
    }

    override fun clearFilters() {
        _searchFilters.value = SearchFilters()
    }
}
