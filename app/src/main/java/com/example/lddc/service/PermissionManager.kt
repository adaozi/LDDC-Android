package com.example.lddc.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 权限管理器
 *
 * 处理应用所需的各种权限请求
 */
object PermissionManager {

    /**
     * 获取读取音频文件所需的权限
     *
     * Android 13+ (API 33+): READ_MEDIA_AUDIO
     * Android 12 及以下: READ_EXTERNAL_STORAGE
     */
    fun getAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * 检查是否有读取音频的权限
     */
    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getAudioPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查是否有管理所有文件的权限（Android 11+）
     * 注意：这个权限需要特殊申请，一般不建议使用
     */
    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 获取管理外部存储权限的 Intent（Android 11+）
     * 用户需要在系统设置中手动授予此权限
     */
    fun getManageExternalStorageIntent(): android.content.Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.Intent(
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            )
        } else {
            null
        }
    }

    /**
     * 检查是否需要管理外部存储权限（Android 11+）
     */
    fun needsManageExternalStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

}

