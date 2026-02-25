package com.example.lddc.service.local

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.LyricsWriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * 本地歌词写入器
 *
 * 负责将歌词写入本地音乐文件
 */
class LocalLyricsWriter(private val context: Context) {

    companion object {
        private const val TAG = "LocalLyricsWriter"

        // ID3v2.4 标签头部标识
        private val ID3V2_HEADER = byteArrayOf(0x49, 0x44, 0x33) // "ID3"
    }

    /**
     * 写入歌词到音乐文件
     *
     * @param filePath 音乐文件路径
     * @param lyrics 歌词内容
     * @param mode 写入模式
     * @return 写入结果
     */
    suspend fun writeLyrics(
        filePath: String,
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.AUTO
    ): LyricsWriteResult = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext LyricsWriteResult(
                success = false,
                errorMessage = "文件不存在: $filePath"
            )
        }

        val extension = file.extension.lowercase()

        when (mode) {
            LyricsWriteMode.EMBEDDED -> {
                writeEmbeddedLyrics(filePath, lyrics, extension)
            }

            LyricsWriteMode.SEPARATE_FILE -> {
                writeSeparateLyricsFile(filePath, lyrics)
            }

            LyricsWriteMode.BOTH -> {
                val embeddedResult = writeEmbeddedLyrics(filePath, lyrics, extension)
                val separateResult = writeSeparateLyricsFile(filePath, lyrics)

                LyricsWriteResult(
                    success = embeddedResult.success || separateResult.success,
                    embeddedSuccess = embeddedResult.embeddedSuccess,
                    separateFileSuccess = separateResult.separateFileSuccess,
                    lyricsPath = separateResult.lyricsPath,
                    errorMessage = if (!embeddedResult.success && !separateResult.success) {
                        "嵌入歌词和独立文件都失败"
                    } else null
                )
            }

            LyricsWriteMode.AUTO -> {
                // 先尝试写入内嵌标签
                val embeddedResult = writeEmbeddedLyrics(filePath, lyrics, extension)
                if (embeddedResult.success) {
                    embeddedResult
                } else {
                    // 失败则保存为独立文件
                    writeSeparateLyricsFile(filePath, lyrics)
                }
            }
        }
    }

    /**
     * 写入内嵌歌词
     */
    private suspend fun writeEmbeddedLyrics(
        filePath: String,
        lyrics: String,
        extension: String
    ): LyricsWriteResult = withContext(Dispatchers.IO) {
        try {
            when (extension) {
                "mp3" -> writeMp3Lyrics(filePath, lyrics)
                "flac" -> writeFlacLyrics(filePath, lyrics)
                "m4a", "mp4" -> writeM4aLyrics(filePath, lyrics)
                "ogg", "opus" -> writeOggLyrics(filePath, lyrics)
                "wma" -> writeWmaLyrics(filePath, lyrics)
                else -> LyricsWriteResult(
                    success = false,
                    errorMessage = "不支持的文件格式: $extension"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入内嵌歌词失败: $filePath", e)
            LyricsWriteResult(
                success = false,
                errorMessage = "写入内嵌歌词失败: ${e.message}"
            )
        }
    }

    /**
     * 写入 MP3 歌词 (ID3v2.4 USLT 标签)
     */
    private fun writeMp3Lyrics(filePath: String, lyrics: String): LyricsWriteResult {
        return try {
            // 使用 jaudiotagger 库写入 MP3 标签
            // 注意：这里需要添加 jaudiotagger 依赖
            // 由于依赖库可能未添加，这里提供基本框架

            // 简单的 ID3v2.4 歌词标签写入实现
            val file = File(filePath)
            val tempFile = File(filePath + ".tmp")

            // 读取原始文件
            val originalData = file.readBytes()

            // 创建 USLT 帧 (Unsynchronized Lyrics/Text Transcription)
            val usltFrame = createUSLTFrame(lyrics)

            // 写入新文件
            tempFile.outputStream().use { output ->
                // 检查是否已有 ID3 标签
                if (hasID3v2Tag(originalData)) {
                    // 替换或添加 USLT 帧
                    val newData = replaceOrAddUSLTFrame(originalData, usltFrame)
                    output.write(newData)
                } else {
                    // 创建新的 ID3v2.4 标签
                    val id3Tag = createID3v24Tag(usltFrame)
                    output.write(id3Tag)
                    output.write(originalData)
                }
            }

            // 替换原文件
            if (file.delete()) {
                tempFile.renameTo(file)
                // 通知媒体库更新
                scanFile(filePath)

                LyricsWriteResult(
                    success = true,
                    embeddedSuccess = true
                )
            } else {
                tempFile.delete()
                LyricsWriteResult(
                    success = false,
                    errorMessage = "无法替换原文件"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入 MP3 歌词失败", e)
            LyricsWriteResult(
                success = false,
                errorMessage = "写入 MP3 歌词失败: ${e.message}"
            )
        }
    }

    /**
     * 写入 FLAC 歌词 (Vorbis Comment)
     */
    private fun writeFlacLyrics(filePath: String, lyrics: String): LyricsWriteResult {
        // FLAC 使用 Vorbis Comment
        // 需要专门的库或手动解析 FLAC 文件结构
        return LyricsWriteResult(
            success = false,
            errorMessage = "FLAC 歌词写入暂未实现"
        )
    }

    /**
     * 写入 M4A 歌词 (iTunes 风格)
     */
    private fun writeM4aLyrics(filePath: String, lyrics: String): LyricsWriteResult {
        // M4A/MP4 使用 iTunes 风格的元数据
        return LyricsWriteResult(
            success = false,
            errorMessage = "M4A 歌词写入暂未实现"
        )
    }

    /**
     * 写入 OGG 歌词 (Vorbis Comment)
     */
    private fun writeOggLyrics(filePath: String, lyrics: String): LyricsWriteResult {
        return LyricsWriteResult(
            success = false,
            errorMessage = "OGG 歌词写入暂未实现"
        )
    }

    /**
     * 写入 WMA 歌词
     */
    private fun writeWmaLyrics(filePath: String, lyrics: String): LyricsWriteResult {
        return LyricsWriteResult(
            success = false,
            errorMessage = "WMA 歌词写入暂未实现"
        )
    }

    /**
     * 保存为独立歌词文件
     */
    private suspend fun writeSeparateLyricsFile(
        filePath: String,
        lyrics: String
    ): LyricsWriteResult = withContext(Dispatchers.IO) {
        try {
            val musicFile = File(filePath)
            val parentDir = musicFile.parentFile
            val baseName = musicFile.nameWithoutExtension
            val lyricsFile = File(parentDir, "$baseName.lrc")

            lyricsFile.writeText(lyrics, Charsets.UTF_8)

            // 通知媒体库更新
            scanFile(lyricsFile.absolutePath)

            LyricsWriteResult(
                success = true,
                separateFileSuccess = true,
                lyricsPath = lyricsFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "保存歌词文件失败", e)
            LyricsWriteResult(
                success = false,
                errorMessage = "保存歌词文件失败: ${e.message}"
            )
        }
    }

    /**
     * 创建 USLT 帧
     */
    private fun createUSLTFrame(lyrics: String): ByteArray {
        // USLT 帧结构:
        // Frame ID: "USLT" (4 bytes)
        // Size: 4 bytes (big-endian)
        // Flags: 2 bytes
        // Encoding: 1 byte (0x03 for UTF-8)
        // Language: 3 bytes (e.g., "eng")
        // Content Descriptor: null terminated string
        // Lyrics text: the actual lyrics

        val encoding = 0x03 // UTF-8
        val language = "eng".toByteArray(Charsets.ISO_8859_1)
        val descriptor = byteArrayOf(0x00) // Empty descriptor, null terminated
        val lyricsBytes = lyrics.toByteArray(Charsets.UTF_8)

        val frameContent = ByteArray(1 + 3 + descriptor.size + lyricsBytes.size)
        var pos = 0

        frameContent[pos++] = encoding.toByte()
        System.arraycopy(language, 0, frameContent, pos, 3)
        pos += 3
        System.arraycopy(descriptor, 0, frameContent, pos, descriptor.size)
        pos += descriptor.size
        System.arraycopy(lyricsBytes, 0, frameContent, pos, lyricsBytes.size)

        val frameId = "USLT".toByteArray(Charsets.ISO_8859_1)
        val size = frameContent.size
        val flags = byteArrayOf(0x00, 0x00)

        val frame = ByteArray(4 + 4 + 2 + frameContent.size)
        pos = 0

        System.arraycopy(frameId, 0, frame, pos, 4)
        pos += 4

        // Size (big-endian)
        frame[pos++] = ((size shr 24) and 0xFF).toByte()
        frame[pos++] = ((size shr 16) and 0xFF).toByte()
        frame[pos++] = ((size shr 8) and 0xFF).toByte()
        frame[pos++] = (size and 0xFF).toByte()

        System.arraycopy(flags, 0, frame, pos, 2)
        pos += 2
        System.arraycopy(frameContent, 0, frame, pos, frameContent.size)

        return frame
    }

    /**
     * 检查是否有 ID3v2 标签
     */
    private fun hasID3v2Tag(data: ByteArray): Boolean {
        return data.size >= 3 &&
                data[0] == ID3V2_HEADER[0] &&
                data[1] == ID3V2_HEADER[1] &&
                data[2] == ID3V2_HEADER[2]
    }

    /**
     * 替换或添加 USLT 帧
     */
    private fun replaceOrAddUSLTFrame(originalData: ByteArray, newUSLTFrame: ByteArray): ByteArray {
        // 这是一个简化的实现
        // 实际实现需要完整解析 ID3v2 标签结构
        // 这里仅作为示例

        // 读取 ID3v2 标签大小
        val tagSize = readID3v2TagSize(originalData)

        // 提取音频数据
        val audioData = originalData.copyOfRange(tagSize, originalData.size)

        // 创建新的 ID3v2 标签（包含新的 USLT 帧）
        val newTag = createID3v24Tag(newUSLTFrame)

        // 合并新标签和音频数据
        return newTag + audioData
    }

    /**
     * 读取 ID3v2 标签大小
     */
    private fun readID3v2TagSize(data: ByteArray): Int {
        if (data.size < 10) return 0

        // ID3v2 标签大小存储在第 6-9 字节，使用同步安全整数
        val size = ((data[6].toInt() and 0x7F) shl 21) or
                ((data[7].toInt() and 0x7F) shl 14) or
                ((data[8].toInt() and 0x7F) shl 7) or
                (data[9].toInt() and 0x7F)

        return size + 10 // +10 是标签头部大小
    }

    /**
     * 创建 ID3v2.4 标签
     */
    private fun createID3v24Tag(usltFrame: ByteArray): ByteArray {
        val header = ByteArray(10)

        // 标识符 "ID3"
        header[0] = 0x49 // 'I'
        header[1] = 0x44 // 'D'
        header[2] = 0x33 // '3'

        // 版本 2.4.0
        header[3] = 0x04
        header[4] = 0x00

        // 标志字节
        header[5] = 0x00

        // 标签大小（同步安全整数，不包含头部）
        val size = usltFrame.size
        header[6] = ((size shr 21) and 0x7F).toByte()
        header[7] = ((size shr 14) and 0x7F).toByte()
        header[8] = ((size shr 7) and 0x7F).toByte()
        header[9] = (size and 0x7F).toByte()

        return header + usltFrame
    }

    /**
     * 通知媒体库扫描文件
     */
    private fun scanFile(filePath: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            null,
            null
        )
    }

    /**
     * 读取本地歌词
     * 优先读取独立歌词文件，如果没有则尝试读取内嵌歌词
     * 支持多种编码格式自动检测
     *
     * @param filePath 音乐文件路径
     * @param lyricsPath 独立歌词文件路径（如果有）
     * @return 歌词内容，如果没有则返回 null
     */
    suspend fun readLocalLyrics(
        filePath: String,
        lyricsPath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. 优先读取独立歌词文件
            if (!lyricsPath.isNullOrEmpty()) {
                val lyricsFile = File(lyricsPath)
                if (lyricsFile.exists()) {
                    return@withContext readFileWithEncodingDetection(lyricsFile)
                }
            }

            // 2. 尝试查找同目录下的 .lrc 文件
            val musicFile = File(filePath)
            if (musicFile.exists()) {
                val parentDir = musicFile.parentFile
                val fileNameWithoutExt = musicFile.nameWithoutExtension

                // 查找同名 .lrc 文件
                val lrcFile = File(parentDir, "$fileNameWithoutExt.lrc")
                if (lrcFile.exists()) {
                    return@withContext readFileWithEncodingDetection(lrcFile)
                }

                // 3. 尝试读取内嵌歌词
                val fileExtension = filePath.substringAfterLast(".", "").lowercase()
                when (fileExtension) {
                    "mp3" -> {
                        val lyrics = readMP3EmbeddedLyrics(filePath)
                        if (!lyrics.isNullOrEmpty()) return@withContext lyrics
                    }
                    // 支持所有与 PC 端 LDDC 相同的格式
                    "flac", "wav", "ogg", "oga", "ape", "opus", "tta", "mpc", "mp+",
                    "wma", "asf", "aiff", "aif", "wv", "m4a", "m4b", "mp4", "aac",
                    "3g2", "dff", "dsf", "mid", "ofr", "ofs", "spx", "tak" -> {
                        // 使用 AudioMetadataReader 读取各种格式的歌词
                        val metadata = AudioMetadataReader.readExtendedMetadata(filePath)
                        if (!metadata.lyrics.isNullOrEmpty()) return@withContext metadata.lyrics
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "读取本地歌词失败", e)
            null
        }
    }

    /**
     * 自动检测文件编码并读取
     * 参考 PC 端 charset_normalizer 的实现思路：
     * 1. 优先尝试系统编码（中文系统通常是 GB18030/GBK/GB2312）
     * 2. 检测替换字符 � (U+FFFD) 作为乱码指标
     * 3. 使用评分机制选择最佳编码
     */
    private fun readFileWithEncodingDetection(file: File): String? {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null

        // 按优先级排序的编码列表
        // 中文系统优先尝试 GB18030（兼容 GBK 和 GB2312）
        val charsetsToTry = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),  // 优先尝试 GB18030，兼容 GBK 和 GB2312
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charsets.UTF_16LE,  // Windows 常用
            Charsets.UTF_16BE,
            Charsets.UTF_16,
            Charsets.ISO_8859_1
        )

        var bestResult: String? = null
        var bestScore = -1

        for (charset in charsetsToTry) {
            try {
                val content = String(bytes, charset)
                val score = evaluateEncodingQuality(content, charset.name())

                if (score > bestScore) {
                    bestScore = score
                    bestResult = content
                }

                // 如果得分完美，直接返回
                if (score >= 100) {
                    Log.d(TAG, "使用编码 ${charset.name()} 完美读取文件: ${file.name}")
                    return content
                }
            } catch (e: Exception) {
                // 继续尝试下一个编码
            }
        }

        // 返回最佳结果
        if (bestResult != null && bestScore > 50) {
            Log.d(TAG, "使用最佳编码读取文件: ${file.name}, 得分: $bestScore")
            return bestResult
        }

        // 如果都失败了，使用 UTF-8 并替换无法解码的字符
        Log.w(TAG, "无法自动检测编码，使用 UTF-8 读取: ${file.name}")
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * 评估编码质量
     * 参考 PC 端实现，返回 0-100 的分数
     */
    private fun evaluateEncodingQuality(content: String, charsetName: String): Int {
        if (content.isEmpty()) return 0

        var score = 100

        // 1. 检查替换字符（U+FFFD）- 严重扣分
        val replacementCount = content.count { it == '\uFFFD' }
        if (replacementCount > 0) {
            score -= replacementCount * 20
        }

        // 2. 检查 GB18030 特定乱码模式
        if (charsetName.contains("GB", ignoreCase = true)) {
            // 检查 "锘縍EM" 模式（UTF-8 BOM 被错误解码为 GBK）
            if (content.contains("锘縍EM")) {
                score -= 50
            }
        }

        // 3. 检查控制字符（适度扣分）
        val controlCount = content.count { it.code in 0..31 && it !in "\n\r\t" }
        score -= controlCount * 2

        // 4. 检查空字符（适度扣分）
        val nullCount = content.count { it == '\u0000' }
        score -= nullCount * 2

        // 5. 检查常见乱码字符（严重扣分）
        val garbledChars = setOf('�', '锟', '斤', '拷', '浣', '犲', '紶', '柇', '戦', '浜')
        val garbledCount = content.count { it in garbledChars }
        score -= garbledCount * 10

        // 6. 奖励可打印字符比例高的
        val printableRatio = content.count {
            it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:'\"()-[]{}[]【】" ||
                    it.code in 0x4E00..0x9FFF || // CJK 统一汉字
                    it.code in 0x3000..0x303F || // CJK 标点符号
                    it.code in 0xFF00..0xFFEF    // 全角字符
        }.toFloat() / content.length
        score += (printableRatio * 30).toInt()

        // 7. 奖励包含中文字符的（对于中文歌词）
        val chineseCharCount = content.count { it.code in 0x4E00..0x9FFF }
        if (chineseCharCount > 0) {
            score += 20
        }

        // 8. 检查时间戳格式（如果是 LRC 歌词）
        if (content.contains("[") && content.contains("]")) {
            val timestampPattern = "\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]".toRegex()
            val timestampCount = timestampPattern.findAll(content).count()
            if (timestampCount > 0) {
                score += 10  // 奖励包含时间戳的
            }
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 读取 MP3 文件中的内嵌歌词（USLT 标签）
     */
    private fun readMP3EmbeddedLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val data = file.readBytes()
            if (!hasID3v2Tag(data)) return null

            // 读取 ID3v2 标签大小
            val tagSize = readID3v2TagSize(data)
            if (tagSize <= 10) return null

            // 提取标签数据
            val tagData = data.copyOfRange(10, tagSize)

            // 查找 USLT 帧
            val usltFrame = findUSLTFrame(tagData)
            usltFrame
        } catch (e: Exception) {
            Log.e(TAG, "读取内嵌歌词失败", e)
            null
        }
    }

    /**
     * 在 ID3v2 标签数据中查找 USLT 帧
     */
    private fun findUSLTFrame(tagData: ByteArray): String? {
        var pos = 0
        while (pos < tagData.size - 10) {
            // 读取帧 ID
            val frameId = String(tagData, pos, 4, Charsets.ISO_8859_1)

            if (frameId == "USLT") {
                // 读取帧大小（大端序）
                val frameSize = ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                        ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                        ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                        (tagData[pos + 7].toInt() and 0xFF)

                // 跳过标志字节
                val frameContent = tagData.copyOfRange(pos + 10, pos + 10 + frameSize)

                // 解析 USLT 帧内容
                return parseUSLTFrame(frameContent)
            }

            // 跳过其他帧
            if (frameId[0] == 0.toChar()) break // 到达填充区域

            val frameSize = ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                    ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                    ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                    (tagData[pos + 7].toInt() and 0xFF)

            pos += 10 + frameSize
        }
        return null
    }

    /**
     * 解析 USLT 帧内容
     */
    private fun parseUSLTFrame(frameContent: ByteArray): String? {
        return try {
            if (frameContent.isEmpty()) return null

            val encoding = frameContent[0].toInt()

            // 跳过编码字节和语言代码（3字节）
            var pos = 4

            // 跳过内容描述符（null 终止）
            while (pos < frameContent.size && frameContent[pos] != 0.toByte()) {
                pos++
            }
            pos++ // 跳过 null 终止符

            if (pos >= frameContent.size) return null

            val contentBytes = frameContent.copyOfRange(pos, frameContent.size)
            return decodeID3Text(contentBytes, encoding)
        } catch (e: Exception) {
            Log.e(TAG, "解析 USLT 帧失败", e)
            null
        }
    }

    /**
     * 解码 ID3 文本内容
     * 根据 ID3 编码字节选择合适的解码方式
     * 参考 PC 端 mutagen 的处理方式
     */
    private fun decodeID3Text(data: ByteArray, encoding: Int): String? {
        if (data.isEmpty()) return null

        return when (encoding) {
            0x00 -> {
                // ISO-8859-1，但中文歌曲通常实际使用 GBK/GB2312
                // MAV 数字问题通常出现在这里
                decodeISO8859WithChineseFallback(data)
            }

            0x01 -> {
                // UTF-16 with BOM
                decodeUTF16WithBOM(data)
            }

            0x02 -> {
                // UTF-16 BE without BOM
                decodeWithCharsetDetection(data, Charsets.UTF_16BE)
            }

            0x03 -> {
                // UTF-8
                decodeWithCharsetDetection(data, Charsets.UTF_8)
            }

            else -> {
                // 未知编码，尝试自动检测
                autoDecode(data)
            }
        }
    }

    /**
     * 解码 ISO-8859-1 编码的文本，但检测是否实际是中文编码
     * 这是处理 MAV 数字乱码的关键
     */
    private fun decodeISO8859WithChineseFallback(data: ByteArray): String? {
        // 首先尝试 ISO-8859-1
        try {
            val isoResult = String(data, Charsets.ISO_8859_1).trim { it == '\u0000' }

            // 检查是否包含乱码特征
            // MAV 数字乱码通常表现为：数字正常，但中文显示为乱码
            val hasGarbledChinese = isoResult.contains(Regex("[锟斤拷浣犲紶柇戦浜]"))
            val hasReplacementChar = isoResult.contains('\uFFFD')

            // 如果 ISO-8859-1 解码结果看起来正常（没有明显乱码），直接返回
            if (!hasGarbledChinese && !hasReplacementChar) {
                // 进一步检查：如果包含大量非 ASCII 字符，可能是中文编码
                val nonAsciiCount = isoResult.count { it.code > 127 }
                if (nonAsciiCount < isoResult.length * 0.3) {
                    return isoResult
                }
            }
        } catch (e: Exception) {
            // 继续尝试其他编码
        }

        // 尝试中文编码（GB18030 兼容 GBK 和 GB2312）
        val chineseCharsets = listOf(
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("GB2312")
        )

        var bestResult: String? = null
        var bestScore = -1

        for (charset in chineseCharsets) {
            try {
                val result = String(data, charset).trim { it == '\u0000' }
                val score = evaluateEncodingQuality(result, charset.name())

                if (score > bestScore) {
                    bestScore = score
                    bestResult = result
                }

                if (score >= 90) {
                    Log.d(TAG, "使用 $charset 成功解码 ISO-8859-1 标记的中文内容")
                    return result
                }
            } catch (e: Exception) {
                // 继续尝试下一个编码
            }
        }

        // 返回最佳中文编码结果，如果没有则回退到 ISO-8859-1
        return bestResult ?: try {
            String(data, Charsets.ISO_8859_1).trim { it == '\u0000' }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解码 UTF-16 with BOM
     */
    private fun decodeUTF16WithBOM(data: ByteArray): String? {
        if (data.size < 2) return null

        // 检查 BOM
        val hasBOM = data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte() ||
                data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()

        return if (hasBOM) {
            // 有 BOM，使用标准 UTF-16 解码
            try {
                String(data, Charsets.UTF_16).trim { it == '\u0000' }
            } catch (e: Exception) {
                autoDecode(data)
            }
        } else {
            // 没有 BOM，可能是 UTF-16LE（Windows 常见）
            decodeWithCharsetDetection(data, Charsets.UTF_16LE)
        }
    }

    /**
     * 使用指定字符集解码，如果失败则尝试其他编码
     */
    private fun decodeWithCharsetDetection(data: ByteArray, primaryCharset: Charset): String? {
        // 首先尝试主要字符集
        try {
            val result = String(data, primaryCharset).trim { it == '\u0000' }
            if (!containsGarbledChars(result)) {
                return result
            }
        } catch (e: Exception) {
            // 继续尝试其他编码
        }

        // 尝试自动检测
        return autoDecode(data)
    }

    /**
     * 自动检测编码并解码
     * 参考 PC 端 charset_normalizer 的思路
     */
    private fun autoDecode(data: ByteArray): String? {
        if (data.isEmpty()) return null

        // 按优先级尝试各种编码
        val charsetsToTry = listOf(
            Charsets.UTF_8,
            Charsets.UTF_16LE,
            Charsets.UTF_16BE,
            Charsets.UTF_16,
            Charset.forName("GB18030"),  // GB18030 兼容 GBK 和 GB2312
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("Big5"),
            Charsets.ISO_8859_1
        )

        var bestResult: String? = null
        var bestScore = -1

        for (charset in charsetsToTry) {
            try {
                val result = String(data, charset).trim { it == '\u0000' }
                val score = evaluateEncoding(result)

                if (score > bestScore) {
                    bestScore = score
                    bestResult = result
                }

                // 如果得分很高，直接返回
                if (score >= 95) {
                    return result
                }
            } catch (e: Exception) {
                // 继续尝试下一个
            }
        }

        return bestResult
    }

    /**
     * 评估解码结果的质量
     * 返回 0-100 的分数，分数越高表示越可能是正确的编码
     */
    private fun evaluateEncoding(text: String): Int {
        if (text.isEmpty()) return 0

        var score = 100

        // 1. 检查替换字符（严重扣分）
        val replacementCount = text.count { it == '\uFFFD' }
        score -= replacementCount * 10

        // 2. 检查控制字符（适度扣分）
        val controlCount = text.count { it.code in 0..31 && it !in "\n\r\t" }
        score -= controlCount * 2

        // 3. 检查空字符（适度扣分）
        val nullCount = text.count { it == '\u0000' }
        score -= nullCount * 2

        // 4. 检查常见乱码字符（严重扣分）
        val garbledChars = setOf('�', '锟', '斤', '拷', '浣', '犲', '紶', '柇')
        val garbledCount = text.count { it in garbledChars }
        score -= garbledCount * 5

        // 5. 奖励可打印字符比例高的
        val printableRatio =
            text.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:'\"()-[]{}" }
                .toFloat() / text.length
        score += (printableRatio * 20).toInt()

        // 6. 奖励包含中文字符的（对于中文歌曲）
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        if (chineseCharCount > 0) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 检查内容是否包含乱码
     * 参考 PC 端 mutagen 的处理方式：检测替换字符 � (U+FFFD)
     */
    private fun containsGarbledChars(content: String): Boolean {
        if (content.isEmpty()) return false

        // 1. 检查 Unicode 替换字符 (U+FFFD) - 这是最主要的乱码指标
        if (content.contains('\uFFFD')) return true

        // 2. 检查大量控制字符（除了常见的换行、回车、制表符）
        val controlChars = content.count { it.code in 0..31 && it !in "\n\r\t" }
        if (controlChars > content.length * 0.05) return true

        // 3. 检查问号字符（可能是编码错误的回退）
        // 但要排除正常的问号使用场景
        val questionCount = content.count { it == '?' }
        if (questionCount > content.length * 0.15) return true

        // 4. 检查是否有大量连续的空字符
        val nullCount = content.count { it == '\u0000' }
        if (nullCount > content.length * 0.1) return true

        // 5. 检查是否有大量乱码常见字符
        val commonGarbled =
            content.count { it == '�' || it == '�' || it == '锟' || it == '斤' || it == '拷' }
        return commonGarbled > 0
    }
}
