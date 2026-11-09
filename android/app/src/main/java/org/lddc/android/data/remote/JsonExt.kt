// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON 安全取值：缺失 / null / 字面量 "null" 一律返回 null */

internal fun JsonObject.obj(key: String): JsonObject? =
    (this[key] as? JsonObject)?.takeIf { it !is JsonNull }

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.str(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.content?.takeIf { it != "null" }
}

internal fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

internal fun JsonObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()

internal fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()

internal fun JsonArray.objects(): List<JsonObject> = this.mapNotNull { it as? JsonObject }
