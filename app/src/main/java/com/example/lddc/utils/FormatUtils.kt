package com.example.lddc.utils

import android.annotation.SuppressLint

/**
 * 格式化工具类
 *
 * 提供各种格式化功能，避免代码重复
 */
object FormatUtils {

    /**
     * 格式化时长
     *
     * 支持毫秒和秒两种输入格式
     * - 如果值大于10000，认为是毫秒，转换为秒
     * - 输出格式：mm:ss
     *
     * @param duration 时长（毫秒或秒）
     * @return 格式化后的时长字符串
     */
    @SuppressLint("DefaultLocale")
    fun formatDuration(duration: Long): String {
        return try {
            // 如果大于10000，认为是毫秒，转换为秒
            val totalSeconds = if (duration > 10000) duration / 1000 else duration
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            String.format("%d:%02d", minutes, remainingSeconds)
        } catch (_: Exception) {
            duration.toString()
        }
    }

    /**
     * 格式化时长（字符串版本）
     *
     * @param duration 时长字符串
     * @return 格式化后的时长字符串
     */
    @SuppressLint("DefaultFloat")
    fun formatDuration(duration: String): String {
        return try {
            val durationMs = duration.toLong()
            formatDuration(durationMs)
        } catch (_: NumberFormatException) {
            duration
        }
    }

}
