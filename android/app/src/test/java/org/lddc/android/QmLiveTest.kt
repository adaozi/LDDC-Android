// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.lddc.android.data.model.LyricsType
import org.lddc.android.data.remote.QmApi
import org.lddc.android.data.remote.str
import java.io.IOException

/**
 * QQ 音乐真实接口集成测试：GetSession → 搜索 → 解密 QRC 取逐字歌词。
 */
class QmLiveTest {

    @Test
    fun `search and fetch qrc verbatim lyrics`() {
        runBlocking {
            val songs = try {
                QmApi.searchSongs("夜曲 周杰伦")
            } catch (e: IOException) {
                assumeNoException("网络不可用，跳过", e)
                return@runBlocking
            } catch (e: RuntimeException) {
                e.printStackTrace()
                assumeNoException("QQ音乐接口不可用，跳过: ${e.message}", e)
                return@runBlocking
            }

            assertTrue("应搜到歌曲", songs.isNotEmpty())
            val first = songs.first()
            assertTrue("歌曲应有 id", !first.id.isNullOrEmpty())
            println("QM 首条: ${first.artistTitle()} / ${first.album} / ${first.duration}ms")

            val lyrics = runCatching { QmApi.getLyrics(first) }.getOrElse { error ->
                val raw = QmApi.debugRawLyric(first)
                val summary = raw.keys.joinToString(", ") { key ->
                    val value = raw.str(key)
                    if (value == null) "$key=<obj>" else "$key=${value.length}"
                }
                throw AssertionError("取词失败：${error.message}；原始字段：$summary", error)
            }
            assertTrue("应取到原文歌词", lyrics.get("orig").isNotEmpty())
            println(
                "QM 歌词类型=${lyrics.types["orig"]} 行数=${lyrics.get("orig").size} " +
                    "首行字数=${lyrics.get("orig").first().words.size}",
            )
            assertEquals("QRC 应为逐字", LyricsType.VERBATIM, lyrics.types["orig"])
            assertTrue("首行应有时间戳", lyrics.get("orig").first().start != null)
        }
    }
}
