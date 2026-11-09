// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

/**
 * 匹配评分算法，逐函数移植自上游 LDDC/core/algorithm.py。
 *
 * 「移植版比原版差」大多出在这里，改动前先跑 MatchScoreGoldenTest（基准由上游代码生成）。
 */
object MatchAlgorithm {

    private val SYMBOL_MAP = mapOf(
        '（' to '(', '）' to ')', '：' to ':', '！' to '!', '？' to '?', '／' to '/',
        '＆' to '&', '＊' to '*', '＠' to '@', '＃' to '#', '＄' to '$', '％' to '%',
        '＼' to '\\', '｜' to '|', '＝' to '=', '＋' to '+', '－' to '-', '＜' to '<',
        '＞' to '>', '［' to '[', '］' to ']', '｛' to '{', '｝' to '}',
    )

    private val WHITESPACE = Regex("\\s")

    /** 全角符号统一 + 每个空白字符替换为一个空格（上游用 re.sub(r"\s"," ")，不折叠） */
    fun unifiedSymbol(text: String): String {
        val builder = StringBuilder(text.length)
        text.trim().forEach { ch -> builder.append(SYMBOL_MAP[ch] ?: ch) }
        return WHITESPACE.replace(builder.toString(), " ")
    }

    /** 等价于 difflib.SequenceMatcher(junk=lambda x: x == " ").ratio() */
    fun textDifference(text1: String, text2: String): Double {
        if (text1 == text2) return 1.0
        if (text1.isEmpty() || text2.isEmpty()) return 0.0
        return SequenceMatcher(text1, text2).ratio()
    }

    fun listMaxDifference(
        origList1: List<Any>,
        origList2: List<Any>,
        filterEmpty: Boolean = true,
    ): Double {
        fun normalize(list: List<Any>): List<List<String>> = list.map { item ->
            when (item) {
                is List<*> -> item.filterIsInstance<String>()
                is String -> listOf(item)
                else -> emptyList()
            }
        }

        fun listStrMaxDifference(l1: List<String>, l2: List<String>): Double {
            val a = if (filterEmpty) l1.filter { it.isNotEmpty() } else l1
            val b = if (filterEmpty) l2.filter { it.isNotEmpty() } else l2
            var best = 0.0
            var has = false
            for (x in a) for (y in b) {
                has = true
                best = maxOf(best, textDifference(x, y))
            }
            return if (has) best else 0.0
        }

        val list1 = normalize(origList1)
        val list2 = normalize(origList2)
        if (list1.isEmpty() || list2.isEmpty()) return 0.0

        data class Candidate(val i1: Int, val i2: Int, val score: Double)

        val candidates = ArrayList<Candidate>()
        if (list1.size >= list2.size) {
            list1.forEachIndexed { i1, l1 ->
                list2.forEachIndexed { i2, l2 -> candidates.add(Candidate(i1, i2, listStrMaxDifference(l1, l2))) }
            }
        } else {
            list2.forEachIndexed { i2, l2 ->
                list1.forEachIndexed { i1, l1 -> candidates.add(Candidate(i2, i1, listStrMaxDifference(l2, l1))) }
            }
        }
        candidates.sortByDescending { it.score }

        var total = 0.0
        val usedI1 = HashSet<Int>()
        val usedI2 = HashSet<Int>()
        for (candidate in candidates) {
            if (candidate.i1 !in usedI1 && candidate.i2 !in usedI2) {
                usedI1.add(candidate.i1)
                usedI2.add(candidate.i2)
                total += candidate.score
                if (usedI1.size == list1.size || usedI2.size == list2.size) break
            }
        }
        return total / maxOf(list1.size, list2.size)
    }

    // ---------------- 歌手 ----------------

    data class ArtistParts(val groups: List<String>, val singers: List<List<String>>)

    private val CV_STYLE = Regex("^(.*)\\s?\\((.+)\\)/[Cc][Vv][.:]\\s?(.+)$")
    private val CV_BLOCK = Regex("^(.*)\\s?\\((.+)[Cc][Vv][.:](.+)\\)$")
    private val GROUP_SINGERS = Regex("^(.*)\\s?\\(+([^)]+)\\)+$")
    private val GROUP_ARTIST = Regex("^(.*[^&])\\s([^(&a-zA-Z].*)$")
    private val FEAT_PATTERN = Regex("^(.*)\\s?feat\\.(.*)\\s?\\((.*)\\)$")
    private val ALIAS_PATTERN = Regex("^(.*)\\s?\\((?:[Cc][Vv][.:]|[Vv][Oo][.:])?(.*)\\)$")
    private val CV_MARK = Regex("[Cc][Vv][.:]")

    fun artistStr2List(artistRaw: String): ArtistParts {
        var artist = artistRaw
        if (artist.split(" ").all { it.length == 1 }) artist = artist.split(" ").joinToString("")

        artist = artist.trim()
            .replace("·", "・")
            .replace("（", "(")
            .replace("）", ")")
            .replace("：", ":")

        // 组织名(角色1・角色2...)/CV:歌手1・歌手2...
        if (artist.contains("・") && CV_MARK.containsMatchIn(artist)) {
            CV_STYLE.find(artist)?.let { match ->
                val characters = match.groupValues[2]
                val songers = match.groupValues[3]
                if (characters.contains("・") && songers.contains("・")) {
                    val chars = characters.split("・").map(::unifiedSymbol)
                    val names = songers.split("・").map(::unifiedSymbol)
                    if (chars.size == names.size) {
                        return ArtistParts(
                            listOf(match.groupValues[1]),
                            chars.zip(names).map { (c, s) -> listOf(c, s).distinct() },
                        )
                    }
                }
            }

            val groups = ArrayList<String>()
            val singers = ArrayList<List<String>>()
            var allMatched = true
            for (block in artist.split("/")) {
                val match = CV_BLOCK.find(block)
                val characters = match?.groupValues?.get(2)
                val songers = match?.groupValues?.get(3)
                if (match != null && characters != null && songers != null &&
                    characters.contains("・") && songers.contains("・")
                ) {
                    val chars = characters.split("・").map(::unifiedSymbol)
                    val names = songers.split("・").map(::unifiedSymbol)
                    if (chars.size == names.size) {
                        chars.zip(names).forEach { (c, s) -> singers.add(listOf(c, s).distinct()) }
                        groups.add(match.groupValues[1])
                    } else {
                        allMatched = false
                        break
                    }
                } else {
                    allMatched = false
                    break
                }
            }
            if (allMatched && groups.isNotEmpty()) return ArtistParts(groups, singers)
        }

        // 组织名(歌手名)
        if (artist.count { it == '(' } == artist.count { it == ')' } && artist.count { it == '(' } in 1..2) {
            GROUP_SINGERS.find(artist)?.let { match ->
                val parts = match.groupValues[2].split(Regex("[,、・]"))
                if (parts.size > 1) {
                    return ArtistParts(listOf(match.groupValues[1]), parts.map { listOf(unifiedSymbol(it)) })
                }
            }
        }

        // 组织名 ...
        var groups: List<String> = emptyList()
        GROUP_ARTIST.find(artist)?.let { match ->
            groups = listOf(match.groupValues[1])
            artist = match.groupValues[2]
        }

        // 以 "." 分隔（原文用 (\))\. 作为切分）
        var artistsStr: List<String>? = null
        val dotted = artist.split(Regex("\\)\\."))
        if (dotted.size > 1 && (dotted.size + 1) % 2 == 0) {
            val result = ArrayList<String>()
            dotted.forEachIndexed { index, part ->
                if (index % 2 == 0) result.add(unifiedSymbol(part)) else result[result.size - 1] += part
            }
            artistsStr = result
        }

        // 以 ", 、 / \ &" 分隔
        val separated = artist.split(Regex("[,、/\\\\&]"))
        if (separated.size > 1) artistsStr = separated.map(::unifiedSymbol)

        if (artistsStr == null) artistsStr = listOf(unifiedSymbol(artist))

        val singers = ArrayList<List<String>>()
        for (item in artistsStr) {
            val feat = FEAT_PATTERN.find(item)
            if (feat != null) {
                singers.add(listOf(unifiedSymbol(feat.groupValues[1])))
                val songer2 = feat.groupValues[3].trim()
                val character = feat.groupValues[2].trim()
                singers.add(listOf(songer2, character).distinct())
                continue
            }
            val alias = ALIAS_PATTERN.find(item)
            if (alias != null) {
                val name1 = alias.groupValues[1].trim()
                val name2 = alias.groupValues[2].trim()
                singers.add(listOf(name1, name2).distinct())
                continue
            }
            singers.add(listOf(item))
        }

        return ArtistParts(groups, singers)
    }

    /** 歌手匹配分（0~100） */
    fun calculateArtistScore(artist1: String, artist2: String): Double {
        val a = artistStr2List(artist1)
        val b = artistStr2List(artist2)

        var score = listMaxDifference(a.groups + a.singers, b.groups + b.singers)
        if (score == 1.0) return 100.0

        score = maxOf(
            score,
            textDifference(
                a.groups.joinToString("") + a.singers.joinToString("") { it.joinToString("") },
                b.groups.joinToString("") + b.singers.joinToString("") { it.joinToString("") },
            ),
        )

        val aOnlyGroups = a.singers.isEmpty() && a.groups.isNotEmpty() && b.groups.isNotEmpty()
        val bOnlyGroups = b.singers.isEmpty() && b.groups.isNotEmpty() && a.groups.isNotEmpty()
        if (aOnlyGroups || bOnlyGroups) {
            score = maxOf(score, listMaxDifference(a.singers, b.singers) * 0.6)
        } else if (a.singers.isNotEmpty() && b.singers.isNotEmpty()) {
            score = maxOf(score, listMaxDifference(a.singers, b.singers))
        }
        return maxOf(score * 100, 0.0)
    }

    // ---------------- 标题 ----------------

    private val TITLE_TAG_PATTERN = Regex(
        "[-<(\\[～]([～\\]^)>-]*)[～\\]^)>-]" +
            "|([\\p{L}\\p{N}_]+ ?(?:(?:solo |size )?ver(?:sion)?\\.?|size|style|mix(?:ed)?|edit(?:ed)?|版|solo))" +
            "|(纯音乐|inst\\.?(?:rumental)|off ?vocal(?: ?[Vv]er.)?)",
    )

    private fun getTags(notSame: String): Pair<MutableList<String>, String> {
        val tags = ArrayList<String>()
        TITLE_TAG_PATTERN.findAll(notSame).forEach { match ->
            for (index in 1 until match.groupValues.size) {
                val value = match.groupValues[index].trim()
                if (value.isNotEmpty()) tags.add(value)
            }
        }

        val other = runCatching {
            Regex(tags.joinToString("|") + "|[-><)(\\]\\[～]").replace(notSame, "")
        }.getOrElse { notSame }

        tags.replaceAll { tag ->
            var value = Regex("ver(?:sion)?\\.?").replace(tag, "ver")
            value = Regex("伴奏|纯音乐|inst\\.?(?:rumental)|off ?vocal(?: ?[Vv]er.)?").replace(value, "inst")
            value = value.replace("mixed", "mix").replace("edited", "edit")
            value = Regex("(solo|mix|edit|style|size) ver").replace(value, "${'$'}1")
            value = Regex("(?:tv|anime) ?(?:サイズ|size)?(?: ?edit)?(?: ?ver)?").replace(value, "tv size")
            value
        }
        return tags to other
    }

    /** 标题匹配分（0~100） */
    fun calculateTitleScore(title1Raw: String, title2Raw: String): Double {
        val title1 = unifiedSymbol(title1Raw).lowercase()
        val title2 = unifiedSymbol(title2Raw).lowercase()
        if (title1 == title2) return 100.0

        val score0 = maxOf(textDifference(title1, title2), 0.0) * 100

        val sameBegin = StringBuilder()
        for (index in title1.indices) {
            if (title2.length > index && title1[index] == title2[index]) {
                sameBegin.append(title1[index])
            } else {
                break
            }
        }

        val common = sameBegin.toString()
        if (common == title1 || common == title2 || common.isEmpty()) return score0

        val (tags1, other1Raw) = getTags(title1.substring(common.length))
        val (tags2, other2Raw) = getTags(title2.substring(common.length))
        var other1 = other1Raw
        var other2 = other2Raw

        val tag1NoMatch = ArrayList<String>()
        val tag2NoMatch = tags2.toMutableList()
        for (tag1 in tags1) {
            if (tag1 in tag2NoMatch) {
                tag2NoMatch.remove(tag1)
            } else if (Regex("(?:solo|mix|edit|style|size|edit|inst)$").containsMatchIn(tag1)) {
                tag1NoMatch.add(tag1)
            } else if (other2.contains(tag1)) {
                other1 += tag1
            }
        }

        val iterator = tag2NoMatch.iterator()
        while (iterator.hasNext()) {
            val tag2 = iterator.next()
            if (!Regex("(?:solo|mix|edit|style|size|edit|inst)$").containsMatchIn(tag2) && other1.contains(tag2)) {
                other2 += tag2
                iterator.remove()
            }
        }

        val kp = common.length.toDouble() / ((other1.length + other2.length) / 2.0 + common.length)
        val score1 = 100 * kp + maxOf(textDifference(other1, other2), 0.0) * (1 - kp)

        if (tag1NoMatch.isEmpty() && tag2NoMatch.isEmpty()) {
            return maxOf(score1 * 0.7 + 30, score0)
        }

        var score2 = 0.0
        var score3 = 0.0
        if (tag1NoMatch.isNotEmpty() && tag2NoMatch.isNotEmpty()) {
            for (tag1 in tag1NoMatch) {
                score2 += tag2NoMatch.maxOf { textDifference(tag1, it) } * (30 / tag1NoMatch.size)
            }
            for (tag2 in tag2NoMatch) {
                score3 += tag1NoMatch.maxOf { textDifference(it, tag2) } * (30 / tag2NoMatch.size)
            }
        }
        return maxOf(score1 * 0.7 + maxOf(score2, score3), score0)
    }
}

/**
 * difflib.SequenceMatcher 的最小移植（junk = 空格，autojunk 生效）。
 */
internal class SequenceMatcher(private val a: String, private val b: String) {

    private val b2j: MutableMap<Char, MutableList<Int>> = HashMap()

    init {
        b.forEachIndexed { index, ch ->
            if (!isJunk(ch)) b2j.getOrPut(ch) { ArrayList() }.add(index)
        }
        if (b.length >= 200) {
            val ntest = b.length / 100 + 1
            b2j.filterValues { it.size > ntest }.keys.toList().forEach(b2j::remove)
        }
    }

    private fun isJunk(ch: Char) = ch == ' '

    private fun findLongestMatch(alo: Int, ahi: Int, blo: Int, bhi: Int): IntArray {
        var bestI = alo
        var bestJ = blo
        var bestSize = 0
        var j2len = HashMap<Int, Int>()

        for (i in alo until ahi) {
            val newJ2len = HashMap<Int, Int>()
            val positions = b2j[a[i]]
            if (positions != null) {
                for (j in positions) {
                    if (j < blo) continue
                    if (j >= bhi) break
                    val k = (j2len[j - 1] ?: 0) + 1
                    newJ2len[j] = k
                    if (k > bestSize) {
                        bestI = i - k + 1
                        bestJ = j - k + 1
                        bestSize = k
                    }
                }
            }
            j2len = newJ2len
        }

        // 注意：Python 3.11+ 的 difflib 在向两端扩展时不再跳过 junk，
        // 空格也会被算进匹配块（实测 "song name" vs "song  name" 首块 size=5）
        while (bestI > alo && bestJ > blo && a[bestI - 1] == b[bestJ - 1]) {
            bestI--
            bestJ--
            bestSize++
        }
        while (bestI + bestSize < ahi && bestJ + bestSize < bhi &&
            a[bestI + bestSize] == b[bestJ + bestSize]
        ) {
            bestSize++
        }
        return intArrayOf(bestI, bestJ, bestSize)
    }

    private fun matchingBlocks(): List<IntArray> {
        val queue = ArrayDeque<IntArray>()
        queue.add(intArrayOf(0, a.length, 0, b.length))
        val blocks = ArrayList<IntArray>()

        while (queue.isNotEmpty()) {
            val range = queue.removeFirst()
            val alo = range[0]
            val ahi = range[1]
            val blo = range[2]
            val bhi = range[3]
            val match = findLongestMatch(alo, ahi, blo, bhi)
            val i = match[0]
            val j = match[1]
            val k = match[2]
            // 关键：只有找到匹配块时才继续切分，否则范围不变会死循环
            if (k > 0) {
                blocks.add(match)
                if (alo < i && blo < j) queue.add(intArrayOf(alo, i, blo, j))
                if (i + k < ahi && j + k < bhi) queue.add(intArrayOf(i + k, ahi, j + k, bhi))
            }
        }

        blocks.sortWith(compareBy({ it[0] }, { it[1] }))

        val merged = ArrayList<IntArray>()
        var i1 = 0
        var j1 = 0
        var k1 = 0
        for (block in blocks) {
            if (i1 + k1 == block[0] && j1 + k1 == block[1]) {
                k1 += block[2]
            } else {
                if (k1 > 0) merged.add(intArrayOf(i1, j1, k1))
                i1 = block[0]
                j1 = block[1]
                k1 = block[2]
            }
        }
        if (k1 > 0) merged.add(intArrayOf(i1, j1, k1))
        merged.add(intArrayOf(a.length, b.length, 0))
        return merged
    }

    fun ratio(): Double {
        val matches = matchingBlocks().sumOf { it[2] }
        val total = a.length + b.length
        return if (total > 0) 2.0 * matches / total else 1.0
    }
}
