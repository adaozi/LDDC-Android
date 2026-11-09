// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.crypto

import java.util.zip.Inflater

/**
 * 酷狗 KRC 歌词解密。
 * 对应上游 LDDC/core/decryptor/__init__.py 的 krc_decrypt：
 *   1. 去掉前 4 字节
 *   2. 与 KRC_KEY 循环异或
 *   3. zlib 解压
 */
object KrcCrypto {

    private val KRC_KEY: ByteArray =
        "@Gaw^2tGQ61-".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0xCE.toByte(), 0xD2.toByte()) +
            "ni".toByteArray(Charsets.UTF_8)

    fun decrypt(data: ByteArray): String {
        require(data.size > 4) { "KRC 数据过短" }
        val body = data.copyOfRange(4, data.size)
        val xored = ByteArray(body.size) { i ->
            (body[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
        }
        return String(inflate(xored), Charsets.UTF_8)
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = java.io.ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buffer, 0, read)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
