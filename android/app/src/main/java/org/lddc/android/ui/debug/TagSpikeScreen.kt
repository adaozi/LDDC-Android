// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.debug

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lddc.android.data.tag.AudioTagRepository
import org.lddc.android.data.tag.SyltPayload
import java.io.File

/**
 * M1 打样页：验证「能不能把歌词写进各种格式的歌曲文件」。
 * 流程：选择音频 → 复制到应用缓存（保留扩展名）→ 写入 USLT/SYLT 或 LYRICS → 读回比对。
 */
@Composable
fun TagSpikeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("尚未开始。\n选择一首歌后会写入测试歌词并读回比对。") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        log = "处理中…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runSpike(context, uri) }
            log = result
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("标签写入自检", style = MaterialTheme.typography.headlineSmall)
        Text(
            "选取任意音频文件，应用会把它复制到缓存目录后写入测试歌词，再读回校验。不会改动你原始文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = !busy,
            onClick = { picker.launch(arrayOf("audio/*", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "处理中…" else "选择音频文件")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回设置") }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = log,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun runSpike(context: Context, uri: Uri): String {
    val out = StringBuilder()
    return try {
        val name = queryDisplayName(context, uri) ?: "unknown.bin"
        val dir = File(context.cacheDir, "tagspike").apply { mkdirs() }
        val target = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return "无法读取所选文件"

        out.appendLine("文件：${target.name}")
        out.appendLine("大小：${target.length() / 1024} KB")
        out.appendLine("TagLib：${AudioTagRepository.nativeVersion()}")
        out.appendLine("标签支持：${AudioTagRepository.isSupported(target)}")
        out.appendLine("--- 原始信息 ---")
        out.appendLine(AudioTagRepository.readInfo(target)?.toString() ?: "读取失败")

        val testText = buildString {
            appendLine("[00:00.00]LDDC 标签写入测试 第一行")
            appendLine("[00:01.00]第二行 LRC 文本")
            appendLine("[00:02.00]第三行")
        }
        val sylt = SyltPayload(
            timesMs = intArrayOf(0, 1000, 2000),
            words = listOf("LDDC ", "标签写入", "测试"),
        )

        out.appendLine("--- 写入 ---")
        val result = AudioTagRepository.writeLyrics(target, testText, sylt, id3Version = 3)
        out.appendLine("写入结果：$result")

        out.appendLine("--- 读回 ---")
        val readBack = AudioTagRepository.readLyrics(target)
        out.appendLine("读取到歌词：${readBack?.length ?: 0} 字符")
        out.appendLine("内容匹配：${readBack?.replace("\r\n", "\n") == testText.replace("\r\n", "\n")}")
        out.appendLine(readBack?.take(200) ?: "(空)")
        out.appendLine("hasLyrics：${AudioTagRepository.hasLyrics(target)}")
        out.appendLine("缓存副本：${target.absolutePath}")
        out.toString()
    } catch (t: Throwable) {
        out.appendLine("异常：${t.javaClass.simpleName}: ${t.message}")
        out.toString()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
