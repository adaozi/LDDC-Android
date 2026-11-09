// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import java.util.concurrent.TimeUnit

/** 一次 HTTP 响应（保留原始字节与响应头，网易云需要读 Set-Cookie） */
class ApiResponse(
    val code: Int,
    val body: ByteArray,
    private val setCookies: List<String>,
) {
    val text: String get() = String(body, Charsets.UTF_8)

    /** 解析 Set-Cookie 里的 key=value */
    fun cookies(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        setCookies.forEach { raw ->
            val pair = raw.substringBefore(';')
            val name = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "").trim()
            if (name.isNotEmpty()) result[name] = value
        }
        return result
    }
}

/**
 * 统一 HTTP 客户端。所有歌词源共用一个 OkHttp 实例（连接池复用）。
 * 上游大量接口使用 Brotli 压缩，这里统一挂 BrotliInterceptor。
 */
object Http {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(BrotliInterceptor)
            .build()
    }

    fun execute(request: Request): ApiResponse {
        val response: Response = client.newCall(request).execute()
        response.use {
            val body = it.body?.bytes() ?: ByteArray(0)
            return ApiResponse(it.code, body, it.headers.values("Set-Cookie"))
        }
    }

    fun get(url: String, headers: List<Pair<String, String>> = emptyList()): ApiResponse {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build())
    }

    fun post(
        url: String,
        body: ByteArray,
        contentType: String = "application/json",
        headers: List<Pair<String, String>> = emptyList(),
    ): ApiResponse {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return execute(builder.build())
    }

    /** 要求 2xx，否则抛 [HttpException] */
    fun requireOk(response: ApiResponse, url: String): ApiResponse {
        if (response.code !in 200..299) throw HttpException(response.code, url)
        return response
    }
}

class HttpException(val statusCode: Int, val url: String) :
    RuntimeException("HTTP $statusCode: $url")
