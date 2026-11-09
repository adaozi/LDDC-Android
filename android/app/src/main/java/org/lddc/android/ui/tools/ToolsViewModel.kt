// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.tools

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
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.domain.LyricsFileConverter
import org.lddc.android.domain.SongListDownloader
import java.io.File

data class ToolsUiState(
    val sourceDir: String = SongListDownloader.defaultRoot().absolutePath,
    val outDir: String = "",
    val format: LyricsFormat = LyricsFormat.SRT,
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val files: List<Pair<String, Boolean>> = emptyList(),
)

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

    fun onSourceChange(value: String) = _state.update { it.copy(sourceDir = value) }
    fun onOutChange(value: String) = _state.update { it.copy(outDir = value) }
    fun onFormatChange(format: LyricsFormat) = _state.update { it.copy(format = format) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun start() {
        val current = _state.value
        if (current.running) return
        val source = File(current.sourceDir.trim())
        if (!source.isDirectory) {
            _state.update { it.copy(message = "源目录不存在") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(running = true, done = 0, total = 0, files = emptyList(), message = null) }
            val files = withContext(Dispatchers.IO) { LyricsFileConverter.scanLrc(source) }
            if (files.isEmpty()) {
                _state.update { it.copy(running = false, message = "该目录下没有 .lrc 文件") }
                return@launch
            }

            val outDir = current.outDir.trim().takeIf { it.isNotEmpty() }?.let(::File)
                ?: File(source, "converted_${current.format.name.lowercase()}")

            _state.update { it.copy(total = files.size) }
            var ok = 0
            val results = ArrayList<Pair<String, Boolean>>()
            withContext(Dispatchers.IO) {
                files.forEachIndexed { index, file ->
                    val success = LyricsFileConverter.convert(file, current.format, outDir)
                    if (success) ok++
                    results.add(file.name to success)
                    _state.update { it.copy(done = index + 1, files = results.toList()) }
                }
            }
            _state.update {
                it.copy(
                    running = false,
                    message = "完成：成功 $ok / ${files.size}，输出到 ${outDir.absolutePath}",
                )
            }
        }
    }

    fun resetOutDir() = _state.update { it.copy(outDir = "") }

    companion object {
        fun defaultSource(): String = File(Environment.getExternalStorageDirectory(), "Music/LDDC").absolutePath
    }
}
