package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 解锁广播接收器
 *
 * 注意：Android 8.0+ 静态注册收不到 USER_PRESENT（系统白名单限制）
 * 所以同时在 WallpaperApp 中动态注册了本接收器，进程存活期间可触发。
 * 主要的自动更换靠 WorkManager 定时任务兜底。
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

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 优先级1：有等待更换（定时任务息屏时跳过的）→ 立即补换
                // 独立于「解锁时更换」开关，也不受最小间隔限制
                if (prefs.pendingChange) {
                    prefs.pendingChange = false
                    val ok = WallpaperChanger.change(context)
                    if (ok) {
                        prefs.nextRunAt = System.currentTimeMillis() + prefs.periodicMinutes * 60_000L
                    }
                    return@launch
                }

                // 优先级2：「解锁时更换」开关开启 → 每次解锁都换
                if (!prefs.enabled) return@launch

                // 最小间隔检查
                val interval = prefs.minIntervalMs
                if (interval > 0 && System.currentTimeMillis() - prefs.lastAppliedAt < interval) {
                    return@launch
                }

                WallpaperChanger.change(context)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
