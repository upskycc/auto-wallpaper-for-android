package com.example.autowallpaper

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 配置管理：图源列表、定时、开关等
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    /**
     * 图源：jsonPath 为空 = 302 直连模式；非空 = JSON 模式用该路径提取 URL
     */
    data class Source(val url: String, val jsonPath: String)

    /** 解锁时更换壁纸的开关（false = 解锁不触发更换） */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** 自动切换间隔（毫秒），0=每次解锁都换 */
    var minIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL, 0L)
        set(v) = prefs.edit().putLong(KEY_INTERVAL, v).apply()

    /** 图源列表 */
    var sources: List<Source>
        get() {
            val json = prefs.getString(KEY_SOURCES, null)
            if (json != null) {
                return try {
                    gson.fromJson(json, object : TypeToken<List<Source>>() {}.type)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            // 迁移旧版单图源/换行分隔配置（全部按 302 直连处理）
            val legacy = prefs.getString(KEY_URL, DEFAULT_URL)!!
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
            return legacy.map { Source(it, "") }
        }
        set(v) = prefs.edit().putString(KEY_SOURCES, gson.toJson(v)).apply()

    /** 从多行文本解析并保存图源（每行：URL 或 URL|JSON路径） */
    fun saveSourcesFromText(text: String) {
        val list = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split("|")
            if (parts.size >= 2 && parts[1].trim().isNotEmpty()) {
                Source(parts[0].trim(), parts[1].trim())
            } else {
                Source(line, "") // 302 直连
            }
        }
        sources = list
    }

    /** 图源列表转为多行文本（用于界面回显） */
    fun sourcesToText(): String =
        sources.joinToString("\n") {
            if (it.jsonPath.isEmpty()) it.url else "${it.url}|${it.jsonPath}"
        }

    /** 随机取一个图源 */
    fun randomSource(): Source? = sources.randomOrNull()

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

    /** 定时自动更换（WorkManager，省电无进程常驻） */
    var periodicEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERIODIC, false)
        set(v) = prefs.edit().putBoolean(KEY_PERIODIC, v).apply()

    /** 定时更换周期（分钟，WorkManager 最小 15 分钟） */
    var periodicMinutes: Long
        get() = prefs.getLong(KEY_PERIODIC_MINUTES, 360L)
        set(v) = prefs.edit().putLong(KEY_PERIODIC_MINUTES, v.coerceAtLeast(15L)).apply()

    /** 预计下次定时切换时间（估算值，系统可能延迟） */
    var nextRunAt: Long
        get() = prefs.getLong(KEY_NEXT_RUN, 0L)
        set(v) = prefs.edit().putLong(KEY_NEXT_RUN, v).apply()

    /**
     * 等待更换标记：定时任务在息屏时被跳过后置位，
     * 下次解锁时立即补换并清除（独立于「解锁时更换」开关）
     */
    var pendingChange: Boolean
        get() = prefs.getBoolean(KEY_PENDING_CHANGE, false)
        set(v) = prefs.edit().putBoolean(KEY_PENDING_CHANGE, v).apply()

    companion object {
        private const val NAME = "auto_wallpaper_prefs"
        private const val DEFAULT_URL = "https://wp.upx8.com/api.php?resolution={w}x{h}"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "min_interval"
        private const val KEY_URL = "api_url"                 // 旧版兼容
        private const val KEY_SOURCES = "sources_json"
        private const val KEY_LAST_APPLIED = "last_applied"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_LOCK = "lock_screen"
        private const val KEY_UNLOCK_COUNT = "unlock_count"
        private const val KEY_LAST_UNLOCK = "last_unlock"
        private const val KEY_PERIODIC = "periodic_enabled"
        private const val KEY_PERIODIC_MINUTES = "periodic_minutes"
        private const val KEY_NEXT_RUN = "next_run_at"
        private const val KEY_PENDING_CHANGE = "pending_change"
    }
}
