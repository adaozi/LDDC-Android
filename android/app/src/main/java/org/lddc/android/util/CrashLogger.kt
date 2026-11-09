// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录：把未捕获异常的堆栈写到文件，供设置页「复制崩溃日志」取用。
 * 只在 debug 包里用于定位问题，release 版可保留（体积可忽略）。
 */
object CrashLogger {

    private const val FILE_NAME = "crash-last.txt"
    private const val TAG = "LDDC"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                Log.e(TAG, "uncaught exception in ${thread.name}", throwable)
                val file = file(appContext)
                file.parentFile?.mkdirs()
                file.writeText(
                    buildString {
                        appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("thread: ${thread.name}")
                        appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / API ${android.os.Build.VERSION.SDK_INT}")
                        appendLine(Log.getStackTraceString(throwable))
                    },
                    Charsets.UTF_8,
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = file(context.applicationContext)
        return if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { file(context.applicationContext).delete() }
    }

    fun path(context: Context): String = file(context.applicationContext).absolutePath

    private fun file(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
}
