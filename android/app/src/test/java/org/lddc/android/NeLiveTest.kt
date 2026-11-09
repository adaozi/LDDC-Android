// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.lddc.android.data.model.LyricsType
import org.lddc.android.data.model.SearchType
import org.lddc.android.data.remote.NeApi
import org.lddc.android.data.remote.obj
import org.lddc.android.data.remote.str
import java.io.IOException

/**
 * 网易云真实接口集成测试：游客登录 → 搜索 → 取词（含逐字 yrc）。
 * 离线或接口风控时跳过，不算失败。
 */
class NeLiveTest {

    @Test
    fun `anonymous login search and fetch lyrics`() {
        runBlocking {
            val songs = try {
                NeApi.searchSongs("夜曲 周杰伦")
            } catch (e: IOException) {
                assumeNoException("网络不可用，跳过", e)
                return@runBlocking
            } catch (e: RuntimeException) {
                assumeNoException("网易云接口不可用（可能被风控），跳过", e)
                return@runBlocking
            }

            assertTrue("应搜到歌曲", songs.isNotEmpty())
            val first = songs.first()
            assertTrue("歌曲应有 id", !first.id.isNullOrEmpty())
            assertTrue("歌曲应有时长", (first.duration ?: 0) > 0)

            val lyrics = NeApi.getLyrics(first)
            assertTrue("应取到原文歌词", lyrics.get("orig").isNotEmpty())
            println("NE 歌词类型=" + lyrics.types["orig"] + " 行数=" + lyrics.get("orig").size +
                " 首行字数=" + lyrics.get("orig").first().words.size)
            assertTrue(
                "原文应为逐字或逐行",
                lyrics.types["orig"] == LyricsType.VERBATIM || lyrics.types["orig"] == LyricsType.LINE_BY_LINE,
            )
            assertTrue("首行应有时间戳", lyrics.get("orig").first().start != null)
        }
    }

    @Test
    fun `inspect raw lyric payload keys`() {
        runBlocking {
            val songs = try {
                NeApi.searchSongs("夜曲 周杰伦")
            } catch (e: Exception) {
                assumeNoException("跳过", e)
                return@runBlocking
            }
            val first = songs.firstOrNull() ?: return@runBlocking
            val raw = NeApi.debugRawLyric(first.id!!)
            val summary = raw.keys.joinToString(", ") { key ->
                val lyric = raw.obj(key)?.str("lyric")
                if (lyric == null) "$key=<obj>" else "$key=${lyric.length}"
            }
            println("NE 原始歌词字段: $summary")
            assertTrue("响应应包含 lrc 字段", raw.containsKey("lrc"))
        }
    }

    @Test
    fun `search albums and playlists`() {        runBlocking {
            val albums = try {
                NeApi.searchSongLists("周杰伦", SearchType.ALBUM)
            } catch (e: Exception) {
                assumeNoException("跳过", e)
                return@runBlocking
            }
            assertTrue("应搜到专辑", albums.isNotEmpty())
            assertTrue("专辑应有封面与作者", albums.first().imgUrl.isNotEmpty())
        }
    }

    @Test
    fun `search short keyword returns results`() {
        runBlocking {
            val results = try {
                NeApi.searchSongs("爱错")
            } catch (e: Exception) {
                assumeNoException("跳过: ${e.message}", e)
                return@runBlocking
            }
            println("NE 关键词「爱错」结果数=${results.size} 首条=${results.firstOrNull()?.artistTitle()}")
            assertTrue("短关键词也应能搜到结果", results.isNotEmpty())
        }
    }
}
