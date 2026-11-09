// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.crypto

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 eapi 加解密。
 * 对应上游 LDDC/core/decryptor/eapi.py（pyaes AES-ECB + PKCS7 → JCE AES/ECB/PKCS5Padding）
 */
object EapiCrypto {

    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val CACHE_KEY_SECRET = ")(13daqP@ssw0rd~"
    private const val DEVICE_ID_XOR_KEY = "3go8&$8*3*3h0k(2)2"
    private const val SEPARATOR = "-36cd479b6b5-"

    fun aesEncrypt(data: ByteArray, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(data)
    }

    fun aesDecrypt(data: ByteArray, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(data)
    }

    fun md5Hex(data: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(data).joinToString("") { "%02x".format(it) }

    /** 生成 eapi 请求体：params=HEX(密文)，返回 UTF-8 字节 */
    fun eapiParamsEncrypt(path: String, paramsJson: String): ByteArray {
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        val paramsBytes = paramsJson.toByteArray(Charsets.UTF_8)
        val signSource = "nobody".toByteArray() + pathBytes + "use".toByteArray() + paramsBytes + "md5forencrypt".toByteArray()
        val sign = md5Hex(signSource)

        val aesSource = pathBytes + SEPARATOR.toByteArray() + paramsBytes + SEPARATOR.toByteArray() + sign.toByteArray()
        val encrypted = aesEncrypt(aesSource, EAPI_KEY)
        val hex = encrypted.joinToString("") { "%02X".format(it) }
        return "params=$hex".toByteArray(Charsets.UTF_8)
    }

    /** 解密 eapi 响应体为 JSON 字符串 */
    fun eapiResponseDecrypt(cipher: ByteArray): String =
        String(aesDecrypt(cipher, EAPI_KEY), Charsets.UTF_8)

    /** 网易云 cache_key：AES 后用 base64 */
    fun getCacheKey(data: String): String =
        Base64.getEncoder().encodeToString(aesEncrypt(data.toByteArray(Charsets.UTF_8), CACHE_KEY_SECRET))

    /** 匿名登录用户名：deviceId 异或 + md5 再 base64 组合 */
    fun getAnonymousUsername(deviceId: String): String {
        val key = DEVICE_ID_XOR_KEY
        val xored = buildString {
            deviceId.forEachIndexed { i, ch ->
                append((ch.code xor key[i % key.length].code).toChar())
            }
        }
        val digest = MessageDigest.getInstance("MD5").digest(xored.toByteArray(Charsets.UTF_8))
        val inner = Base64.getEncoder().encodeToString(digest)
        val combined = "$deviceId $inner"
        return Base64.getEncoder().encodeToString(combined.toByteArray(Charsets.UTF_8))
    }
}
