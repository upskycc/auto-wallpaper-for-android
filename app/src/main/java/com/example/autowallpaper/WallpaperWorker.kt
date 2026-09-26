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
        val prefs = PrefsManager(applicationContext)
        // 开关被关闭后不再执行
        if (!prefs.periodicEnabled) return Result.success()

        // 息屏时跳过本次切换（省电：不为换壁纸唤醒系统）
        // 到点亮屏后由「解锁时更换」触发，或等下一次定时周期
        val pm = applicationContext.getSystemService(android.content.Context.POWER_SERVICE)
                as android.os.PowerManager
        if (!pm.isInteractive) {
            // 息屏跳过：下次尝试时间顺延一个周期
            prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
            return Result.success()
        }

        val ok = WallpaperChanger.change(applicationContext)
        if (ok) {
            // 更新预计下次执行时间（估算，系统调度可能延迟）
            prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
        }
        return if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val UNIQUE_WORK = "auto_wallpaper_periodic"
    }
}
