package com.example.lddc.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.Music

/**
 * 公共UI组件库
 * 提取网络搜索和本地音乐共用的组件
 */

/**
 * 歌曲卡片组件
 * 用于搜索结果列表和本地音乐列表
 *
 * @param title 歌曲标题
 * @param artist 艺术家
 * @param album 专辑
 * @param imageUrl 封面图片URL
 * @param extraInfo 额外信息（如时长、平台等）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun MusicCard(
    title: String,
    artist: String,
    album: String,
    imageUrl: String?,
    extraInfo: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            AlbumCover(
                imageUrl = imageUrl,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = album,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 额外信息（如时长、平台标签）
                extraInfo?.invoke()
            }
        }
    }
}

/**
 * 网络搜索结果用的歌曲卡片
 */
@Composable
fun NetworkMusicCard(
    music: Music,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MusicCard(
        title = music.title,
        artist = music.artist,
        album = music.album,
        imageUrl = music.imageUrl.takeIf { it.isNotEmpty() && it != "default" },
        extraInfo = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                InfoChip(text = music.duration)
                InfoChip(text = music.platform)
            }
        },
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * 本地音乐用的歌曲卡片
 */
@Composable
fun LocalMusicCard(
    music: LocalMusicInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 使用专辑封面 URI 或 null
    val albumArtUri = music.albumArtUri?.toString()
    
    MusicCard(
        title = music.title,
        artist = music.artist,
        album = music.album,
        imageUrl = albumArtUri, // 使用本地专辑封面
        extraInfo = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                InfoChip(text = formatDuration(music.duration))
                if (music.hasLyrics) {
                    InfoChip(
                        text = "已有歌词",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * 专辑封面组件
 * 支持网络图片URL和本地Content URI
 */
@Composable
fun AlbumCover(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrEmpty() && imageUrl != "default") {
            // 判断是否为本地Content URI
            val imageModel = if (imageUrl.startsWith("content://")) {
                // 本地Content URI
                Uri.parse(imageUrl)
            } else {
                // 网络URL
                imageUrl
            }
            
            AsyncImage(
                model = imageModel,
                contentDescription = "专辑封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = android.R.drawable.ic_media_play),
                error = painterResource(id = android.R.drawable.ic_media_play)
            )
        } else {
            // 默认音乐图标
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 信息标签组件
 */
@Composable
fun InfoChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = contentColor,
        modifier = Modifier
            .background(
                color = containerColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 加载更多指示器
 */
@Composable
fun LoadMoreIndicator(
    isLoading: Boolean,
    hasMoreData: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(enabled = !isLoading && hasMoreData, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            hasMoreData -> {
                Text(
                    text = "点击加载更多",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            else -> {
                Text(
                    text = "没有更多数据了",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 空状态组件
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 加载状态组件
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
