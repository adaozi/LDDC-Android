package com.example.lddc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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

/**
 * 详情页面公共组件
 */

/**
 * 歌曲信息卡片（左侧）
 */
@Composable
fun SongInfoCard(
    title: String,
    artist: String,
    album: String,
    imageUrl: String?,
    duration: String,
    tags: @Composable () -> Unit,
    onCopyTitle: (() -> Unit)? = null,
    onCopyArtist: (() -> Unit)? = null,
    onCopyAlbum: (() -> Unit)? = null,
    onSaveImage: (() -> Unit)? = null,
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 专辑封面
            AlbumCoverWithLongPress(
                imageUrl = imageUrl,
                onLongPress = onSaveImage,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 歌曲标题
            CopyableText(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                onLongPress = onCopyTitle
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 歌手
            CopyableText(
                text = artist,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onLongPress = onCopyArtist
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 专辑名
            CopyableText(
                text = album,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                onLongPress = onCopyAlbum
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 信息标签
            tags()
        }
    }
}

/**
 * 歌词显示卡片（右侧）
 */
@Composable
fun LyricsCard(
    lyrics: String,
    onCopyLyrics: () -> Unit,
    actionButton: @Composable (() -> Unit)? = null,
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
                    actionButton?.invoke()

                    Button(
                        onClick = onCopyLyrics,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("复制歌词")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 歌词内容
            androidx.compose.foundation.text.BasicTextField(
                value = lyrics,
                onValueChange = { },
                readOnly = true,
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
 * 支持长按保存的专辑封面
 */
@Composable
private fun AlbumCoverWithLongPress(
    imageUrl: String?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() }
                )
            }
    ) {
        if (!imageUrl.isNullOrEmpty() && imageUrl != "default") {
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
 * 支持长按复制的文本
 */
@Composable
private fun CopyableText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onLongPress: (() -> Unit)? = null
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() }
            )
        }
    )
}

/**
 * 竖屏布局的歌曲详情
 */
@Composable
fun PortraitDetailLayout(
    title: String,
    artist: String,
    album: String,
    imageUrl: String?,
    duration: String,
    lyrics: String,
    platform: String,
    lyricsType: String,
    onCopyTitle: () -> Unit,
    onCopyArtist: () -> Unit,
    onCopyAlbum: () -> Unit,
    onCopyLyrics: () -> Unit,
    onSaveImage: (() -> Unit)? = null,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 歌曲信息卡片
        SongInfoCard(
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            duration = duration,
            tags = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(text = duration)
                    InfoChip(text = platform)
                    InfoChip(text = lyricsType)
                }
            },
            onCopyTitle = onCopyTitle,
            onCopyArtist = onCopyArtist,
            onCopyAlbum = onCopyAlbum,
            onSaveImage = onSaveImage,
            modifier = Modifier.fillMaxWidth()
        )

        // 歌词卡片
        LyricsCard(
            lyrics = lyrics,
            onCopyLyrics = onCopyLyrics,
            actionButton = actionButton,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
    }
}

/**
 * 横屏布局的歌曲详情
 */
@Composable
fun LandscapeDetailLayout(
    title: String,
    artist: String,
    album: String,
    imageUrl: String?,
    duration: String,
    lyrics: String,
    platform: String,
    lyricsType: String,
    onCopyTitle: () -> Unit,
    onCopyArtist: () -> Unit,
    onCopyAlbum: () -> Unit,
    onCopyLyrics: () -> Unit,
    onSaveImage: (() -> Unit)? = null,
    actionButton: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 左侧：歌曲信息
        SongInfoCard(
            title = title,
            artist = artist,
            album = album,
            imageUrl = imageUrl,
            duration = duration,
            tags = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(text = duration)
                    InfoChip(text = platform)
                    InfoChip(text = lyricsType)
                }
            },
            onCopyTitle = onCopyTitle,
            onCopyArtist = onCopyArtist,
            onCopyAlbum = onCopyAlbum,
            onSaveImage = onSaveImage,
            modifier = Modifier.weight(0.4f)
        )

        // 右侧：歌词
        LyricsCard(
            lyrics = lyrics,
            onCopyLyrics = onCopyLyrics,
            actionButton = actionButton,
            modifier = Modifier.weight(0.6f)
        )
    }
}
