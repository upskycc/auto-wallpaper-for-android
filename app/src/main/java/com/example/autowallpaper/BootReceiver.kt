package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启接收器（空实现，主要是确保 App 被系统拉起后，
 * 动态注册的解锁接收器能正常工作）。
 *
 * 注意：Android 8.0+ 静态注册的 SCREEN_OFF / USER_PRESENT 广播仍然
 * 可以正常接收，不需要动态注册。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 开机时什么也不做，等待用户下次解锁
    }
}
