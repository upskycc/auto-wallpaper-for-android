package com.example.autowallpaper

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.autowallpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        loadInputs()
        refreshDiagnostics()
        setupListeners()
    }

    /** 把已保存的配置回填到输入控件（仅 onCreate 调用，避免覆盖用户未保存的输入） */
    private fun loadInputs() {
        binding.switchEnable.isChecked = prefs.enabled
        binding.switchLock.isChecked = prefs.lockScreen
        binding.switchPeriodic.isChecked = prefs.periodicEnabled
        binding.etUrl.setText(prefs.sourcesToText())

        // 定时周期回显（分钟）
        binding.etInterval.setText(prefs.periodicMinutes.toString())
    }

    /** 刷新诊断信息（时间/计数），不影响输入控件 */
    private fun refreshDiagnostics() {
        // 上次切换时间展示
        val last = prefs.lastAppliedAt
        binding.tvLastApplied.text = if (last > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(last))
        } else {
            getString(R.string.never)
        }

        // 预计下次切换时间展示
        val next = prefs.nextRunAt
        binding.tvNextApplied.text = when {
            !prefs.periodicEnabled -> getString(R.string.periodic_disabled)
            next <= 0 -> getString(R.string.never)
            next <= System.currentTimeMillis() -> getString(R.string.next_applied_delayed)
            else -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(next))
        }

        // 定时任务诊断信息
        binding.tvWorkerInfo.text = if (!prefs.periodicEnabled) {
            ""
        } else if (prefs.lastWorkerRunAt <= 0) {
            getString(R.string.worker_never_run)
        } else {
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(prefs.lastWorkerRunAt))
            val action = when {
                prefs.lastWorkerSkipped -> getString(R.string.worker_skipped)
                prefs.lastWorkerOk -> getString(R.string.worker_executed)
                else -> getString(R.string.worker_failed)
            }
            String.format(java.util.Locale.getDefault(), getString(R.string.worker_run_fmt), time, action)
        }

        // 解锁触发诊断信息
        val unlockTime = if (prefs.lastUnlockAt > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(prefs.lastUnlockAt))
        } else {
            getString(R.string.never)
        }
        binding.tvUnlockInfo.text =
            getString(R.string.unlock_info_fmt, prefs.unlockCount, unlockTime)

        // 最近一次失败原因（成功后清空）
        binding.tvFetchError.text = prefs.lastFetchError
    }

    override fun onResume() {
        super.onResume()
        // 只刷新诊断信息，不覆盖输入框里未保存的修改
        refreshDiagnostics()
        catchUpOverduePeriodic()
    }

    /**
     * 打开 App 时发现定时任务已到点但系统还没执行（Doze/应用待机把后台任务推迟了）：
     * 视为欠账，立即投递一次性任务补换。
     * App 正在前台 → 系统会立刻执行，无需等待维护窗口。
     */
    private fun catchUpOverduePeriodic() {
        if (!prefs.periodicEnabled) return
        val next = prefs.nextRunAt
        if (next > 0 && next <= System.currentTimeMillis()) {
            // 复用解锁补换通道：置欠账标记，Worker 成功后会顺延预计时间
            prefs.pendingChange = true
            val request = androidx.work.OneTimeWorkRequestBuilder<OneTimeChangeWorker>().build()
            androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                OneTimeChangeWorker.UNIQUE_WORK,
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    /** 调度 WorkManager 定时换壁纸（系统级调度，省电） */
    private fun schedulePeriodicWork() {
        val minutes = prefs.periodicMinutes
        val request = androidx.work.PeriodicWorkRequestBuilder<WallpaperWorker>(
            minutes, java.util.concurrent.TimeUnit.MINUTES
        ).build()

        val wm = WorkManager.getInstance(this)
        // REPLACE：取消旧任务并重新入队，周期从"现在"重新计时（与 2.10+ 的 CANCEL_AND_REENQUEUE 同义）
        // （不能 cancel + KEEP 分开调：两个异步操作有竞态，cancel 可能后执行把新任务也删掉，
        //   导致定时任务整体丢失、到点永远不执行）
        wm.enqueueUniquePeriodicWork(
            WallpaperWorker.UNIQUE_WORK,
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        // 记录预计下次执行时间（估算，系统可能延迟）
        prefs.nextRunAt = System.currentTimeMillis() + minutes * 60_000L
    }

    /** 保存输入框中的定时周期（分钟），并在开启定时的情况下重新调度 */
    private fun saveIntervalAndReschedule() {
        val inputMinutes = binding.etInterval.text.toString().trim().toLongOrNull()
        prefs.periodicMinutes = inputMinutes ?: 360L
        if (prefs.periodicEnabled) {
            schedulePeriodicWork()
        }
    }

    private fun setupListeners() {
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
        }

        binding.switchLock.setOnCheckedChangeListener { _, checked ->
            prefs.lockScreen = checked
        }

        binding.switchPeriodic.setOnCheckedChangeListener { _, checked ->
            prefs.periodicEnabled = checked
            if (checked) {
                // 先读输入框里的周期再调度
                saveIntervalAndReschedule()
            } else {
                WorkManager.getInstance(this)
                    .cancelUniqueWork(WallpaperWorker.UNIQUE_WORK)
            }
        }

        binding.btnSave.setOnClickListener {
            prefs.saveSourcesFromText(binding.etUrl.text.toString())
            saveIntervalAndReschedule()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnNow.setOnClickListener {
            prefs.saveSourcesFromText(binding.etUrl.text.toString())
            saveIntervalAndReschedule()
            lifecycleScope.launch { changeNow() }
        }

        binding.btnBrowse.setOnClickListener {
            startActivity(Intent(this, BrowseActivity::class.java))
        }
    }

    private suspend fun changeNow() {
        withContext(Dispatchers.Main) {
            binding.btnNow.isEnabled = false
            binding.btnNow.setText(R.string.loading)
        }

        try {
            val imageUrl = WallpaperFetcher.fetchImageUrl(this, prefs)
            if (imageUrl == null) {
                withContext(Dispatchers.Main) {
                    refreshDiagnostics()
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.fetch_failed) + "\n" + prefs.lastFetchError,
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }

            val file = WallpaperFetcher.downloadImage(imageUrl, this, prefs)
            if (file == null) {
                withContext(Dispatchers.Main) {
                    refreshDiagnostics()
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.download_failed) + "\n" + prefs.lastFetchError,
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }

            // 手动触发：不做去重，强制下载
            val ok = WallpaperApplier.applyFile(this, file, prefs.lockScreen)
            if (ok) {
                // 清理旧缓存图，只保留刚应用的这一张
                WallpaperFetcher.cleanupOldWallpapers(this, file)
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    prefs.lastImageUrl = imageUrl
                    prefs.lastAppliedAt = System.currentTimeMillis()
                    prefs.lastFetchError = ""
                    prefs.pendingChange = false
                    refreshDiagnostics()
                    Toast.makeText(this@MainActivity, R.string.apply_ok, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, R.string.apply_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            withContext(Dispatchers.Main) {
                binding.btnNow.isEnabled = true
                binding.btnNow.setText(R.string.change_now)
            }
        }
    }
}
