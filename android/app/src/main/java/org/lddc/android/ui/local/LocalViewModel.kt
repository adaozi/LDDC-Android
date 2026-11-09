// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.local

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lddc.android.data.remote.LyricsRepository
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.domain.BatchController
import org.lddc.android.domain.LocalMatchEngine
import java.io.File

data class LocalUiState(
    val rootPath: String = File(Environment.getExternalStorageDirectory(), "Music").absolutePath,
    val items: List<BatchController.Item> = emptyList(),
    val scanning: Boolean = false,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
)

class LocalViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val _state = MutableStateFlow(LocalUiState())
    val state: StateFlow<LocalUiState> = _state.asStateFlow()

    init {
        // 批量任务由 BatchController 持有，页面退出/切后台仍继续
        viewModelScope.launch {
            BatchController.state.collect { batch ->
                if (batch.items.isEmpty()) return@collect
                _state.update {
                    it.copy(
                        items = batch.items,
                        done = batch.done,
                        total = batch.total,
                        running = batch.running,
                        message = batch.message ?: it.message,
                    )
                }
            }
        }
    }

    fun onRootChange(value: String) = _state.update { it.copy(rootPath = value) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun scan() {
        val root = _state.value.rootPath.trim()
        if (root.isEmpty()) return
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val dir = File(root)
            if (!dir.isDirectory) {
                _state.update { it.copy(scanning = false, message = "目录不存在：$root") }
                return@launch
            }
            val files = withContext(Dispatchers.IO) { LocalMatchEngine.scan(dir) }
            _state.update {
                it.copy(
                    scanning = false,
                    items = files.map { file -> BatchController.Item(key = file.absolutePath, label = file.name, file = file) },
                    done = 0,
                    total = files.size,
                    message = if (files.isEmpty()) "该目录下没有找到支持的音频文件" else null,
                )
            }
        }
    }

    fun start() {
        val items = _state.value.items
        if (items.isEmpty() || BatchController.isRunning()) return

        viewModelScope.launch {
            val settings = settingsRepository.current()
            val sources = settings.sourcePriority.filter {
                it in settings.enabledSources &&
                    LyricsRepository.isAvailable(it) &&
                    !LyricsRepository.isCircuitOpen(it)
            }
            if (sources.isEmpty()) {
                _state.update { it.copy(message = "没有可用的歌词源，请到设置里勾选") }
                return@launch
            }
            BatchController.startFiles(
                context = getApplication(),
                files = items.mapNotNull { it.file },
                settings = settings,
                sources = sources,
            )
        }
    }

    fun stop() = BatchController.stop(getApplication())

    /** 清空断点，下次全量重跑 */
    fun resetProgress() {
        BatchController.clearProgress(getApplication())
        _state.update {
            it.copy(
                items = it.items.map { item -> item.copy(status = LocalMatchEngine.Status.PENDING, detail = "") },
                done = 0,
                message = "已清空断点记录",
            )
        }
    }
}
