package com.example.lddc.service.crypto

import android.annotation.SuppressLint
import com.example.lddc.service.network.NetworkModule
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 加密模块，提供各种加密和签名功能
 */
object CryptoModule {

    // 网易云音乐EAPI密钥
    private val EAPI_KEY = "e82ckenh8dichen8"

    /**
     * 网易云音乐EAPI参数加密
     */
    fun eapiParamsEncrypt(path: String, params: Map<String, Any>): String {
        // 将参数转换为紧凑JSON字符串（无空格，与Python一致）
        val paramsJson = buildCompactJson(params)

        // 构建签名源 - 与Python保持一致
        val signSrc = "nobody${path}use${paramsJson}md5forencrypt"
        val sign = NetworkModule.md5(signSrc)

        // 构建AES加密源 - 与Python保持一致
        val aesSrc = "${path}-36cd479b6b5-${paramsJson}-36cd479b6b5-${sign}"
        val encryptedData = encryptAES(aesSrc, EAPI_KEY)

        // 转换为十六进制字符串（大写）
        val hexString = encryptedData.joinToString("") { "%02X".format(it) }

        // 返回表单格式
        return "params=$hexString"
    }

    /**
     * 构建紧凑JSON字符串（无空格，与Python的separators=(',', ':')一致）
     */
    private fun buildCompactJson(params: Map<String, Any>): String {
        val entries = params.entries.joinToString(",") { (key, value) ->
            val jsonValue = when (value) {
                is String -> "\"${escapeJson(value)}\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "\"${escapeJson(value.toString())}\""
            }
            "\"${key}\":$jsonValue"
        }
        return "{$entries}"
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * 网易云音乐EAPI响应解密
     */
    fun eapiResponseDecrypt(data: ByteArray): String {
        return decryptAES(data, EAPI_KEY)
    }

    // 设备ID XOR 密钥（与Python一致）
    private val DEVICEID_XOR_KEY = "3go8&$8*3*3h0k(2)2"

    /**
     * 生成网易云音乐匿名用户名
     * 与Python实现保持一致
     */
    fun getAnonimousUsername(deviceId: String): String {
        // XOR 运算
        val xoredChars = mutableListOf<Char>()
        for (i in deviceId.indices) {
            val xorChar = deviceId[i].code xor DEVICEID_XOR_KEY[i % DEVICEID_XOR_KEY.length].code
            xoredChars.add(xorChar.toChar())
        }
        val xoredString = xoredChars.joinToString("")

        // MD5 哈希 - 使用原始字节（与Python的digest()一致）
        val messageDigest = java.security.MessageDigest.getInstance("MD5")
        val md5Digest = messageDigest.digest(xoredString.toByteArray(Charsets.UTF_8))

        // Base64 编码 - 使用Android的Base64（支持API 24+），使用NO_WRAP避免换行
        val combinedStr = "$deviceId ${
            android.util.Base64.encodeToString(
                md5Digest,
                android.util.Base64.NO_WRAP
            )
        }"
        return android.util.Base64.encodeToString(
            combinedStr.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }

    /**
     * AES加密
     */
    @SuppressLint("GetInstance")
    private fun encryptAES(content: String, key: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(content.toByteArray())
    }

    /**
     * AES解密
     */
    @SuppressLint("GetInstance")
    private fun decryptAES(data: ByteArray, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(), "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decryptedData = cipher.doFinal(data)
        return String(decryptedData)
    }

    /**
     * 生成QQ音乐搜索ID
     */
    fun generateQQMusicSearchId(): String {
        val random = Random()
        val part1 = (random.nextInt(20) * 18014398509481984L)
        val part2 = (random.nextInt(4194304) * 4294967296L)
        val part3 = (System.currentTimeMillis() % 86400000L)
        return (part1 + part2 + part3).toString()
    }
}
