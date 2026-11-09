// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.lddc.android.R

/**
 * 批量匹配的前台服务：保证锁屏/切后台时任务不被系统回收。
 */
class BatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 必须在 onCreate 就进前台：如果只放在 onStartCommand，
     * 出现「启动后立刻 stopService」时系统会判定 startForegroundService 未履行契约，
     * 5 秒后抛 ForegroundServiceDidNotStartInTimeException 直接闪退。
     */
    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForeground(NOTIFICATION_ID, build(this, "正在匹配歌词", "准备中…", 0, 0))
        foregroundStarted = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        foregroundStarted = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lddc_batch"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var foregroundStarted = false

        fun isForegroundStarted(): Boolean = foregroundStarted

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BatchService::class.java))
        }

        fun update(context: Context, title: String, text: String, done: Int, total: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, build(context, title, text, done, total))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatchService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "歌词匹配", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

        private fun build(
            context: Context,
            title: String,
            text: String,
            done: Int,
            total: Int,
        ): Notification {
            ensureChannel(context)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total.coerceAtLeast(1), done, total == 0)
                .build()
        }
    }
}
