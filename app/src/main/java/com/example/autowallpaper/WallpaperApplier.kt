package com.example.autowallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.DisplayMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 将下载好的图片应用为系统壁纸
 */
object WallpaperApplier {

    /**
     * 应用指定图片文件
     * @param lockScreen 是否同时设置锁屏壁纸（Android 7.0+ 支持）
     */
    suspend fun applyFile(context: Context, file: File, lockScreen: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            // 供 finally 统一回收（注意三者可能是同一个对象）
            var bitmap: Bitmap? = null
            var cropped: Bitmap? = null
            var sized: Bitmap? = null
            try {
                val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager

                // 先居中裁剪成屏幕比例，再缩放为屏幕精确尺寸
                // 壁纸尺寸 == 屏幕尺寸，桌面滑动时壁纸不再滚动、也不会显示不全
                // 注：suggestDesiredDimensions 需要系统权限（SET_WALLPAPER_HINTS），第三方应用不可用
                val dm: DisplayMetrics = context.resources.displayMetrics
                val screenW = dm.widthPixels
                val screenH = dm.heightPixels

                // 大图按屏幕尺寸（1.5 倍裕量）采样解码，避免整图解码导致 OOM
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
                val sample = calcInSampleSize(
                    bounds, (screenW * 1.5f).toInt(), (screenH * 1.5f).toInt()
                )

                val bmp = BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return@withContext false
                bitmap = bmp

                val crop = centerCrop(bmp, screenW, screenH)
                cropped = crop

                val size = if (crop.width == screenW && crop.height == screenH) {
                    crop
                } else {
                    Bitmap.createScaledBitmap(crop, screenW, screenH, true)
                }
                sized = size

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+：可以分别设置主屏和锁屏
                    val flags = if (lockScreen) {
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                    wm.setBitmap(size, null, true, flags)
                } else {
                    // Android 7.0 以下：只能设主屏
                    wm.setBitmap(size)
                }
                true
            } catch (e: Throwable) {
                // 覆盖 OOM（OutOfMemoryError）等所有异常；协程取消正常传播
                if (e is CancellationException) throw e
                false
            } finally {
                // setBitmap 内部已完成拷贝，之后再 recycle 不影响已设置的壁纸
                if (sized != null && sized !== cropped && sized !== bitmap) sized.recycle()
                if (cropped != null && cropped !== bitmap) cropped.recycle()
                bitmap?.recycle()
            }
        }

    /**
     * 按目标比例居中裁剪（不缩放，只裁掉多余部分）
     * 尺寸用 min 钳制在源图内，避免浮点误差导致 createBitmap 越界崩溃
     */
    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = targetW.toFloat() / targetH

        return if (srcRatio > dstRatio) {
            // 图片比目标更宽：裁左右
            val newW = (src.height * dstRatio).toInt().coerceAtMost(src.width)
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            // 图片比目标更高：裁上下
            val newH = (src.width / dstRatio).toInt().coerceAtMost(src.height)
            val y = (src.height - newH) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }

    /**
     * 计算解码采样率：保证解码后单边不小于目标尺寸（Android 官方标准算法）
     */
    private fun calcInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val w = options.outWidth
        val h = options.outHeight
        var sample = 1
        if (w > reqW || h > reqH) {
            val halfW = w / 2
            val halfH = h / 2
            while (halfW / sample >= reqW && halfH / sample >= reqH) {
                sample *= 2
            }
        }
        return sample
    }
}
