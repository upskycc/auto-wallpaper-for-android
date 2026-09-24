package com.example.autowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 将下载好的图片应用为系统壁纸
 */
object WallpaperApplier {

    /**
     * 应用缓存中的图片
     * @param lockScreen 是否同时设置锁屏壁纸（Android 7.0+ 支持）
     */
    suspend fun applyCached(context: Context, lockScreen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "wallpaper/current.jpg")
            if (!file.exists()) return@withContext false
            applyFile(context, file, lockScreen)
        }

    /**
     * 应用指定图片文件
     */
    suspend fun applyFile(context: Context, file: File, lockScreen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@withContext false

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+：可以分别设置主屏和锁屏
                    val flags = if (lockScreen) {
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                    wm.setBitmap(bitmap, null, true, flags)
                } else {
                    // Android 7.0 以下：只能设主屏
                    wm.setBitmap(bitmap)
                }
                true
            } catch (_: SecurityException) {
                // 极少量设备需要权限，一般不会
                false
            }
        }
}
