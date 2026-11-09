// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * 把 SAF 选中的目录 URI 还原成真实文件路径。
 *
 * 应用持有 MANAGE_EXTERNAL_STORAGE，因此统一用「真实路径」做扫描/写入，
 * 比 SAF 树遍历简单得多；这里只做 URI → 路径的解析，失败时返回 null 由调用方兜底。
 */
object StoragePath {

    fun fromTreeUri(context: Context, uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return fromPathSegments(uri)
        val parts = docId.split(":", limit = 2)
        val volume = parts[0]
        val relative = parts.getOrElse(1) { "" }
        val base = when {
            volume.equals("primary", ignoreCase = true) -> Environment.getExternalStorageDirectory().absolutePath
            volume.contains("/") -> "/$volume" // 少见：已是绝对路径
            else -> "/storage/$volume"
        }
        val path = if (relative.isBlank()) base else "$base/$relative"
        return path.takeIf { File(it).isDirectory } ?: path
    }

    private fun fromPathSegments(uri: Uri): String? {
        val segments = uri.pathSegments
        val treeIndex = segments.indexOf("tree")
        if (treeIndex < 0 || treeIndex + 1 >= segments.size) return null
        val docId = segments[treeIndex + 1]
        val parts = docId.split(":", limit = 2)
        if (parts.size < 2) return null
        val base = if (parts[0] == "primary") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/${parts[0]}"
        }
        return if (parts[1].isBlank()) base else "$base/${parts[1]}"
    }

    /** 路径太长时只保留尾部，用于卡片里展示 */
    fun shorten(path: String, keep: Int = 34): String {
        if (path.length <= keep) return path
        return "…" + path.takeLast(keep)
    }
}
