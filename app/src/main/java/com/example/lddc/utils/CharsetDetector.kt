package com.example.lddc.utils

import android.util.Log
import java.nio.charset.Charset

/**
 * 字符编码检测器
 *
 * 检测字节数组的编码格式，支持常见中文编码
 */
object CharsetDetector {

    private const val TAG = "CharsetDetector"

    /**
     * 检测字节数组的编码
     *
     * @param bytes 字节数组
     * @return 检测到的编码，如果无法检测则返回 null
     */
    fun detect(bytes: ByteArray): Charset? {
        // 1. 首先检查是否是 UTF-8
        if (isValidUTF8(bytes)) {
            Log.d(TAG, "检测到 UTF-8 编码")
            return Charsets.UTF_8
        }

        // 2. 检查是否是 GBK/GB2312
        if (isValidGBK(bytes)) {
            Log.d(TAG, "检测到 GBK/GB2312 编码")
            return Charset.forName("GBK")
        }

        // 3. 检查是否是 UTF-16
        if (isValidUTF16(bytes)) {
            Log.d(TAG, "检测到 UTF-16 编码")
            return Charsets.UTF_16
        }

        // 4. 检查是否是 UTF-16LE
        if (isValidUTF16LE(bytes)) {
            Log.d(TAG, "检测到 UTF-16LE 编码")
            return Charsets.UTF_16LE
        }

        // 5. 检查是否是 UTF-16BE
        if (isValidUTF16BE(bytes)) {
            Log.d(TAG, "检测到 UTF-16BE 编码")
            return Charsets.UTF_16BE
        }

        // 6. 检查是否是 ISO-8859-1
        if (isValidISO8859(bytes)) {
            Log.d(TAG, "检测到 ISO-8859-1 编码")
            return Charsets.ISO_8859_1
        }

        Log.w(TAG, "无法检测编码，默认使用 UTF-8")
        return null
    }

    /**
     * 检测字符串的编码并转换为 UTF-8
     *
     * @param input 输入字符串（可能是乱码）
     * @return 转换后的 UTF-8 字符串
     */
    fun convertToUTF8(input: String): String {
        if (input.isEmpty()) return input

        return try {
            // 尝试将字符串转为字节数组再检测编码
            val bytes = input.toByteArray(Charsets.ISO_8859_1)
            val detectedCharset = detect(bytes)

            if (detectedCharset != null && detectedCharset != Charsets.UTF_8) {
                // 使用检测到的编码重新解码
                String(bytes, detectedCharset)
            } else {
                input
            }
        } catch (e: Exception) {
            Log.e(TAG, "编码转换失败", e)
            input
        }
    }

    /**
     * 检测字节数组是否是有效的 UTF-8
     */
    private fun isValidUTF8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF

            // 单字节 ASCII (0x00-0x7F)
            if (b < 0x80) {
                i++
                continue
            }

            // 双字节序列 (110xxxxx 10xxxxxx)
            if (b in 0xC2..0xDF) {
                if (i + 1 >= bytes.size) return false
                if ((bytes[i + 1].toInt() and 0xC0) != 0x80) return false
                i += 2
                continue
            }

            // 三字节序列 (1110xxxx 10xxxxxx 10xxxxxx)
            if (b in 0xE0..0xEF) {
                if (i + 2 >= bytes.size) return false
                if ((bytes[i + 1].toInt() and 0xC0) != 0x80) return false
                if ((bytes[i + 2].toInt() and 0xC0) != 0x80) return false

                // 检查是否是中文字符范围
                val codePoint = ((b and 0x0F) shl 12) or
                        ((bytes[i + 1].toInt() and 0x3F) shl 6) or
                        (bytes[i + 2].toInt() and 0x3F)

                // 中文字符范围：0x4E00-0x9FA5 (CJK Unified Ideographs)
                // 中文标点：0x3000-0x303F

                i += 3
                continue
            }

            // 四字节序列 (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
            if (b in 0xF0..0xF4) {
                if (i + 3 >= bytes.size) return false
                if ((bytes[i + 1].toInt() and 0xC0) != 0x80) return false
                if ((bytes[i + 2].toInt() and 0xC0) != 0x80) return false
                if ((bytes[i + 3].toInt() and 0xC0) != 0x80) return false
                i += 4
                continue
            }

            // 无效的 UTF-8 字节
            return false
        }
        return true
    }

    /**
     * 检测字节数组是否是有效的 GBK/GB2312
     */
    private fun isValidGBK(bytes: ByteArray): Boolean {
        var i = 0
        var validChars = 0
        var totalChars = 0

        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF

            // ASCII 范围
            if (b < 0x80) {
                i++
                continue
            }

            // GBK 双字节字符 (第一个字节 0x81-0xFE，第二个字节 0x40-0xFE)
            if (b in 0x81..0xFE) {
                if (i + 1 >= bytes.size) return false
                val b2 = bytes[i + 1].toInt() and 0xFF

                // GB2312: 0xA1-0xF7, 0xA1-0xFE
                // GBK: 0x81-0xFE, 0x40-0xFE (不包括 0x7F)
                if (b2 in 0x40..0xFE && b2 != 0x7F) {
                    totalChars++
                    // 检查是否是中文字符范围
                    if (b in 0xB0..0xF7 && b2 in 0xA1..0xFE) {
                        validChars++
                    }
                    i += 2
                    continue
                }
                return false
            }

            i++
        }

        // 如果检测到足够多的中文字符，认为是 GBK
        return totalChars > 0 && validChars.toFloat() / totalChars > 0.5f
    }

    /**
     * 检测是否是 UTF-16 (带 BOM)
     */
    private fun isValidUTF16(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        // UTF-16 BOM: FE FF (大端序) 或 FF FE (小端序)
        return (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
                (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
    }

    /**
     * 检测是否是 UTF-16LE
     */
    private fun isValidUTF16LE(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        // UTF-16LE BOM: FF FE
        return bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
    }

    /**
     * 检测是否是 UTF-16BE
     */
    private fun isValidUTF16BE(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        // UTF-16BE BOM: FE FF
        return bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
    }

    /**
     * 检测是否是有效的 ISO-8859-1
     * ISO-8859-1 是单字节编码，所有字节都是有效的
     */
    private fun isValidISO8859(bytes: ByteArray): Boolean {
        // ISO-8859-1 可以编码所有 0x00-0xFF 的字节
        // 这里我们检查是否包含明显不是文本的字节
        var textBytes = 0
        for (b in bytes) {
            val ub = b.toInt() and 0xFF
            // 可打印字符或常见空白字符
            if (ub in 0x20..0x7E || ub in 0xA0..0xFF || ub in listOf(0x09, 0x0A, 0x0D)) {
                textBytes++
            }
        }
        // 如果大部分是文本字符，认为是 ISO-8859-1
        return textBytes.toFloat() / bytes.size > 0.8f
    }

    /**
     * 尝试修复可能的乱码字符串
     *
     * @param input 输入字符串
     * @return 修复后的字符串
     */
    fun fixGarbledText(input: String): String {
        if (input.isEmpty()) return input

        // 检查是否已经包含乱码特征
        if (!containsGarbledChars(input)) {
            return input
        }

        Log.d(TAG, "检测到可能的乱码，尝试修复")
        return convertToUTF8(input)
    }

    /**
     * 检查字符串是否包含乱码特征
     */
    private fun containsGarbledChars(input: String): Boolean {
        // 检查是否包含常见的乱码字符
        val garbledPatterns = listOf(
            "\u00EF\u00BF\u00BD", // UTF-8 替换字符的 ISO-8859-1 表示
            "\u00C3\u00A9",      // é 的 UTF-8 字节被错误解码
            "\u00E2\u0080\u0099", // ' 的 UTF-8 字节被错误解码
        )

        for (pattern in garbledPatterns) {
            if (input.contains(pattern)) {
                return true
            }
        }

        // 检查是否包含大量 Latin-1 补充字符（可能是 UTF-8 被错误解码）
        var latin1SupplementCount = 0
        for (char in input) {
            if (char.code in 0x80..0xFF) {
                latin1SupplementCount++
            }
        }

        // 如果超过 20% 是 Latin-1 补充字符，可能是乱码
        return latin1SupplementCount.toFloat() / input.length > 0.2f
    }
}
