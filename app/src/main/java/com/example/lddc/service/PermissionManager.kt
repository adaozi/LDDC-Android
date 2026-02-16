package com.example.lddc.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
     * 获取写入存储权限（Android 10 及以下）
     */
    fun getWriteStoragePermission(): String {
        return Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    /**
     * 检查是否有写入存储权限
     */
    fun hasWriteStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                getWriteStoragePermission()
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11+ 不需要 WRITE_EXTERNAL_STORAGE 权限来写入媒体文件
            true
        }
    }

    /**
     * 检查是否有管理所有文件的权限（Android 11+）
     * 注意：这个权限需要特殊申请，一般不建议使用
     */
    fun hasManageExternalStoragePermission(context: Context): Boolean {
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

    /**
     * 获取所有需要的权限列表
     */
    fun getAllRequiredPermissions(): List<String> {
        return mutableListOf<String>().apply {
            add(getAudioPermission())
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(getWriteStoragePermission())
            }
        }
    }

    /**
     * 检查是否有所需的所有权限
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getAllRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * 权限请求状态
 */
sealed class PermissionState {
    object Initial : PermissionState()
    object Granted : PermissionState()
    object Denied : PermissionState()
    object ShowRationale : PermissionState()
}

/**
 * 音频权限请求 Composable
 *
 * 使用示例:
 * ```
 * val permissionState = rememberAudioPermissionState()
 *
 * when (permissionState.value) {
 *     PermissionState.Granted -> { /* 有权限，可以扫描音乐 */ }
 *     PermissionState.Denied -> { /* 无权限，显示提示 */ }
 *     else -> { /* 请求权限 */ }
 * }
 * ```
 */
@Composable
fun rememberAudioPermissionState(
    onPermissionResult: (Boolean) -> Unit = {}
): androidx.compose.runtime.State<PermissionState> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionState = remember { mutableStateOf<PermissionState>(PermissionState.Initial) }

    // 检查当前权限状态
    LaunchedEffect(Unit) {
        if (PermissionManager.hasAudioPermission(context)) {
            permissionState.value = PermissionState.Granted
            onPermissionResult(true)
        }
    }

    // 权限请求启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState.value = if (isGranted) {
            PermissionState.Granted
        } else {
            PermissionState.Denied
        }
        onPermissionResult(isGranted)
    }

    // 请求权限
    LaunchedEffect(permissionState.value) {
        if (permissionState.value == PermissionState.Initial) {
            permissionLauncher.launch(PermissionManager.getAudioPermission())
        }
    }

    return permissionState
}

/**
 * 多个权限请求 Composable
 */
@Composable
fun rememberMultiplePermissionsState(
    permissions: List<String>,
    onPermissionsResult: (Map<String, Boolean>) -> Unit = {}
): androidx.compose.runtime.State<Map<String, Boolean>> {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 检查已授权的权限
    val initialState = permissions.associateWith { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val permissionsState = remember { mutableStateOf(initialState) }

    // 权限请求启动器
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsState.value = results
        onPermissionsResult(results)
    }

    // 如果有未授权的权限，自动请求
    LaunchedEffect(Unit) {
        val hasAllPermissions = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    return permissionsState
}
