// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lddc.android.domain.MatchAlgorithm

/**
 * 匹配分数一致性：基准由上游 algorithm.py 生成（tools/golden/gen_match_scores.py）。
 * 容差 0.5 分（满分 100），覆盖 difflib 实现细节带来的极小差异。
 */
class MatchScoreGoldenTest {

    private val tolerance = 0.5

    @Test
    fun `title and artist scores match upstream`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("match_scores.json")
            ?: error("match_scores.json 未找到，请运行 tools/golden/gen_match_scores.py")
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject

        val failures = ArrayList<String>()

        root["titles"]!!.jsonArray.forEach { element ->
            val item = element.jsonObject
            val a = item["a"]!!.jsonPrimitive.content
            val b = item["b"]!!.jsonPrimitive.content
            val expected = item["score"]!!.jsonPrimitive.content.toDouble()
            val actual = MatchAlgorithm.calculateTitleScore(a, b)
            if (kotlin.math.abs(actual - expected) > tolerance) {
                failures.add("title($a, $b): 期望 $expected 实际 $actual")
            }
        }

        root["artists"]!!.jsonArray.forEach { element ->
            val item = element.jsonObject
            val a = item["a"]!!.jsonPrimitive.content
            val b = item["b"]!!.jsonPrimitive.content
            val expected = item["score"]!!.jsonPrimitive.content.toDouble()
            val actual = MatchAlgorithm.calculateArtistScore(a, b)
            if (kotlin.math.abs(actual - expected) > tolerance) {
                failures.add("artist($a, $b): 期望 $expected 实际 $actual")
            }
        }

        assertTrue("与上游评分不一致：\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `text difference behaves like SequenceMatcher`() {
        // 与 Python difflib 的已知值对照
        assertTrue(MatchAlgorithm.textDifference("abc", "abc") == 1.0)
        assertTrue(MatchAlgorithm.textDifference("", "") == 1.0)
        assertTrue(MatchAlgorithm.textDifference("abc", "") == 0.0)
        val ratio = MatchAlgorithm.textDifference("abcd", "abce")
        assertTrue("abcd/abce 相似度应约 0.75，实际 $ratio", kotlin.math.abs(ratio - 0.75) < 0.01)
    }
}
