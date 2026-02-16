package com.example.lddc.model

import android.net.Uri

/**
 * 本地音乐信息数据类
 *
 * 用于表示设备上的本地音乐文件
 *
 * @param id 媒体库ID
 * @param filePath 文件路径
 * @param fileName 文件名
 * @param title 歌曲标题（从元数据读取）
 * @param artist 艺术家（从元数据读取）
 * @param album 专辑（从元数据读取）
 * @param duration 时长（毫秒）
 * @param fileSize 文件大小（字节）
 * @param hasLyrics 是否已有内嵌歌词
 * @param lyricsPath 独立歌词文件路径（如果有）
 * @param uri 内容URI
 * @param albumArtUri 专辑封面URI
 * @param folderPath 所属文件夹路径
 * @param year 年份
 * @param genre 流派
 * @param bitrate 比特率（bps）
 * @param sampleRate 采样率（Hz）
 * @param trackNumber 音轨号
 * @param composer 作曲家
 * @param dateAdded 添加日期（时间戳）
 * @param dateModified 修改日期（时间戳）
 */
data class LocalMusicInfo(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val fileSize: Long,
    val hasLyrics: Boolean = false,
    val lyricsPath: String? = null,
    val uri: Uri? = null,
    val albumArtUri: Uri? = null,
    val folderPath: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val trackNumber: Int? = null,
    val composer: String? = null,
    val dateAdded: Long? = null,
    val dateModified: Long? = null
) {
    /**
     * 获取用于搜索的关键词
     */
    fun getSearchKeyword(): String {
        return when {
            title.isNotBlank() && artist.isNotBlank() -> "$title $artist"
            title.isNotBlank() -> title
            artist.isNotBlank() -> artist
            else -> fileNameWithoutExtension()
        }
    }

    /**
     * 获取不带扩展名的文件名
     */
    fun fileNameWithoutExtension(): String {
        return fileName.substringBeforeLast(".", fileName)
    }

    /**
     * 获取文件扩展名
     */
    fun fileExtension(): String {
        return fileName.substringAfterLast(".", "").lowercase()
    }
}

/**
 * 本地音乐匹配状态
 */
enum class LocalMusicMatchStatus {
    IDLE,           // 初始状态
    SCANNING,       // 扫描中
    SCANNED,        // 扫描完成
    MATCHING,       // 匹配中
    MATCHED,        // 已匹配
    FAILED,         // 匹配失败
    WRITING,        // 写入中
    WRITTEN,        // 已写入
    ERROR           // 发生错误
}

/**
 * 本地音乐匹配结果
 *
 * @param localMusic 本地音乐信息
 * @param matchedMusic 匹配到的网络音乐（可能为null）
 * @param lyrics 匹配到的歌词（可能为null）
 * @param confidence 匹配置信度（0-1）
 * @param status 匹配状态
 * @param errorMessage 错误信息（如果有）
 * @param lyricsSaved 歌词是否已保存
 */
data class LocalMusicMatchResult(
    val localMusic: LocalMusicInfo,
    val matchedMusic: Music? = null,
    val lyrics: String? = null,
    val confidence: Float = 0f,
    val status: LocalMusicMatchStatus = LocalMusicMatchStatus.IDLE,
    val errorMessage: String? = null,
    val lyricsSaved: Boolean = false
) {
    /**
     * 是否匹配成功
     */
    fun isMatched(): Boolean = status == LocalMusicMatchStatus.MATCHED ||
            status == LocalMusicMatchStatus.WRITTEN

    /**
     * 是否可以写入歌词
     */
    fun canWriteLyrics(): Boolean = status == LocalMusicMatchStatus.MATCHED &&
            !lyrics.isNullOrBlank()
}

/**
 * 歌词写入模式
 */
enum class LyricsWriteMode {
    EMBEDDED,       // 仅写入文件标签
    SEPARATE_FILE,  // 仅保存为独立文件
    BOTH,           // 同时写入标签和保存文件
    AUTO            // 自动选择（优先写入标签，失败则保存文件）
}

/**
 * 歌词写入结果
 *
 * @param success 是否成功
 * @param embeddedSuccess 内嵌歌词写入是否成功
 * @param separateFileSuccess 独立文件保存是否成功
 * @param lyricsPath 歌词文件路径（如果保存为独立文件）
 * @param errorMessage 错误信息
 */
data class LyricsWriteResult(
    val success: Boolean,
    val embeddedSuccess: Boolean = false,
    val separateFileSuccess: Boolean = false,
    val lyricsPath: String? = null,
    val errorMessage: String? = null
)

/**
 * 本地音乐扫描配置
 *
 * @param directories 要扫描的目录列表
 * @param includeSubDirectories 是否包含子目录
 * @param supportedFormats 支持的音频格式
 */
data class LocalMusicScanConfig(
    val directories: List<String> = emptyList(),
    val includeSubDirectories: Boolean = true,
    val supportedFormats: List<String> = listOf(
        "mp3", "flac", "m4a", "ogg", "wma", "wav", "ape", "aac"
    )
)

/**
 * 扫描进度信息
 *
 * @param current 当前扫描数量
 * @param total 总数
 * @param currentPath 当前扫描的路径
 * @param foundMusic 已发现的音乐数量
 * @param threadCount 扫描使用的线程数（并行扫描时）
 * @param performanceLevel 设备性能等级
 */
data class ScanProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentPath: String = "",
    val foundMusic: Int = 0,
    val threadCount: Int = 1,
    val performanceLevel: String = ""
) {
    /**
     * 获取进度百分比
     */
    fun percentage(): Int {
        return if (total > 0) (current * 100 / total) else 0
    }

    /**
     * 是否正在扫描
     */
    fun isScanning(): Boolean = current > 0 && (total == 0 || current < total)
}

/**
 * 匹配进度信息
 *
 * @param current 当前匹配数量
 * @param total 总数
 * @param currentMusic 当前匹配的歌曲名称
 * @param successCount 成功匹配数量
 * @param failedCount 失败数量
 */
data class MatchProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentMusic: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0
) {
    /**
     * 获取进度百分比
     */
    fun percentage(): Int {
        return if (total > 0) (current * 100 / total) else 0
    }

    /**
     * 是否正在匹配
     */
    fun isMatching(): Boolean = current > 0 && current <= total
}
