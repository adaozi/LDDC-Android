// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lddc.android.data.model.Id3Version
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.SaveMode
import org.lddc.android.data.model.Source
import org.lddc.android.data.model.TranslateBackend
import org.lddc.android.data.settings.AppSettings
import org.lddc.android.data.settings.SettingsRepository

val TargetLanguages = listOf(
    "SIMPLIFIED_CHINESE" to "简中",
    "TRADITIONAL_CHINESE" to "繁中",
    "ENGLISH" to "英文",
    "JAPANESE" to "日文",
    "KOREAN" to "韩文",
)

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var showSpike by remember { mutableStateOf(false) }

    if (showSpike) {
        org.lddc.android.ui.debug.TagSpikeScreen(onBack = { showSpike = false })
        return
    }

    fun mutate(block: (AppSettings) -> AppSettings) {
        scope.launch { repository.update(block) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            SettingCard("歌词源与优先级") {
                Text(
                    "勾选参与搜索的源；越靠前优先级越高（用于自动匹配时的取值顺序）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    settings.sourcePriority.forEach { source ->
                        SourceRow(
                            source = source,
                            checked = source in settings.enabledSources,
                            onCheckedChange = { checked ->
                                mutate { s ->
                                    if (checked) s.copy(enabledSources = s.enabledSources + source)
                                    else s.copy(enabledSources = s.enabledSources - source)
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingCard("匹配") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("匹配阈值", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${settings.minScore}", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = settings.minScore.toFloat(),
                    onValueChange = { v -> mutate { it.copy(minScore = v.toInt()) } },
                    valueRange = 30f..95f,
                    steps = 12,
                )
                SwitchRow(
                    title = "跳过已有歌词的歌曲",
                    subtitle = "歌曲里已经存在歌词标签时不再处理",
                    checked = settings.skipExistingLyrics,
                    onCheckedChange = { v -> mutate { it.copy(skipExistingLyrics = v) } },
                )
                SwitchRow(
                    title = "跳过纯音乐",
                    subtitle = "匹配到的歌曲被标记为纯音乐时跳过",
                    checked = settings.skipInstrumental,
                    onCheckedChange = { v -> mutate { it.copy(skipInstrumental = v) } },
                )
                SwitchRow(
                    title = "并发数",
                    subtitle = "同时处理的歌曲数量：${settings.concurrency}（网速慢或接口限流时调小）",
                    checked = settings.concurrency > 4,
                    onCheckedChange = { v -> mutate { it.copy(concurrency = if (v) 8 else 4) } },
                )
            }
        }

        item {
            SettingCard("保存方式") {
                Text(
                    "默认写入歌曲文件；也可以选择同时另存一份歌词文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.saveMode == mode,
                            onClick = { mutate { s -> s.copy(saveMode = mode) } },
                            label = { Text(mode.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("歌词格式", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LyricsFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = settings.lyricsFormat == fmt,
                            onClick = { mutate { s -> s.copy(lyricsFormat = fmt) } },
                            label = { Text(fmt.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Id3Version.entries.forEach { v ->
                        FilterChip(
                            selected = settings.id3Version == v,
                            onClick = { mutate { s -> s.copy(id3Version = v) } },
                            label = { Text(v.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = "同时另存 .lrc 文件",
                    subtitle = "写入标签的同时，在歌曲目录生成同名 .lrc（播放器兼容性更好）",
                    checked = settings.alsoSaveLrcFile,
                    onCheckedChange = { v -> mutate { it.copy(alsoSaveLrcFile = v) } },
                )
            }
        }

        if (org.lddc.android.BuildConfig.DEBUG) {
            item {
                SettingCard("开发者") {
                    Text(
                        "标签写入自检：验证各格式能否写入 USLT/SYLT/LYRICS 并正确读回。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showSpike = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("打开标签写入自检")
                    }
                    Spacer(Modifier.height(8.dp))
                    val crashContext = androidx.compose.ui.platform.LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val text = org.lddc.android.util.CrashLogger.read(crashContext) ?: "暂无崩溃记录"
                            val clipboard = crashContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LDDC crash", text))
                            android.widget.Toast.makeText(crashContext, "已复制（${text.length} 字符）", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("复制崩溃日志")
                    }
                }
            }
        }

        item {
            SettingCard("保存路径") {
                Text(
                    "歌单/专辑下载只写 .lrc 文件，不会修改歌曲。支持占位符：%<artist> %<title> %<album> %<id> %<langs>",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.saveRootPath,
                    onValueChange = { v -> mutate { s -> s.copy(saveRootPath = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("保存目录") },
                    placeholder = { Text("留空则用 Music/LDDC") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.fileNameFormat,
                    onValueChange = { v -> mutate { s -> s.copy(fileNameFormat = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("文件名格式") },
                    placeholder = { Text("%<artist> - %<title>") },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "预览：" + org.lddc.android.domain.PathPlaceholder.apply(
                        settings.fileNameFormat.ifBlank { "%<artist> - %<title>" },
                        org.lddc.android.data.model.SongInfo(
                            source = org.lddc.android.data.model.Source.LOCAL,
                            title = "夜曲",
                            artist = listOf("周杰伦"),
                            album = "十一月的萧邦",
                        ),
                        listOf("orig"),
                    ) + ".lrc",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SettingCard("歌词语言组合") {
                Text(
                    "勾选需要的语言，输出顺序固定为：原文 → 译文 → 罗马音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                listOf("orig" to "原文", "ts" to "译文", "roma" to "罗马音").forEach { (key, label) ->
                    SwitchRow(
                        title = label,
                        subtitle = if (key == "orig") "必选，关闭后不输出歌词" else "需要源提供或翻译生成",
                        checked = key in settings.langsOrder,
                        onCheckedChange = { checked ->
                            mutate { s ->
                                val order = listOf("orig", "ts", "roma")
                                val enabled = s.langsOrder.toMutableSet()
                                if (checked) enabled.add(key) else enabled.remove(key)
                                enabled.add("orig")
                                s.copy(langsOrder = order.filter { it in enabled })
                            }
                        },
                    )
                }
            }
        }

        item {
            SettingCard("翻译") {
                Text(
                    "只有在歌词源没有自带译文时才需要。翻译结果作为「译文」写入，沿用原文时间轴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text("翻译后端", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TranslateBackend.entries.forEach { backend ->
                        FilterChip(
                            selected = settings.translateBackend == backend,
                            onClick = { mutate { s -> s.copy(translateBackend = backend) } },
                            label = { Text(backend.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("目标语言", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetLanguages.forEach { (code, label) ->
                        FilterChip(
                            selected = settings.translateTargetLang == code,
                            onClick = { mutate { s -> s.copy(translateTargetLang = code) } },
                            label = { Text(label) },
                        )
                    }
                }
                if (settings.translateBackend == TranslateBackend.OPENAI) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = settings.openAiBaseUrl,
                        onValueChange = { v -> mutate { s -> s.copy(openAiBaseUrl = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("OpenAI 兼容 base_url") },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.openAiApiKey,
                        onValueChange = { v -> mutate { s -> s.copy(openAiApiKey = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("API Key") },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.openAiModel,
                        onValueChange = { v -> mutate { s -> s.copy(openAiModel = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("模型名") },
                        placeholder = { Text("deepseek-chat") },
                    )
                }
            }
        }

        item {
            SettingCard("关于") {
                Text("LDDC Android 移植版 0.1.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "基于 chenmozhijin/LDDC（GPL-3.0-only）移植。本应用同样以 GPL-3.0 发布，源码可自由获取与修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = { }, label = { Text("GPL-3.0") })
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SourceRow(source: Source, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
