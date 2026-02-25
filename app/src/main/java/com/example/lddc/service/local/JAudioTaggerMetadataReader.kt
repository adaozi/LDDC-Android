package com.example.lddc.service.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.lddc.model.AudioMetadata
import com.example.lddc.utils.CharsetDetector
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 使用 JAudioTagger 库读取音频文件元数据
 *
 * JAudioTagger 支持多种音频格式：
 * - MP3 (ID3v1, ID3v2.2, ID3v2.3, ID3v2.4)
 * - FLAC (Vorbis Comment)
 * - OGG (Vorbis Comment)
 * - MP4/M4A (MP4 tags)
 * - WMA/ASF (ASF tags)
 * - WAV (ID3 and INFO chunks)
 * - APE (APEv2 tags)
 * - WavPack
 * - DSF
 * - AIFF
 *
 * 参考 PC 端 mutagen 的实现方式
 */
class JAudioTaggerMetadataReader {

    companion object {
        private const val TAG = "JAudioTaggerReader"
    }

    /**
     * 读取音频文件的完整元数据
     */
    fun readMetadata(filePath: String): AudioMetadata? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "文件不存在: $filePath")
                return null
            }

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            if (tag == null) {
                Log.w(TAG, "文件没有标签: $filePath")
                // 即使没有标签，也返回基本信息
                return createBasicMetadata(filePath, header)
            }

            createAudioMetadata(filePath, tag, header)
        } catch (e: Exception) {
            Log.e(TAG, "读取元数据失败: $filePath", e)
            null
        }
    }

    /**
     * 读取专辑封面
     * 支持多种音频格式，包括 WAV 文件的特殊处理
     */
    fun readAlbumArt(filePath: String): Bitmap? {
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

            // 获取 artwork 列表
            val artworkList = tag.artworkList
            Log.d(TAG, "找到 ${artworkList.size} 个 artwork")

            // 获取第一个 artwork
            val artwork = artworkList.firstOrNull()
            if (artwork != null) {
                val imageData = artwork.binaryData
                Log.d(
                    TAG,
                    "读取到 artwork，大小: ${imageData.size} bytes, 类型: ${artwork.mimeType}"
                )

                // 尝试解码图片
                val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(imageData))
                if (bitmap != null) {
                    Log.d(TAG, "成功解码图片: ${bitmap.width}x${bitmap.height}")
                    return bitmap
                } else {
                    Log.w(TAG, "BitmapFactory 解码失败，尝试其他方式")
                    // 尝试直接解码字节数组
                    return BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                }
            }

            // 对于 WAV 文件，尝试从 ID3 标签中读取
            val ext = file.extension.lowercase()
            if (ext == "wav") {
                Log.d(TAG, "WAV 文件，尝试特殊处理")
                return readWavAlbumArt(audioFile)
            }

            Log.w(TAG, "没有找到 artwork")
            null
        } catch (e: Exception) {
            Log.e(TAG, "读取专辑封面失败: $filePath", e)
            null
        }
    }

    /**
     * 专门处理 WAV 文件的专辑封面
     * WAV 文件可能使用 ID3 标签或 INFO chunk 存储图片
     */
    private fun readWavAlbumArt(audioFile: AudioFile): Bitmap? {
        return try {
            val tag = audioFile.tag

            // 方法1: 尝试从所有字段中查找图片数据
            // 有些 WAV 文件将图片存储在自定义字段中
            val fieldNames = listOf("APIC", "PIC", "COVER", "COVERART", "METADATA_BLOCK_PICTURE")

            for (fieldName in fieldNames) {
                try {
                    val field = tag.getFirst(fieldName)
                    if (field.isNotBlank()) {
                        Log.d(TAG, "找到可能的图片字段: $fieldName")
                        // 这里可能需要特殊处理，因为字段可能包含二进制数据
                    }
                } catch (_: Exception) {
                    // 忽略
                }
            }

            // 方法2: 尝试直接访问 tag 的 artworkList（已经在上面的方法中尝试过）

            // 方法3: 检查是否有额外的 artwork
            if (tag.artworkList.size > 1) {
                for ((index, art) in tag.artworkList.withIndex()) {
                    val bitmap =
                        BitmapFactory.decodeByteArray(art.binaryData, 0, art.binaryData.size)
                    if (bitmap != null) {
                        Log.d(TAG, "从 artworkList[$index] 成功解码图片")
                        return bitmap
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "读取 WAV 专辑封面失败", e)
            null
        }
    }

    /**
     * 读取内嵌歌词
     * 参考 PC 端 mutagen 的实现
     */
    fun readEmbeddedLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: return null

            // 尝试不同的歌词字段
            // 1. 标准 LYRICS 字段
            var lyrics = tag.getFirst(FieldKey.LYRICS)
            if (!lyrics.isNullOrBlank()) {
                return CharsetDetector.fixGarbledText(lyrics)
            }

            // 2. 尝试 USLT 帧 (ID3v2.3/2.4)
            lyrics = tag.getFirst("USLT")
            if (!lyrics.isNullOrBlank()) {
                return CharsetDetector.fixGarbledText(lyrics)
            }

            // 3. 尝试自定义字段
            lyrics = tag.getFirst("LYRICS")
            if (!lyrics.isNullOrBlank()) {
                return CharsetDetector.fixGarbledText(lyrics)
            }

            // 4. 对于 WMA/ASF 文件，尝试 WM/LYRICS
            lyrics = tag.getFirst("WM/LYRICS")
            if (!lyrics.isNullOrBlank()) {
                return CharsetDetector.fixGarbledText(lyrics)
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "读取内嵌歌词失败: $filePath", e)
            null
        }
    }

    /**
     * 检查文件是否有内嵌歌词
     */
    fun hasEmbeddedLyrics(filePath: String): Boolean {
        return !readEmbeddedLyrics(filePath).isNullOrBlank()
    }

    /**
     * 创建完整的 AudioMetadata 对象
     */
    private fun createAudioMetadata(
        filePath: String,
        tag: Tag,
        header: AudioHeader
    ): AudioMetadata {
        val file = File(filePath)

        // 读取原始字段值并修复编码
        val rawTitle = tag.getFirst(FieldKey.TITLE)
        val rawArtist = tag.getFirst(FieldKey.ARTIST)
        val rawAlbum = tag.getFirst(FieldKey.ALBUM)
        val rawYear = tag.getFirst(FieldKey.YEAR)
        val rawGenre = tag.getFirst(FieldKey.GENRE)
        val rawComment = tag.getFirst(FieldKey.COMMENT)
        val rawAlbumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST)
        val rawComposer = tag.getFirst(FieldKey.COMPOSER)

        // 修复编码问题
        val title = CharsetDetector.fixGarbledText(rawTitle).takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val artist =
            CharsetDetector.fixGarbledText(rawArtist).takeIf { it.isNotBlank() } ?: "未知艺术家"
        val album =
            CharsetDetector.fixGarbledText(rawAlbum).takeIf { it.isNotBlank() } ?: "未知专辑"
        val year = CharsetDetector.fixGarbledText(rawYear).takeIf { it.isNotBlank() }
        val genre = CharsetDetector.fixGarbledText(rawGenre).takeIf { it.isNotBlank() }
        val comment = CharsetDetector.fixGarbledText(rawComment).takeIf { it.isNotBlank() }
        val albumArtist = CharsetDetector.fixGarbledText(rawAlbumArtist).takeIf { it.isNotBlank() }
        val composer = CharsetDetector.fixGarbledText(rawComposer).takeIf { it.isNotBlank() }

        return AudioMetadata(
            id = filePath.hashCode().toString(),
            title = title,
            artist = artist,
            album = album,
            duration = (header.trackLength * 1000).toLong(), // 转换为毫秒
            path = filePath,
            fileName = file.name,
            fileSize = file.length(),
            hasLyrics = hasEmbeddedLyrics(filePath),
            // 扩展字段
            year = year,
            trackNumber = tag.getFirst(FieldKey.TRACK).takeIf { it.isNotBlank() }?.toIntOrNull(),
            genre = genre,
            comment = comment,
            albumArtist = albumArtist,
            composer = composer,
            discNumber = tag.getFirst(FieldKey.DISC_NO).takeIf { it.isNotBlank() }?.toIntOrNull(),
            lyrics = readEmbeddedLyrics(filePath),
            bitrate = header.bitRate?.toIntOrNull(),
            sampleRate = header.sampleRate?.toIntOrNull(),
            channels = header.channels
        )
    }

    /**
     * 创建基本的 AudioMetadata（无标签时）
     */
    private fun createBasicMetadata(filePath: String, header: AudioHeader): AudioMetadata {
        val file = File(filePath)

        return AudioMetadata(
            id = filePath.hashCode().toString(),
            title = file.nameWithoutExtension,
            artist = "未知艺术家",
            album = "未知专辑",
            duration = (header.trackLength * 1000).toLong(),
            path = filePath,
            fileName = file.name,
            fileSize = file.length(),
            hasLyrics = false,
            bitrate = header.bitRate?.toIntOrNull(),
            sampleRate = header.sampleRate?.toIntOrNull(),
            channels = header.channels
        )
    }

}

