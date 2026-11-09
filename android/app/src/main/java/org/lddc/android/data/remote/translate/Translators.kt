// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.lddc.android.data.remote.Http
import org.lddc.android.data.settings.AppSettings
import org.lddc.android.data.model.TranslateBackend

/**
 * 翻译后端，对应上游 LDDC/core/api/translate/。
 * 约定：一次传入整批行文本，返回同样长度的译文列表。
 */
interface Translator {
    suspend fun translate(texts: List<String>, targetLang: String): List<String>
}

private val json = Json { ignoreUnknownKeys = true }

/** 上游 lang_map 的取值 */
fun targetLangCode(settings: AppSettings, bingStyle: Boolean): String {
    val code = settings.translateTargetLang
    return when {
        bingStyle -> when (code) {
            "SIMPLIFIED_CHINESE" -> "zh-Hans"
            "TRADITIONAL_CHINESE" -> "zh-Hant"
            "ENGLISH" -> "en"
            "JAPANESE" -> "ja"
            "KOREAN" -> "ko"
            else -> "zh-Hans"
        }

        else -> when (code) {
            "SIMPLIFIED_CHINESE" -> "zh-CN"
            "TRADITIONAL_CHINESE" -> "zh-TW"
            "ENGLISH" -> "en"
            "JAPANESE" -> "ja"
            "KOREAN" -> "ko"
            else -> "zh-CN"
        }
    }
}

/** Bing（edge.microsoft.com），国内可用性较好 */
object BingTranslator : Translator {

    private val headers = listOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0",
        "Content-Type" to "application/json",
        "Accept" to "*/*",
    )

    override suspend fun translate(texts: List<String>, targetLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val body = buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                .toString().toByteArray(Charsets.UTF_8)
            val url = "https://edge.microsoft.com/translate/translatetext?from=&to=$targetLang"
            val response = Http.post(url, body, "application/json", headers)
            Http.requireOk(response, url)
            val array = json.parseToJsonElement(response.text).jsonArray
            array.map { element ->
                element.jsonObject["translations"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
            }
        }
}

/** Google（translate-pa），国内多数网络不可达，仅作备选 */
object GoogleTranslator : Translator {

    private const val API_KEY = "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520"

    override suspend fun translate(texts: List<String>, targetLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val payload = buildJsonArray {
                add(buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                add(kotlinx.serialization.json.JsonPrimitive("auto"))
                add(kotlinx.serialization.json.JsonPrimitive(targetLang))
            }
            val url = "https://translate-pa.googleapis.com/v1/translateHtml"
            val headers = listOf(
                "Content-Type" to "application/json+protobuf",
                "X-Goog-API-Key" to API_KEY,
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/114.0.0.0",
            )
            val response = Http.post(url, payload.toString().toByteArray(Charsets.UTF_8), "application/json", headers)
            Http.requireOk(response, url)
            val array = json.parseToJsonElement(response.text).jsonArray
            array.firstOrNull()?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }
}

/** OpenAI 兼容接口（可填任意 base_url / model，如 DeepSeek、通义、本地 Ollama） */
class OpenAiTranslator(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : Translator {

    override suspend fun translate(texts: List<String>, targetLang: String): List<String> =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val numbered = texts.mapIndexed { index, text -> "$index\t$text" }.joinToString("\n")
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put(
                            "content",
                            "你是歌词翻译器。把用户给出的每一行歌词翻译成「$targetLang」，" +
                                "保持行数与顺序不变，输出格式为「序号<TAB>译文」，不要输出多余解释。",
                        )
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", numbered)
                    })
                })
            }
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val headers = buildList {
                add("Content-Type" to "application/json")
                if (apiKey.isNotBlank()) add("Authorization" to "Bearer $apiKey")
            }
            val response = Http.post(url, payload.toString().toByteArray(Charsets.UTF_8), "application/json", headers)
            Http.requireOk(response, url)

            val content = json.parseToJsonElement(response.text).jsonObject
                .get("choices")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()

            val parsed = LinkedHashMap<Int, String>()
            content.lines().forEach { line ->
                val index = line.substringBefore('\t', "").trim().toIntOrNull() ?: return@forEach
                parsed[index] = line.substringAfter('\t', "").trim()
            }
            texts.indices.map { parsed[it].orEmpty() }
        }
}

object Translators {
    fun create(settings: AppSettings): Translator? = when (settings.translateBackend) {
        TranslateBackend.BING -> BingTranslator
        TranslateBackend.GOOGLE -> GoogleTranslator
        TranslateBackend.OPENAI -> {
            if (settings.openAiBaseUrl.isBlank() || settings.openAiModel.isBlank()) {
                null
            } else {
                OpenAiTranslator(settings.openAiBaseUrl, settings.openAiApiKey, settings.openAiModel)
            }
        }
    }
}
