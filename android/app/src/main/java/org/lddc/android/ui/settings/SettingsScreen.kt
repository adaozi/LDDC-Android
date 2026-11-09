// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lddc.android.BuildConfig
import org.lddc.android.data.model.Id3Version
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.SaveMode
import org.lddc.android.data.model.Source
import org.lddc.android.data.model.TagLyricsContent
import org.lddc.android.data.model.TranslateBackend
import org.lddc.android.data.settings.AppSettings
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.ui.components.ChoiceChips
import org.lddc.android.ui.components.HairlineDivider
import org.lddc.android.ui.components.PathField
import org.lddc.android.ui.components.ScreenHeader
import org.lddc.android.ui.components.SectionCard
import org.lddc.android.ui.components.SettingSwitchRow
import org.lddc.android.ui.components.lddc
import org.lddc.android.ui.theme.Space

val TargetLanguages = listOf(
    "SIMPLIFIED_CHINESE" to "简中",
    "TRADITIONAL_CHINESE" to "繁中",
    "ENGLISH" to "英文",
    "JAPANESE" to "日文",
    "KOREAN" to "韩文",
)

private val ConcurrencyOptions = listOf(2, 4, 6, 8)

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showSpike by remember { mutableStateOf(false) }

    if (showSpike) {
        org.lddc.android.ui.debug.TagSpikeScreen(onBack = { showSpike = false })
        return
    }

    fun mutate(block: (AppSettings) -> AppSettings) {
        scope.launch { repository.update(block) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val dirPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            org.lddc.android.ui.util.StoragePath.fromTreeUri(context, it)?.let { path ->
                mutate { s -> s.copy(saveRootPath = path) }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item(key = "header") {
            ScreenHeader(title = "设置", subtitle = "改动即时生效，下次任务自动使用")
        }

        item(key = "sources") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "歌词源与优先级",
                icon = Icons.Rounded.LibraryMusic,
            ) {
                Text(
                    text = "勾选参与搜索的源，排在前面的优先取值。用箭头调整顺序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                settings.sourcePriority.forEachIndexed { index, source ->
                    SourceRow(
                        index = index,
                        lastIndex = settings.sourcePriority.lastIndex,
                        source = source,
                        checked = source in settings.enabledSources,
                        onCheckedChange = { checked ->
                            mutate { s ->
                                if (checked) s.copy(enabledSources = s.enabledSources + source)
                                else s.copy(enabledSources = s.enabledSources - source)
                            }
                        },
                        onMove = { delta ->
                            mutate { s ->
                                val list = s.sourcePriority.toMutableList()
                                val target = index + delta
                                if (target in list.indices) {
                                    list[index] = list[target]
                                    list[target] = source
                                }
                                s.copy(sourcePriority = list)
                            }
                        },
                    )
                }
            }
        }

        item(key = "matching") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "匹配",
                icon = Icons.Rounded.Tune,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("匹配阈值", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "分数低于此值视为未找到，调低更宽松",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    ) {
                        Text(
                            "${settings.minScore}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
                Slider(
                    value = settings.minScore.toFloat(),
                    onValueChange = { v -> mutate { it.copy(minScore = v.toInt()) } },
                    valueRange = 30f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                )
                SettingSwitchRow(
                    title = "跳过已有歌词的歌曲",
                    subtitle = "歌曲里已存在歌词标签时不再处理",
                    checked = settings.skipExistingLyrics,
                    onCheckedChange = { v -> mutate { it.copy(skipExistingLyrics = v) } },
                )
                SettingSwitchRow(
                    title = "逐字歌词才跳过",
                    subtitle = if (settings.skipExistingLyrics) {
                        "已有逐行/纯文本歌词时不跳过，重新匹配并覆盖"
                    } else {
                        "需先开启「跳过已有歌词的歌曲」"
                    },
                    checked = settings.refreshNonVerbatimLyrics,
                    enabled = settings.skipExistingLyrics,
                    onCheckedChange = { v -> mutate { it.copy(refreshNonVerbatimLyrics = v) } },
                )
                if (settings.skipExistingLyrics && settings.refreshNonVerbatimLyrics && !settings.lyricsFormat.isWordLevel) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = lddc.warning.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = Space.md, end = Space.sm, top = Space.sm, bottom = Space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "当前输出格式是「${settings.lyricsFormat.displayName}」，写进去仍会被判为非逐字，下次会重复匹配。",
                                style = MaterialTheme.typography.labelSmall,
                                color = lddc.warning,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { mutate { it.copy(lyricsFormat = LyricsFormat.VERBATIM_LRC) } }) {
                                Text("改为逐字", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                SettingSwitchRow(
                    title = "跳过纯音乐",
                    subtitle = "匹配结果被标记为纯音乐时跳过",
                    checked = settings.skipInstrumental,
                    onCheckedChange = { v -> mutate { it.copy(skipInstrumental = v) } },
                )
                Spacer(Modifier.height(Space.sm))
                Text("并发数", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "接口限流或网速慢时调小；酷狗/QQ 建议 4 以内",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                ChoiceChips(
                    items = ConcurrencyOptions,
                    label = { "$it 首" },
                    isSelected = { it == settings.concurrency },
                    onSelect = { v -> mutate { it.copy(concurrency = v) } },
                )
            }
        }

        item(key = "save") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "保存方式",
                icon = Icons.Rounded.Save,
            ) {
                ChoiceGroup("写入位置") {
                    ChoiceChips(
                        items = SaveMode.entries,
                        label = { it.displayName },
                        isSelected = { it == settings.saveMode },
                        onSelect = { mode -> mutate { it.copy(saveMode = mode) } },
                    )
                }
                ChoiceGroup("歌词格式") {
                    ChoiceChips(
                        items = LyricsFormat.entries,
                        label = { it.displayName },
                        isSelected = { it == settings.lyricsFormat },
                        onSelect = { fmt -> mutate { it.copy(lyricsFormat = fmt) } },
                    )
                }
                ChoiceGroup("ID3 版本") {
                    ChoiceChips(
                        items = Id3Version.entries,
                        label = { it.displayName },
                        isSelected = { it == settings.id3Version },
                        onSelect = { v -> mutate { it.copy(id3Version = v) } },
                    )
                }
                ChoiceGroup("写入标签的内容") {
                    ChoiceChips(
                        items = TagLyricsContent.entries,
                        label = { it.displayName },
                        isSelected = { it == settings.tagLyricsContent },
                        onSelect = { v -> mutate { it.copy(tagLyricsContent = v) } },
                    )
                }
                SettingSwitchRow(
                    title = "同时另存 .lrc 文件",
                    subtitle = "写入标签的同时在歌曲目录生成同名 .lrc，播放器兼容性更好",
                    checked = settings.alsoSaveLrcFile,
                    onCheckedChange = { v -> mutate { it.copy(alsoSaveLrcFile = v) } },
                )
            }
        }

        item(key = "path") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "保存路径",
                icon = Icons.Rounded.FolderOpen,
            ) {
                Text(
                    text = "歌单/专辑下载只写 .lrc，不改歌曲。可用占位符：%<artist> %<title> %<album> %<id> %<langs>",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                PathField(
                    label = "保存目录",
                    value = settings.saveRootPath,
                    onValueChange = { v -> mutate { s -> s.copy(saveRootPath = v) } },
                    onPick = { dirPicker.launch(null) },
                    placeholder = "留空则用 Music/LDDC",
                )
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = settings.fileNameFormat,
                    onValueChange = { v -> mutate { s -> s.copy(fileNameFormat = v) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("文件名格式", style = MaterialTheme.typography.labelMedium) },
                    placeholder = { Text("%<artist> - %<title>", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Space.sm))
                val preview = org.lddc.android.domain.PathPlaceholder.apply(
                    settings.fileNameFormat.ifBlank { "%<artist> - %<title>" },
                    org.lddc.android.data.model.SongInfo(
                        source = Source.LOCAL,
                        title = "夜曲",
                        artist = listOf("周杰伦"),
                        album = "十一月的萧邦",
                    ),
                    listOf("orig"),
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = lddc.surfaceSunken,
                    border = BorderStroke(1.dp, lddc.hairline),
                ) {
                    Text(
                        text = "预览：$preview.lrc",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        item(key = "langs") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "歌词语言组合",
                icon = Icons.Rounded.Translate,
            ) {
                Text(
                    text = "输出顺序固定为：原文 → 译文 → 罗马音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf("orig" to "原文", "ts" to "译文", "roma" to "罗马音").forEach { (key, label) ->
                    SettingSwitchRow(
                        title = label,
                        subtitle = if (key == "orig") "必选，关闭后不输出任何歌词" else "需要歌词源自带或手动翻译",
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

        item(key = "translate") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "翻译",
                icon = Icons.Rounded.Translate,
            ) {
                Text(
                    text = "仅在歌词源没有自带译文时需要，译文沿用原文时间轴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceGroup("翻译后端") {
                    ChoiceChips(
                        items = TranslateBackend.entries,
                        label = { it.displayName },
                        isSelected = { it == settings.translateBackend },
                        onSelect = { backend -> mutate { it.copy(translateBackend = backend) } },
                    )
                }
                if (settings.translateBackend == TranslateBackend.GOOGLE) {
                    Text(
                        text = "Google 翻译在国内网络通常不可达，建议使用 Bing 或 OpenAI 兼容接口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = lddc.warning,
                    )
                    Spacer(Modifier.height(Space.sm))
                }
                ChoiceGroup("目标语言") {
                    ChoiceChips(
                        items = TargetLanguages,
                        label = { it.second },
                        isSelected = { it.first == settings.translateTargetLang },
                        onSelect = { pair -> mutate { it.copy(translateTargetLang = pair.first) } },
                    )
                }
                if (settings.translateBackend == TranslateBackend.OPENAI) {
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = settings.openAiBaseUrl,
                        onValueChange = { v -> mutate { s -> s.copy(openAiBaseUrl = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("base_url", style = MaterialTheme.typography.labelMedium) },
                        placeholder = { Text("https://api.deepseek.com/v1", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = settings.openAiApiKey,
                        onValueChange = { v -> mutate { s -> s.copy(openAiApiKey = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("API Key", style = MaterialTheme.typography.labelMedium) },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = settings.openAiModel,
                        onValueChange = { v -> mutate { s -> s.copy(openAiModel = v) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("模型名", style = MaterialTheme.typography.labelMedium) },
                        placeholder = { Text("deepseek-chat", style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (BuildConfig.DEBUG) {
            item(key = "dev") {
                SectionCard(
                    modifier = Modifier.padding(horizontal = Space.lg),
                    title = "开发者",
                    icon = Icons.Rounded.BugReport,
                ) {
                    Text(
                        text = "标签写入自检：逐格式验证 USLT / SYLT / LYRICS 能否写入并正确读回。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.md))
                    Button(
                        onClick = { showSpike = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("打开标签写入自检") }
                    Spacer(Modifier.height(Space.sm))
                    val crashContext = androidx.compose.ui.platform.LocalContext.current
                    OutlinedButton(
                        onClick = {
                            val text = org.lddc.android.util.CrashLogger.read(crashContext) ?: "暂无崩溃记录"
                            val clipboard = crashContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("LDDC crash", text))
                            android.widget.Toast.makeText(
                                crashContext,
                                "已复制（${text.length} 字符）",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("复制崩溃日志") }
                }
            }
        }

        item(key = "about") {
            SectionCard(
                modifier = Modifier.padding(horizontal = Space.lg),
                title = "关于",
                icon = Icons.Rounded.Info,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LDDC Android", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "基于 chenmozhijin/LDDC（GPL-3.0-only）移植，本应用同样以 GPL-3.0 开源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "GPL-3.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    TextButton(onClick = { uriHandler.openUri("https://github.com/chenmozhijin/LDDC") }) {
                        Text("查看上游项目", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item(key = "bottom_space") { Spacer(Modifier.height(Space.xl)) }
    }
}

@Composable
private fun ChoiceGroup(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Space.md))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(Space.sm))
    content()
}

@Composable
private fun SourceRow(
    index: Int,
    lastIndex: Int,
    source: Source,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = source.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onMove(-1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = "上移",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onMove(1) }, enabled = index < lastIndex, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = "下移",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(2.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
