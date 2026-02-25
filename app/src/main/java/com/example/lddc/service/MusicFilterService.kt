package com.example.lddc.service

/**
 * 音乐筛选服务
 *
 * 负责根据用户设置的筛选条件过滤音乐列表
 * 支持按歌曲名、歌手、专辑、时长、平台等条件筛选
 */
class MusicFilterService {

    /**
     * 将秒数转换为可读的时间格式
     *
     * 转换规则：
     * - 输入："300"（秒）
     * - 输出："5:00"
     *
     * @param duration 时长（秒）
     * @return 格式化后的时长字符串
     */
    fun formatDuration(duration: String): String {
        return try {
            val seconds = duration.toInt()
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "%d:%02d".format(minutes, remainingSeconds)
        } catch (_: NumberFormatException) {
            // 如果无法转换为数字，返回原值
            duration
        }
    }
}
