package com.example.autowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.DisplayMetrics
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

            // 先居中裁剪成屏幕比例，再缩放为屏幕精确尺寸
            // 壁纸尺寸 == 屏幕尺寸，桌面滑动时壁纸不再滚动、也不会显示不全
            // 注：suggestDesiredDimensions 需要系统权限（SET_WALLPAPER_HINTS），第三方应用不可用
            val dm: DisplayMetrics = context.resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels
            val cropped = centerCrop(bitmap, screenW, screenH)
            val sized = if (cropped.width == screenW && cropped.height == screenH) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, screenW, screenH, true)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+：可以分别设置主屏和锁屏
                    val flags = if (lockScreen) {
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                    wm.setBitmap(sized, null, true, flags)
                } else {
                    // Android 7.0 以下：只能设主屏
                    wm.setBitmap(sized)
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                if (sized !== bitmap) sized.recycle()
                if (cropped !== bitmap && cropped !== sized) cropped.recycle()
            }
        }

    /**
     * 按目标比例居中裁剪（不缩放，只裁掉多余部分）
     */
    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = targetW.toFloat() / targetH

        return if (srcRatio > dstRatio) {
            // 图片比目标更宽：裁左右
            val newW = (src.height * dstRatio).toInt()
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            // 图片比目标更高：裁上下
            val newH = (src.width / dstRatio).toInt()
            val y = (src.height - newH) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }
}
