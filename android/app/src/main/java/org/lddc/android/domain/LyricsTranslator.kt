// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.domain

import org.lddc.android.data.model.Lyrics
import org.lddc.android.data.model.LyricsLine
import org.lddc.android.data.model.LyricsWord
import org.lddc.android.data.model.TranslateBackend
import org.lddc.android.data.remote.translate.Translators
import org.lddc.android.data.remote.translate.targetLangCode
import org.lddc.android.data.settings.AppSettings

/**
 * 歌词翻译：把 orig 逐行翻译后写回 ts（译文行沿用原文时间轴）。
 */
object LyricsTranslator {

    suspend fun translate(lyrics: Lyrics, settings: AppSettings): Result<Unit> {
        val translator = Translators.create(settings)
            ?: return Result.failure(IllegalStateException("翻译未配置（OpenAI 需要填 base_url 与 model）"))

        val orig = lyrics.get("orig")
        if (orig.isEmpty()) return Result.failure(IllegalStateException("没有原文歌词"))
        if (lyrics.has("ts")) return Result.success(Unit)

        val texts = orig.map { it.text }
        val target = targetLangCode(settings, settings.translateBackend == TranslateBackend.BING)

        return runCatching {
            val translated = ArrayList<String>(texts.size)
            texts.chunked(40).forEach { chunk ->
                translated += translator.translate(chunk, target)
            }
            require(translated.size == texts.size) { "译文行数与原文不一致（${translated.size}/${texts.size}）" }

            lyrics["ts"] = orig.mapIndexed { index, line ->
                LyricsLine(
                    start = line.start,
                    end = line.end,
                    words = listOf(LyricsWord(line.start, line.end, translated[index])),
                )
            }
        }
    }
}
