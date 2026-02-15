package com.example.lddc.service

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import coil.imageLoader
import coil.request.ImageRequest

/**
 * 平台服务 - 负责处理与Android平台相关的操作
 */
class PlatformService(private val context: Context) {

    /**
     * 复制文本到剪贴板
     */
    fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            showToast("已复制到剪贴板")
        } catch (_: Exception) {
            showToast("复制失败")
        }
    }

    /**
     * 显示Toast消息
     */
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 保存图片到相册
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    fun saveImageToGallery(imageName: String): Boolean {
        return try {
            // 这里需要根据实际的图片URL或资源来保存图片
            // 目前先使用默认图片作为示例
            val bitmap = (context.getDrawable(android.R.drawable.ic_media_play) as BitmapDrawable).bitmap

            // 保存图片到相册
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${imageName}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LDDC")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                val outputStream = context.contentResolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从URL保存图片到相册
     */
    suspend fun saveImageFromUrl(imageUrl: String, imageName: String): Boolean {
        return try {
            // 使用Coil下载图片
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()

            val drawable = context.imageLoader.execute(request).drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap

            if (bitmap != null) {
                saveBitmapToGallery(bitmap, imageName)
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 保存Bitmap到相册
     */
    private fun saveBitmapToGallery(bitmap: Bitmap, imageName: String): Boolean {
        return try {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${imageName}_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LDDC")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}