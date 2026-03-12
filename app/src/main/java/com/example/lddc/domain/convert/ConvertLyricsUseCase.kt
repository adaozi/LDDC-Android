package com.example.lddc.domain.convert

import com.example.lddc.common.models.enums.LyricsFormat
import com.example.lddc.common.models.lyrics.Lyrics

class ConvertLyricsUseCase {

    operator fun invoke(
        lyrics: Lyrics,
        targetFormat: LyricsFormat,
        languages: List<String> = listOf("orig")
    ): String {
        return lyrics.toFormat(targetFormat, languages)
    }

    fun getAvailableFormats(): List<LyricsFormat> {
        return listOf(
            LyricsFormat.VERBATIMLRC,
            LyricsFormat.LINEBYLINELRC,
            LyricsFormat.ENHANCEDLRC,
            LyricsFormat.SRT,
            LyricsFormat.ASS
        )
    }

    fun getFormatDescription(format: LyricsFormat): String {
        return when (format) {
            LyricsFormat.VERBATIMLRC -> "逐字LRC格式，每字都有时间戳"
            LyricsFormat.LINEBYLINELRC -> "逐行LRC格式，每行一个时间戳"
            LyricsFormat.ENHANCEDLRC -> "增强LRC格式，支持逐字显示"
            LyricsFormat.SRT -> "SRT字幕格式，适合视频使用"
            LyricsFormat.ASS -> "ASS字幕格式，支持高级样式"
            else -> "未知格式"
        }
    }
}
