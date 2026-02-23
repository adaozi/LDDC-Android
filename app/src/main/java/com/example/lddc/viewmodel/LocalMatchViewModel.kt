package com.example.lddc.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lddc.data.UserPreferences
import com.example.lddc.di.AppModule
import com.example.lddc.domain.usecase.FilterAndSortMusicUseCase
import com.example.lddc.domain.usecase.GetLyricsUseCase
import com.example.lddc.domain.usecase.MatchLocalMusicUseCase
import com.example.lddc.domain.usecase.MatchProgressResult
import com.example.lddc.domain.usecase.SearchSongsUseCase
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LocalMusicMatchResult
import com.example.lddc.model.LocalMusicMatchStatus
import com.example.lddc.model.Lyrics
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.MatchProgress
import com.example.lddc.model.Music
import com.example.lddc.model.ScanProgress
import com.example.lddc.model.SearchFilters
import com.example.lddc.model.SongInfo
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsApiServiceImpl
import com.example.lddc.utils.SortUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 本地音乐匹配 ViewModel
 *
 * 管理本地音乐扫描、歌词匹配和写入的完整流程
 * 遵循MVVM架构，通过UseCase层执行业务逻辑
 */
class LocalMatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LocalMatchViewModel"
    }

    // UseCase层依赖（通过DI获取）
    private val matchLocalMusicUseCase: MatchLocalMusicUseCase = AppModule.provideMatchLocalMusicUseCase(application)
    private val searchSongsUseCase: SearchSongsUseCase = AppModule.provideSearchSongsUseCase(application)
    private val getLyricsUseCase: GetLyricsUseCase = AppModule.provideGetLyricsUseCase(application)
    private val filterAndSortMusicUseCase: FilterAndSortMusicUseCase = AppModule.provideFilterAndSortMusicUseCase()
    private val userPreferences: UserPreferences = UserPreferences(application)

    // 直接使用 LyricsApiServiceImpl 获取歌词（保留原始 SongInfo）
    private val lyricsApiService = LyricsApiServiceImpl()

    // ==================== 扫描状态 ====================
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    // ==================== 音乐列表 ====================
    private val _localMusicList = MutableStateFlow<List<LocalMusicInfo>>(emptyList())
    val localMusicList: StateFlow<List<LocalMusicInfo>> = _localMusicList.asStateFlow()

    // ==================== 匹配状态 ====================
    private val _matchState = MutableStateFlow<MatchState>(MatchState.Idle)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    private val _matchProgress = MutableStateFlow(MatchProgress())
    val matchProgress: StateFlow<MatchProgress> = _matchProgress.asStateFlow()

    private val _matchResults = MutableStateFlow<List<LocalMusicMatchResult>>(emptyList())
    val matchResults: StateFlow<List<LocalMusicMatchResult>> = _matchResults.asStateFlow()

    // ==================== 保存方式选择 ====================
    private val _showSaveModeDialog = MutableStateFlow(false)
    val showSaveModeDialog: StateFlow<Boolean> = _showSaveModeDialog.asStateFlow()

    private val _pendingMatchList = MutableStateFlow<List<LocalMusicInfo>?>(null)
    val pendingMatchList: StateFlow<List<LocalMusicInfo>?> = _pendingMatchList.asStateFlow()

    private val _defaultSaveMode = MutableStateFlow(LyricsWriteMode.EMBEDDED)
    val defaultSaveMode: StateFlow<LyricsWriteMode> = _defaultSaveMode.asStateFlow()

    // ==================== 选中状态 ====================
    private val _selectedLocalMusic = MutableStateFlow<LocalMusicInfo?>(null)
    val selectedLocalMusic: StateFlow<LocalMusicInfo?> = _selectedLocalMusic.asStateFlow()

    private val _selectedMatchResult = MutableStateFlow<LocalMusicMatchResult?>(null)
    val selectedMatchResult: StateFlow<LocalMusicMatchResult?> = _selectedMatchResult.asStateFlow()

    // ==================== 歌词相关 ====================
    private val _localLyrics = MutableStateFlow<String?>(null)
    val localLyrics: StateFlow<String?> = _localLyrics.asStateFlow()

    // ==================== 搜索相关 ====================
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Music>>(emptyList())
    val searchResults: StateFlow<List<Music>> = _searchResults.asStateFlow()

    /**
     * SongInfo 映射表
     * Key: 歌曲ID
     * Value: 原始 SongInfo 对象（用于获取歌词详情）
     */
    private var songInfoMap: Map<String, SongInfo> = emptyMap()

    private val _selectedSearchResult = MutableStateFlow<Music?>(null)
    val selectedSearchResult: StateFlow<Music?> = _selectedSearchResult.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    // ==================== 搜索筛选条件 ====================
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()

    fun updateSearchFilters(filters: SearchFilters) {
        _searchFilters.value = filters
    }

    // ==================== 视图模式 ====================
    private val _viewMode = MutableStateFlow<ViewMode>(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // ==================== 派生状态 ====================
    val folderList: StateFlow<List<String>> = combine(
        _localMusicList,
        _viewMode
    ) { musicList, mode ->
        if (mode == ViewMode.FOLDER) {
            musicList.map { it.folderPath }.distinct().sorted()
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val musicByFolder: StateFlow<Map<String, List<LocalMusicInfo>>> = _localMusicList.map { list ->
        list.groupBy { it.folderPath }.toSortedMap()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val displayedMusicList: StateFlow<List<LocalMusicInfo>> = combine(
        _localMusicList,
        _viewMode,
        _selectedFolder
    ) { list, mode, folder ->
        val filteredList = when (mode) {
            ViewMode.LIST -> list
            ViewMode.FOLDER -> {
                if (folder != null) {
                    list.filter { it.folderPath == folder }
                } else {
                    emptyList()
                }
            }
        }
        // 按标题排序（拼音/字母顺序，符号开头的排后面）
        SortUtils.sortByTitle(filteredList)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ==================== 协程任务 ====================
    private var scanJob: Job? = null
    private var matchJob: Job? = null
    private var searchJob: Job? = null

    // ==================== 公共方法 ====================

    init {
        // 初始化时读取上次保存的视图模式和文件夹选择
        viewModelScope.launch {
            // 读取上次选择的视图模式
            val savedViewMode = userPreferences.lastViewMode.first()
            if (savedViewMode != null) {
                try {
                    _viewMode.value = ViewMode.valueOf(savedViewMode)
                } catch (e: Exception) {
                    Log.w(TAG, "读取保存的视图模式失败: $savedViewMode")
                }
            }

            // 读取上次选择的文件夹
            val savedFolder = userPreferences.lastSelectedFolder.first()
            if (savedFolder != null && _viewMode.value == ViewMode.FOLDER) {
                _selectedFolder.value = savedFolder
                Log.d(TAG, "恢复上次选择的文件夹: $savedFolder")
            }
        }
    }

    /**
     * 切换视图模式
     */
    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        if (mode == ViewMode.LIST) {
            _selectedFolder.value = null
        }
        // 保存视图模式
        viewModelScope.launch {
            userPreferences.saveLastViewMode(mode.name)
        }
    }

    /**
     * 选择文件夹
     */
    fun selectFolder(folderPath: String?) {
        _selectedFolder.value = folderPath
        // 保存文件夹选择
        viewModelScope.launch {
            userPreferences.saveLastSelectedFolder(folderPath)
            Log.d(TAG, "保存文件夹选择: $folderPath")
        }
    }

    /**
     * 开始扫描本地音乐（使用并行扫描）
     */
    fun startScan() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _scanState.value = ScanState.Scanning
                _localMusicList.value = emptyList()
                _matchResults.value = emptyList()

                val musicList = mutableListOf<LocalMusicInfo>()

                // 使用并行扫描所有音乐
                matchLocalMusicUseCase.scanAllMusicParallel().collect { (music, progress) ->
                    musicList.add(music)
                    _localMusicList.value = musicList.toList()
                    _scanProgress.value = progress
                }

                _scanState.value = ScanState.Completed(musicList.size)

            } catch (e: CancellationException) {
                _scanState.value = ScanState.Cancelled
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "扫描失败", e)
                _scanState.value = ScanState.Error(e.message ?: "扫描失败")
            }
        }
    }

    /**
     * 取消扫描
     */
    fun cancelScan() {
        scanJob?.cancel()
    }

    /**
     * 使用并行扫描扫描指定目录
     * 根据设备性能动态调整线程数
     *
     * @param directoryPath 目录路径
     * @param includeSubDirs 是否包含子目录
     */
    fun startParallelScan(directoryPath: String, includeSubDirs: Boolean = true) {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                _scanState.value = ScanState.Scanning
                _localMusicList.value = emptyList()
                _matchResults.value = emptyList()

                val musicList = mutableListOf<LocalMusicInfo>()

                matchLocalMusicUseCase.scanDirectoryParallel(directoryPath, includeSubDirs).collect { (music, progress) ->
                    musicList.add(music)
                    _localMusicList.value = musicList.toList()
                    _scanProgress.value = progress
                }

                _scanState.value = ScanState.Completed(musicList.size)

            } catch (e: CancellationException) {
                _scanState.value = ScanState.Cancelled
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "并行扫描失败", e)
                _scanState.value = ScanState.Error(e.message ?: "扫描失败")
            }
        }
    }

    /**
     * 显示保存方式选择对话框
     *
     * @param musicList 要匹配的音乐列表
     */
    fun showSaveModeDialog(musicList: List<LocalMusicInfo>) {
        if (musicList.isEmpty()) return
        _pendingMatchList.value = musicList
        _showSaveModeDialog.value = true
    }

    /**
     * 隐藏保存方式选择对话框
     */
    fun hideSaveModeDialog() {
        _showSaveModeDialog.value = false
        _pendingMatchList.value = null
    }

    /**
     * 设置默认保存方式
     *
     * @param mode 保存方式
     */
    fun setDefaultSaveMode(mode: LyricsWriteMode) {
        _defaultSaveMode.value = mode
    }

    /**
     * 开始自动匹配歌词（带保存方式选择）
     *
     * @param musicList 要匹配的音乐列表，默认为空则匹配所有本地音乐
     * @param saveLyrics 是否保存歌词
     * @param writeMode 歌词写入模式
     */
    fun startMatch(
        musicList: List<LocalMusicInfo>? = null,
        saveLyrics: Boolean = true,
        writeMode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ) {
        if (matchJob?.isActive == true) return

        // 使用传入的列表或所有本地音乐
        val targetList = musicList ?: _localMusicList.value
        if (targetList.isEmpty()) return

        // 按标题排序（拼音/字母顺序，符号开头的排后面）
        val sortedList = SortUtils.sortByTitle(targetList)
        Log.d(TAG, "开始匹配 ${sortedList.size} 首音乐（已排序）, 保存歌词: $saveLyrics, 模式: $writeMode")

        // 清空之前的结果
        _matchResults.value = emptyList()

        matchJob = viewModelScope.launch {
            try {
                _matchState.value = MatchState.Matching

                // 使用多线程并行匹配
                matchLocalMusicUseCase.matchMultipleMusicParallel(sortedList, saveLyrics, writeMode)
                    .collect { result ->
                        when (result) {
                            is MatchProgressResult.InProgress -> {
                                _matchProgress.value = result.progress
                                _matchResults.value = result.results
                            }
                            is MatchProgressResult.Completed -> {
                                _matchResults.value = result.results
                                _matchState.value = MatchState.Completed(result.successCount)
                            }
                        }
                    }

            } catch (e: CancellationException) {
                _matchState.value = MatchState.Cancelled
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "匹配失败", e)
                _matchState.value = MatchState.Error(e.message ?: "匹配失败")
            }
        }
    }

    /**
     * 确认开始匹配（从对话框调用）
     *
     * @param saveLyrics 是否保存歌词
     * @param writeMode 歌词写入模式
     */
    fun confirmStartMatch(saveLyrics: Boolean, writeMode: LyricsWriteMode) {
        val musicList = _pendingMatchList.value
        if (musicList != null) {
            hideSaveModeDialog()
            startMatch(musicList, saveLyrics, writeMode)
        }
    }

    /**
     * 取消匹配
     */
    fun cancelMatch() {
        matchJob?.cancel()
    }

    /**
     * 选中本地音乐
     */
    fun selectLocalMusic(music: LocalMusicInfo) {
        _selectedLocalMusic.value = music
        _localLyrics.value = null
        _searchResults.value = emptyList()
        _selectedSearchResult.value = null
        songInfoMap = emptyMap() // 清空之前的 SongInfo 映射

        // 加载本地歌词
        viewModelScope.launch {
            val lyrics = matchLocalMusicUseCase.readLocalLyrics(music.filePath)
            _localLyrics.value = lyrics
        }
    }

    /**
     * 清除选中的本地音乐
     */
    fun clearSelectedLocalMusic() {
        _selectedLocalMusic.value = null
        _localLyrics.value = null
        songInfoMap = emptyMap()
    }

    /**
     * 搜索歌曲
     */
    fun searchSongs(keyword: String, source: Source = Source.QM) {
        if (searchJob?.isActive == true) searchJob?.cancel()
        if (keyword.isBlank()) return

        _searchKeyword.value = keyword
        searchJob = viewModelScope.launch {
            try {
                _isSearching.value = true

                // 直接使用 LyricsApiServiceImpl 进行搜索，保留原始 SongInfo
                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = com.example.lddc.model.SearchType.SONG,
                    sources = listOf(source),
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
                        lyrics = "",
                        lyricsType = "未知"
                    )
                }

                songInfoMap = songInfoMapBuilder
                _searchResults.value = musicResults

            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 搜索歌曲并根据本地音乐信息筛选结果
     * 同时搜索多个平台（QQ音乐、网易云音乐、酷狗音乐）并合并结果
     * 按平台交叉排序（QQ、KG、NE 轮流），然后按匹配度排序
     */
    fun searchSongsWithFilter(keyword: String, localMusic: LocalMusicInfo) {
        if (searchJob?.isActive == true) searchJob?.cancel()
        if (keyword.isBlank()) return

        _searchKeyword.value = keyword
        _isSearching.value = true
        searchJob = viewModelScope.launch {
            try {
                // 直接使用 LyricsApiServiceImpl 进行多平台搜索，保留原始 SongInfo
                val results = lyricsApiService.multiSearch(
                    keyword = keyword,
                    searchType = com.example.lddc.model.SearchType.SONG,
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
                        lyrics = "",
                        lyricsType = "未知"
                    )
                }

                songInfoMap = songInfoMapBuilder

                // 根据本地音乐信息筛选和排序
                _searchResults.value = filterAndSortMusicUseCase.filterAndSortByLocalMusic(musicResults, localMusic)

            } catch (e: Exception) {
                Log.e(TAG, "搜索失败", e)
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * 选中搜索结果
     */
    fun selectSearchResult(music: Music) {
        _selectedSearchResult.value = music
    }

    /**
     * 加载搜索结果的歌曲歌词
     *
     * 流程：
     * 1. 立即更新选中状态（显示基本信息）
     * 2. 使用原始 SongInfo 异步获取歌词
     * 3. 更新选中状态（包含歌词）
     */
    fun loadLyricsForSearchResult(music: Music) {
        viewModelScope.launch {
            try {
                _isLoadingLyrics.value = true

                val songInfo = songInfoMap[music.id]
                if (songInfo != null) {
                    // 使用 LyricsApiServiceImpl 的 getSongDetails 方法获取歌词
                    val (lyrics, lyricsType) = lyricsApiService.getSongDetails(songInfo)
                    val updatedMusic = music.copy(
                        lyrics = lyrics.content,
                        lyricsType = lyricsType
                    )
                    Log.d(TAG, "歌词加载成功，长度: ${updatedMusic.lyrics.length}")
                    _selectedSearchResult.value = updatedMusic
                } else {
                    Log.e(TAG, "无法获取歌词：未找到对应的 SongInfo")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载歌词失败", e)
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    /**
     * 写入歌词
     */
    fun writeLyrics(
        music: LocalMusicInfo,
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.EMBEDDED,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                val result = matchLocalMusicUseCase.writeLyrics(music.filePath, lyrics, mode)

                if (result.success) {
                    // 更新匹配结果状态
                    val updatedResults = _matchResults.value.map { r ->
                        if (r.localMusic.id == music.id) {
                            r.copy(status = LocalMusicMatchStatus.WRITTEN)
                        } else r
                    }
                    _matchResults.value = updatedResults

                    // 重新读取本地歌词
                    val newLyrics = matchLocalMusicUseCase.readLocalLyrics(music.filePath)
                    _localLyrics.value = newLyrics

                    onComplete(true, null)
                } else {
                    onComplete(false, result.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入歌词失败", e)
                onComplete(false, e.message)
            }
        }
    }

    /**
     * 获取歌曲歌词
     */
    suspend fun getLyrics(music: Music): Result<Lyrics> {
        val songInfo = songInfoMap[music.id]
        return if (songInfo != null) {
            try {
                val lyrics = lyricsApiService.getLyrics(songInfo)
                Result.success(lyrics)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException("未找到对应的 SongInfo"))
        }
    }

    /**
     * 将选中的搜索结果歌词写入当前选中的本地音乐
     */
    fun writeSelectedLyricsToLocalMusic(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val localMusic = _selectedLocalMusic.value
                val searchResult = _selectedSearchResult.value

                if (localMusic == null || searchResult == null || searchResult.lyrics.isBlank()) {
                    Log.e(TAG, "无法写入歌词：本地音乐或搜索结果为空")
                    onComplete(false)
                    return@launch
                }

                // 写入歌词到本地音乐文件
                val result = matchLocalMusicUseCase.writeLyrics(
                    localMusic.filePath,
                    searchResult.lyrics,
                    LyricsWriteMode.EMBEDDED
                )

                if (result.success) {
                    // 更新本地歌词状态
                    _localLyrics.value = searchResult.lyrics

                    // 更新匹配结果状态
                    val updatedResults = _matchResults.value.map { r ->
                        if (r.localMusic.id == localMusic.id) {
                            r.copy(status = LocalMusicMatchStatus.WRITTEN)
                        } else r
                    }
                    _matchResults.value = updatedResults

                    Log.d(TAG, "歌词写入成功: ${localMusic.filePath}")
                    onComplete(true)
                } else {
                    Log.e(TAG, "歌词写入失败: ${result.errorMessage}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入歌词失败", e)
                onComplete(false)
            }
        }
    }

    /**
     * 将指定的歌词写入当前选中的本地音乐
     */
    fun writeLyricsToLocalMusic(
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.EMBEDDED,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                val localMusic = _selectedLocalMusic.value

                if (localMusic == null || lyrics.isBlank()) {
                    Log.e(TAG, "无法写入歌词：本地音乐为空或歌词为空")
                    onComplete(false, "本地音乐为空或歌词为空")
                    return@launch
                }

                // 写入歌词到本地音乐文件（使用用户选择的保存模式）
                val result = matchLocalMusicUseCase.writeLyrics(
                    localMusic.filePath,
                    lyrics,
                    mode
                )

                if (result.success) {
                    // 更新本地歌词状态
                    _localLyrics.value = lyrics

                    // 更新匹配结果状态
                    val updatedResults = _matchResults.value.map { r ->
                        if (r.localMusic.id == localMusic.id) {
                            r.copy(status = LocalMusicMatchStatus.WRITTEN)
                        } else r
                    }
                    _matchResults.value = updatedResults

                    Log.d(TAG, "歌词写入成功: ${localMusic.filePath}, 模式: $mode")
                    onComplete(true, null)
                } else {
                    Log.e(TAG, "歌词写入失败: ${result.errorMessage}")
                    onComplete(false, result.errorMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "写入歌词失败", e)
                onComplete(false, e.message)
            }
        }
    }

    /**
     * 刷新本地歌词
     */
    fun refreshLocalLyrics() {
        val music = _selectedLocalMusic.value ?: return
        viewModelScope.launch {
            val lyrics = matchLocalMusicUseCase.readLocalLyrics(music.filePath)
            _localLyrics.value = lyrics
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

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        matchJob?.cancel()
        searchJob?.cancel()
    }
}

// ==================== 状态类定义 ====================

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Completed(val count: Int) : ScanState()
    object Cancelled : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class MatchState {
    object Idle : MatchState()
    object Matching : MatchState()
    data class Completed(val matchedCount: Int) : MatchState()
    object Cancelled : MatchState()
    data class Error(val message: String) : MatchState()
}

enum class ViewMode {
    LIST,
    FOLDER
}
