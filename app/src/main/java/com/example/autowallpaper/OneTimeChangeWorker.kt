package com.example.autowallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 一次性换壁纸任务：解锁触发 / 磁贴点击 都投递到这里执行
 *
 * BroadcastReceiver 的 goAsync 只有约 10 秒窗口（超时即广播 ANR），
 * TileService 点击返回后进程随时可能被回收（协程会被中途杀死），
 * 都不适合直接做网络下载，统一交给 WorkManager 执行。
 */
class OneTimeChangeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = PrefsManager(applicationContext)
        // 先读等待标记：WallpaperChanger.change 成功路径会清除它
        val hadPending = prefs.pendingChange
        val ok = WallpaperChanger.change(applicationContext)
        if (ok && hadPending) {
            // 补换成功：清除等待标记，预计下次时间顺延一个周期
            // 失败时不清除，下次解锁仍会补换
            prefs.pendingChange = false
            prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
        }
        return if (ok) Result.success() else Result.failure()
    }

    companion object {
        /** 解锁触发的一次性任务（ExistingWorkPolicy.KEEP 去重 + 天然串行） */
        const val UNIQUE_WORK = "auto_wallpaper_onetime"
        /** 磁贴触发的一次性任务（独立名字，避免与解锁路径互相挤占） */
        const val TILE_WORK = "auto_wallpaper_tile"
    }
}
