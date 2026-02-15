package com.example.lddc.service.decryptor

import android.util.Base64
import android.util.Log
import com.example.lddc.service.network.ApiException
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * KRC 歌词解码器
 * 参考 Python 实现: LDDC/core/decryptor/__init__.py
 */
object KrcDecoder {
    private const val TAG = "KrcDecoder"

    // KRC 解密密钥: b"@Gaw^2tGQ61-\xce\xd2ni"
    private val KRC_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
    )

    /**
     * 解密 KRC 歌词
     * @param encryptedContent Base64 编码的加密歌词
     * @return 解密后的歌词文本
     */
    fun decrypt(encryptedContent: String): String {
        Log.d(TAG, "Decrypting KRC, input length: ${encryptedContent.length}")
        Log.d(TAG, "Input content first 100 chars: ${encryptedContent.take(100)}")

        return try {
            // Base64 解码
            val encryptedBytes = Base64.decode(encryptedContent, Base64.NO_WRAP)
            Log.d(TAG, "Base64 decoded, byte array size: ${encryptedBytes.size}")
            Log.d(TAG, "First 20 bytes: ${encryptedBytes.take(20).joinToString(" ") { "%02x".format(it) }}")

            // 跳过前 4 个字节
            if (encryptedBytes.size < 5) {
                throw ApiException("KRC 数据太短")
            }
            val dataToDecrypt = encryptedBytes.copyOfRange(4, encryptedBytes.size)
            Log.d(TAG, "Data to decrypt size: ${dataToDecrypt.size}")

            // XOR 解密
            val decryptedData = ByteArray(dataToDecrypt.size)
            for (i in dataToDecrypt.indices) {
                decryptedData[i] = (dataToDecrypt[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
            }
            Log.d(TAG, "XOR decrypted, size: ${decryptedData.size}")

            // Zlib 解压缩
            val decompressed = decompress(decryptedData)
            Log.d(TAG, "Decompressed, size: ${decompressed.size}")

            val result = String(decompressed, Charsets.UTF_8)
            Log.d(TAG, "KRC decrypted successfully, result length: ${result.length}")
            Log.d(TAG, "First 100 chars: ${result.take(100)}")

            result
        } catch (e: Exception) {
            Log.e(TAG, "KRC decryption failed: ${e.message}", e)
            throw ApiException("KRC解密失败: ${e.message}")
        }
    }

    /**
     * Zlib 解压缩
     */
    private fun decompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)

        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)

        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        return outputStream.toByteArray()
    }
}
