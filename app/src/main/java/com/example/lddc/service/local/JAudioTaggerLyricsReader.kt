package com.example.lddc.service.local

import android.util.Log
import com.example.lddc.utils.CharsetDetector
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.asf.AsfTag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

/**
 * 使用 JAudioTagger 库读取歌词
 *
 * 参考 PC 端 mutagen 的实现方式，支持多种音频格式的歌词读取
 */
class JAudioTaggerLyricsReader {

    companion object {
        private const val TAG = "JAudioTaggerLyrics"
    }

    /**
     * 从音频文件中读取歌词
     * 参考 PC 端 read_lyrics 函数的实现
     */
    fun readLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "文件不存在: $filePath")
                return null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag

            if (tag == null) {
                Log.w(TAG, "文件没有标签: $filePath")
                return null
            }

            // 根据标签类型调用相应的读取方法
            when (tag) {
                is AbstractID3v2Tag -> readID3Lyrics(tag)
                is VorbisCommentTag -> readVorbisCommentLyrics(tag)
                is FlacTag -> readFlacLyrics(tag)
                is Mp4Tag -> readMP4Lyrics(tag)
                is AsfTag -> readASFLyrics(tag)
                is WavTag -> readWavLyrics(tag)
                else -> readGenericLyrics(tag)
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取歌词失败: $filePath", e)
            null
        }
    }

    /**
     * 读取 ID3 标签中的歌词（MP3 文件）
     * 参考 PC 端：优先使用 USLT，其次 SYLT
     */
    private fun readID3Lyrics(tag: AbstractID3v2Tag): String? {
        // 1. 尝试标准 LYRICS 字段
        try {
            val lyrics = tag.getFirst(FieldKey.LYRICS)
            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "从 ID3 LYRICS 字段读取到歌词，长度: ${lyrics.length}")
                // 检测并修复编码问题
                return CharsetDetector.fixGarbledText(lyrics)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 LYRICS 字段失败", e)
        }

        // 2. 尝试 USLT (Unsynchronized Lyrics/Text Transcription) 帧
        try {
            val usltFrame = tag.getFirst("USLT")
            if (!usltFrame.isNullOrBlank()) {
                Log.d(TAG, "从 ID3 USLT 帧读取到歌词")
                return CharsetDetector.fixGarbledText(usltFrame)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 USLT 帧失败", e)
        }

        // 3. 尝试 COMMENT 字段（有些歌曲把歌词放在这里）
        try {
            val comment = tag.getFirst(FieldKey.COMMENT)
            if (!comment.isNullOrBlank() && comment.length > 50) {
                // 如果评论很长，可能是歌词
                Log.d(TAG, "从 ID3 COMMENT 读取到可能的歌词")
                return CharsetDetector.fixGarbledText(comment)
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 COMMENT 失败", e)
        }

        return null
    }

    /**
     * 读取 Vorbis Comment 标签中的歌词（FLAC, OGG, Opus）
     */
    private fun readVorbisCommentLyrics(tag: VorbisCommentTag): String? {
        // 尝试 LYRICS 字段
        var lyrics = tag.getFirst("LYRICS")
        if (!lyrics.isNullOrBlank()) {
            Log.d(TAG, "从 VorbisComment LYRICS 读取到歌词")
            return lyrics
        }

        // 尝试 DESCRIPTION 字段
        lyrics = tag.getFirst("DESCRIPTION")
        if (!lyrics.isNullOrBlank() && lyrics.length > 50) {
            Log.d(TAG, "从 VorbisComment DESCRIPTION 读取到可能的歌词")
            return lyrics
        }

        return null
    }

    /**
     * 读取 FLAC 标签中的歌词
     */
    private fun readFlacLyrics(tag: FlacTag): String? {
        // FLAC 使用 Vorbis Comment
        val vorbisTag = tag.vorbisCommentTag
        if (vorbisTag != null) {
            return readVorbisCommentLyrics(vorbisTag)
        }
        return null
    }

    /**
     * 读取 MP4/M4A 标签中的歌词
     * 参考 PC 端：使用 lyr 字段
     */
    private fun readMP4Lyrics(tag: Mp4Tag): String? {
        return try {
            // MP4 使用 LYRICS 字段存储歌词
            val lyrics = tag.getFirst(FieldKey.LYRICS)
            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "从 MP4 标签读取到歌词")
                return lyrics
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "读取 MP4 歌词失败", e)
            null
        }
    }

    /**
     * 读取 ASF/WMA 标签中的歌词
     * 参考 PC 端：使用 WM/LYRICS 或 Lyrics 字段
     */
    private fun readASFLyrics(tag: AsfTag): String? {
        // 1. 尝试 WM/LYRICS 字段
        var lyrics = tag.getFirst("WM/LYRICS")
        if (!lyrics.isNullOrBlank()) {
            Log.d(TAG, "从 ASF WM/LYRICS 读取到歌词")
            return lyrics
        }

        // 2. 尝试 Lyrics 字段（别名）
        lyrics = tag.getFirst("Lyrics")
        if (!lyrics.isNullOrBlank()) {
            Log.d(TAG, "从 ASF Lyrics 读取到歌词")
            return lyrics
        }

        // 3. 尝试 LYRICS 字段
        lyrics = tag.getFirst("LYRICS")
        if (!lyrics.isNullOrBlank()) {
            Log.d(TAG, "从 ASF LYRICS 读取到歌词")
            return lyrics
        }

        return null
    }

    /**
     * 读取 WAV 标签中的歌词
     */
    private fun readWavLyrics(tag: WavTag): String? {
        // WAV 可能使用 ID3 标签或 INFO chunk
        val id3Tag = tag.iD3Tag
        if (id3Tag != null) {
            val lyrics = readID3Lyrics(id3Tag)
            if (!lyrics.isNullOrBlank()) {
                return lyrics
            }
        }

        // 尝试 INFO chunk
        val infoTag = tag.infoTag
        if (infoTag != null) {
            // INFO chunk 通常不存储歌词，但可以尝试
            try {
                val lyrics = infoTag.getFirst(FieldKey.COMMENT)
                if (!lyrics.isNullOrBlank() && lyrics.length > 50) {
                    Log.d(TAG, "从 WAV INFO 读取到可能的歌词")
                    return lyrics
                }
            } catch (e: Exception) {
                // 忽略
            }
        }

        return null
    }

    /**
     * 通用歌词读取（备用方案）
     */
    private fun readGenericLyrics(tag: Tag): String? {
        // 尝试 LYRICS 字段
        return try {
            val lyrics = tag.getFirst(FieldKey.LYRICS)
            if (!lyrics.isNullOrBlank()) {
                Log.d(TAG, "从通用字段 LYRICS 读取到歌词")
                return lyrics
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 简单的编码检测
     */
    private fun detectEncoding(data: ByteArray): String {
        // 检查 UTF-8 BOM
        if (data.size >= 3 &&
            data[0] == 0xEF.toByte() &&
            data[1] == 0xBB.toByte() &&
            data[2] == 0xBF.toByte()
        ) {
            return "UTF-8 with BOM"
        }

        // 检查 UTF-16 LE BOM
        if (data.size >= 2 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xFE.toByte()
        ) {
            return "UTF-16 LE"
        }

        // 检查 UTF-16 BE BOM
        if (data.size >= 2 &&
            data[0] == 0xFE.toByte() &&
            data[1] == 0xFF.toByte()
        ) {
            return "UTF-16 BE"
        }

        // 尝试检测是否为有效的 UTF-8
        return if (isValidUTF8(data)) {
            "UTF-8"
        } else {
            "可能需要编码转换"
        }
    }

    /**
     * 检查是否为有效的 UTF-8
     */
    private fun isValidUTF8(data: ByteArray): Boolean {
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC0..0xDF -> {
                    if (i + 1 >= data.size) return false
                    if ((data[i + 1].toInt() and 0xC0) != 0x80) return false
                    i += 2
                }

                b in 0xE0..0xEF -> {
                    if (i + 2 >= data.size) return false
                    if ((data[i + 1].toInt() and 0xC0) != 0x80) return false
                    if ((data[i + 2].toInt() and 0xC0) != 0x80) return false
                    i += 3
                }

                b in 0xF0..0xF7 -> {
                    if (i + 3 >= data.size) return false
                    if ((data[i + 1].toInt() and 0xC0) != 0x80) return false
                    if ((data[i + 2].toInt() and 0xC0) != 0x80) return false
                    if ((data[i + 3].toInt() and 0xC0) != 0x80) return false
                    i += 4
                }

                else -> return false
            }
        }
        return true
    }
}
