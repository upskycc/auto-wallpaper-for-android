package com.example.autowallpaper

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 下拉快捷开关磁贴：点一下立即换壁纸
 * 零耗电，只有点击时才工作
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

        // 点击时短暂进入后台执行
        val tile = qsTile
        tile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }

        CoroutineScope(Dispatchers.IO).launch {
            WallpaperChanger.change(applicationContext)
            tile?.let {
                it.state = Tile.STATE_INACTIVE
                it.updateTile()
            }
        }
    }
}
