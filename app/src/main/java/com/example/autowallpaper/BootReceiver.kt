package com.example.autowallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启接收器
 * 静态广播接收器会自动注册，无需额外操作
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 静态广播接收器在 AndroidManifest.xml 中注册后会自动工作
        // 无需在开机时启动任何服务
    }
}
