package com.example.autowallpaper

import android.app.Application
import android.content.IntentFilter

/**
 * Application 入口：动态注册解锁接收器
 * （Android 8.0+ 只能动态注册，进程存活期间有效）
 */
class WallpaperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val receiver = UnlockReceiver()
        val filter = IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, filter)
    }
}
