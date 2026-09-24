package com.example.autowallpaper

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理：API地址、解析模式、JSON路径等
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    enum class FetchMode {
        /** 302重定向，直接返回图片 */
        REDIRECT,
        /** 返回JSON，需要解析URL */
        JSON
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** 自动切换间隔（毫秒），0=每次解锁都换 */
    var minIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, 0L)
        set(v) = prefs.edit().putLong(KEY_INTERVAL, v).apply()

    /** 壁纸API URL，支持 {w} {h} 占位符 */
    var apiUrl: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL)!!
        set(v) = prefs.edit().putString(KEY_URL, v).apply()

    var fetchMode: FetchMode
        get() = FetchMode.valueOf(prefs.getString(KEY_MODE, FetchMode.REDIRECT.name)!!)
        set(v) = prefs.edit().putString(KEY_MODE, v.name).apply()

    /** JSON 路径，如 "data.url" 或 "data[0].url" */
    var jsonPath: String
        get() = prefs.getString(KEY_JSON_PATH, "data.url")!!
        set(v) = prefs.edit().putString(KEY_JSON_PATH, v).apply()

    /** 上次成功切换的时间戳 */
    var lastAppliedAt: Long
        get() = prefs.getLong(KEY_LAST_APPLIED, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_APPLIED, v).apply()

    /** 上次使用的图片URL（避免重复下载同一张） */
    var lastImageUrl: String
        get() = prefs.getString(KEY_LAST_URL, "")!!
        set(v) = prefs.edit().putString(KEY_LAST_URL, v).apply()

    /** 同时设置锁屏壁纸 */
    var lockScreen: Boolean
        get() = prefs.getBoolean(KEY_LOCK, true)
        set(v) = prefs.edit().putBoolean(KEY_LOCK, v).apply()

    /** 解锁广播触发次数（诊断用：确认接收器是否工作） */
    var unlockCount: Int
        get() = prefs.getInt(KEY_UNLOCK_COUNT, 0)
        set(v) = prefs.edit().putInt(KEY_UNLOCK_COUNT, v).apply()

    /** 最近一次解锁广播触发时间 */
    var lastUnlockAt: Long
        get() = prefs.getLong(KEY_LAST_UNLOCK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_UNLOCK, v).apply()

    companion object {
        private const val NAME = "auto_wallpaper_prefs"
        private const val DEFAULT_URL = "https://wp.upx8.com/api.php?resolution={w}x{h}"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "min_interval"
        private const val KEY_URL = "api_url"
        private const val KEY_MODE = "fetch_mode"
        private const val KEY_JSON_PATH = "json_path"
        private const val KEY_LAST_APPLIED = "last_applied"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_LOCK = "lock_screen"
        private const val KEY_UNLOCK_COUNT = "unlock_count"
        private const val KEY_LAST_UNLOCK = "last_unlock"
    }
}
