// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.crypto

import java.security.SecureRandom
import java.util.Locale

/**
 * 网易云 deviceId 池（来自上游 LDDC/res/ne_deviceids.py，共 24641 条）。
 * 放在 src/main/resources 里，Android 运行时与 JVM 单测都能通过 classloader 读取。
 */
object DeviceIdProvider {

    private const val RESOURCE = "ne_device_ids.txt"

    private val ids: List<String> by lazy {
        val stream = DeviceIdProvider::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: error("$RESOURCE 未打包")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    private val random = SecureRandom()

    val size: Int get() = ids.size

    fun random(): String = ids[random.nextInt(ids.size)]

    /** 形如 AA:BB:CC:DD:EE:FF 的随机 MAC */
    fun randomMac(): String = (0 until 6)
        .joinToString(":") { String.format(Locale.US, "%02X", random.nextInt(256)) }

    /** 8 位随机大写字母 */
    fun randomUpperLetters(length: Int = 8): String {
        val chars = CharArray(length) { ('A'.code + random.nextInt(26)).toChar() }
        return String(chars)
    }

    /** 32 字节随机数转 64 位十六进制小写 */
    fun randomHex64(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
