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

    /**
     * 格式化文件大小
     *
     * @param bytes 字节数
     * @return 格式化后的文件大小字符串（如：1.5 MB）
     */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * 格式化比特率
     *
     * @param bitrate 比特率（bps）
     * @return 格式化后的比特率字符串（如：320 kbps）
     */
    fun formatBitrate(bitrate: Int): String {
        return "${bitrate / 1000} kbps"
    }

    /**
     * 格式化采样率
     *
     * @param sampleRate 采样率（Hz）
     * @return 格式化后的采样率字符串（如：44.1 kHz）
     */
    fun formatSampleRate(sampleRate: Int): String {
        return "${sampleRate / 1000} kHz"
    }
}
