package com.example.autowallpaper

import android.content.Context

/**
 * 换壁纸的统一入口：获取 -> 下载 -> 应用
 * 解锁接收器 / Worker / 磁贴 都调用这里
 */
object WallpaperChanger {

    /**
     * 执行一次完整的换壁纸流程
     * 开关判断由调用方负责（解锁接收器查 enabled，Worker 查 periodicEnabled）
     * @return 是否成功
     */
    suspend fun change(context: Context): Boolean {
        val prefs = PrefsManager(context)

        val imageUrl = WallpaperFetcher.fetchImageUrl(context, prefs) ?: return false

        // 避免连续换同一张
        if (imageUrl == prefs.lastImageUrl) return false

        val file = WallpaperFetcher.downloadImage(imageUrl, context) ?: return false
        val ok = WallpaperApplier.applyFile(context, file, prefs.lockScreen)

        if (ok) {
            prefs.lastImageUrl = imageUrl
            prefs.lastAppliedAt = System.currentTimeMillis()
        }
        return ok
    }
}
