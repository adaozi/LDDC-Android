package com.example.lddc.service.parser

import android.util.Base64
import org.json.JSONObject

/**
 * KRC 歌词解析器（酷狗音乐）
 *
 * KRC格式特点：
 * - 文件头有加密标记，需要先用KrcDecoder解密
 * - 行格式：[开始时间,持续时间]内容
 * - 逐字格式：<相对开始时间,持续时间,0>单词
 * - 时间单位：毫秒（行时间是绝对时间，字时间是相对行开始）
 * - 支持多语言：通过language标签的Base64编码JSON存储翻译和罗马音
 *
 * 对应 Python 中的 core/parser/krc.py
 */

/** 标签匹配模式：[ar:艺术家] */
private val TAG_SPLIT_PATTERN = Regex("^\\[(\\w+):([^]]*)]$")

/** 行时间戳匹配模式：[开始时间,持续时间] */
private val LINE_SPLIT_PATTERN = Regex("^\\[(\\d+),(\\d+)](.*)$")

/** 逐字时间戳匹配模式：<相对开始时间,持续时间,0>单词 */
private val WORD_SPLIT_PATTERN = Regex("<(?<start>\\d+),(?<duration>\\d+),\\d+>(?<content>(?:.(?!\\d+,\\d+,\\d+>))*)")

/**
 * 将KRC文本解析为多语言歌词数据
 *
 * KRC支持多语言歌词存储在language标签中（Base64编码的JSON）
 * type=0: 逐字罗马音
 * type=1: 逐行翻译
 *
 * @param krc KRC格式字符串（已解密）
 * @return Pair<标签字典, 多语言歌词数据>
 *         标签：包含歌曲元数据和language标签
 *         多语言数据：包含orig（原文）、roma（罗马音）、ts（翻译）
 */
fun krc2mdata(krc: String): Pair<Map<String, String>, MultiLyricsData> {
    val lyricsDict = createMultiLyricsData()
    val tags = mutableMapOf<String, String>()

    val origList = createLyricsData()
    val romaList = createLyricsData()
    val tsList = createLyricsData()

    // 第一遍：解析原文歌词
    krc.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (!line.startsWith("[")) return@forEach

        // 处理标签行 [ar:艺术家]
        val tagMatch = TAG_SPLIT_PATTERN.find(line)
        if (tagMatch != null) {
            tags[tagMatch.groupValues[1]] = tagMatch.groupValues[2]
            return@forEach
        }

        // 处理歌词行 [开始时间,持续时间]内容
        val lineMatch = LINE_SPLIT_PATTERN.find(line)
        if (lineMatch != null) {
            val lineStart = lineMatch.groupValues[1].toLong()
            val lineDuration = lineMatch.groupValues[2].toLong()
            val lineEnd = lineStart + lineDuration
            val lineContent = lineMatch.groupValues[3]

            // 解析逐字时间戳
            val words = WORD_SPLIT_PATTERN.findAll(lineContent)
                .map { wordMatch ->
                    val wordStart = wordMatch.groupValues[1].toLong()
                    val wordDuration = wordMatch.groupValues[2].toLong()
                    val wordContent = wordMatch.groupValues[3]

                    // 字时间是相对于行开始时间的偏移
                    LyricsWord(
                        lineStart + wordStart,
                        lineStart + wordStart + wordDuration,
                        wordContent
                    )
                }
                .toList()

            if (words.isEmpty()) {
                // 无逐字信息，整行作为一个单词
                origList.add(LyricsLine(lineStart, lineEnd, listOf(LyricsWord(lineStart, lineEnd, lineContent))))
            } else {
                origList.add(LyricsLine(lineStart, lineEnd, words))
            }
        }
    }

    // 第二遍：解析多语言歌词（罗马音、翻译）
    if (tags.containsKey("language") && tags["language"]?.isNotBlank() == true) {
        try {
            // language标签是Base64编码的JSON
            val languageJson = String(Base64.decode(tags["language"]!!, Base64.DEFAULT))
            val languageObj = JSONObject(languageJson)
            val contentArray = languageObj.getJSONArray("content")

            for (i in 0 until contentArray.length()) {
                val language = contentArray.getJSONObject(i)
                val type = language.getInt("type")
                val lyricContent = language.getJSONArray("lyricContent")

                when (type) {
                    0 -> { // 逐字罗马音
                        var offset = 0
                        for (j in origList.indices) {
                            val origLine = origList[j]
                            // 跳过空行
                            if (origLine.words.all { it.text.isEmpty() }) {
                                offset++
                                continue
                            }

                            val romaWords = mutableListOf<LyricsWord>()
                            val romaLineContent = lyricContent.getJSONArray(j - offset)
                            for (k in origLine.words.indices) {
                                if (k < romaLineContent.length()) {
                                    val origWord = origLine.words[k]
                                    romaWords.add(LyricsWord(
                                        origWord.start,
                                        origWord.end,
                                        romaLineContent.getString(k)
                                    ))
                                }
                            }
                            romaList.add(LyricsLine(origLine.start, origLine.end, romaWords))
                        }
                    }
                    1 -> { // 逐行翻译
                        for (j in origList.indices) {
                            val origLine = origList[j]
                            if (j < lyricContent.length()) {
                                val tsLineContent = lyricContent.getJSONArray(j)
                                if (tsLineContent.length() > 0) {
                                    tsList.add(LyricsLine(
                                        origLine.start,
                                        origLine.end,
                                        listOf(LyricsWord(
                                            origLine.start,
                                            origLine.end,
                                            tsLineContent.getString(0)
                                        ))
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 解析失败，忽略多语言歌词
        }
    }

    // 组装结果
    lyricsDict["orig"] = origList
    if (romaList.isNotEmpty()) {
        lyricsDict["roma"] = romaList
    }
    if (tsList.isNotEmpty()) {
        lyricsDict["ts"] = tsList
    }

    return Pair(tags, lyricsDict)
}
