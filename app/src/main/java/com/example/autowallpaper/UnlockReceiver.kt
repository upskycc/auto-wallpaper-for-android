package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 解锁广播接收器
 *
 * 触发链：SCREEN_OFF -> 用户输入密码/指纹 -> USER_PRESENT
 * 我们在 USER_PRESENT 时执行换壁纸。
 */
class UnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UnlockReceiver"

        @Volatile
        private var screenWasOff = false
    }

    private var job: Job? = null

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                screenWasOff = true
            }
            Intent.ACTION_USER_PRESENT -> {
                if (!screenWasOff) return
                screenWasOff = false

                val prefs = PrefsManager(context)
                if (!prefs.enabled) return

                // 最小间隔检查
                val interval = prefs.minIntervalMs
                if (interval > 0 && System.currentTimeMillis() - prefs.lastAppliedAt < interval) {
                    return
                }

                // 异步执行换壁纸
                val pending = goAsync()
                job = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        doChange(context, prefs)
                    } catch (e: Exception) {
                        // 静默吞掉，不影响用户
                    } finally {
                        pending.finish()
                    }
                }
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
