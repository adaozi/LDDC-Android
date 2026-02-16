package com.example.lddc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lddc.service.PlatformService
import kotlinx.coroutines.launch

/**
 * 歌曲详情页通用组件库
 */

/**
 * 专辑封面组件（支持长按保存）
 *
 * @param imageUrl 图片URL
 * @param title 歌曲标题（用于保存文件名）
 * @param artist 艺术家（用于保存文件名）
 * @param platformService 平台服务
 * @param modifier 修饰符
 */
@Composable
fun AlbumCoverWithSave(
    imageUrl: String,
    title: String,
    artist: String,
    platformService: PlatformService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (imageUrl.isNotEmpty() && imageUrl != "default") {
                            scope.launch {
                                val success = platformService.saveImageFromUrl(
                                    imageUrl,
                                    "${title}_${artist}"
                                )
                                if (success) {
                                    platformService.showToast("图片已保存到相册")
                                } else {
                                    platformService.showToast("保存图片失败")
                                }
                            }
                        }
                    }
                )
            }
    ) {
        if (imageUrl.isNotEmpty() && imageUrl != "default") {
            AsyncImage(
                model = imageUrl,
                contentDescription = "歌曲封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = android.R.drawable.ic_media_play),
                contentDescription = "歌曲封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(48.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * 可复制的文本组件
 *
 * @param text 文本内容
 * @param fontSize 字体大小
 * @param fontWeight 字重
 * @param color 颜色
 * @param platformService 平台服务
 * @param label 复制标签
 * @param modifier 修饰符
 */
@Composable
fun CopyableText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    platformService: PlatformService,
    label: String = "文本",
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    platformService.copyToClipboard(label, text)
                    platformService.showToast("$label 已复制")
                }
            )
        }
    )
}

/**
 * 信息标签组件
 *
 * @param text 标签文本
 * @param isAlbum 是否为专辑标签（使用不同颜色）
 */
@Composable
fun InfoChip(
    text: String,
    isAlbum: Boolean = false,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isAlbum) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                } else {
                    containerColor.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isAlbum) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                contentColor
            }
        )
    }
}

/**
 * 歌词显示卡片
 *
 * @param lyrics 歌词内容
 * @param platformService 平台服务
 * @param modifier 修饰符
 */
@Composable
fun LyricsCard(
    lyrics: String,
    platformService: PlatformService,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = {
                        platformService.copyToClipboard("歌词", lyrics)
                        platformService.showToast("歌词已复制")
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("复制歌词")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 歌词内容
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                BasicTextField(
                    value = lyrics,
                    onValueChange = { },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    readOnly = true,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 可编辑歌词卡片
 *
 * @param lyrics 歌词内容
 * @param onLyricsChange 歌词变化回调
 * @param platformService 平台服务
 * @param onWriteLyrics 写入歌词回调
 * @param modifier 修饰符
 */
@Composable
fun EditableLyricsCard(
    lyrics: String,
    onLyricsChange: (String) -> Unit,
    platformService: PlatformService,
    onWriteLyrics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 写入歌词按钮（如果有回调）
                    if (onWriteLyrics != null && lyrics.isNotBlank() && !lyrics.startsWith("暂无歌词")) {
                        Button(
                            onClick = onWriteLyrics,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("写入歌词")
                        }
                    }

                    // 复制歌词按钮
                    Button(
                        onClick = {
                            platformService.copyToClipboard("歌词", lyrics)
                            platformService.showToast("歌词已复制")
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("复制")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 歌词内容（可编辑）
            BasicTextField(
                value = lyrics,
                onValueChange = onLyricsChange,
                readOnly = false,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * 歌曲信息卡片
 *
 * @param title 歌曲标题
 * @param artist 艺术家
 * @param album 专辑
 * @param imageUrl 封面图片URL
 * @param platformService 平台服务
 * @param chips 信息标签列表
 * @param modifier 修饰符
 */
@Composable
fun SongInfoCard(
    title: String,
    artist: String,
    album: String,
    imageUrl: String,
    platformService: PlatformService,
    chips: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 专辑封面
            AlbumCoverWithSave(
                imageUrl = imageUrl,
                title = title,
                artist = artist,
                platformService = platformService,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 歌曲标题
            CopyableText(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                platformService = platformService,
                label = "歌曲名"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 歌手
            CopyableText(
                text = artist,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                platformService = platformService,
                label = "歌手"
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 专辑名
            CopyableText(
                text = album,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                platformService = platformService,
                label = "专辑"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 信息标签
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips()
            }
        }
    }
}
