package com.example.lddc.utils

import android.app.ActivityManager
import android.content.Context
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 性能工具类
 *
 * 检测设备性能并动态调整线程数
 */
object PerformanceUtils {

    /**
     * 设备性能等级
     */
    enum class PerformanceLevel {
        LOW,      // 低端设备
        MEDIUM,   // 中端设备
        HIGH,     // 高端设备
        PREMIUM   // 旗舰设备
    }

    /**
     * 获取设备性能等级（需要 Context）
     */
    fun getPerformanceLevel(context: Context): PerformanceLevel {
        val memoryInfo = getMemoryInfo(context)
        val cpuCores = Runtime.getRuntime().availableProcessors()

        return when {
            memoryInfo.totalMemory >= 8 * 1024 * 1024 * 1024L && cpuCores >= 8 -> PerformanceLevel.PREMIUM
            memoryInfo.totalMemory >= 6 * 1024 * 1024 * 1024L && cpuCores >= 6 -> PerformanceLevel.HIGH
            memoryInfo.totalMemory >= 4 * 1024 * 1024 * 1024L && cpuCores >= 4 -> PerformanceLevel.MEDIUM
            else -> PerformanceLevel.LOW
        }
    }

    /**
     * 获取设备性能等级（无需 Context，仅基于 CPU 核心数）
     */
    fun getPerformanceLevel(): PerformanceLevel {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val totalMemory = getTotalMemory()

        return when {
            totalMemory >= 8 * 1024 * 1024 * 1024L && cpuCores >= 8 -> PerformanceLevel.PREMIUM
            totalMemory >= 6 * 1024 * 1024 * 1024L && cpuCores >= 6 -> PerformanceLevel.HIGH
            totalMemory >= 4 * 1024 * 1024 * 1024L && cpuCores >= 4 -> PerformanceLevel.MEDIUM
            else -> PerformanceLevel.LOW
        }
    }

    /**
     * 获取内存信息
     */
    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        return MemoryInfo(
            totalMemory = getTotalMemory(),
            availableMemory = memoryInfo.availMem,
            isLowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold
        )
    }

    /**
     * 获取总内存（通过读取 /proc/meminfo）
     */
    internal fun getTotalMemory(): Long {
        return try {
            val reader = File("/proc/meminfo").bufferedReader()
            val line = reader.readLine()
            reader.close()

            // MemTotal: 12345678 kB
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                parts[1].toLong() * 1024 // 转换为字节
            } else {
                4L * 1024 * 1024 * 1024 // 默认4GB
            }
        } catch (e: Exception) {
            4L * 1024 * 1024 * 1024 // 默认4GB
        }
    }

    /**
     * 获取CPU核心数
     */
    fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    /**
     * 计算最佳线程数
     *
     * @param context 上下文
     * @param ioBound 是否为IO密集型任务（默认为true）
     * @return 推荐的线程数
     */
    fun getOptimalThreadCount(context: Context, ioBound: Boolean = true): Int {
        getCpuCores()
        val memoryInfo = getMemoryInfo(context)
        val performanceLevel = getPerformanceLevel(context)

        // 基础线程数
        val baseThreads = when (performanceLevel) {
            PerformanceLevel.LOW -> 2
            PerformanceLevel.MEDIUM -> 4
            PerformanceLevel.HIGH -> 6
            PerformanceLevel.PREMIUM -> 8
        }

        // 根据可用内存调整
        val memoryFactor = when {
            memoryInfo.availableMemory < 512 * 1024 * 1024L -> 0.5 // 可用内存小于512MB，减半
            memoryInfo.availableMemory < 1 * 1024 * 1024 * 1024L -> 0.75 // 可用内存小于1GB，减至75%
            else -> 1.0
        }

        // IO密集型任务可以使用更多线程
        val ioMultiplier = if (ioBound) 2 else 1

        // 计算最终线程数
        val threadCount = (baseThreads * memoryFactor * ioMultiplier).toInt()

        // 限制范围：最少2个，最多16个
        return max(2, min(16, threadCount))
    }

    /**
     * 获取批量处理的最佳批次大小
     */
    fun getOptimalBatchSize(context: Context): Int {
        val performanceLevel = getPerformanceLevel(context)
        return when (performanceLevel) {
            PerformanceLevel.LOW -> 10
            PerformanceLevel.MEDIUM -> 20
            PerformanceLevel.HIGH -> 50
            PerformanceLevel.PREMIUM -> 100
        }
    }

    /**
     * 内存信息数据类
     */
    data class MemoryInfo(
        val totalMemory: Long,        // 总内存（字节）
        val availableMemory: Long,    // 可用内存（字节）
        val isLowMemory: Boolean,     // 是否低内存状态
        val threshold: Long           // 内存阈值
    )
}
