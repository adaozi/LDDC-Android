// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.tag

/**
 * TagLib 原生层入口。字符串一律用 UTF-8 字节数组传递，
 * 避免 JNI Modified-UTF8 破坏 emoji 等 4 字节字符。
 */
object TagLibBridge {

    init {
        System.loadLibrary("lddc_tagbridge")
    }

    /**
     * 写入歌词。
     * @param path 文件绝对路径（UTF-8）
     * @param text 歌词全文（通常是带时间轴的 LRC 文本，写入 USLT / LYRICS 字段）
     * @param syltTimes 字级时间戳（毫秒），为空则不写 SYLT
     * @param syltWords 与 syltTimes 一一对应的字/词（UTF-8），可为 null
     * @param id3Version 3 = ID3v2.3，4 = ID3v2.4
     * @return 0=成功 1=格式不支持 2=IO 失败 3=参数错误
     */
    external fun writeLyrics(
        path: ByteArray,
        text: ByteArray,
        syltTimes: IntArray,
        syltWords: Array<ByteArray>?,
        id3Version: Int,
    ): Int

    external fun readLyrics(path: ByteArray): ByteArray?

    external fun hasLyrics(path: ByteArray): Boolean

    /** @return UTF-8 JSON：{"title":..,"artist":..,"album":..,"duration":毫秒} */
    external fun readAudioInfo(path: ByteArray): ByteArray?

    external fun nativeVersion(): String
}
