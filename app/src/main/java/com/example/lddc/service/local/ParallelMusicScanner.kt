package com.example.lddc.service.local

import android.content.Context
import android.util.Log
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.ScanProgress
import com.example.lddc.utils.PerformanceUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 并行音乐扫描器
 *
 * 使用多线程并发处理音乐文件扫描，根据设备性能动态调整线程数
 */
class ParallelMusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "ParallelMusicScanner"
    }

    private val scanner = LocalMusicScanner(context)

    /**
     * 并行扫描所有本地音乐
     *
     * @param progressUpdateInterval 进度更新间隔（每处理多少文件更新一次进度）
     * @return 扫描进度和音乐信息流
     */
    fun scanAllMusicParallel(progressUpdateInterval: Int = 5): Flow<Pair<LocalMusicInfo, ScanProgress>> = flow {
        val startTime = System.currentTimeMillis()

        // 获取最优线程数
        val threadCount = PerformanceUtils.getOptimalThreadCount(context, ioBound = true)
        val batchSize = PerformanceUtils.getOptimalBatchSize(context)

        Log.d(TAG, "开始并行扫描，线程数: $threadCount, 批次大小: $batchSize")

        // 创建自定义线程池
        val executor = Executors.newFixedThreadPool(threadCount)
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            // 第一阶段：收集所有音乐文件URI
            val musicUris = collectAllMusicUris()
            val totalCount = musicUris.size

            if (totalCount == 0) {
                Log.d(TAG, "没有找到音乐文件")
                return@flow
            }

            Log.d(TAG, "找到 $totalCount 个音乐文件，开始并行处理")

            // 第二阶段：并行处理音乐文件
            val processedCount = AtomicInteger(0)
            val foundCount = AtomicInteger(0)
            val progressMutex = Mutex()
            var lastEmittedProgress = 0

            // 使用Channel来收集结果
            val resultChannel = Channel<Pair<LocalMusicInfo, Int>>(Channel.BUFFERED)

            // 启动生产者协程（并行处理）
            val producerJob = CoroutineScope(dispatcher).launch {
                musicUris.chunked(batchSize).forEach { batch ->
                    batch.map { uriInfo ->
                        async {
                            try {
                                val localMusic = processMusicFile(uriInfo)
                                if (localMusic != null) {
                                    val currentProcessed = processedCount.incrementAndGet()
                                    foundCount.incrementAndGet()
                                    resultChannel.send(Pair(localMusic, currentProcessed))
                                } else {
                                    processedCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "处理文件失败: ${uriInfo.id}", e)
                                processedCount.incrementAndGet()
                            }
                        }
                    }.awaitAll()
                }
                resultChannel.close()
            }

            // 消费者：收集结果并发送进度
            for ((localMusic, currentProcessed) in resultChannel) {
                // 控制进度更新频率
                val shouldEmit = progressMutex.withLock {
                    if (currentProcessed - lastEmittedProgress >= progressUpdateInterval ||
                        currentProcessed >= totalCount
                    ) {
                        lastEmittedProgress = currentProcessed
                        true
                    } else {
                        false
                    }
                }

                if (shouldEmit) {
                    val progress = ScanProgress(
                        current = currentProcessed,
                        total = totalCount,
                        currentPath = localMusic.filePath,
                        foundMusic = foundCount.get()
                    )
                    emit(Pair(localMusic, progress))
                }
            }

            producerJob.join()

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "并行扫描完成，共处理 $totalCount 个文件，找到 ${foundCount.get()} 首音乐，耗时 ${elapsedTime}ms")

        } finally {
            executor.shutdown()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 并行扫描指定目录
     *
     * @param directoryPath 目录路径
     * @param includeSubDirs 是否包含子目录
     * @param progressUpdateInterval 进度更新间隔
     * @return 扫描进度和音乐信息流
     */
    fun scanDirectoryParallel(
        directoryPath: String,
        includeSubDirs: Boolean = true,
        progressUpdateInterval: Int = 5
    ): Flow<Pair<LocalMusicInfo, ScanProgress>> = flow {
        val startTime = System.currentTimeMillis()

        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            Log.w(TAG, "目录不存在或不是有效目录: $directoryPath")
            return@flow
        }

        // 获取最优线程数
        val threadCount = PerformanceUtils.getOptimalThreadCount(context, ioBound = true)
        val batchSize = PerformanceUtils.getOptimalBatchSize(context)

        Log.d(TAG, "开始并行扫描目录: $directoryPath, 线程数: $threadCount, 批次大小: $batchSize")

        // 创建自定义线程池
        val executor = Executors.newFixedThreadPool(threadCount)
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            // 第一阶段：收集所有音频文件
            val audioFiles = mutableListOf<File>()
            collectAudioFilesParallel(directory, includeSubDirs, audioFiles)
            val totalCount = audioFiles.size

            if (totalCount == 0) {
                Log.d(TAG, "目录中没有音频文件: $directoryPath")
                return@flow
            }

            Log.d(TAG, "找到 $totalCount 个音频文件，开始并行处理")

            // 第二阶段：并行处理音频文件
            val processedCount = AtomicInteger(0)
            val foundCount = AtomicInteger(0)
            val progressMutex = Mutex()
            var lastEmittedProgress = 0

            // 使用Channel来收集结果
            val resultChannel = Channel<Pair<LocalMusicInfo, Int>>(Channel.BUFFERED)

            // 启动生产者协程（并行处理）
            val producerJob = CoroutineScope(dispatcher).launch {
                audioFiles.chunked(batchSize).forEach { batch ->
                    batch.map { file ->
                        async {
                            try {
                                val localMusic = createLocalMusicFromFile(file)
                                if (localMusic != null) {
                                    val currentProcessed = processedCount.incrementAndGet()
                                    foundCount.incrementAndGet()
                                    resultChannel.send(Pair(localMusic, currentProcessed))
                                } else {
                                    processedCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "处理文件失败: ${file.absolutePath}", e)
                                processedCount.incrementAndGet()
                            }
                        }
                    }.awaitAll()
                }
                resultChannel.close()
            }

            // 消费者：收集结果并发送进度
            for ((localMusic, currentProcessed) in resultChannel) {
                val shouldEmit = progressMutex.withLock {
                    if (currentProcessed - lastEmittedProgress >= progressUpdateInterval ||
                        currentProcessed >= totalCount
                    ) {
                        lastEmittedProgress = currentProcessed
                        true
                    } else {
                        false
                    }
                }

                if (shouldEmit) {
                    val progress = ScanProgress(
                        current = currentProcessed,
                        total = totalCount,
                        currentPath = localMusic.filePath,
                        foundMusic = foundCount.get()
                    )
                    emit(Pair(localMusic, progress))
                }
            }

            producerJob.join()

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "并行扫描目录完成，共处理 $totalCount 个文件，找到 ${foundCount.get()} 首音乐，耗时 ${elapsedTime}ms")

        } finally {
            executor.shutdown()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 收集所有音乐文件URI（单线程，快速）
     */
    private suspend fun collectAllMusicUris(): List<MusicUriInfo> {
        return withContext(Dispatchers.IO) {
            val uriList = mutableListOf<MusicUriInfo>()
            // TODO: 实现从MediaStore收集URI的逻辑
            // 这里简化处理，实际应该查询MediaStore
            uriList
        }
    }

    /**
     * 处理单个音乐文件
     */
    private suspend fun processMusicFile(uriInfo: MusicUriInfo): LocalMusicInfo? {
        return withContext(Dispatchers.IO) {
            // TODO: 实现处理逻辑
            null
        }
    }

    /**
     * 并行收集音频文件
     */
    private suspend fun collectAudioFilesParallel(
        directory: File,
        includeSubDirs: Boolean,
        result: MutableList<File>
    ): Unit = withContext(Dispatchers.IO) {
        val files = directory.listFiles() ?: return@withContext

        // 先处理当前目录的文件
        val audioFiles = files.filter { file ->
            file.isFile && isAudioFile(file)
        }
        result.addAll(audioFiles)

        // 并行处理子目录
        if (includeSubDirs) {
            val subDirs = files.filter { it.isDirectory }
            val jobs: List<Deferred<Unit>> = subDirs.map { subDir ->
                async {
                    collectAudioFilesParallel(subDir, true, result)
                }
            }
            jobs.awaitAll()
        }
    }

    /**
     * 从文件创建LocalMusicInfo
     */
    private fun createLocalMusicFromFile(file: File): LocalMusicInfo? {
        return scanner.getMusicInfo(file.absolutePath)
    }

    /**
     * 检查是否为音频文件
     */
    private fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in LocalMusicScanner.SUPPORTED_FORMATS
    }

    /**
     * 音乐URI信息
     */
    data class MusicUriInfo(
        val id: Long,
        val uri: android.net.Uri,
        val filePath: String? = null
    )
}
