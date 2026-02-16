package com.example.lddc.service.local

import android.util.Log
import com.example.lddc.model.LocalMusicInfo
import java.io.File
import java.nio.charset.Charset

/**
 * 音频元数据读取器
 *
 * 用于从各种音频格式文件中读取元数据（年份、歌词等）
 */
object AudioMetadataReader {

    private const val TAG = "AudioMetadataReader"

    /**
     * 音频文件扩展元数据
     */
    data class ExtendedMetadata(
        val year: Int? = null,
        val genre: String? = null,
        val lyrics: String? = null,
        val composer: String? = null,
        val trackNumber: Int? = null
    )

    /**
     * 从文件读取扩展元数据
     */
    fun readExtendedMetadata(filePath: String): ExtendedMetadata {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return ExtendedMetadata()
            }

            val extension = filePath.substringAfterLast(".", "").lowercase()

            when (extension) {
                "flac" -> readFLACMetadata(file)
                "wav" -> readWAVMetadata(file)
                "mp3" -> readMP3Metadata(file)
                "ogg", "oga" -> readOGGMetadata(file)
                "m4a", "mp4", "m4b", "aac" -> readMP4Metadata(file)
                "wma", "asf" -> readASFMetadata(file)
                "ape" -> readAPEMetadata(file)
                "opus" -> readOPUSMetadata(file)
                "aiff", "aif" -> readAIFFMetadata(file)
                "wv" -> readWavPackMetadata(file)
                "tta" -> readTrueAudioMetadata(file)
                "mpc", "mp+" -> readMusepackMetadata(file)
                // 额外格式
                "3g2" -> readMP4Metadata(file)  // 3GPP2 使用类似 MP4 的格式
                "dff", "dsf" -> readDSDMetadata(file)  // DSD 格式
                "mid" -> readMIDIMetadata(file)  // MIDI
                "ofr", "ofs" -> readOptimFROGMetadata(file)  // OptimFROG
                "spx" -> readOGGMetadata(file)  // Speex 使用 OGG 容器
                "tak" -> readTAKMetadata(file)  // TAK 无损音频
                else -> ExtendedMetadata()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取扩展元数据失败: $filePath", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 FLAC 文件元数据（Vorbis Comment）
     */
    private fun readFLACMetadata(file: File): ExtendedMetadata {
        val data = file.readBytes()
        if (data.size < 4 || !String(data, 0, 4, Charsets.ISO_8859_1).startsWith("fLaC")) {
            return ExtendedMetadata()
        }

        return parseFLACVorbisCommentForMetadata(data)
    }

    /**
     * 解析 FLAC Vorbis Comment 获取元数据
     */
    private fun parseFLACVorbisCommentForMetadata(data: ByteArray): ExtendedMetadata {
        var year: Int? = null
        var genre: String? = null
        var lyrics: String? = null
        var composer: String? = null
        var trackNumber: Int? = null

        var pos = 4 // 跳过 "fLaC"

        while (pos < data.size) {
            if (pos + 4 > data.size) break

            val blockHeader = data[pos].toInt()
            val isLastBlock = (blockHeader and 0x80) != 0
            val blockType = blockHeader and 0x7F

            val blockSize = ((data[pos + 1].toInt() and 0xFF) shl 16) or
                    ((data[pos + 2].toInt() and 0xFF) shl 8) or
                    (data[pos + 3].toInt() and 0xFF)

            pos += 4

            // Vorbis Comment 块类型是 4
            if (blockType == 4) {
                val commentData = parseVorbisComments(data, pos, blockSize)
                year = commentData["DATE"]?.toIntOrNull()
                    ?: commentData["YEAR"]?.toIntOrNull()
                genre = commentData["GENRE"]
                lyrics = commentData["LYRICS"] ?: commentData["UNSYNCEDLYRICS"]
                // 只使用 COMPOSER 字段，不要使用 ARTIST（避免重复显示）
                composer = commentData["COMPOSER"]
                trackNumber = commentData["TRACKNUMBER"]?.toIntOrNull()
                break
            }

            pos += blockSize
            if (isLastBlock) break
        }

        return ExtendedMetadata(year, genre, lyrics, composer, trackNumber)
    }

    /**
     * 解析 Vorbis Comments 为键值对
     */
    private fun parseVorbisComments(data: ByteArray, start: Int, size: Int): Map<String, String> {
        val comments = mutableMapOf<String, String>()

        try {
            var pos = start
            val end = start + size

            if (pos + 4 > end) return comments

            // 跳过供应商字符串
            val vendorLength = readLittleEndianInt(data, pos)
            pos += 4 + vendorLength

            if (pos + 4 > end) return comments

            // 读取评论数量
            val commentCount = readLittleEndianInt(data, pos)
            pos += 4

            for (i in 0 until commentCount) {
                if (pos + 4 > end) break

                val commentLength = readLittleEndianInt(data, pos)
                pos += 4

                if (pos + commentLength > end) break

                val commentBytes = data.copyOfRange(pos, pos + commentLength)
                pos += commentLength

                // Vorbis Comment 应该是 UTF-8，但有时可能有编码问题
                val comment = decodeWithFallback(commentBytes)

                // 解析键值对（格式：KEY=value）
                val equalPos = comment.indexOf('=')
                if (equalPos > 0) {
                    val key = comment.substring(0, equalPos).uppercase()
                    val value = comment.substring(equalPos + 1)
                    comments[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 Vorbis Comments 失败", e)
        }

        return comments
    }

    /**
     * 读取 WAV 文件元数据
     */
    private fun readWAVMetadata(file: File): ExtendedMetadata {
        val data = file.readBytes()
        if (data.size < 12) return ExtendedMetadata()

        val riffHeader = String(data, 0, 4, Charsets.ISO_8859_1)
        val waveHeader = String(data, 8, 4, Charsets.ISO_8859_1)
        if (riffHeader != "RIFF" || waveHeader != "WAVE") {
            return ExtendedMetadata()
        }

        var year: Int? = null
        var genre: String? = null
        var lyrics: String? = null
        var composer: String? = null
        var trackNumber: Int? = null

        // 解析 RIFF 块
        var pos = 12
        while (pos < data.size - 8) {
            val chunkId = String(data, pos, 4, Charsets.ISO_8859_1)
            val chunkSize = readLittleEndianInt(data, pos + 4)

            if (chunkId == "LIST") {
                val listType = String(data, pos + 8, 4, Charsets.ISO_8859_1)
                if (listType == "INFO") {
                    val infoData = parseWAVInfoChunk(data, pos + 12, chunkSize - 4)
                    year = infoData["ICRD"]?.toIntOrNull()
                    genre = infoData["IGNR"]
                    composer = infoData["IART"]
                    trackNumber = infoData["ITRK"]?.toIntOrNull()
                }
            }

            // 查找 ID3 标签
            if (chunkId == "id3 " || chunkId.startsWith("ID3")) {
                val id3Data = data.copyOfRange(pos + 8, pos + 8 + chunkSize)
                val id3Metadata = parseID3Metadata(id3Data)
                if (year == null) year = id3Metadata.year
                if (genre == null) genre = id3Metadata.genre
                if (lyrics == null) lyrics = id3Metadata.lyrics
                if (composer == null) composer = id3Metadata.composer
                if (trackNumber == null) trackNumber = id3Metadata.trackNumber
            }

            pos += 8 + chunkSize
            if (chunkSize % 2 != 0) pos++
        }

        return ExtendedMetadata(year, genre, lyrics, composer, trackNumber)
    }

    /**
     * 解析 WAV INFO 块
     * RIFF INFO 块通常使用 ASCII 或系统默认编码，需要尝试多种编码
     */
    private fun parseWAVInfoChunk(data: ByteArray, start: Int, size: Int): Map<String, String> {
        val info = mutableMapOf<String, String>()
        var pos = start
        val end = start + size

        while (pos < end - 8) {
            val chunkId = String(data, pos, 4, Charsets.ISO_8859_1)
            val chunkSize = readLittleEndianInt(data, pos + 4)

            if (pos + 8 + chunkSize > end) break

            // 尝试多种编码读取内容
            val contentBytes = data.copyOfRange(pos + 8, pos + 8 + chunkSize)
            val content = decodeWithFallback(contentBytes)
                .trim { it == '\u0000' }
            info[chunkId] = content

            pos += 8 + chunkSize
            if (chunkSize % 2 != 0) pos++
        }

        return info
    }

    /**
     * 使用多种编码尝试解码字节数组
     * 参考 PC 端 charset_normalizer 的思路
     */
    private fun decodeWithFallback(data: ByteArray): String {
        if (data.isEmpty()) return ""

        // 按优先级尝试各种编码
        val charsetsToTry = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),  // GB18030 兼容 GBK 和 GB2312
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("Big5"),
            Charsets.UTF_16LE,
            Charsets.UTF_16BE,
            Charsets.ISO_8859_1
        )

        var bestResult: String = String(data, Charsets.UTF_8)
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
        val printableRatio = text.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:'\"()-[]{}" }.toFloat() / text.length
        score += (printableRatio * 20).toInt()

        // 6. 奖励包含中文字符的（对于中文歌曲）
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        if (chineseCharCount > 0) {
            score += 10
        }

        return score.coerceIn(0, 100)
    }

    /**
     * 读取 MP3 文件元数据（ID3v2）
     */
    private fun readMP3Metadata(file: File): ExtendedMetadata {
        val data = file.readBytes()
        if (data.size < 10) return ExtendedMetadata()

        // 检查 ID3v2 标记
        if (data[0] != 0x49.toByte() || data[1] != 0x44.toByte() || data[2] != 0x33.toByte()) {
            return ExtendedMetadata()
        }

        return parseID3Metadata(data)
    }

    /**
     * 解析 ID3v2 元数据
     */
    private fun parseID3Metadata(data: ByteArray): ExtendedMetadata {
        var year: Int? = null
        var genre: String? = null
        var lyrics: String? = null
        var composer: String? = null
        var trackNumber: Int? = null

        try {
            val version = data[3].toInt()
            val tagSize = readID3v2TagSize(data)

            if (tagSize <= 10 || tagSize > data.size) return ExtendedMetadata()

            val tagData = data.copyOfRange(10, tagSize)
            var pos = 0

            while (pos < tagData.size - 10) {
                val frameId = String(tagData, pos, 4, Charsets.ISO_8859_1)

                // 读取帧大小
                val frameSize = when (version) {
                    2 -> readID3v22FrameSize(tagData, pos)
                    3 -> readID3v23FrameSize(tagData, pos)
                    4 -> readID3v24FrameSize(tagData, pos)
                    else -> break
                }

                if (frameSize <= 0 || pos + 10 + frameSize > tagData.size) break

                val frameContent = tagData.copyOfRange(pos + 10, pos + 10 + frameSize)

                when (frameId) {
                    "TYER", "TDRC" -> year = parseID3TextFrame(frameContent)?.toIntOrNull()
                    "TCON" -> genre = parseID3TextFrame(frameContent)
                    "USLT" -> lyrics = parseID3LyricsFrame(frameContent)
                    "TCOM" -> composer = parseID3TextFrame(frameContent)
                    "TRCK" -> trackNumber = parseID3TextFrame(frameContent)?.substringBefore("/")?.toIntOrNull()
                }

                if (frameId[0] == 0.toChar()) break
                pos += 10 + frameSize
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 ID3 元数据失败", e)
        }

        return ExtendedMetadata(year, genre, lyrics, composer, trackNumber)
    }

    /**
     * 读取 ID3v2 标签大小
     */
    private fun readID3v2TagSize(data: ByteArray): Int {
        return ((data[6].toInt() and 0x7F) shl 21) or
                ((data[7].toInt() and 0x7F) shl 14) or
                ((data[8].toInt() and 0x7F) shl 7) or
                (data[9].toInt() and 0x7F)
    }

    /**
     * 读取 ID3v2.2 帧大小
     */
    private fun readID3v22FrameSize(data: ByteArray, offset: Int): Int {
        return ((data[offset + 5].toInt() and 0xFF) shl 16) or
                ((data[offset + 6].toInt() and 0xFF) shl 8) or
                (data[offset + 7].toInt() and 0xFF)
    }

    /**
     * 读取 ID3v2.3 帧大小
     */
    private fun readID3v23FrameSize(data: ByteArray, offset: Int): Int {
        return ((data[offset + 4].toInt() and 0xFF) shl 24) or
                ((data[offset + 5].toInt() and 0xFF) shl 16) or
                ((data[offset + 6].toInt() and 0xFF) shl 8) or
                (data[offset + 7].toInt() and 0xFF)
    }

    /**
     * 读取 ID3v2.4 帧大小
     */
    private fun readID3v24FrameSize(data: ByteArray, offset: Int): Int {
        return ((data[offset + 4].toInt() and 0x7F) shl 21) or
                ((data[offset + 5].toInt() and 0x7F) shl 14) or
                ((data[offset + 6].toInt() and 0x7F) shl 7) or
                (data[offset + 7].toInt() and 0x7F)
    }

    /**
     * 解析 ID3 文本帧
     */
    private fun parseID3TextFrame(frameContent: ByteArray): String? {
        if (frameContent.isEmpty()) return null

        val encoding = frameContent[0].toInt()
        val contentBytes = frameContent.copyOfRange(1, frameContent.size)

        return decodeID3Text(contentBytes, encoding)
    }

    /**
     * 解析 ID3 歌词帧
     */
    private fun parseID3LyricsFrame(frameContent: ByteArray): String? {
        if (frameContent.isEmpty()) return null

        val encoding = frameContent[0].toInt()

        // 跳过编码字节和语言代码（3字节）
        var pos = 4

        // 跳过内容描述符（null 终止）
        while (pos < frameContent.size && frameContent[pos] != 0.toByte()) {
            pos++
        }
        pos++

        if (pos >= frameContent.size) return null

        val contentBytes = frameContent.copyOfRange(pos, frameContent.size)
        return decodeID3Text(contentBytes, encoding)
    }

    /**
     * 解码 ID3 文本内容
     * 根据 ID3 编码字节选择合适的解码方式
     */
    private fun decodeID3Text(data: ByteArray, encoding: Int): String? {
        if (data.isEmpty()) return null

        return when (encoding) {
            0x00 -> {
                // ISO-8859-1，但可能是 GBK/GB2312
                decodeWithCharsetDetection(data, Charsets.ISO_8859_1)
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
        val commonGarbled = content.count { it == '�' || it == '�' || it == '锟' || it == '斤' || it == '拷' }
        return commonGarbled > 0
    }

    /**
     * 读取 OGG 文件元数据（Vorbis Comment）
     */
    private fun readOGGMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // OGG 文件以 "OggS" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "OggS") {
                return ExtendedMetadata()
            }

            // 查找 Vorbis Comment（通常在第一个 Vorbis 头之后）
            // 这里简化处理，尝试查找 Vorbis Comment 标记
            parseOGGVorbisComment(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 OGG 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 OGG Vorbis Comment
     */
    private fun parseOGGVorbisComment(data: ByteArray): ExtendedMetadata {
        // OGG 解析比较复杂，这里简化处理
        // 实际应该解析 OGG 页结构
        return ExtendedMetadata()
    }

    /**
     * 读取 MP4/M4A/AAC 文件元数据
     */
    private fun readMP4Metadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // MP4 文件以 ftyp 或 moov 开头
            if (data.size < 8) return ExtendedMetadata()

            // 检查是否是 MP4/M4A 文件
            val fileType = String(data, 4, 4, Charsets.ISO_8859_1)
            if (fileType != "ftyp" && fileType != "moov" && fileType != "mdat") {
                return ExtendedMetadata()
            }

            parseMP4Metadata(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 MP4 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 MP4 元数据
     */
    private fun parseMP4Metadata(data: ByteArray): ExtendedMetadata {
        // MP4 解析比较复杂，这里简化处理
        // 实际应该解析 moov/udta/meta 结构
        return ExtendedMetadata()
    }

    /**
     * 读取 ASF/WMA 文件元数据
     */
    private fun readASFMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // ASF 文件以 GUID 开头
            if (data.size < 16) return ExtendedMetadata()

            // ASF Header GUID: 30 26 B2 75 8E 66 CF 11 A6 D9 00 AA 00 62 CE 6C
            val asfGuid = byteArrayOf(0x30, 0x26, 0xB2.toByte(), 0x75, 0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11, 0xA6.toByte(), 0xD9.toByte(), 0x00, 0xAA.toByte(), 0x00, 0x62, 0xCE.toByte(), 0x6C.toByte())
            if (!data.copyOfRange(0, 16).contentEquals(asfGuid)) {
                return ExtendedMetadata()
            }

            parseASFMetadata(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 ASF/WMA 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 ASF 元数据
     */
    private fun parseASFMetadata(data: ByteArray): ExtendedMetadata {
        // ASF 解析比较复杂，这里简化处理
        return ExtendedMetadata()
    }

    /**
     * 读取 APE 文件元数据
     */
    private fun readAPEMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // APE 文件以 "MAC " 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "MAC ") {
                return ExtendedMetadata()
            }

            // APE 使用 APEv2 标签，通常在文件末尾
            parseAPEv2Tag(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 APE 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 APEv2 标签
     */
    private fun parseAPEv2Tag(data: ByteArray): ExtendedMetadata {
        // APEv2 标签解析
        return ExtendedMetadata()
    }

    /**
     * 读取 OPUS 文件元数据
     */
    private fun readOPUSMetadata(file: File): ExtendedMetadata {
        // OPUS 使用 OGG 容器，元数据格式与 OGG Vorbis 类似
        return readOGGMetadata(file)
    }

    /**
     * 读取 AIFF 文件元数据
     */
    private fun readAIFFMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // AIFF 文件以 "FORM" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "FORM") {
                return ExtendedMetadata()
            }

            // AIFF 使用 IFF 格式，元数据在 NAME、AUTH、ANNO 等块中
            parseAIFFMetadata(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 AIFF 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 AIFF 元数据
     */
    private fun parseAIFFMetadata(data: ByteArray): ExtendedMetadata {
        // AIFF 解析
        return ExtendedMetadata()
    }

    /**
     * 读取 WavPack 文件元数据
     */
    private fun readWavPackMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // WavPack 文件以 "wvpk" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "wvpk") {
                return ExtendedMetadata()
            }

            // WavPack 使用 APEv2 标签
            parseAPEv2Tag(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 WavPack 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 True Audio 文件元数据
     */
    private fun readTrueAudioMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // TTA 文件以 "TTA1" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "TTA1") {
                return ExtendedMetadata()
            }

            // TTA 使用 ID3v2 标签
            parseID3Metadata(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 TTA 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 Musepack 文件元数据
     */
    private fun readMusepackMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // MPC 文件以 "MP+" 或 "MPCK" 开头
            if (data.size < 3 || (String(data, 0, 3, Charsets.ISO_8859_1) != "MP+" &&
                        String(data, 0, 4, Charsets.ISO_8859_1) != "MPCK")) {
                return ExtendedMetadata()
            }

            // Musepack 使用 APEv2 标签
            parseAPEv2Tag(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 MPC 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 DSD 文件元数据 (DFF/DSF)
     */
    private fun readDSDMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            if (data.size < 4) return ExtendedMetadata()

            val header = String(data, 0, 4, Charsets.ISO_8859_1)
            when (header) {
                "DSD " -> parseDFFMetadata(data)  // DFF 格式
                "DSDS" -> parseDSFMetadata(data)  // DSF 格式
                else -> ExtendedMetadata()
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 DSD 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 解析 DFF 格式元数据
     */
    private fun parseDFFMetadata(data: ByteArray): ExtendedMetadata {
        // DFF 使用 ID3v2 标签（在文件末尾）
        // 查找 ID3 标签
        for (i in data.size - 10 downTo 0) {
            if (data[i] == 'I'.code.toByte() &&
                data[i + 1] == 'D'.code.toByte() &&
                data[i + 2] == '3'.code.toByte()) {
                val id3Data = data.copyOfRange(i, data.size)
                return parseID3Metadata(id3Data)
            }
        }
        return ExtendedMetadata()
    }

    /**
     * 解析 DSF 格式元数据
     */
    private fun parseDSFMetadata(data: ByteArray): ExtendedMetadata {
        // DSF 格式在文件头后有元数据块
        // 简化处理：尝试查找 ID3 标签
        return parseDFFMetadata(data)
    }

    /**
     * 读取 MIDI 文件元数据
     */
    private fun readMIDIMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // MIDI 文件以 "MThd" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "MThd") {
                return ExtendedMetadata()
            }
            // MIDI 元数据通常在轨道名称或序列器特定事件中
            // 简化处理
            ExtendedMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "读取 MIDI 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 OptimFROG 文件元数据
     */
    private fun readOptimFROGMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // OptimFROG 使用 APEv2 标签
            parseAPEv2Tag(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 OptimFROG 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取 TAK 文件元数据
     */
    private fun readTAKMetadata(file: File): ExtendedMetadata {
        return try {
            val data = file.readBytes()
            // TAK 文件以 "tBaK" 开头
            if (data.size < 4 || String(data, 0, 4, Charsets.ISO_8859_1) != "tBaK") {
                return ExtendedMetadata()
            }
            // TAK 使用 APEv2 标签
            parseAPEv2Tag(data)
        } catch (e: Exception) {
            Log.e(TAG, "读取 TAK 元数据失败", e)
            ExtendedMetadata()
        }
    }

    /**
     * 读取小端序 32 位整数
     */
    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    /**
     * 合并元数据到 LocalMusicInfo
     */
    fun mergeMetadata(music: LocalMusicInfo): LocalMusicInfo {
        val extendedMetadata = readExtendedMetadata(music.filePath)

        return music.copy(
            year = music.year ?: extendedMetadata.year,
            genre = music.genre ?: extendedMetadata.genre,
            composer = music.composer ?: extendedMetadata.composer,
            trackNumber = music.trackNumber ?: extendedMetadata.trackNumber
        )
    }
}
