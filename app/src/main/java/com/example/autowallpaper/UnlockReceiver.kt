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
 * 静态注册，按需唤醒，省电
 * SCREEN_OFF 和 USER_PRESENT 是系统允许静态注册的特殊广播
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_USER_PRESENT) return

        val prefs = PrefsManager(context)

        // 诊断计数：无论后续条件如何，只要广播到达就记录
        prefs.unlockCount = prefs.unlockCount + 1
        prefs.lastUnlockAt = System.currentTimeMillis()

        // 检查屏幕是否真的亮着
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return

        if (!prefs.enabled) return

        // 最小间隔检查
        val interval = prefs.minIntervalMs
        if (interval > 0 && System.currentTimeMillis() - prefs.lastAppliedAt < interval) {
            return
        }

        // 异步执行换壁纸
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                doChange(context, prefs)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun doChange(context: Context, prefs: PrefsManager) {
        val imageUrl = WallpaperFetcher.fetchImageUrl(context, prefs) ?: return

        // 避免连续换同一张
        if (imageUrl == prefs.lastImageUrl) return

        val file = WallpaperFetcher.downloadImage(imageUrl, context) ?: return
        val ok = WallpaperApplier.applyFile(context, file, prefs.lockScreen)

        if (ok) {
            prefs.lastImageUrl = imageUrl
            prefs.lastAppliedAt = System.currentTimeMillis()
        }
    }
}
