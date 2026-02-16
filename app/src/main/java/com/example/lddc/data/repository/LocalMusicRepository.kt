package com.example.lddc.data.repository

import android.content.Context
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.LyricsWriteResult
import com.example.lddc.model.ScanProgress
import com.example.lddc.service.local.AudioMetadataReader
import com.example.lddc.service.local.JAudioTaggerLyricsReader
import com.example.lddc.service.local.JAudioTaggerLyricsWriter
import com.example.lddc.service.local.JAudioTaggerMetadataReader
import com.example.lddc.service.local.LocalLyricsWriter
import com.example.lddc.service.local.LocalMusicScanner
import kotlinx.coroutines.flow.Flow

/**
 * 本地音乐仓库接口
 *
 * 定义本地音乐数据的访问接口，遵循Repository模式
 */
interface LocalMusicRepository {

    /**
     * 扫描所有本地音乐
     */
    fun scanAllMusic(): Flow<Pair<LocalMusicInfo, ScanProgress>>

    /**
     * 并行扫描所有本地音乐
     * 使用多线程并发处理，根据设备性能动态调整线程数
     */
    fun scanAllMusicParallel(progressUpdateInterval: Int = 5): Flow<Pair<LocalMusicInfo, ScanProgress>>

    /**
     * 扫描指定目录
     */
    fun scanDirectory(directoryPath: String, includeSubDirs: Boolean = true): Flow<Pair<LocalMusicInfo, ScanProgress>>

    /**
     * 并行扫描指定目录
     * 使用多线程并发处理，根据设备性能动态调整线程数
     */
    fun scanDirectoryParallel(
        directoryPath: String,
        includeSubDirs: Boolean = true,
        progressUpdateInterval: Int = 5
    ): Flow<Pair<LocalMusicInfo, ScanProgress>>

    /**
     * 获取音乐文件总数
     */
    fun getMusicCount(): Int

    /**
     * 检查音乐文件是否有歌词
     */
    fun checkHasLyrics(filePath: String): Boolean

    /**
     * 查找音乐文件对应的歌词文件
     */
    fun findLyricsFile(filePath: String): String?

    /**
     * 从文件读取本地歌词
     */
    suspend fun readLocalLyrics(filePath: String): String?

    /**
     * 写入歌词到音乐文件
     */
    suspend fun writeLyrics(
        filePath: String,
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ): LyricsWriteResult

    /**
     * 读取扩展元数据
     */
    fun readExtendedMetadata(localMusic: LocalMusicInfo): LocalMusicInfo

    /**
     * 获取支持的音频格式
     */
    fun getSupportedFormats(): List<String>
}

/**
 * 本地音乐仓库实现
 *
 * 使用 JAudioTagger 库作为主要音频元数据读取工具
 * 参考 PC 端 mutagen 的实现方式
 */
class LocalMusicRepositoryImpl(
    private val context: Context
) : LocalMusicRepository {

    private val scanner = LocalMusicScanner(context)
    private val lyricsWriter = LocalLyricsWriter(context)

    // JAudioTagger 组件
    private val jAudioTaggerReader = JAudioTaggerMetadataReader()
    private val jAudioTaggerLyricsReader = JAudioTaggerLyricsReader()
    private val jAudioTaggerLyricsWriter = JAudioTaggerLyricsWriter()

    override fun scanAllMusic(): Flow<Pair<LocalMusicInfo, ScanProgress>> {
        return scanner.scanAllMusic()
    }

    override fun scanAllMusicParallel(progressUpdateInterval: Int): Flow<Pair<LocalMusicInfo, ScanProgress>> {
        return scanner.scanAllMusicParallel(progressUpdateInterval)
    }

    override fun scanDirectory(
        directoryPath: String,
        includeSubDirs: Boolean
    ): Flow<Pair<LocalMusicInfo, ScanProgress>> {
        return scanner.scanDirectory(directoryPath, includeSubDirs)
    }

    override fun scanDirectoryParallel(
        directoryPath: String,
        includeSubDirs: Boolean,
        progressUpdateInterval: Int
    ): Flow<Pair<LocalMusicInfo, ScanProgress>> {
        return scanner.scanDirectoryParallel(directoryPath, includeSubDirs, progressUpdateInterval)
    }

    override fun getMusicCount(): Int {
        return scanner.getMusicCount()
    }

    override fun checkHasLyrics(filePath: String): Boolean {
        return scanner.checkHasLyrics(filePath)
    }

    override fun findLyricsFile(filePath: String): String? {
        return scanner.findLyricsFile(filePath)
    }

    override suspend fun readLocalLyrics(filePath: String): String? {
        // 优先使用 JAudioTagger 读取内嵌歌词
        val embeddedLyrics = jAudioTaggerLyricsReader.readLyrics(filePath)
        if (!embeddedLyrics.isNullOrBlank()) {
            return embeddedLyrics
        }

        // 回退到原有的歌词文件读取方式
        return lyricsWriter.readLocalLyrics(filePath)
    }

    override suspend fun writeLyrics(
        filePath: String,
        lyrics: String,
        mode: LyricsWriteMode
    ): LyricsWriteResult {
        // 优先使用 JAudioTagger 写入歌词
        return if (jAudioTaggerLyricsWriter.isSupported(filePath)) {
            jAudioTaggerLyricsWriter.writeLyrics(filePath, lyrics, mode)
        } else {
            // 回退到原有的写入方式
            lyricsWriter.writeLyrics(filePath, lyrics, mode)
        }
    }

    override fun readExtendedMetadata(localMusic: LocalMusicInfo): LocalMusicInfo {
        // 尝试使用 JAudioTagger 读取扩展元数据
        val audioMetadata = jAudioTaggerReader.readMetadata(localMusic.filePath)
        return if (audioMetadata != null) {
            // 使用 JAudioTagger 读取到的数据更新 LocalMusicInfo
            audioMetadata.toLocalMusicInfo(localMusic.id)
        } else {
            // 回退到原有的方式
            AudioMetadataReader.mergeMetadata(localMusic)
        }
    }

    override fun getSupportedFormats(): List<String> {
        return LocalMusicScanner.SUPPORTED_FORMATS
    }
}
