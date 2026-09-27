package com.example.autowallpaper

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 下拉快捷开关磁贴：点一下立即换壁纸
 * 零耗电，只有点击时才工作。
 *
 * 点击后只投递 WorkManager 一次性任务：TileService 的 onClick 返回后
 * 进程随时可能被系统回收（裸协程下载会被中途杀死），不在本进程内做网络。
 * onClick 运行在主线程，updateTile 线程安全。
 */
class ChangeWallpaperTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            OneTimeChangeWorker.TILE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OneTimeChangeWorker>().build()
        )

        // 真正的更换由 Worker 异步完成，磁贴状态立即复位
        qsTile?.let {
            it.state = Tile.STATE_INACTIVE
            it.updateTile()
        }
    }
}
