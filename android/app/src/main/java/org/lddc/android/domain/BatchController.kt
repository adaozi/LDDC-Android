// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.lddc.android.data.model.SongInfo
import org.lddc.android.data.model.SongListInfo
import org.lddc.android.data.model.Source
import org.lddc.android.data.remote.LyricsRepository
import org.lddc.android.data.settings.AppSettings
import org.lddc.android.service.BatchService
import java.io.File

/**
 * 批量任务控制器（应用级作用域）：本地目录匹配、云端歌单下载共用同一套
 * 前台服务 + 并发控制 + 断点续跑。
 */
object BatchController {

    /** 一个待处理条目：本地文件用 [file]，云端歌曲用 [song] */
    data class Item(
        val key: String,
        val label: String,
        val status: LocalMatchEngine.Status = LocalMatchEngine.Status.PENDING,
        val detail: String = "",
        val file: File? = null,
        val song: SongInfo? = null,
    )

    data class State(
        val running: Boolean = false,
        val title: String = "",
        val items: List<Item> = emptyList(),
        val done: Int = 0,
        val total: Int = 0,
        val message: String? = null,
    )

    private const val PREFS = "batch_progress"
    private const val KEY_LOCAL = "done_paths"
    private const val KEY_SONGLIST = "done_songlist"

    /** 复核断点条目时的并发数（只读标签，IO 密集） */
    private const val REVIEW_CONCURRENCY = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun isRunning(): Boolean = _state.value.running

    /** 等待当前任务结束（调用方在 UI 协程里等待完成提示） */
    suspend fun awaitIdle() {
        job?.join()
    }

    // ---------- 入口一：本地文件 ----------

    fun startFiles(
        context: Context,
        files: List<File>,
        settings: AppSettings,
        sources: List<Source>,
    ) {
        if (_state.value.running || files.isEmpty()) return
        val appContext = context.applicationContext

        val items = files.map { Item(key = it.absolutePath, label = it.name, file = it) }
        job = scope.launch {
            runBatch(appContext, KEY_LOCAL, "本地匹配", items, settings, sources) { item, s, src ->
                val file = item.file
                if (file == null) {
                    LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.FAILED, "文件为空")
                } else {
                    LocalMatchEngine.matchOne(file, s, src)
                }
            }
        }
    }

    // ---------- 入口二：云端歌单 / 专辑 ----------

    fun startSongList(
        context: Context,
        songList: SongListInfo,
        settings: AppSettings,
        sources: List<Source>,
    ) {
        if (_state.value.running) return
        val appContext = context.applicationContext

        android.util.Log.i("LDDC", "startSongList: ${songList.title} id=${songList.id}")
        _state.update { State(running = true, title = songList.title, message = "正在获取曲目列表…") }
        startServiceSafely(appContext)

        job = scope.launch {
            val songs = runCatching { LyricsRepository.getSongList(songList.source, songList) }
                .getOrElse { error ->
                    android.util.Log.e("LDDC", "getSongList failed", error)
                    _state.update { it.copy(running = false, message = "获取曲目失败：${error.message ?: error.javaClass.simpleName}") }
                    stopServiceSafely(appContext)
                    return@launch
                }
            android.util.Log.i("LDDC", "startSongList resolved songs=${songs.size}")
            if (songs.isEmpty()) {
                _state.update { it.copy(running = false, message = "该${songList.type}没有曲目") }
                stopServiceSafely(appContext)
                return@launch
            }

            val items = songs.map { song ->
                Item(
                    key = "${song.source}|${song.id}|${song.title}",
                    label = song.artistTitle().ifEmpty { song.title.orEmpty() },
                    song = song,
                )
            }
            runBatch(appContext, KEY_SONGLIST, songList.title, items, settings, sources) { item, s, src ->
                val song = item.song
                if (song == null) {
                    LocalMatchEngine.MatchOutcome(LocalMatchEngine.Status.FAILED, "歌曲为空")
                } else {
                    SongListDownloader.downloadOne(song, s, src)
                }
            }
        }
    }

    // ---------- 公共执行逻辑 ----------

    private suspend fun runBatch(
        appContext: Context,
        prefsKey: String,
        title: String,
        items: List<Item>,
        settings: AppSettings,
        sources: List<Source>,
        runner: suspend (Item, AppSettings, List<Source>) -> LocalMatchEngine.MatchOutcome,
    ) {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val finished = prefs.getStringSet(prefsKey, emptySet())?.toMutableSet() ?: mutableSetOf()

        val marked = items.map { item ->
            // 断点只服务于「跳过已有歌词」；用户关掉跳过后应当全量重跑
            if (settings.skipExistingLyrics && item.key in finished) {
                item.copy(status = LocalMatchEngine.Status.SUCCESS, detail = "上次已完成")
            } else {
                item
            }
        }
        _state.update {
            State(
                running = true,
                title = title,
                items = marked,
                done = marked.count { item -> item.status == LocalMatchEngine.Status.SUCCESS },
                total = marked.size,
            )
        }

        // 断点条目也要复核一次：上次写成逐行的，这次要能重新匹配，而不是永远显示「上次已完成」
        reviewCompleted(prefs, prefsKey, finished, settings)

        val pending = _state.value.items.indices.filter {
            _state.value.items[it].status == LocalMatchEngine.Status.PENDING
        }
        if (pending.isEmpty()) {
            _state.update { it.copy(running = false, message = "全部已完成") }
            stopServiceSafely(appContext)
            return
        }

        startServiceSafely(appContext)
        val semaphore = Semaphore(settings.concurrency.coerceIn(1, 8))

        coroutineScope {
            pending.forEach { index ->
                launch {
                    semaphore.withPermit {
                        val item = _state.value.items.getOrNull(index) ?: return@withPermit
                        setItem(index) { it.copy(status = LocalMatchEngine.Status.MATCHING) }
                        val outcome = runCatching { runner(item, settings, sources) }
                            .getOrElse {
                                LocalMatchEngine.MatchOutcome(
                                    LocalMatchEngine.Status.FAILED,
                                    it.message ?: it.javaClass.simpleName,
                                )
                            }
                        setItem(index) { it.copy(status = outcome.status, detail = outcome.detail) }

                        if (outcome.status == LocalMatchEngine.Status.SUCCESS) {
                            // 立即落盘：进程被杀也不丢已完成记录
                            synchronized(finished) {
                                finished.add(item.key)
                                prefs.edit().putStringSet(prefsKey, HashSet(finished)).commit()
                            }
                        }

                        val done = _state.updateAndGetCount()
                        val total = _state.value.total
                        BatchService.update(
                            appContext,
                            title,
                            "$done/$total  ${item.label}",
                            done,
                            total,
                        )
                    }
                }
            }
        }

        _state.update { it.copy(running = false, message = "完成：${it.total} 项") }
        stopServiceSafely(appContext)
    }

    /**
     * 复核断点条目（仅在「跳过已有歌词」+「逐字歌词才跳过」都开启时执行）：
     * - 已有逐字 → 改成「跳过 / 已有逐字歌词」
     * - 非逐字或歌词已丢失 → 从断点里移除并回到待处理队列，重新匹配覆盖
     * - 有标签但读不出内容 → 保留「上次已完成」
     *
     * 只读本地标签，不联网，1000 首也只会多花几秒。
     */
    private suspend fun reviewCompleted(
        prefs: SharedPreferences,
        prefsKey: String,
        finished: MutableSet<String>,
        settings: AppSettings,
    ) {
        if (!settings.skipExistingLyrics || !settings.refreshNonVerbatimLyrics) return

        val targets = _state.value.items.indices.filter { index ->
            val item = _state.value.items[index]
            item.status == LocalMatchEngine.Status.SUCCESS && item.file != null
        }
        if (targets.isEmpty()) return

        val semaphore = Semaphore(REVIEW_CONCURRENCY)
        coroutineScope {
            targets.forEach { index ->
                launch {
                    semaphore.withPermit {
                        val item = _state.value.items.getOrNull(index) ?: return@withPermit
                        val file = item.file ?: return@withPermit
                        // 读取失败（文件损坏/权限）时按「保持原状」处理，不要让整批任务中断
                        val review = runCatching { LocalMatchEngine.reviewCompleted(file) }.getOrNull()
                        when (review) {
                            LocalMatchEngine.CompletedReview.VERBATIM ->
                                setItem(index) {
                                    it.copy(status = LocalMatchEngine.Status.SKIPPED, detail = "已有逐字歌词")
                                }

                            LocalMatchEngine.CompletedReview.NEED_REMATCH -> {
                                synchronized(finished) {
                                    finished.remove(item.key)
                                    prefs.edit().putStringSet(prefsKey, HashSet(finished)).commit()
                                }
                                setItem(index) { it.copy(status = LocalMatchEngine.Status.PENDING, detail = "") }
                                _state.update { state -> state.copy(done = (state.done - 1).coerceAtLeast(0)) }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    fun stop(context: Context) {        job?.cancel()
        job = null
        _state.update { it.copy(running = false, message = "已停止") }
        stopServiceSafely(context.applicationContext)
    }

    fun clearProgress(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOCAL).remove(KEY_SONGLIST).apply()
    }

    private fun setItem(index: Int, transform: (Item) -> Item) {
        _state.update { current ->
            if (index !in current.items.indices) return@update current
            val list = current.items.toMutableList()
            list[index] = transform(list[index])
            current.copy(items = list)
        }
    }

    private fun MutableStateFlow<State>.updateAndGetCount(): Int {
        var count = 0
        update { current ->
            count = current.done + 1
            current.copy(done = count)
        }
        return count
    }

    private fun startServiceSafely(context: Context) {
        runCatching { BatchService.start(context) }
            .onFailure { _state.update { state -> state.copy(message = "前台服务启动失败：${it.message}") } }
    }

    /**
     * 停服务要等它真正进入前台之后再停。
     * 直接 stop 会踩到 startForegroundService 未履行契约的 5 秒计时器。
     */
    private fun stopServiceSafely(context: Context) {
        scope.launch {
            var waited = 0
            while (!BatchService.isForegroundStarted() && waited < 30) {
                kotlinx.coroutines.delay(50)
                waited++
            }
            runCatching { BatchService.stop(context) }
        }
    }
}
