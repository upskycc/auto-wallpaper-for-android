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
     * @param dedup true（自动触发）：取到的 URL 与上次相同时视为成功、跳过下载；
     *              false（手动触发）：强制下载
     * @return 是否成功
     */
    suspend fun change(context: Context, dedup: Boolean = true): Boolean {
        val prefs = PrefsManager(context)

        val imageUrl = WallpaperFetcher.fetchImageUrl(context, prefs) ?: return false

        // 避免连续换同一张：URL 未变时自动触发视为成功（无需更换），
        // 同时清除等待更换标记，避免解锁补换反复空跑
        if (dedup && imageUrl == prefs.lastImageUrl) {
            prefs.pendingChange = false
            return true
        }

        val file = WallpaperFetcher.downloadImage(imageUrl, context) ?: return false
        val ok = WallpaperApplier.applyFile(context, file, prefs.lockScreen)

        if (ok) {
            prefs.lastImageUrl = imageUrl
            prefs.lastAppliedAt = System.currentTimeMillis()
            // 任何成功切换（定时/解锁/手动/磁贴/浏览）都清除等待更换标记
            prefs.pendingChange = false
            // 只保留刚应用的这一张，清理旧缓存图
            WallpaperFetcher.cleanupOldWallpapers(context, file)
        }
        return ok
    }
}
