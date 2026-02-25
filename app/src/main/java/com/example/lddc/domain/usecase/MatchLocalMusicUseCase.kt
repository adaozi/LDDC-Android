package com.example.lddc.domain.usecase

import android.util.Log
import com.example.lddc.data.repository.LocalMusicRepository
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.LocalMusicMatchResult
import com.example.lddc.model.LocalMusicMatchStatus
import com.example.lddc.model.LyricsFormat
import com.example.lddc.model.LyricsWriteMode
import com.example.lddc.model.LyricsWriteResult
import com.example.lddc.model.MatchProgress
import com.example.lddc.model.Music
import com.example.lddc.model.ScanProgress
import com.example.lddc.model.Source
import com.example.lddc.service.LyricsConvertOptions
import com.example.lddc.service.LyricsOutputFormat
import com.example.lddc.service.LyricsService
import com.example.lddc.utils.PerformanceUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 匹配进度结果密封类
 */
sealed class MatchProgressResult {
    data class InProgress(val progress: MatchProgress, val results: List<LocalMusicMatchResult>) :
        MatchProgressResult()

    data class Completed(val results: List<LocalMusicMatchResult>, val successCount: Int) :
        MatchProgressResult()
}

/**
 * 本地音乐匹配用例
 *
 * 封装本地音乐扫描、匹配和歌词写入逻辑
 */
class MatchLocalMusicUseCase(
    private val localMusicRepository: LocalMusicRepository,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val getLyricsUseCase: GetLyricsUseCase,
    private val lyricsService: LyricsService
) {
    companion object {
        private const val TAG = "MatchLocalMusicUseCase"
        private const val MIN_CONFIDENCE = 0.6f
    }

    /**
     * 并行扫描所有本地音乐
     * 使用多线程并发处理，根据设备性能动态调整线程数
     *
     * @param progressUpdateInterval 进度更新间隔
     * @return 扫描进度和音乐信息流
     */
    fun scanAllMusicParallel(progressUpdateInterval: Int = 5): Flow<Pair<LocalMusicInfo, ScanProgress>> {
        return localMusicRepository.scanAllMusicParallel(progressUpdateInterval)
    }

    /**
     * 匹配单首本地音乐
     *
     * @param music 本地音乐信息
     * @param saveLyrics 是否保存歌词
     * @param writeMode 歌词写入模式
     * @return 匹配结果
     */
    suspend fun matchSingleMusic(
        music: LocalMusicInfo,
        saveLyrics: Boolean = false,
        writeMode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ): LocalMusicMatchResult {
        return try {
            val keyword = music.getSearchKeyword()
            val searchResult = searchSongsUseCase(keyword, page = 1, sources = listOf(Source.QM))

            searchResult.fold(
                onSuccess = { songs ->
                    if (songs.isNotEmpty()) {
                        val bestMatch = songs.first()
                        val confidence = calculateConfidence(music, bestMatch)

                        // 如果匹配成功且需要保存歌词
                        var lyricsSaved = false
                        if (saveLyrics && confidence >= MIN_CONFIDENCE) {
                            lyricsSaved = saveLyricsToFile(bestMatch, music.filePath, writeMode)
                        }

                        LocalMusicMatchResult(
                            localMusic = music,
                            matchedMusic = bestMatch,
                            status = if (confidence >= MIN_CONFIDENCE) LocalMusicMatchStatus.MATCHED else LocalMusicMatchStatus.FAILED,
                            confidence = confidence,
                            lyricsSaved = lyricsSaved
                        )
                    } else {
                        LocalMusicMatchResult(
                            localMusic = music,
                            status = LocalMusicMatchStatus.FAILED
                        )
                    }
                },
                onFailure = {
                    LocalMusicMatchResult(
                        localMusic = music,
                        status = LocalMusicMatchStatus.ERROR,
                        errorMessage = it.message
                    )
                }
            )
        } catch (e: Exception) {
            LocalMusicMatchResult(
                localMusic = music,
                status = LocalMusicMatchStatus.ERROR,
                errorMessage = e.message
            )
        }
    }

    /**
     * 保存歌词到文件
     *
     * @param music 匹配到的音乐信息
     * @param filePath 本地音乐文件路径
     * @param writeMode 写入模式
     * @return 是否保存成功
     */
    private suspend fun saveLyricsToFile(
        music: Music,
        filePath: String,
        writeMode: LyricsWriteMode
    ): Boolean {
        return try {
            // 获取歌词
            val lyricsResult = getLyricsUseCase(music)

            lyricsResult.fold(
                onSuccess = { lyrics ->
                    // 转换歌词为逐字 LRC 格式
                    val convertedLyrics = lyricsService.convertLyrics(
                        lyrics = lyrics,
                        format = LyricsOutputFormat.LRC,
                        options = LyricsConvertOptions(
                            lyricsFormat = LyricsFormat.VERBATIMLRC
                        )
                    )

                    // 写入转换后的歌词到文件
                    val result =
                        localMusicRepository.writeLyrics(filePath, convertedLyrics, writeMode)
                    result.success
                },
                onFailure = {
                    Log.e(TAG, "获取歌词失败: ${music.title}, ${it.message}")
                    false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "保存歌词失败: ${music.title}", e)
            false
        }
    }

    /**
     * 批量匹配本地音乐（多线程并行处理）
     *
     * @param musicList 本地音乐列表
     * @param saveLyrics 是否保存歌词
     * @param writeMode 歌词写入模式
     * @return 匹配进度流
     */
    fun matchMultipleMusicParallel(
        musicList: List<LocalMusicInfo>,
        saveLyrics: Boolean = false,
        writeMode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ): Flow<MatchProgressResult> = channelFlow {
        if (musicList.isEmpty()) {
            send(MatchProgressResult.Completed(emptyList(), 0))
            return@channelFlow
        }

        // 根据设备性能确定并发数
        val performanceLevel = PerformanceUtils.getPerformanceLevel()
        val threadCount = when (performanceLevel) {
            PerformanceUtils.PerformanceLevel.PREMIUM -> 10
            PerformanceUtils.PerformanceLevel.HIGH -> 8
            PerformanceUtils.PerformanceLevel.MEDIUM -> 5
            PerformanceUtils.PerformanceLevel.LOW -> 3
        }

        Log.d(
            TAG,
            "开始批量匹配: ${musicList.size} 首音乐, 并发数: $threadCount, 保存歌词: $saveLyrics"
        )

        val results = mutableListOf<LocalMusicMatchResult>()
        val mutex = Mutex()
        var successCount = 0
        var failedCount = 0

        // 使用信号量限制并发数
        val semaphore = kotlinx.coroutines.sync.Semaphore(threadCount)

        coroutineScope {
            musicList.mapIndexed { index, music ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val result = matchSingleMusic(music, saveLyrics, writeMode)

                        mutex.withLock {
                            results.add(result)
                            if (result.status == LocalMusicMatchStatus.MATCHED) {
                                successCount++
                            } else {
                                failedCount++
                            }

                            val progress = MatchProgress(
                                current = results.size,
                                total = musicList.size,
                                currentMusic = music.title,
                                successCount = successCount,
                                failedCount = failedCount
                            )

                            // 发送进度更新
                            send(MatchProgressResult.InProgress(progress, results.toList()))
                        }

                        result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "匹配失败: ${music.title}", e)
                        mutex.withLock {
                            failedCount++
                            val errorResult = LocalMusicMatchResult(
                                localMusic = music,
                                status = LocalMusicMatchStatus.ERROR,
                                errorMessage = e.message
                            )
                            results.add(errorResult)

                            val progress = MatchProgress(
                                current = results.size,
                                total = musicList.size,
                                currentMusic = music.title,
                                successCount = successCount,
                                failedCount = failedCount
                            )
                            send(MatchProgressResult.InProgress(progress, results.toList()))
                        }
                        null
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }

        send(MatchProgressResult.Completed(results.toList(), successCount))
    }

    /**
     * 读取本地歌词
     *
     * @param filePath 文件路径
     * @return 歌词内容
     */
    suspend fun readLocalLyrics(filePath: String): String? {
        return localMusicRepository.readLocalLyrics(filePath)
    }

    /**
     * 写入歌词到本地音乐
     *
     * @param filePath 文件路径
     * @param lyrics 歌词内容
     * @param mode 写入模式
     * @return 写入结果
     */
    suspend fun writeLyrics(
        filePath: String,
        lyrics: String,
        mode: LyricsWriteMode = LyricsWriteMode.EMBEDDED
    ): LyricsWriteResult {
        return localMusicRepository.writeLyrics(filePath, lyrics, mode)
    }

    /**
     * 计算匹配置信度
     */
    private fun calculateConfidence(localMusic: LocalMusicInfo, matchedSong: Music): Float {
        var score = 0f
        var totalWeight = 0f

        // 标题匹配
        if (localMusic.title.isNotBlank() && matchedSong.title.isNotBlank()) {
            val titleSimilarity = calculateSimilarity(
                localMusic.title.lowercase(),
                matchedSong.title.lowercase()
            )
            score += titleSimilarity * 0.5f
            totalWeight += 0.5f
        }

        // 艺术家匹配
        if (localMusic.artist.isNotBlank() && matchedSong.artist.isNotBlank()) {
            val artistSimilarity = calculateSimilarity(
                localMusic.artist.lowercase(),
                matchedSong.artist.lowercase()
            )
            score += artistSimilarity * 0.3f
            totalWeight += 0.3f
        }

        // 专辑匹配
        if (localMusic.album.isNotBlank() && matchedSong.album.isNotBlank()) {
            val albumSimilarity = calculateSimilarity(
                localMusic.album.lowercase(),
                matchedSong.album.lowercase()
            )
            score += albumSimilarity * 0.2f
            totalWeight += 0.2f
        }

        return if (totalWeight > 0) score / totalWeight else 0f
    }

    /**
     * 计算字符串相似度（Levenshtein 距离）
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val maxLength = maxOf(s1.length, s2.length)
        if (maxLength == 0) return 1f

        val distance = levenshteinDistance(s1, s2)
        return 1f - distance.toFloat() / maxLength
    }

    /**
     * Levenshtein 距离算法
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }

        return dp[m][n]
    }
}
