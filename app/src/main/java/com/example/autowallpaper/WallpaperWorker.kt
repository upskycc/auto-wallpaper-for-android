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

        // 诊断：记录 Worker 实际运行时间和息屏跳过状态
        prefs.lastWorkerRunAt = System.currentTimeMillis()
        prefs.lastWorkerSkipped = !pm(applicationContext).isInteractive
        if (prefs.lastWorkerSkipped) {
            // 息屏跳过本次：置位等待标记，下次解锁时补换；
            // 「预计下次切换」顺延一个周期
            prefs.pendingChange = true
            prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
            return Result.success()
        }

        val ok = WallpaperChanger.change(applicationContext)
        // 成败都记录，界面诊断如实展示
        prefs.lastWorkerOk = ok
        if (ok) {
            // 更新预计下次执行时间（估算，系统调度可能延迟）
            // pendingChange 由 WallpaperChanger 成功路径统一清除
            prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
        }
        return if (ok) Result.success() else Result.failure()
    }

    private fun pm(context: android.content.Context) =
        context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager

    companion object {
        const val UNIQUE_WORK = "auto_wallpaper_periodic"
    }
}
