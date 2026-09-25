package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启接收器
 *
 * 真正的作用：BOOT_COMPLETED 是系统白名单广播，系统会为了投递它
 * 而拉起本应用进程 —— 进程启动后 WallpaperApp.onCreate 会自动
 * 动态注册解锁接收器，从而实现"开机后解锁触发可用"。
 * （需要用户在系统设置中允许自启动）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 无需任何代码，进程拉起即完成使命
        // 保留 Log 便于确认
        android.util.Log.d("BootReceiver", "boot completed, process started")
    }
}
