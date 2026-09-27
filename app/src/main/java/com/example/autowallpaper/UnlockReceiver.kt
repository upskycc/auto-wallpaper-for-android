package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 解锁广播接收器
 *
 * 注意：Android 8.0+ 静态注册收不到 USER_PRESENT（系统白名单限制），
 * 所以在 WallpaperApp 中动态注册本接收器，进程存活期间可触发。
 *
 * onReceive / goAsync 只有约 10 秒窗口，完整网络下载必然超时（广播 ANR），
 * 所以这里只做开关判断 + 投递 WorkManager 一次性任务，不做任何网络操作。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_USER_PRESENT) return

        val prefs = PrefsManager(context)

        // 诊断计数：无论后续条件如何，只要广播到达就记录
        prefs.unlockCount = prefs.unlockCount + 1
        prefs.lastUnlockAt = System.currentTimeMillis()

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return

        // 优先级1：有等待更换（定时任务息屏时跳过的）→ 立即补换
        //         独立于「解锁时更换」开关，也不受最小间隔限制
        // 优先级2：「解锁时更换」开关开启 → 每次解锁都换（受最小间隔限制）
        val needChange = prefs.pendingChange ||
            (prefs.enabled && (prefs.minIntervalMs <= 0L ||
                System.currentTimeMillis() - prefs.lastAppliedAt >= prefs.minIntervalMs))
        if (!needChange) return

        // KEEP：已有未完成的同类任务时不再重复入队（去重 + 天然串行）
        WorkManager.getInstance(context).enqueueUniqueWork(
            OneTimeChangeWorker.UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OneTimeChangeWorker>().build()
        )
    }
}
