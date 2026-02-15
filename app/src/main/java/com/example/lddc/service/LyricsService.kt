package com.example.lddc.service

import android.content.Context
import com.example.lddc.model.*
import com.example.lddc.model.LyricsFormat  // 显式导入 LyricsFormat
import com.example.lddc.service.converter.assConverter
import com.example.lddc.service.converter.lrcConverter
import com.example.lddc.service.converter.srtConverter
import com.example.lddc.service.logger.Logger
import com.example.lddc.service.parser.*

/**
 * 增强版歌词服务
 * 整合所有功能：搜索、获取歌词、格式转换、缓存等
 */
class LyricsService(context: Context) {

    private val logger = Logger.getInstance(context)

    companion object;

    /**
     * 将歌词转换为指定格式
     */
    fun convertLyrics(
        lyrics: Lyrics,
        format: LyricsOutputFormat,
        options: LyricsConvertOptions = LyricsConvertOptions()
    ): String {
        logger.info("Converting lyrics to format: $format")

        // 解析原始歌词
        val (tags, lyricsData) = parseLyricsToData(lyrics)

        return when (format) {
            LyricsOutputFormat.LRC -> {
                lrcConverter(
                    tags = tags,
                    lyricsDict = lyricsData,
                    lyricsFormat = options.lyricsFormat,
                    langsMapping = options.langsMapping,
                    langsOrder = options.langsOrder,
                    version = options.version,
                    lrcMsDigitCount = options.lrcMsDigitCount,
                    addEndTimestampLine = options.addEndTimestampLine,
                    lastRefLineTimeSty = options.lastRefLineTimeSty
                )
            }
            LyricsOutputFormat.ASS -> {
                assConverter(
                    title = lyrics.title,
                    lyricsDict = lyricsData,
                    langsMapping = options.langsMapping,
                    langsOrder = options.langsOrder,
                    duration = lyrics.duration,
                    version = options.version
                )
            }
            LyricsOutputFormat.SRT -> {
                srtConverter(
                    lyricsDict = lyricsData,
                    langsMapping = options.langsMapping,
                    langsOrder = options.langsOrder,
                    duration = lyrics.duration
                )
            }
        }
    }

    /**
     * 解析歌词为数据结构
     */
    private fun parseLyricsToData(lyrics: Lyrics): Pair<Map<String, String>, MultiLyricsData> {
        val tags = mutableMapOf<String, String>()
        val lyricsData = createMultiLyricsData()

        logger.info("Parsing lyrics, orig length: ${lyrics.orig?.length ?: 0}")
        logger.info("Orig first 100 chars: ${lyrics.orig?.take(100) ?: "null"}")

        // 解析原始歌词
        if (!lyrics.orig.isNullOrEmpty()) {
            when {
                lyrics.orig.contains("<Lyric_1") -> {
                    // QRC 格式 (优先检测，因为 QRC 也包含 [0 开头的行)
                    logger.info("Detected QRC format")
                    val (parsedTags, parsedData) = qrc2data(lyrics.orig)
                    tags.putAll(parsedTags)
                    lyricsData["orig"] = parsedData
                    logger.info("QRC parsed: ${parsedData.size} lines")
                }
                Regex("""\[\d+,\d+]""").containsMatchIn(lyrics.orig) &&
                    Regex("""<\d+,\d+,\d+>""").containsMatchIn(lyrics.orig) &&
                    !lyrics.orig.contains("[00:") -> {
                    // KRC 格式: [毫秒,持续时间]<相对开始,持续时间,0>内容
                    logger.info("Detected KRC format")
                    val (parsedTags, parsedData) = krc2mdata(lyrics.orig)
                    tags.putAll(parsedTags)
                    lyricsData.putAll(parsedData)
                    logger.info("KRC parsed: ${lyricsData["orig"]?.size ?: 0} lines")
                }
                Regex("""\[\d+,\d+]""").containsMatchIn(lyrics.orig) &&
                    Regex("""\(\d+,\d+,\d+\)""").containsMatchIn(lyrics.orig) -> {
                    // YRC 格式: [毫秒,持续时间](相对开始,持续时间,0)内容
                    logger.info("Detected YRC format")
                    lyricsData["orig"] = yrc2data(lyrics.orig)
                    logger.info("YRC parsed: ${lyricsData["orig"]?.size ?: 0} lines")
                }
                lyrics.orig.contains("[00:") ||
                    (lyrics.orig.contains("[0") && lyrics.orig.contains("]")) -> {
                    // LRC 格式: [mm:ss.xx] 或 [mm:ss.xxx]
                    logger.info("Detected LRC format")
                    val (parsedTags, parsedData) = lrc2data(lyrics.orig, lyrics.source)
                    tags.putAll(parsedTags)
                    lyricsData["orig"] = parsedData.firstOrNull() ?: createLyricsData()
                    logger.info("LRC parsed: ${lyricsData["orig"]?.size ?: 0} lines")
                }
                else -> {
                    // 纯文本
                    logger.info("Detected plain text format")
                    lyricsData["orig"] = plaintext2data(lyrics.orig)
                    logger.info("Plain text parsed: ${lyricsData["orig"]?.size ?: 0} lines")
                }
            }
            logger.info("Parsed lyrics data: ${lyricsData["orig"]?.size ?: 0} lines")
        } else {
            logger.error("Original lyrics is null or empty")
        }

        // 解析翻译歌词
        if (!lyrics.ts.isNullOrEmpty()) {
            val (_, tsData) = lrc2data(lyrics.ts, lyrics.source)
            lyricsData["ts"] = tsData.firstOrNull() ?: createLyricsData()
        }

        // 解析罗马音歌词
        if (!lyrics.roma.isNullOrEmpty()) {
            val (_, romaData) = lrc2data(lyrics.roma, lyrics.source)
            lyricsData["roma"] = romaData.firstOrNull() ?: createLyricsData()
        }

        // 添加基本信息到 tags
        if (lyrics.title.isNotEmpty()) tags["ti"] = lyrics.title
        if (lyrics.artist.isNotEmpty()) tags["ar"] = lyrics.artist
        if (lyrics.album.isNotEmpty()) tags["al"] = lyrics.album

        return Pair(tags, lyricsData)
    }

}

/**
 * 歌词输出格式
 */
enum class LyricsOutputFormat {
    LRC,    // LRC 歌词格式
    ASS,    // ASS 字幕格式
    SRT     // SRT 字幕格式
}

/**
 * 歌词转换选项
 */
data class LyricsConvertOptions(
    val lyricsFormat: LyricsFormat = LyricsFormat.LINEBYLINELRC,
    val langsMapping: Map<String, Map<Int, Int>> = emptyMap(),
    val langsOrder: List<String> = listOf("orig"),
    val version: String = "1.0",
    val lrcMsDigitCount: Int = 3,
    val addEndTimestampLine: Boolean = false,
    val lastRefLineTimeSty: Int = 0
)
