package com.example.lddc.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 专辑封面加载器
 * 使用 Coil 高效加载专辑封面，支持内存和磁盘缓存
 */
class AlbumArtLoader(private val context: Context) {

    companion object {
        private const val TAG = "AlbumArtLoader"

        // 封面尺寸配置
        const val THUMBNAIL_SIZE = 200  // 列表缩略图
    }

    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }

    /**
     * 加载专辑封面为 Bitmap
     * @param uri 封面 URI（content:// 或 file://）
     * @param size 目标尺寸
     * @return Bitmap 或 null
     */
    suspend fun loadBitmap(
        uri: String?,
        size: Int = THUMBNAIL_SIZE
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uri.isNullOrEmpty()) return@withContext null

        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(false)  // 需要软件位图用于处理
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> {
                    (result.drawable as? BitmapDrawable)?.bitmap
                }

                is ErrorResult -> {
                    Log.w(TAG, "加载封面失败: $uri, ${result.throwable.message}")
                    null
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "加载封面异常: $uri", e)
            null
        }
    }

}

