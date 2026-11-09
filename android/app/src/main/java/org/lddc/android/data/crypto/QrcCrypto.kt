// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.crypto

import java.util.zip.Inflater

/**
 * QQ 音乐 QRC 歌词解密。
 * 上游 LDDC/core/decryptor/__init__.py 的 qrc_decrypt：
 *   1. hex 解码
 *   2. 3DES-EDE 解密（QRC_KEY，24 字节 → 标准 DESede/ECB/NoPadding）
 *   3. zlib 解压
 */
object QrcCrypto {

    private val QRC_KEY = "!@#)(*\$%123ZXC!@!@#)(NHL".toByteArray(Charsets.UTF_8)

    fun decryptHex(hex: String): String {
        val encrypted = hexToBytes(hex)
        require(encrypted.size % 8 == 0) { "QRC 密文长度不是 8 的倍数" }

        // QQ 的 QRC 用的是其自有 DES 变体，不能用 JCE 的 DESede
        val compressed = TripleDes.decodeBlocks(encrypted, QRC_KEY)
        return String(inflate(compressed), Charsets.UTF_8)
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

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "非法 hex 字符串" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
