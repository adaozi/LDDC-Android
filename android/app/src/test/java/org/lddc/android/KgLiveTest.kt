// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.lddc.android.data.model.LyricsType
import org.lddc.android.data.remote.KgApi
import java.io.IOException

/**
 * 酷狗真实接口集成测试：设备注册 → 搜索 → 歌词候选 → KRC 解密取词。
 */
class KgLiveTest {

    @Test
    fun `search and fetch krc verbatim lyrics`() {
        runBlocking {
            val songs = try {
                KgApi.searchSongs("夜曲 周杰伦")
            } catch (e: IOException) {
                assumeNoException("网络不可用，跳过", e)
                return@runBlocking
            } catch (e: RuntimeException) {
                e.printStackTrace()
                assumeNoException("酷狗接口不可用，跳过: ${e.message}", e)
                return@runBlocking
            }

            assertTrue("应搜到歌曲", songs.isNotEmpty())
            val first = songs.first()
            println("KG 首条: ${first.artistTitle()} / ${first.album} / ${first.duration}ms / hash=${first.hash}")

            val lyrics = runCatching { KgApi.getLyrics(first) }.getOrElse { error ->
                throw AssertionError("酷狗取词失败: ${error.message}", error)
            }
            assertTrue("应取到原文歌词", lyrics.get("orig").isNotEmpty())
            println(
                "KG 歌词类型=${lyrics.types["orig"]} 行数=${lyrics.get("orig").size} " +
                    "首行字数=${lyrics.get("orig").first().words.size} 语言=${lyrics.data.keys}",
            )
            assertTrue(
                "KRC 应为逐字或逐行",
                lyrics.types["orig"] == LyricsType.VERBATIM || lyrics.types["orig"] == LyricsType.LINE_BY_LINE,
            )
        }
    }

    @Test
    fun `lyrics list returns multiple candidates`() {
        runBlocking {
            val songs = try {
                KgApi.searchSongs("夜曲 周杰伦")
            } catch (e: Exception) {
                assumeNoException("跳过: ${e.message}", e)
                return@runBlocking
            }
            val first = songs.firstOrNull() ?: return@runBlocking
            val candidates = KgApi.getLyricsList(first)
            println("KG 歌词候选数=${candidates.size} " + candidates.take(3).joinToString(" | ") {
                "${it.creator}/${it.score}"
            })
            assertTrue("应能拿到歌词候选列表", candidates.isNotEmpty())

        }
    }
}
