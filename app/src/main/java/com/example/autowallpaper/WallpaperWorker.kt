package com.example.autowallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager 定时任务：系统调度、按需唤醒、无进程常驻
 */
class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ok = WallpaperChanger.change(applicationContext)
        return if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val UNIQUE_WORK = "auto_wallpaper_periodic"
    }
}
