package com.example.lddc.service.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.lddc.model.LocalMusicInfo
import com.example.lddc.model.ScanProgress
import com.example.lddc.utils.PerformanceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 本地音乐扫描器
 *
 * 负责扫描设备上的本地音乐文件
 */
class LocalMusicScanner(private val context: Context) {

    companion object {
        private const val TAG = "LocalMusicScanner"

        // 与 PC 端 LDDC 保持一致的音频格式支持
        // 参考: LDDC-main/LDDC/core/song_info.py
        val SUPPORTED_FORMATS = listOf(
            // 常见格式
            "mp3", "flac", "m4a", "m4b", "ogg", "oga", "wma", "wav",
            "ape", "aac", "opus", "mp4", "mpc", "mp+", "tta", "wv",
            // 额外格式 (与 PC 端同步)
            "3g2",          // 3GPP2 音频
            "aif", "aiff",  // AIFF 音频
            "dff", "dsf",   // DSD 音频格式
            "mid",          // MIDI
            "ofr", "ofs",   // OptimFROG
            "spx",          // Speex
            "tak"           // TAK 无损音频
        )
    }

    /**
     * 使用 MediaStore API 扫描所有音乐文件
     * 这是推荐的方式，可以获取系统媒体库中的所有音乐
     *
     * 注意：在 Android 10+ (API 29+) 上，DATA 列已被弃用，
     * 但我们仍然可以使用它来获取文件路径，只是不能保证所有文件都能访问。
     * 更好的方式是使用 ContentUris 构建 URI 来访问文件。
     */
    fun scanAllMusic(): Flow<Pair<LocalMusicInfo, ScanProgress>> = flow {
        val contentResolver = context.contentResolver
        var foundCount = 0

        // 查询投影 - 注意：在 Android 10+ 上，DATA 列可能为空或不可靠
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATA,  // 在 Q+ 上可能不可靠，但仍尝试获取
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.BITRATE,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.COMPOSER,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED
            )
        }

        // 选择条件：是音乐文件
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // 排序：按标题排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val totalCount = cursor.count
                Log.d(TAG, "找到 $totalCount 个音乐文件")

                // 获取列索引
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                val bitrateColumn = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val composerColumn = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
                val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                var currentIndex = 0

                while (cursor.moveToNext()) {
                    currentIndex++

                    val id = cursor.getLong(idColumn)
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1
                    val displayName = cursor.getString(displayNameColumn)
                    val title = cursor.getString(titleColumn) ?: displayName
                    val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                    val album = cursor.getString(albumColumn) ?: "未知专辑"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)

                    // 读取扩展信息
                    val year =
                        if (yearColumn != -1) cursor.getInt(yearColumn).takeIf { it > 0 } else null
                    val genre = if (genreColumn != -1) cursor.getString(genreColumn) else null
                    val bitrate = if (bitrateColumn != -1) cursor.getInt(bitrateColumn)
                        .takeIf { it > 0 } else null
                    // SAMPLE_RATE 只在 Android Q+ 可用，使用字符串常量避免编译错误
                    val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val sampleRateColumn = cursor.getColumnIndex("sample_rate")
                        if (sampleRateColumn != -1) cursor.getInt(sampleRateColumn)
                            .takeIf { it > 0 } else null
                    } else null
                    val trackNumber = if (trackColumn != -1) cursor.getInt(trackColumn)
                        .takeIf { it > 0 } else null
                    val composer =
                        if (composerColumn != -1) cursor.getString(composerColumn) else null
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn)
                        .takeIf { it > 0 } else null
                    val dateModified =
                        if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn)
                            .takeIf { it > 0 } else null

                    // 使用 ContentUris 构建内容 URI
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // 尝试获取文件路径
                    val filePath = if (dataColumn != -1) {
                        cursor.getString(dataColumn) ?: ""
                    } else {
                        ""
                    }

                    // 检查文件是否存在（仅在有路径时检查）
                    if (filePath.isNotEmpty() && !File(filePath).exists()) {
                        Log.w(TAG, "文件不存在: $filePath")
                        continue
                    }

                    // 检查是否有歌词
                    val hasLyrics = if (filePath.isNotEmpty()) checkHasLyrics(filePath) else false
                    val lyricsPath = if (filePath.isNotEmpty()) findLyricsFile(filePath) else null

                    // 获取专辑封面 URI（使用 albumId 而不是 audioId）
                    var albumArtUri = if (albumId != -1L) getAlbumArtUri(albumId) else null

                    // 如果 MediaStore 没有专辑封面，尝试从文件本身读取内嵌封面（适用于 WAV 等格式）
                    if (albumArtUri == null && filePath.isNotEmpty()) {
                        val embeddedArt = getEmbeddedAlbumArt(filePath)
                        if (embeddedArt != null) {
                            // 将内嵌封面保存为临时文件并返回 URI
                            albumArtUri = saveEmbeddedArtToCache(filePath, embeddedArt)
                            Log.d(TAG, "从文件内嵌数据读取到专辑封面: $filePath")
                        }
                    }

                    // 如果仍然没有专辑封面，尝试从文件夹获取
                    if (albumArtUri == null && filePath.isNotEmpty()) {
                        albumArtUri = getAlbumArtFromFolder(filePath)
                    }

                    // 获取文件夹路径
                    val folderPath = if (filePath.isNotEmpty()) {
                        File(filePath).parent ?: ""
                    } else {
                        ""
                    }

                    var localMusic = LocalMusicInfo(
                        id = id,
                        filePath = filePath.ifEmpty { contentUri.toString() },
                        fileName = displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        fileSize = size,
                        hasLyrics = hasLyrics,
                        lyricsPath = lyricsPath,
                        uri = contentUri,
                        albumArtUri = albumArtUri,
                        folderPath = folderPath,
                        year = year,
                        genre = genre,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        trackNumber = trackNumber,
                        composer = composer,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )

                    // 对于需要扩展元数据读取的格式，从文件本身读取额外元数据
                    // 参考 PC 端 LDDC 支持的格式
                    if (filePath.isNotEmpty()) {
                        val extension = filePath.substringAfterLast(".", "").lowercase()
                        val needsExtendedMetadata = extension in setOf(
                            // 原有格式
                            "flac", "wav", "ogg", "oga", "ape", "opus", "tta",
                            "mpc", "mp+", "wma", "asf", "aiff", "aif", "wv",
                            // 新增格式 (与 PC 端同步)
                            "3g2",          // 3GPP2
                            "dff", "dsf",   // DSD
                            "mid",          // MIDI
                            "ofr", "ofs",   // OptimFROG
                            "spx",          // Speex
                            "tak"           // TAK
                        )
                        if (needsExtendedMetadata) {
                            // 优先使用 JAudioTagger 读取元数据
                            val jAudioReader = JAudioTaggerMetadataReader()
                            val audioMetadata = jAudioReader.readMetadata(filePath)
                            if (audioMetadata != null) {
                                Log.d(
                                    TAG,
                                    "使用 JAudioTagger 读取到元数据: ${audioMetadata.title} - ${audioMetadata.artist}"
                                )
                                localMusic = audioMetadata.toLocalMusicInfo(localMusic.id).copy(
                                    filePath = localMusic.filePath,
                                    uri = localMusic.uri,
                                    albumArtUri = localMusic.albumArtUri,
                                    folderPath = localMusic.folderPath,
                                    hasLyrics = localMusic.hasLyrics,
                                    lyricsPath = localMusic.lyricsPath
                                )
                            } else {
                                // 回退到旧的实现
                                localMusic = AudioMetadataReader.mergeMetadata(localMusic)
                            }
                        }
                    }

                    foundCount++

                    val progress = ScanProgress(
                        current = currentIndex,
                        total = totalCount,
                        currentPath = filePath.ifEmpty { contentUri.toString() },
                        foundMusic = foundCount,
                        threadCount = 1,
                        performanceLevel = "SINGLE_THREAD"
                    )

                    emit(Pair(localMusic, progress))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描音乐失败", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 并行扫描所有音乐
     *
     * 使用 MediaStore 获取所有音乐文件，然后并行处理元数据读取
     *
     * @param progressUpdateInterval 进度更新间隔
     * @return 扫描进度和音乐信息流
     */
    fun scanAllMusicParallel(
        progressUpdateInterval: Int = 5
    ): Flow<Pair<LocalMusicInfo, ScanProgress>> = flow {
        val startTime = System.currentTimeMillis()
        val contentResolver = context.contentResolver

        // 获取设备性能参数
        val threadCount = PerformanceUtils.getOptimalThreadCount(context, ioBound = true)
        val batchSize = PerformanceUtils.getOptimalBatchSize(context)
        val performanceLevel = PerformanceUtils.getPerformanceLevel(context)

        Log.d(TAG, "开始并行扫描所有音乐")
        Log.d(TAG, "设备性能等级: $performanceLevel, 线程数: $threadCount, 批次大小: $batchSize")

        // 第一阶段：从 MediaStore 收集所有音乐文件信息
        val musicUriList = mutableListOf<MusicUriInfo>()

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA
            )
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else null

                musicUriList.add(MusicUriInfo(id, contentUri, filePath))
            }
        }

        val totalCount = musicUriList.size
        if (totalCount == 0) {
            Log.d(TAG, "没有找到音乐文件")
            return@flow
        }

        Log.d(TAG, "找到 $totalCount 个音乐文件，开始并行处理")

        // 第二阶段：并行处理音乐文件
        val executor = Executors.newFixedThreadPool(threadCount)
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            val processedCount = AtomicInteger(0)
            val foundCount = AtomicInteger(0)
            val progressMutex = Mutex()
            var lastEmittedProgress = 0

            val resultChannel = Channel<Pair<LocalMusicInfo, Int>>(Channel.BUFFERED)

            // 启动生产者协程
            val producerJob = CoroutineScope(dispatcher).launch {
                musicUriList.chunked(batchSize).forEach { batch: List<MusicUriInfo> ->
                    val jobs: List<Deferred<Unit?>> = batch.map { musicInfo: MusicUriInfo ->
                        async {
                            try {
                                val localMusic = createLocalMusicFromUri(musicInfo)
                                val currentProcessed = processedCount.incrementAndGet()

                                if (localMusic != null) {
                                    foundCount.incrementAndGet()
                                    resultChannel.send(Pair(localMusic, currentProcessed))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "处理音乐失败: ${musicInfo.uri}", e)
                                processedCount.incrementAndGet()
                            }
                        } as Deferred<Unit?>
                    }
                    jobs.awaitAll()
                }
                resultChannel.close()
            }

            // 消费者：收集结果
            val allMusicList = mutableListOf<LocalMusicInfo>()
            for ((localMusic, currentProcessed) in resultChannel) {
                allMusicList.add(localMusic)

                progressMutex.withLock {
                    if (currentProcessed - lastEmittedProgress >= progressUpdateInterval ||
                        currentProcessed >= totalCount
                    ) {
                        lastEmittedProgress = currentProcessed
                        true
                    } else {
                        false
                    }
                }

                // 发送进度更新，同时包含当前音乐
                val progress = ScanProgress(
                    current = currentProcessed,
                    total = totalCount,
                    currentPath = localMusic.filePath,
                    foundMusic = foundCount.get(),
                    threadCount = threadCount,
                    performanceLevel = performanceLevel.name
                )
                emit(Pair(localMusic, progress))
            }

            producerJob.join()

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "并行扫描完成，共处理 $totalCount 个文件，找到 ${foundCount.get()} 首音乐")
            Log.d(TAG, "总耗时: ${elapsedTime}ms")

        } finally {
            executor.shutdown()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 从 URI 创建 LocalMusicInfo
     */
    private fun createLocalMusicFromUri(musicInfo: MusicUriInfo): LocalMusicInfo? {
        return try {
            val contentResolver = context.contentResolver
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.GENRE,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.COMPOSER,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
            } else {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.GENRE,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.COMPOSER,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
            }

            contentResolver.query(
                musicInfo.uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val displayNameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    val bitrateColumn = cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
                    val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    val composerColumn = cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER)
                    val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedColumn =
                        cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                    val id = cursor.getLong(idColumn)
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1
                    val displayName = cursor.getString(displayNameColumn)
                    val title = cursor.getString(titleColumn) ?: displayName
                    val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                    val album = cursor.getString(albumColumn) ?: "未知专辑"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val year =
                        if (yearColumn != -1) cursor.getInt(yearColumn).takeIf { it > 0 } else null
                    val genre = if (genreColumn != -1) cursor.getString(genreColumn) else null
                    val bitrate = if (bitrateColumn != -1) cursor.getInt(bitrateColumn)
                        .takeIf { it > 0 } else null
                    val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val sampleRateColumn = cursor.getColumnIndex("sample_rate")
                        if (sampleRateColumn != -1) cursor.getInt(sampleRateColumn)
                            .takeIf { it > 0 } else null
                    } else null
                    val trackNumber = if (trackColumn != -1) cursor.getInt(trackColumn)
                        .takeIf { it > 0 } else null
                    val composer =
                        if (composerColumn != -1) cursor.getString(composerColumn) else null
                    val dateAdded = if (dateAddedColumn != -1) cursor.getLong(dateAddedColumn)
                        .takeIf { it > 0 } else null
                    val dateModified =
                        if (dateModifiedColumn != -1) cursor.getLong(dateModifiedColumn)
                            .takeIf { it > 0 } else null

                    val filePath = musicInfo.filePath ?: ""

                    // 检查文件是否存在
                    if (filePath.isNotEmpty() && !File(filePath).exists()) {
                        return null
                    }

                    val hasLyrics = if (filePath.isNotEmpty()) checkHasLyrics(filePath) else false
                    val lyricsPath = if (filePath.isNotEmpty()) findLyricsFile(filePath) else null

                    var albumArtUri = if (albumId != -1L) getAlbumArtUri(albumId) else null

                    if (albumArtUri == null && filePath.isNotEmpty()) {
                        val embeddedArt = getEmbeddedAlbumArt(filePath)
                        if (embeddedArt != null) {
                            albumArtUri = saveEmbeddedArtToCache(filePath, embeddedArt)
                        }
                    }

                    if (albumArtUri == null && filePath.isNotEmpty()) {
                        albumArtUri = getAlbumArtFromFolder(filePath)
                    }

                    val folderPath = if (filePath.isNotEmpty()) {
                        File(filePath).parent ?: ""
                    } else {
                        ""
                    }

                    var localMusic = LocalMusicInfo(
                        id = id,
                        filePath = filePath.ifEmpty { musicInfo.uri.toString() },
                        fileName = displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        fileSize = size,
                        hasLyrics = hasLyrics,
                        lyricsPath = lyricsPath,
                        uri = musicInfo.uri,
                        albumArtUri = albumArtUri,
                        folderPath = folderPath,
                        year = year,
                        genre = genre,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        trackNumber = trackNumber,
                        composer = composer,
                        dateAdded = dateAdded,
                        dateModified = dateModified
                    )

                    // 扩展元数据读取
                    if (filePath.isNotEmpty()) {
                        val extension = filePath.substringAfterLast(".", "").lowercase()
                        val needsExtendedMetadata = extension in setOf(
                            "flac", "wav", "ogg", "oga", "ape", "opus", "tta",
                            "mpc", "mp+", "wma", "asf", "aiff", "aif", "wv",
                            "3g2", "dff", "dsf", "mid", "ofr", "ofs", "spx", "tak"
                        )
                        if (needsExtendedMetadata) {
                            val jAudioReader = JAudioTaggerMetadataReader()
                            val audioMetadata = jAudioReader.readMetadata(filePath)
                            if (audioMetadata != null) {
                                localMusic = audioMetadata.toLocalMusicInfo(localMusic.id).copy(
                                    filePath = localMusic.filePath,
                                    uri = localMusic.uri,
                                    albumArtUri = localMusic.albumArtUri,
                                    folderPath = localMusic.folderPath,
                                    hasLyrics = localMusic.hasLyrics,
                                    lyricsPath = localMusic.lyricsPath
                                )
                            } else {
                                localMusic = AudioMetadataReader.mergeMetadata(localMusic)
                            }
                        }
                    }

                    return localMusic
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "创建 LocalMusicInfo 失败: ${musicInfo.uri}", e)
            null
        }
    }

    /**
     * 扫描指定目录
     */
    fun scanDirectory(
        directoryPath: String,
        includeSubDirs: Boolean = true
    ): Flow<Pair<LocalMusicInfo, ScanProgress>> = flow {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            return@flow
        }

        // 收集所有音频文件
        val audioFiles = mutableListOf<File>()
        collectAudioFiles(directory, includeSubDirs, audioFiles)

        var foundCount = 0

        audioFiles.forEachIndexed { index, file ->
            val localMusic = createLocalMusicFromFile(file)
            if (localMusic != null) {
                foundCount++

                val progress = ScanProgress(
                    current = index + 1,
                    total = audioFiles.size,
                    currentPath = file.absolutePath,
                    foundMusic = foundCount,
                    threadCount = 1,
                    performanceLevel = "SINGLE_THREAD"
                )

                emit(Pair(localMusic, progress))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 并行扫描指定目录
     *
     * 使用多线程并发处理音乐文件，根据设备性能动态调整线程数
     *
     * @param directoryPath 目录路径
     * @param includeSubDirs 是否包含子目录
     * @param progressUpdateInterval 进度更新间隔（每处理多少文件更新一次进度）
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

        // 获取设备性能参数
        val threadCount = PerformanceUtils.getOptimalThreadCount(context, ioBound = true)
        val batchSize = PerformanceUtils.getOptimalBatchSize(context)
        val performanceLevel = PerformanceUtils.getPerformanceLevel(context)

        Log.d(TAG, "开始并行扫描目录: $directoryPath")
        Log.d(TAG, "设备性能等级: $performanceLevel, 线程数: $threadCount, 批次大小: $batchSize")

        // 第一阶段：收集所有音频文件
        val audioFiles = mutableListOf<File>()
        collectAudioFiles(directory, includeSubDirs, audioFiles)
        val totalCount = audioFiles.size

        if (totalCount == 0) {
            Log.d(TAG, "目录中没有音频文件: $directoryPath")
            return@flow
        }

        Log.d(TAG, "找到 $totalCount 个音频文件，开始并行处理")

        // 创建自定义线程池
        val executor = Executors.newFixedThreadPool(threadCount)
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            // 第二阶段：并行处理音频文件
            val processedCount = AtomicInteger(0)
            val foundCount = AtomicInteger(0)
            val progressMutex = Mutex()
            var lastEmittedProgress = 0

            // 使用Channel来收集结果，避免并发问题
            val resultChannel = Channel<Pair<LocalMusicInfo, Int>>(Channel.BUFFERED)

            // 启动生产者协程（并行处理）
            val producerJob = CoroutineScope(dispatcher).launch {
                audioFiles.chunked(batchSize).forEach { batch ->
                    batch.map { file ->
                        async {
                            try {
                                val localMusic = createLocalMusicFromFile(file)
                                val currentProcessed = processedCount.incrementAndGet()

                                if (localMusic != null) {
                                    foundCount.incrementAndGet()
                                    resultChannel.send(Pair(localMusic, currentProcessed))
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
                        foundMusic = foundCount.get(),
                        threadCount = threadCount,
                        performanceLevel = performanceLevel.name
                    )
                    emit(Pair(localMusic, progress))
                }
            }

            producerJob.join()

            val elapsedTime = System.currentTimeMillis() - startTime
            val avgTimePerFile = if (totalCount > 0) elapsedTime / totalCount else 0
            Log.d(TAG, "并行扫描完成，共处理 $totalCount 个文件，找到 ${foundCount.get()} 首音乐")
            Log.d(TAG, "总耗时: ${elapsedTime}ms, 平均每个文件: ${avgTimePerFile}ms")

        } finally {
            executor.shutdown()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 根据文件路径获取单个音乐信息
     */
    fun getMusicInfo(filePath: String): LocalMusicInfo? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return null
        }

        return createLocalMusicFromFile(file)
    }

    /**
     * 收集目录中的所有音频文件
     */
    private fun collectAudioFiles(
        directory: File,
        includeSubDirs: Boolean,
        result: MutableList<File>
    ) {
        val files = directory.listFiles() ?: return

        for (file in files) {
            when {
                file.isDirectory && includeSubDirs -> {
                    collectAudioFiles(file, includeSubDirs, result)
                }

                file.isFile && isAudioFile(file) -> {
                    result.add(file)
                }
            }
        }
    }

    /**
     * 检查是否为音频文件
     */
    private fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in SUPPORTED_FORMATS
    }

    /**
     * 从文件创建 LocalMusicInfo
     * 优先使用 JAudioTagger 读取元数据，失败时回退到 MediaMetadataRetriever
     */
    private fun createLocalMusicFromFile(file: File): LocalMusicInfo? {
        if (!isAudioFile(file)) {
            return null
        }

        val hasLyrics = checkHasLyrics(file.absolutePath)
        val lyricsPath = findLyricsFile(file.absolutePath)

        // 方法1: 优先使用 JAudioTagger 读取元数据
        try {
            val jAudioReader = JAudioTaggerMetadataReader()
            val audioMetadata = jAudioReader.readMetadata(file.absolutePath)
            if (audioMetadata != null) {
                Log.d(
                    TAG,
                    "JAudioTagger 读取成功: ${audioMetadata.title} - ${audioMetadata.artist}"
                )
                return audioMetadata.toLocalMusicInfo(file.absolutePath.hashCode().toLong()).copy(
                    hasLyrics = hasLyrics,
                    lyricsPath = lyricsPath
                )
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "JAudioTagger 读取失败，回退到 MediaMetadataRetriever: ${file.absolutePath}",
                e
            )
        }

        // 方法2: 使用 MediaMetadataRetriever 作为回退
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val title =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: file.nameWithoutExtension
            val artist =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "未知艺术家"
            val album =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "未知专辑"
            val durationStr =
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0

            LocalMusicInfo(
                id = file.absolutePath.hashCode().toLong(),
                filePath = file.absolutePath,
                fileName = file.name,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                fileSize = file.length(),
                hasLyrics = hasLyrics,
                lyricsPath = lyricsPath
            )
        } catch (e: Exception) {
            // 如果读取元数据失败，使用文件名作为标题
            LocalMusicInfo(
                id = file.absolutePath.hashCode().toLong(),
                filePath = file.absolutePath,
                fileName = file.name,
                title = file.nameWithoutExtension,
                artist = "未知艺术家",
                album = "未知专辑",
                duration = 0,
                fileSize = file.length(),
                hasLyrics = hasLyrics,
                lyricsPath = lyricsPath
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * 检查音乐文件是否已有内嵌歌词
     */
    fun checkHasLyrics(filePath: String): Boolean {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            // 检查是否有歌词元数据
            // METADATA_KEY_HAS_LYRICS 在 API 10 才添加，使用字符串值 "yes" 来判断
            val hasLyrics = retriever.extractMetadata(22) // METADATA_KEY_HAS_LYRICS = 22
            hasLyrics == "yes"
        } catch (e: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    /**
     * 查找同名的歌词文件
     */
    fun findLyricsFile(filePath: String): String? {
        val file = File(filePath)
        val parentDir = file.parentFile ?: return null
        val baseName = file.nameWithoutExtension

        // 支持的歌词文件扩展名
        val lyricsExtensions = listOf("lrc", "txt", "trc")

        for (ext in lyricsExtensions) {
            val lyricsFile = File(parentDir, "$baseName.$ext")
            if (lyricsFile.exists()) {
                return lyricsFile.absolutePath
            }
        }

        return null
    }

    /**
     * 获取专辑封面 URI
     * 使用 MediaStore 的 Albums 表查询专辑封面
     */
    private fun getAlbumArtUri(albumId: Long): Uri? {
        return try {
            // 方法1: 使用内容 URI 的专辑封面
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )

            // 检查 URI 是否有效
            context.contentResolver.openInputStream(albumArtUri)?.close()
            albumArtUri
        } catch (e: Exception) {
            // 如果没有专辑封面，尝试从文件元数据中获取
            null
        }
    }

    /**
     * 从文件路径获取专辑封面
     * 优先使用 JAudioTagger，失败时回退到 MediaMetadataRetriever
     */
    private fun getEmbeddedAlbumArt(filePath: String): ByteArray? {
        if (filePath.isEmpty() || !File(filePath).exists()) return null

        // 方法1: 使用 JAudioTagger 读取（支持更多格式，包括 WAV）
        try {
            val jAudioReader = JAudioTaggerMetadataReader()
            val bitmap = jAudioReader.readAlbumArt(filePath)
            if (bitmap != null) {
                // 将 Bitmap 转换为字节数组
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                Log.d(TAG, "使用 JAudioTagger 成功读取专辑封面: $filePath")
                return stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "JAudioTagger 读取专辑封面失败: $filePath", e)
        }

        // 方法2: 使用 MediaMetadataRetriever 作为回退
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val picture = retriever.embeddedPicture
            if (picture != null) {
                Log.d(TAG, "使用 MediaMetadataRetriever 成功读取专辑封面: $filePath")
            }
            picture
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever 读取专辑封面失败: $filePath", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * 将内嵌专辑封面保存到缓存目录并返回 URI
     */
    private fun saveEmbeddedArtToCache(filePath: String, imageData: ByteArray): Uri? {
        return try {
            File(filePath)
            // 使用文件哈希作为缓存文件名
            val cacheKey = filePath.hashCode().toString()
            val cacheDir = File(context.cacheDir, "album_art")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val cacheFile = File(cacheDir, "$cacheKey.jpg")

            // 如果缓存文件已存在且未过期（24小时内），直接返回
            if (cacheFile.exists() &&
                (System.currentTimeMillis() - cacheFile.lastModified()) < 24 * 60 * 60 * 1000
            ) {
                return Uri.fromFile(cacheFile)
            }

            // 写入缓存文件
            cacheFile.writeBytes(imageData)
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "保存专辑封面到缓存失败: $filePath", e)
            null
        }
    }

    /**
     * 尝试从同目录下的图片文件获取专辑封面
     * 常用于 WAV 等不支持内嵌专辑封面的格式
     */
    private fun getAlbumArtFromFolder(filePath: String): Uri? {
        if (filePath.isEmpty()) return null

        val file = File(filePath)
        val parentDir = file.parentFile ?: return null

        // 常见的专辑封面文件名
        val coverNames = listOf(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "album.jpg", "album.jpeg", "album.png",
            "front.jpg", "front.jpeg", "front.png",
            file.nameWithoutExtension + ".jpg",
            file.nameWithoutExtension + ".jpeg",
            file.nameWithoutExtension + ".png"
        )

        for (coverName in coverNames) {
            val coverFile = File(parentDir, coverName)
            if (coverFile.exists() && coverFile.isFile) {
                return Uri.fromFile(coverFile)
            }
        }

        // 如果没有找到特定名称的图片，尝试查找目录中的第一个图片文件
        val imageExtensions = setOf("jpg", "jpeg", "png", "bmp", "webp")
        parentDir.listFiles()?.firstOrNull { f ->
            f.isFile && f.extension.lowercase() in imageExtensions
        }?.let {
            return Uri.fromFile(it)
        }

        return null
    }

    /**
     * 获取音乐文件总数（用于显示）
     */
    fun getMusicCount(): Int {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            return cursor.count
        }

        return 0
    }

    /**
     * 音乐URI信息数据类
     * 用于并行扫描时存储音乐文件的基本信息
     */
    private data class MusicUriInfo(
        val id: Long,
        val uri: Uri,
        val filePath: String? = null
    )
}
