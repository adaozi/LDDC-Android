// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.lddc.android.data.model.Source
import org.lddc.android.data.remote.LrclibApi
import java.io.IOException

/**
 * 真实网络集成测试（Lrclib）。离线时会跳过而不是报错。
 */
class LrclibLiveTest {

    @Test
    fun `search and fetch lyrics from lrclib`() {
        runBlocking {
            val results = try {
                LrclibApi.searchSongs("夜曲 周杰伦")
            } catch (e: IOException) {
                assumeNoException("网络不可用，跳过", e)
                return@runBlocking
            }
            assertTrue("搜索结果不应为空", results.isNotEmpty())
            assertTrue("结果应带歌词源标识", results.all { it.source == Source.LRCLIB })
            assertTrue("结果应包含标题与歌手", results.any { it.title != null && it.strArtist.isNotEmpty() })

            val target = results.first { it.title != null && it.strArtist.isNotEmpty() && it.album != null && it.duration != null }
            val lyrics = LrclibApi.getLyrics(target)
            assertTrue("应取到原文歌词", lyrics.get("orig").isNotEmpty())
            assertTrue("歌词应有时间戳", lyrics.get("orig").any { it.start != null })
        }
    }
}
