// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.search

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lddc.android.BuildConfig
import org.lddc.android.data.converter.LyricsConverter
import org.lddc.android.data.model.LyricInfo
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.remote.LyricsRepository
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.data.tag.SyltPayload
import org.lddc.android.domain.LyricsSaver
import org.lddc.android.domain.LyricsTranslator
import org.lddc.android.domain.BatchController

data class PreviewState(
    val song: SongInfo,
    val loading: Boolean = true,
    val lyricsText: String = "",
    val error: String? = null,
    val sylt: SyltPayload? = null,
    val lyrics: org.lddc.android.data.model.Lyrics? = null,
    val translating: Boolean = false,
    val candidates: List<LyricInfo> = emptyList(),
    val loadingCandidates: Boolean = false,
    val showCandidates: Boolean = false,
)

data class SearchUiState(
    val keyword: String = "",
    val searchType: SearchType = SearchType.SONG,
    val selectedSources: Set<Source> = setOf(Source.QM, Source.KG, Source.NE),
    val loading: Boolean = false,
    val results: List<SongInfo> = emptyList(),
    val songLists: List<SongListInfo> = emptyList(),
    val error: String? = null,
    val sourceErrors: Map<Source, String> = emptyMap(),
    val preview: PreviewState? = null,
    val message: String? = null,
    val saving: Boolean = false,
    val downloading: SongListInfo? = null,
    val downloadDone: Int = 0,
    val downloadTotal: Int = 0,
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onKeywordChange(value: String) {
        _state.update { it.copy(keyword = value) }
    }

    fun setSearchType(type: SearchType) {
        _state.update {
            it.copy(searchType = type, results = emptyList(), songLists = emptyList(), error = null)
        }
    }

    fun toggleSource(source: Source) {
        _state.update {
            val next = if (source in it.selectedSources) it.selectedSources - source else it.selectedSources + source
            it.copy(selectedSources = next.ifEmpty { setOf(source) })
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun search() {
        val current = _state.value
        val keyword = current.keyword.trim()
        if (keyword.isEmpty()) return
        val sources = current.selectedSources
        if (sources.isEmpty()) return

        if (current.searchType == SearchType.SONG) searchSongs(keyword, sources) else searchSongLists(keyword, sources, current.searchType)
    }

    private fun searchSongs(keyword: String, sources: Set<Source>) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, results = emptyList(), songLists = emptyList(), sourceErrors = emptyMap()) }
            val settings = settingsRepository.current()
            val ordered = settings.sourcePriority.filter { it in sources && LyricsRepository.isAvailable(it) }

            val outcomes = coroutineScope {
                ordered.map { source ->
                    async { source to runCatching { LyricsRepository.searchSongs(source, keyword) } }
                }.awaitAll()
            }

            val groups = outcomes.mapNotNull { (_, result) -> result.getOrNull()?.takeIf { it.isNotEmpty() } }
            val sourceErrors = outcomes
                .mapNotNull { (source, result) -> result.exceptionOrNull()?.let { source to friendlyError(it) } }
                .toMap()
            val merged = interleave(groups)

            _state.update {
                it.copy(
                    loading = false,
                    results = merged,
                    error = summaryOf(merged.isNotEmpty(), sourceErrors),
                    sourceErrors = sourceErrors,
                )
            }
        }
    }

    private fun searchSongLists(keyword: String, sources: Set<Source>, type: SearchType) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, results = emptyList(), songLists = emptyList(), sourceErrors = emptyMap()) }
            val settings = settingsRepository.current()
            val ordered = settings.sourcePriority.filter {
                it in sources && LyricsRepository.isAvailable(it) && type in LyricsRepository.supportedSearchTypes(it)
            }
            if (ordered.isEmpty()) {
                _state.update { it.copy(loading = false, error = "当前勾选的源都不支持${type.displayName}搜索") }
                return@launch
            }

            val outcomes = coroutineScope {
                ordered.map { source ->
                    async { source to runCatching { LyricsRepository.searchSongLists(source, keyword, type) } }
                }.awaitAll()
            }

            val errors = outcomes
                .mapNotNull { (source, result) -> result.exceptionOrNull()?.let { source to friendlyError(it) } }
                .toMap()
            val merged = outcomes.flatMap { it.second.getOrNull().orEmpty() }

            _state.update {
                it.copy(
                    loading = false,
                    songLists = merged,
                    error = summaryOf(merged.isNotEmpty(), errors),
                    sourceErrors = errors,
                )
            }
        }
    }

    private fun summaryOf(hasResult: Boolean, errors: Map<Source, String>): String? = when {
        hasResult -> null
        errors.isEmpty() -> "没有搜索到结果，换个关键词试试"
        else -> errors.entries.joinToString("；") { "${it.key.displayName}：${it.value}" }
    }

    private fun friendlyError(error: Throwable): String = when {
        error is org.lddc.android.data.remote.HttpException -> "网络错误 HTTP ${error.statusCode}"
        error.message?.contains("2001") == true -> "接口被服务端限制（上游 PC 版同样失败）"
        error.message?.contains("code=") == true -> error.message ?: "接口返回错误"
        error is java.net.SocketTimeoutException -> "网络超时"
        error is java.net.UnknownHostException -> "无法解析域名（检查网络）"
        error is javax.net.ssl.SSLException -> "TLS 握手失败"
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun interleave(groups: List<List<SongInfo>>): List<SongInfo> {
        val result = ArrayList<SongInfo>()
        val maxSize = groups.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            groups.forEach { group -> group.getOrNull(i)?.let(result::add) }
        }
        return result
    }

    // ---------- 歌单 / 专辑下载 ----------

    fun downloadSongList(songList: SongListInfo) {
        android.util.Log.i("LDDC", "downloadSongList: ${songList.title} type=${songList.type} id=${songList.id} source=${songList.source}")
        if (BatchController.isRunning()) {
            _state.update { it.copy(message = "已有任务在运行，请先等待或到本地页停止") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(downloading = songList, downloadDone = 0, downloadTotal = 0) }
            val settings = settingsRepository.current()
            val sources = settings.sourcePriority.filter {
                it in settings.enabledSources &&
                    LyricsRepository.isAvailable(it) &&
                    !LyricsRepository.isCircuitOpen(it)
            }
            BatchController.startSongList(
                context = getApplication(),
                songList = songList,
                settings = settings,
                sources = sources,
            )
            // 把控制器的进度镜像到本页状态
            val mirror = launch {
                BatchController.state.collect { batch ->
                    _state.update { it.copy(downloadDone = batch.done, downloadTotal = batch.total) }
                }
            }
            // 任务在应用级作用域里跑，切页面也不会中断；这里只等它结束好弹提示
            BatchController.awaitIdle()
            mirror.cancel()
            val message = BatchController.state.value.message ?: "下载结束"
            _state.update { it.copy(downloading = null, message = message) }
        }
    }

    // ---------- 单曲预览 ----------

    fun openPreview(song: SongInfo) {
        _state.update { it.copy(preview = PreviewState(song = song)) }
        viewModelScope.launch {
            runCatching { LyricsRepository.getLyrics(song) }
                .onSuccess { lyrics -> renderLyrics(song, lyrics) }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            preview = PreviewState(song = song, loading = false, error = error.message ?: "获取歌词失败"),
                        )
                    }
                }
        }
    }

    /** 渲染歌词到预览状态（首次打开、换版本共用） */
    private suspend fun renderLyrics(song: SongInfo, lyrics: org.lddc.android.data.model.Lyrics, keepCandidates: Boolean = true) {
        val settings = settingsRepository.current()
        val langs = settings.langsOrder.filter { lyrics.has(it) }.ifEmpty { listOf("orig") }
        val text = runCatching {
            LyricsConverter.convert(
                lyrics = lyrics,
                format = settings.lyricsFormat,
                langs = langs,
                msDigitCount = 3,
                tool = "LDDC Android ${BuildConfig.VERSION_NAME}",
            )
        }.getOrElse { "" }
        val sylt = LyricsSaver.buildSyltPayload(lyrics.fsLyrics().get("orig").orEmpty())
        val cached = _state.value.preview
        _state.update {
            it.copy(
                preview = PreviewState(
                    song = song,
                    loading = false,
                    lyricsText = text,
                    error = if (text.isBlank()) "该源没有返回可用歌词" else null,
                    sylt = sylt,
                    lyrics = lyrics,
                    candidates = if (keepCandidates) cached?.candidates.orEmpty() else emptyList(),
                ),
            )
        }
    }

    /** 拉取该歌曲在同一源下的其它歌词版本 */
    fun loadCandidates() {
        val preview = _state.value.preview ?: return
        if (preview.loadingCandidates) return
        viewModelScope.launch {
            _state.update { it.copy(preview = preview.copy(loadingCandidates = true)) }
            val list = runCatching { LyricsRepository.getLyricsList(preview.song.source, preview.song) }
                .getOrNull().orEmpty()
            _state.update {
                it.copy(
                    preview = preview.copy(
                        loadingCandidates = false,
                        candidates = list,
                        showCandidates = list.size > 1,
                    ),
                    message = when {
                        list.isEmpty() -> "${preview.song.source.displayName}没有可选版本"
                        list.size == 1 -> "该源只有一个版本（上传者信息可能为空）"
                        else -> null
                    },
                )
            }
        }
    }

    fun hideCandidates() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = preview.copy(showCandidates = false)) }
    }

    fun selectCandidate(candidate: LyricInfo) {
        val preview = _state.value.preview ?: return
        _state.update {
            it.copy(preview = preview.copy(showCandidates = false, loading = true, error = null))
        }
        viewModelScope.launch {
            runCatching { LyricsRepository.getLyrics(candidate) }
                .onSuccess { lyrics -> renderLyrics(preview.song, lyrics) }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            preview = preview.copy(loading = false, error = error.message ?: "该版本取词失败"),
                        )
                    }
                }
        }
    }

    fun translatePreview() {
        val preview = _state.value.preview ?: return
        val lyrics = preview.lyrics ?: return
        if (preview.translating) return
        viewModelScope.launch {
            if (preview.lyrics.has("ts")) {
                _state.update { it.copy(message = "该源已自带译文，无需翻译") }
                return@launch
            }
            _state.update { it.copy(preview = preview.copy(translating = true)) }
            val settings = settingsRepository.current()
            LyricsTranslator.translate(lyrics, settings).fold(
                onSuccess = {
                    val langs = settings.langsOrder.filter { lyrics.has(it) }.ifEmpty { listOf("orig") }
                    val text = runCatching {
                        LyricsConverter.convert(lyrics, settings.lyricsFormat, langs, msDigitCount = 3, tool = "LDDC Android ${BuildConfig.VERSION_NAME}")
                    }.getOrDefault(preview.lyricsText)
                    _state.update {
                        it.copy(preview = preview.copy(translating = false, lyricsText = text, sylt = LyricsSaver.buildSyltPayload(lyrics.fsLyrics().get("orig").orEmpty())))
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(preview = preview.copy(translating = false), message = "翻译失败：${error.message}") }
                },
            )
        }
    }

    fun closePreview() = _state.update { it.copy(preview = null) }

    fun saveToFile(uri: Uri, displayName: String) {
        val preview = _state.value.preview ?: return
        if (preview.lyricsText.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val settings = settingsRepository.current()
            val result = LyricsSaver.save(
                context = getApplication<Application>(),
                uri = uri,
                displayName = displayName,
                text = preview.lyricsText,
                sylt = preview.sylt,
                id3Version = settings.id3Version.flag,
            )
            val message = when (result) {
                is LyricsSaver.Result.Success -> "已写入 ${preview.song.title.orEmpty()}"
                is LyricsSaver.Result.Failure -> "写入失败：${result.message}"
            }
            _state.update { it.copy(saving = false, message = message) }
        }
    }
}

private val SearchType.displayName: String
    get() = when (this) {
        SearchType.SONG -> "单曲"
        SearchType.ALBUM -> "专辑"
        SearchType.SONGLIST -> "歌单"
    }
