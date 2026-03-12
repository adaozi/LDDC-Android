package com.example.lddc.presentation.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lddc.common.models.enums.Source
import com.example.lddc.common.models.info.SongInfo
import java.io.File

/**
 * 信息标签组件
 */
@Composable
private fun InfoChip(
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
 * 获取文件后缀或平台名称
 * 本地音乐显示文件后缀，网络音乐显示平台简称
 */
private fun getFileExtensionOrSource(song: SongInfo): String {
    return when (song.source) {
        Source.LOCAL -> {
            // 从文件路径获取后缀
            val path = song.path
            if (!path.isNullOrBlank()) {
                val file = File(path)
                file.extension.uppercase().takeIf { it.isNotBlank() } ?: "本地"
            } else {
                "本地"
            }
        }

        Source.QM -> "QQ"
        Source.NE -> "网易"
        Source.KG -> "酷狗"
        else -> song.source.name
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
