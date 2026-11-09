// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lddc.android.data.crypto.QrcCrypto

/**
 * QRC 解密移植验证：夹具由上游 LDDC 的 3DES 实现加密（tools/golden/gen_qrc_fixture.py），
 * 这里用 Android 侧的 DESede + zlib 解回来，必须与原文完全一致。
 */
class QrcCryptoTest {

    @Test
    fun `decrypt qrc encrypted by upstream tripledes`() {
        val stream = javaClass.classLoader!!.getResourceAsStream("qrc_fixture.json")
            ?: error("qrc_fixture.json 未找到，请运行 tools/golden/gen_qrc_fixture.py")
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val expected = root["plaintext"]!!.jsonPrimitive.content
        val encrypted = root["encryptedHex"]!!.jsonPrimitive.content

        val actual = QrcCrypto.decryptHex(encrypted)
        assertEquals("QRC 解密结果应与上游加密前完全一致", expected, actual)
    }
}
