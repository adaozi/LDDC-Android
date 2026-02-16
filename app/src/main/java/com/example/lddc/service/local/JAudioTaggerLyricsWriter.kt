package com.example.lddc.service.local

import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.LyricsWriteResult
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.asf.AsfTag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

/**
 * 使用 JAudioTagger 库写入歌词
 *
 * 参考 PC 端 mutagen 的实现方式，支持多种音频格式的歌词写入
 */
class JAudioTaggerLyricsWriter {

    companion object {
        private const val TAG = "JAudioTaggerLyricsWriter"

        init {
            // 设置 JAudioTagger 选项
            val tagOptions = TagOptionSingleton.getInstance()
            tagOptions.isAndroid = true
            tagOptions.isWriteMp3GenresAsText = true
            // 设置 ID3 标签使用 UTF-8 编码
            tagOptions.id3v23DefaultTextEncoding = 1 // UTF-8
            tagOptions.id3v24DefaultTextEncoding = 1 // UTF-8
        }
    }

    /**
     * 写入歌词到音频文件
     * 参考 PC 端 write_lyrics 函数的实现
     */
    fun writeLyrics(
        filePath: String,
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ): LyricsWriteResult {
        return when (mode) {
            LyricsWriteMode.EMBEDDED -> writeEmbeddedLyrics(filePath, lyrics)
            LyricsWriteMode.SEPARATE_FILE -> writeExternalLyricsFile(filePath, lyrics)
            LyricsWriteMode.BOTH -> {
                val embeddedResult = writeEmbeddedLyrics(filePath, lyrics)
                val externalResult = writeExternalLyricsFile(filePath, lyrics)

                LyricsWriteResult(
                    success = embeddedResult.success || externalResult.success,
                    embeddedSuccess = embeddedResult.success,
                    separateFileSuccess = externalResult.success,
                    lyricsPath = externalResult.lyricsPath,
                    errorMessage = when {
                        !embeddedResult.success && !externalResult.success -> "内嵌和外部歌词都写入失败"
                        !embeddedResult.success -> "内嵌歌词写入失败"
                        !externalResult.success -> "外部歌词文件写入失败"
                        else -> null
                    }
                )
            }
            else -> writeEmbeddedLyrics(filePath, lyrics)
        }
    }

    /**
     * 写入内嵌歌词
     */
    private fun writeEmbeddedLyrics(filePath: String, lyrics: String): LyricsWriteResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return LyricsWriteResult(
                    success = false,
                    errorMessage = "文件不存在: $filePath"
                )
            }

            // 检查文件是否可写
            if (!canWriteFile(file)) {
                return LyricsWriteResult(
                    success = false,
                    errorMessage = "没有权限写入文件。Android 11+ 需要特殊权限才能修改外部存储中的音频文件。"
                )
            }

            val audioFile = AudioFileIO.read(file)
            var tag = audioFile.tag

            // 如果没有标签，创建新标签
            if (tag == null) {
                tag = createTagForFile(audioFile)
                audioFile.tag = tag
            }

            // 根据标签类型写入歌词
            val success = when (tag) {
                is AbstractID3v2Tag -> writeID3Lyrics(tag, lyrics)
                is VorbisCommentTag -> writeVorbisCommentLyrics(tag, lyrics)
                is FlacTag -> writeFlacLyrics(tag, lyrics)
                is Mp4Tag -> writeMP4Lyrics(tag, lyrics)
                is AsfTag -> writeASFLyrics(tag, lyrics)
                is WavTag -> writeWavLyrics(tag, lyrics)
                else -> writeGenericLyrics(tag, lyrics)
            }

            if (success) {
                // 保存文件
                AudioFileIO.write(audioFile)
                Log.d(TAG, "成功写入歌词到: $filePath")
                LyricsWriteResult(
                    success = true,
                    embeddedSuccess = true
                )
            } else {
                LyricsWriteResult(
                    success = false,
                    errorMessage = "写入歌词失败"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入歌词失败: $filePath", e)
            val errorMsg = when {
                e.message?.contains("Permission denied") == true ->
                    "没有写入权限。请确保应用有存储权限，或在 Android 11+ 上授予管理所有文件的权限。"
                e.message?.contains("Read-only") == true ->
                    "文件是只读的。请检查文件权限。"
                else -> "写入失败: ${e.message}"
            }
            LyricsWriteResult(
                success = false,
                errorMessage = errorMsg
            )
        }
    }

    /**
     * 检查文件是否可写
     * Android 11+ 对外部存储文件写入有限制
     */
    private fun canWriteFile(file: File): Boolean {
        // 检查文件是否可写
        if (!file.canWrite()) {
            Log.w(TAG, "文件不可写: ${file.absolutePath}")
            return false
        }

        // Android 11+ 检查
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 检查是否是外部存储中的文件
            val externalStorage = Environment.getExternalStorageDirectory()
            if (file.absolutePath.startsWith(externalStorage.absolutePath)) {
                // 在 Android 11+ 上，普通应用无法直接修改外部存储中的媒体文件
                // 除非有 MANAGE_EXTERNAL_STORAGE 权限
                if (!Environment.isExternalStorageManager()) {
                    Log.w(TAG, "Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限才能修改外部存储文件")
                    // 仍然返回 true，让写入操作尝试，可能会失败
                    // 用户需要在系统设置中授予权限
                }
            }
        }

        return true
    }

    /**
     * 为文件创建合适的标签
     */
    private fun createTagForFile(audioFile: AudioFile): Tag {
        val ext = audioFile.file.extension.lowercase()
        return when (ext) {
            "mp3" -> ID3v24Tag()
            "flac" -> FlacTag()
            "ogg", "opus" -> VorbisCommentTag()
            "m4a", "mp4" -> Mp4Tag()
            "wma", "asf" -> AsfTag()
            "wav" -> ID3v24Tag() // WAV 使用 ID3 标签
            else -> ID3v24Tag() // 默认使用 ID3v2.4
        }
    }

    /**
     * 写入 ID3 标签歌词（MP3 文件）
     * 参考 PC 端：使用 USLT 帧
     */
    private fun writeID3Lyrics(tag: AbstractID3v2Tag, lyrics: String): Boolean {
        return try {
            // 使用 LYRICS 字段
            tag.setField(FieldKey.LYRICS, lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 ID3 歌词失败", e)
            false
        }
    }

    /**
     * 写入 Vorbis Comment 歌词（FLAC, OGG, Opus）
     */
    private fun writeVorbisCommentLyrics(tag: VorbisCommentTag, lyrics: String): Boolean {
        return try {
            tag.setField("LYRICS", lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 VorbisComment 歌词失败", e)
            false
        }
    }

    /**
     * 写入 FLAC 标签歌词
     */
    private fun writeFlacLyrics(tag: FlacTag, lyrics: String): Boolean {
        return try {
            val vorbisTag = tag.vorbisCommentTag
            if (vorbisTag != null) {
                vorbisTag.setField("LYRICS", lyrics)
            } else {
                tag.setField(FieldKey.LYRICS, lyrics)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 FLAC 歌词失败", e)
            false
        }
    }

    /**
     * 写入 MP4/M4A 标签歌词
     * 参考 PC 端：使用 ��lyr 字段
     */
    private fun writeMP4Lyrics(tag: Mp4Tag, lyrics: String): Boolean {
        return try {
            tag.setField(FieldKey.LYRICS, lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 MP4 歌词失败", e)
            false
        }
    }

    /**
     * 写入 ASF/WMA 标签歌词
     * 参考 PC 端：使用 WM/LYRICS 和 Lyrics 字段
     */
    private fun writeASFLyrics(tag: AsfTag, lyrics: String): Boolean {
        return try {
            // ASF 标签使用不同的 API
            tag.setField(FieldKey.LYRICS, lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 ASF 歌词失败", e)
            false
        }
    }

    /**
     * 写入 WAV 标签歌词
     */
    private fun writeWavLyrics(tag: WavTag, lyrics: String): Boolean {
        return try {
            // WAV 使用 ID3 标签
            var id3Tag = tag.iD3Tag
            if (id3Tag == null) {
                id3Tag = ID3v24Tag()
                tag.setID3Tag(id3Tag)
            }
            id3Tag.setField(FieldKey.LYRICS, lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入 WAV 歌词失败", e)
            false
        }
    }

    /**
     * 通用歌词写入（备用方案）
     */
    private fun writeGenericLyrics(tag: Tag, lyrics: String): Boolean {
        return try {
            tag.setField(FieldKey.LYRICS, lyrics)
            true
        } catch (e: Exception) {
            Log.e(TAG, "通用歌词写入失败", e)
            false
        }
    }

    /**
     * 写入外部歌词文件
     */
    private fun writeExternalLyricsFile(filePath: String, lyrics: String): LyricsWriteResult {
        return try {
            val file = File(filePath)
            val lyricsFile = File(file.parent, "${file.nameWithoutExtension}.lrc")

            // 使用 UTF-8 编码写入
            lyricsFile.writeText(lyrics, Charsets.UTF_8)

            Log.d(TAG, "成功写入外部歌词文件: ${lyricsFile.absolutePath}")
            LyricsWriteResult(
                success = true,
                separateFileSuccess = true,
                lyricsPath = lyricsFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "写入外部歌词文件失败", e)
            LyricsWriteResult(
                success = false,
                errorMessage = "写入外部歌词文件失败: ${e.message}"
            )
        }
    }

    /**
     * 删除歌词
     */
    fun deleteLyrics(filePath: String): LyricsWriteResult {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return LyricsWriteResult(
                    success = false,
                    errorMessage = "文件不存在: $filePath"
                )
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag

            if (tag == null) {
                return LyricsWriteResult(
                    success = true,
                    errorMessage = null
                )
            }

            // 删除歌词字段
            try {
                tag.deleteField(FieldKey.LYRICS)
            } catch (e: Exception) {
                // 某些标签可能不支持这些字段
            }

            // 保存文件
            AudioFileIO.write(audioFile)

            // 同时删除外部歌词文件
            val lyricsFile = File(file.parent, "${file.nameWithoutExtension}.lrc")
            if (lyricsFile.exists()) {
                lyricsFile.delete()
            }

            LyricsWriteResult(
                success = true,
                embeddedSuccess = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "删除歌词失败: $filePath", e)
            LyricsWriteResult(
                success = false,
                errorMessage = "删除失败: ${e.message}"
            )
        }
    }

    /**
     * 检查文件是否支持写入歌词
     */
    fun isSupported(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false

            AudioFileIO.read(file)
            // 只要能读取，就支持写入
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取文件支持的歌词字段
     */
    fun getSupportedFields(filePath: String): List<String> {
        return try {
            val file = File(filePath)
            if (!file.exists()) return emptyList()

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return emptyList()

            val fields = mutableListOf<String>()

            // 检查支持的字段
            when (tag) {
                is AbstractID3v2Tag -> {
                    fields.add("USLT (非同步歌词)")
                    fields.add("LYRICS")
                }
                is VorbisCommentTag -> {
                    fields.add("LYRICS")
                }
                is Mp4Tag -> {
                    fields.add("LYRICS")
                }
                is AsfTag -> {
                    fields.add("WM/LYRICS")
                    fields.add("Lyrics")
                }
                else -> {
                    fields.add("LYRICS")
                }
            }

            fields
        } catch (e: Exception) {
            emptyList()
        }
    }
}
