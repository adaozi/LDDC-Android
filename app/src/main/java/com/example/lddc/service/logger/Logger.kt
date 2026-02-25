package com.example.lddc.service.logger

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * 日志管理器
 *
 * 负责应用日志的收集、缓存和持久化存储
 * 特性：
 * - 异步写入：使用队列 + 后台线程，避免阻塞主线程
 * - 自动清理：保留最近7天日志，总大小不超过5MB
 * - 双输出：同时输出到 Android Logcat 和本地文件
 */
class Logger private constructor(context: Context) {

    /** 日志队列，用于异步写入 */
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()

    /** 日志时间格式：2024-01-15 14:30:25.123 */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** 日志文件名格式：2024-01-15.log */
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 日志存储目录：/data/data/com.example.lddc/files/logs/ */
    private val logDir: File = File(context.filesDir, "logs").apply { mkdirs() }

    /** 日志线程运行标志 */
    private var isRunning = true

    companion object {
        /** Logcat 标签 */
        private const val TAG = "LDDC"

        /** 最多保留7天的日志文件 */
        private const val MAX_LOG_FILES = 7

        /** 日志队列最大容量，防止内存溢出 */
        private const val MAX_QUEUE_SIZE = 1000

        /** 日志目录最大大小：5MB */
        private const val MAX_LOG_DIR_SIZE = 5 * 1024 * 1024L

        /** 单例实例 */
        @Volatile
        private var instance: Logger? = null

        /**
         * 获取 Logger 单例实例
         */
        fun getInstance(context: Context): Logger {
            return instance ?: synchronized(this) {
                instance ?: Logger(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 快捷方法：输出调试日志
         */
        fun d(message: String, throwable: Throwable? = null) =
            instance?.debug(message, throwable) ?: Log.d(TAG, message, throwable)
    }

    init {
        // 启动后台日志写入线程（守护线程）
        thread(name = "LoggerThread", isDaemon = true) {
            while (isRunning || logQueue.isNotEmpty()) {
                try {
                    val entry = logQueue.poll()
                    if (entry != null) {
                        writeToFile(entry)
                    } else {
                        Thread.sleep(100) // 队列为空时休眠100ms
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Logger thread error", e)
                }
            }
        }

        // 启动时清理旧日志
        cleanupOldLogs()
    }

    /**
     * 日志条目数据类
     *
     * @param level 日志级别
     * @param message 日志内容
     * @param throwable 异常信息（可选）
     * @param timestamp 时间戳（毫秒）
     */
    private data class LogEntry(
        val level: LogLevel,
        val message: String,
        val throwable: Throwable?,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 日志级别枚举
     */
    enum class LogLevel {
        DEBUG,  // 调试信息
        INFO,   // 一般信息
        WARN,   // 警告
        ERROR   // 错误
    }

    /**
     * 将日志写入文件
     *
     * 日志格式：2024-01-15 14:30:25.123 [ERROR] 错误信息\n堆栈跟踪
     */
    private fun writeToFile(entry: LogEntry) {
        try {
            val logFile = File(logDir, "${fileDateFormat.format(Date(entry.timestamp))}.log")
            FileWriter(logFile, true).use { writer ->
                val logLine = buildString {
                    append(dateFormat.format(Date(entry.timestamp)))
                    append(" [${entry.level.name}] ")
                    append(entry.message)
                    if (entry.throwable != null) {
                        append("\n")
                        append(Log.getStackTraceString(entry.throwable))
                    }
                    append("\n")
                }
                writer.append(logLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    /**
     * 添加日志到队列
     *
     * 同时输出到 Android Logcat 和写入队列
     */
    private fun log(level: LogLevel, message: String, throwable: Throwable? = null) {
        // 输出到 Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message, throwable)
            LogLevel.INFO -> Log.i(TAG, message, throwable)
            LogLevel.WARN -> Log.w(TAG, message, throwable)
            LogLevel.ERROR -> Log.e(TAG, message, throwable)
        }

        // 添加到队列（队列满时丢弃新日志）
        if (logQueue.size < MAX_QUEUE_SIZE) {
            logQueue.offer(LogEntry(level, message, throwable))
        }
    }

    /** 输出调试日志 */
    fun debug(message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, message, throwable)
    }

    /** 输出信息日志 */
    fun info(message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, message, throwable)
    }

    /** 输出错误日志 */
    fun error(message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, message, throwable)
    }

    /**
     * 清理旧日志文件
     *
     * 策略：
     * 1. 删除超过7天的旧日志
     * 2. 如果总大小仍超过5MB，继续删除最旧的日志
     */
    private fun cleanupOldLogs() {
        try {
            // 获取所有日志文件并按修改时间排序
            var logFiles = logDir.listFiles { file ->
                file.extension == "log"
            }?.sortedBy { it.lastModified() } ?: return

            // 删除超过7天的日志
            if (logFiles.size > MAX_LOG_FILES) {
                logFiles.take(logFiles.size - MAX_LOG_FILES).forEach { it.delete() }
                // 重新获取文件列表
                logFiles = logDir.listFiles { file ->
                    file.extension == "log"
                }?.sortedBy { it.lastModified() } ?: return
            }

            // 检查总大小，超过5MB则删除最旧的
            var totalSize = logFiles.sumOf { it.length() }
            while (totalSize > MAX_LOG_DIR_SIZE && logFiles.isNotEmpty()) {
                val oldestFile = logFiles.firstOrNull { it.exists() }
                if (oldestFile != null) {
                    totalSize -= oldestFile.length()
                    oldestFile.delete()
                    // 重新获取文件列表
                    logFiles = logDir.listFiles { file ->
                        file.extension == "log"
                    }?.sortedBy { it.lastModified() } ?: break
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old logs", e)
        }
    }
}
