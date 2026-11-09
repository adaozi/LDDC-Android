// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lddc.android.data.tag.AudioTagRepository
import org.lddc.android.data.tag.SyltPayload
import org.lddc.android.data.tag.WriteResult
import java.io.File

/**
 * 单文件保存：把歌词写进用户选中的歌曲文件。
 *
 * SAF 给的是 content:// URI，无法直接交给 TagLib（它要真实路径），所以：
 *   复制到应用缓存（保留扩展名）→ 写标签 → 覆盖写回原 URI。
 */
object LyricsSaver {

    sealed interface Result {
        data class Success(val wroteTag: Boolean, val unsupportedFormat: Boolean) : Result
        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    suspend fun save(
        context: Context,
        uri: Uri,
        displayName: String,
        text: String,
        sylt: SyltPayload?,
        id3Version: Int,
    ): Result = withContext(Dispatchers.IO) {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return@withContext Result.Failure("无法识别文件扩展名")
        if (extension !in AudioTagRepository.SUPPORTED_EXTENSIONS) {
            return@withContext Result.Failure("该格式暂不支持写入标签（.$extension）")
        }

        val workDir = File(context.cacheDir, "tagwrite").apply { mkdirs() }
        val workFile = File(workDir, "target.$extension")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                workFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.Failure("无法读取所选文件")

            val result = AudioTagRepository.writeLyrics(workFile, text, sylt, id3Version)
            when (result) {
                WriteResult.OK -> Unit
                WriteResult.UNSUPPORTED_FORMAT -> return@withContext Result.Failure("该音频格式不受支持")
                WriteResult.IO_ERROR -> return@withContext Result.Failure("写入标签失败（文件可能只读）")
                WriteResult.BAD_ARGUMENT -> return@withContext Result.Failure("歌词内容为空")
            }

            // 覆盖写回原文件
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                workFile.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext Result.Failure("无法写回原文件（没有写入权限）")

            Result.Success(wroteTag = true, unsupportedFormat = false)
        } catch (t: Throwable) {
            Result.Failure(t.message ?: t.javaClass.simpleName, t)
        } finally {
            runCatching { workFile.delete() }
        }
    }

    /** 把歌词另存为同名 .lrc（放在应用外部目录，需用户后续迁移；暂用于「仅保存为文件」） */
    fun buildSyltPayload(fsLines: List<org.lddc.android.data.model.FsLyricsLine>): SyltPayload? {
        if (fsLines.isEmpty()) return null
        val times = ArrayList<Int>()
        val words = ArrayList<String>()
        fsLines.forEachIndexed { lineIndex, line ->
            line.words.forEachIndexed { wordIndex, word ->
                if (word.text.isEmpty()) return@forEachIndexed
                val text = if (lineIndex != 0 && wordIndex == 0) "\n${word.text}" else word.text
                times.add(word.start)
                words.add(text)
            }
        }
        if (times.isEmpty()) return null
        return SyltPayload(times.toIntArray(), words)
    }
}
