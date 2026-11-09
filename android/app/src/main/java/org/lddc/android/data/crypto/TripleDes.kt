// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.crypto

/**
 * QQ 音乐专用 3DES（非标准 DES 位置换）。
 * 逐函数移植自上游 LDDC/core/decryptor/tripledes.py
 * （其原始出处：WXRIW/QQMusicDecoder 的 DESHelper.cs）。
 *
 * 注意：这里的位序与标准 DES 不同，不能用 JCE 的 DESede 代替——
 * 用真实 QQ 加密数据验证过，标准实现解不开。
 */
object TripleDes {

    const val ENCRYPT = 1
    const val DECRYPT = 0

    private val SBOX: Array<IntArray> = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    private val KEY_RND_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )

    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5,
        60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )

    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
        25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36,
        46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41,
        49, 35, 28, 31,
    )

    private fun bitnum(a: ByteArray, b: Int, c: Int): Int {
        val index = (b / 32) * 4 + 3 - (b % 32) / 8
        return (((a[index].toInt() and 0xFF) shr (7 - b % 8)) and 1) shl c
    }

    private fun bitnumIntr(a: Int, b: Int, c: Int): Int =
        (((a ushr (31 - b)) and 1) shl c)

    private fun bitnumIntl(a: Int, b: Int, c: Int): Int =
        (((a shl b) and Int.MIN_VALUE) ushr c)

    private fun sboxBit(a: Int): Int = (a and 32) or ((a and 31) shr 1) or ((a and 1) shl 4)

    private fun initialPermutation(input: ByteArray): Pair<Int, Int> {
        val s0 = bitnum(input, 57, 31) or bitnum(input, 49, 30) or bitnum(input, 41, 29) or bitnum(input, 33, 28) or
            bitnum(input, 25, 27) or bitnum(input, 17, 26) or bitnum(input, 9, 25) or bitnum(input, 1, 24) or
            bitnum(input, 59, 23) or bitnum(input, 51, 22) or bitnum(input, 43, 21) or bitnum(input, 35, 20) or
            bitnum(input, 27, 19) or bitnum(input, 19, 18) or bitnum(input, 11, 17) or bitnum(input, 3, 16) or
            bitnum(input, 61, 15) or bitnum(input, 53, 14) or bitnum(input, 45, 13) or bitnum(input, 37, 12) or
            bitnum(input, 29, 11) or bitnum(input, 21, 10) or bitnum(input, 13, 9) or bitnum(input, 5, 8) or
            bitnum(input, 63, 7) or bitnum(input, 55, 6) or bitnum(input, 47, 5) or bitnum(input, 39, 4) or
            bitnum(input, 31, 3) or bitnum(input, 23, 2) or bitnum(input, 15, 1) or bitnum(input, 7, 0)

        val s1 = bitnum(input, 56, 31) or bitnum(input, 48, 30) or bitnum(input, 40, 29) or bitnum(input, 32, 28) or
            bitnum(input, 24, 27) or bitnum(input, 16, 26) or bitnum(input, 8, 25) or bitnum(input, 0, 24) or
            bitnum(input, 58, 23) or bitnum(input, 50, 22) or bitnum(input, 42, 21) or bitnum(input, 34, 20) or
            bitnum(input, 26, 19) or bitnum(input, 18, 18) or bitnum(input, 10, 17) or bitnum(input, 2, 16) or
            bitnum(input, 60, 15) or bitnum(input, 52, 14) or bitnum(input, 44, 13) or bitnum(input, 36, 12) or
            bitnum(input, 28, 11) or bitnum(input, 20, 10) or bitnum(input, 12, 9) or bitnum(input, 4, 8) or
            bitnum(input, 62, 7) or bitnum(input, 54, 6) or bitnum(input, 46, 5) or bitnum(input, 38, 4) or
            bitnum(input, 30, 3) or bitnum(input, 22, 2) or bitnum(input, 14, 1) or bitnum(input, 6, 0)

        return s0 to s1
    }

    private fun inversePermutation(s0: Int, s1: Int): ByteArray {
        val data = ByteArray(8)

        data[3] = (bitnumIntr(s1, 7, 7) or bitnumIntr(s0, 7, 6) or bitnumIntr(s1, 15, 5) or
            bitnumIntr(s0, 15, 4) or bitnumIntr(s1, 23, 3) or bitnumIntr(s0, 23, 2) or
            bitnumIntr(s1, 31, 1) or bitnumIntr(s0, 31, 0)).toByte()

        data[2] = (bitnumIntr(s1, 6, 7) or bitnumIntr(s0, 6, 6) or bitnumIntr(s1, 14, 5) or
            bitnumIntr(s0, 14, 4) or bitnumIntr(s1, 22, 3) or bitnumIntr(s0, 22, 2) or
            bitnumIntr(s1, 30, 1) or bitnumIntr(s0, 30, 0)).toByte()

        data[1] = (bitnumIntr(s1, 5, 7) or bitnumIntr(s0, 5, 6) or bitnumIntr(s1, 13, 5) or
            bitnumIntr(s0, 13, 4) or bitnumIntr(s1, 21, 3) or bitnumIntr(s0, 21, 2) or
            bitnumIntr(s1, 29, 1) or bitnumIntr(s0, 29, 0)).toByte()

        data[0] = (bitnumIntr(s1, 4, 7) or bitnumIntr(s0, 4, 6) or bitnumIntr(s1, 12, 5) or
            bitnumIntr(s0, 12, 4) or bitnumIntr(s1, 20, 3) or bitnumIntr(s0, 20, 2) or
            bitnumIntr(s1, 28, 1) or bitnumIntr(s0, 28, 0)).toByte()

        data[7] = (bitnumIntr(s1, 3, 7) or bitnumIntr(s0, 3, 6) or bitnumIntr(s1, 11, 5) or
            bitnumIntr(s0, 11, 4) or bitnumIntr(s1, 19, 3) or bitnumIntr(s0, 19, 2) or
            bitnumIntr(s1, 27, 1) or bitnumIntr(s0, 27, 0)).toByte()

        data[6] = (bitnumIntr(s1, 2, 7) or bitnumIntr(s0, 2, 6) or bitnumIntr(s1, 10, 5) or
            bitnumIntr(s0, 10, 4) or bitnumIntr(s1, 18, 3) or bitnumIntr(s0, 18, 2) or
            bitnumIntr(s1, 26, 1) or bitnumIntr(s0, 26, 0)).toByte()

        data[5] = (bitnumIntr(s1, 1, 7) or bitnumIntr(s0, 1, 6) or bitnumIntr(s1, 9, 5) or
            bitnumIntr(s0, 9, 4) or bitnumIntr(s1, 17, 3) or bitnumIntr(s0, 17, 2) or
            bitnumIntr(s1, 25, 1) or bitnumIntr(s0, 25, 0)).toByte()

        data[4] = (bitnumIntr(s1, 0, 7) or bitnumIntr(s0, 0, 6) or bitnumIntr(s1, 8, 5) or
            bitnumIntr(s0, 8, 4) or bitnumIntr(s1, 16, 3) or bitnumIntr(s0, 16, 2) or
            bitnumIntr(s1, 24, 1) or bitnumIntr(s0, 24, 0)).toByte()

        return data
    }

    private fun f(state: Int, key: IntArray): Int {
        val t1 = bitnumIntl(state, 31, 0) or ((state and 0xF0000000.toInt()) ushr 1) or bitnumIntl(state, 4, 5) or
            bitnumIntl(state, 3, 6) or ((state and 0x0F000000) ushr 3) or bitnumIntl(state, 8, 11) or
            bitnumIntl(state, 7, 12) or ((state and 0x00F00000) ushr 5) or bitnumIntl(state, 12, 17) or
            bitnumIntl(state, 11, 18) or ((state and 0x000F0000) ushr 7) or bitnumIntl(state, 16, 23)

        val t2 = bitnumIntl(state, 15, 0) or ((state and 0x0000F000) shl 15) or bitnumIntl(state, 20, 5) or
            bitnumIntl(state, 19, 6) or ((state and 0x00000F00) shl 13) or bitnumIntl(state, 24, 11) or
            bitnumIntl(state, 23, 12) or ((state and 0x000000F0) shl 11) or bitnumIntl(state, 28, 17) or
            bitnumIntl(state, 27, 18) or ((state and 0x0000000F) shl 9) or bitnumIntl(state, 0, 23)

        val lrg = intArrayOf(
            (t1 ushr 24) and 0xFF,
            (t1 ushr 16) and 0xFF,
            (t1 ushr 8) and 0xFF,
            (t2 ushr 24) and 0xFF,
            (t2 ushr 16) and 0xFF,
            (t2 ushr 8) and 0xFF,
        )
        for (i in 0 until 6) lrg[i] = lrg[i] xor key[i]

        val out = (SBOX[0][sboxBit(lrg[0] ushr 2)] shl 28) or
            (SBOX[1][sboxBit(((lrg[0] and 0x03) shl 4) or (lrg[1] ushr 4))] shl 24) or
            (SBOX[2][sboxBit(((lrg[1] and 0x0F) shl 2) or (lrg[2] ushr 6))] shl 20) or
            (SBOX[3][sboxBit(lrg[2] and 0x3F)] shl 16) or
            (SBOX[4][sboxBit(lrg[3] ushr 2)] shl 12) or
            (SBOX[5][sboxBit(((lrg[3] and 0x03) shl 4) or (lrg[4] ushr 4))] shl 8) or
            (SBOX[6][sboxBit(((lrg[4] and 0x0F) shl 2) or (lrg[5] ushr 6))] shl 4) or
            SBOX[7][sboxBit(lrg[5] and 0x3F)]

        return bitnumIntl(out, 15, 0) or bitnumIntl(out, 6, 1) or bitnumIntl(out, 19, 2) or
            bitnumIntl(out, 20, 3) or bitnumIntl(out, 28, 4) or bitnumIntl(out, 11, 5) or
            bitnumIntl(out, 27, 6) or bitnumIntl(out, 16, 7) or bitnumIntl(out, 0, 8) or
            bitnumIntl(out, 14, 9) or bitnumIntl(out, 22, 10) or bitnumIntl(out, 25, 11) or
            bitnumIntl(out, 4, 12) or bitnumIntl(out, 17, 13) or bitnumIntl(out, 30, 14) or
            bitnumIntl(out, 9, 15) or bitnumIntl(out, 1, 16) or bitnumIntl(out, 7, 17) or
            bitnumIntl(out, 23, 18) or bitnumIntl(out, 13, 19) or bitnumIntl(out, 31, 20) or
            bitnumIntl(out, 26, 21) or bitnumIntl(out, 2, 22) or bitnumIntl(out, 8, 23) or
            bitnumIntl(out, 18, 24) or bitnumIntl(out, 12, 25) or bitnumIntl(out, 29, 26) or
            bitnumIntl(out, 5, 27) or bitnumIntl(out, 21, 28) or bitnumIntl(out, 10, 29) or
            bitnumIntl(out, 3, 30) or bitnumIntl(out, 24, 31)
    }

    private fun crypt(input: ByteArray, key: Array<IntArray>): ByteArray {
        var (s0, s1) = initialPermutation(input)
        for (idx in 0 until 15) {
            val previous = s1
            s1 = f(s1, key[idx]) xor s0
            s0 = previous
        }
        s0 = f(s1, key[15]) xor s0
        return inversePermutation(s0, s1)
    }

    private fun keySchedule(key: ByteArray, mode: Int): Array<IntArray> {
        val schedule = Array(16) { IntArray(6) }

        var c = 0
        var d = 0
        for (i in 0 until 28) {
            c += bitnum(key, KEY_PERM_C[i], 31 - i)
            d += bitnum(key, KEY_PERM_D[i], 31 - i)
        }

        for (i in 0 until 16) {
            val shift = KEY_RND_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0.toInt()

            val target = if (mode == DECRYPT) 15 - i else i
            for (j in 0 until 6) schedule[target][j] = 0

            for (j in 0 until 24) {
                schedule[target][j / 8] = schedule[target][j / 8] or bitnumIntr(c, KEY_COMPRESSION[j], 7 - (j % 8))
            }
            for (j in 24 until 48) {
                schedule[target][j / 8] = schedule[target][j / 8] or bitnumIntr(d, KEY_COMPRESSION[j] - 27, 7 - (j % 8))
            }
        }
        return schedule
    }

    fun keySetup(key: ByteArray, mode: Int): Array<Array<IntArray>> {
        require(key.size >= 24) { "3DES 密钥至少 24 字节" }
        return if (mode == ENCRYPT) {
            arrayOf(
                keySchedule(key.copyOfRange(0, 24), ENCRYPT),
                keySchedule(key.copyOfRange(8, 24), DECRYPT),
                keySchedule(key.copyOfRange(16, 24), ENCRYPT),
            )
        } else {
            arrayOf(
                keySchedule(key.copyOfRange(16, 24), DECRYPT),
                keySchedule(key.copyOfRange(8, 24), ENCRYPT),
                keySchedule(key.copyOfRange(0, 24), DECRYPT),
            )
        }
    }

    fun tripleDesCrypt(data: ByteArray, keys: Array<Array<IntArray>>): ByteArray {
        var current = data
        for (i in 0 until 3) {
            current = crypt(current, keys[i])
        }
        return current
    }

    /** 按 8 字节分组做 3DES（对应上游 for 循环） */
    fun decodeBlocks(cipher: ByteArray, key: ByteArray): ByteArray {
        require(cipher.size % 8 == 0) { "密文长度必须是 8 的倍数" }
        val schedule = keySetup(key, DECRYPT)
        val out = ByteArray(cipher.size)
        var offset = 0
        while (offset < cipher.size) {
            val block = tripleDesCrypt(cipher.copyOfRange(offset, offset + 8), schedule)
            System.arraycopy(block, 0, out, offset, 8)
            offset += 8
        }
        return out
    }
}
