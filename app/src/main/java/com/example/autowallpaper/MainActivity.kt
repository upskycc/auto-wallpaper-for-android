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
        loadPrefsToUi()
        setupListeners()
    }

    private fun loadPrefsToUi() {
        binding.switchEnable.isChecked = prefs.enabled
        binding.switchLock.isChecked = prefs.lockScreen
        binding.switchPeriodic.isChecked = prefs.periodicEnabled
        binding.etUrl.setText(prefs.apiUrl)
        binding.spinnerMode.setSelection(
            if (prefs.fetchMode == PrefsManager.FetchMode.REDIRECT) 0 else 1
        )
        binding.etJsonPath.setText(prefs.jsonPath)
        binding.etJsonPath.isEnabled = prefs.fetchMode == PrefsManager.FetchMode.JSON

        // 上次切换时间展示
        val last = prefs.lastAppliedAt
        binding.tvLastApplied.text = if (last > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(last))
        } else {
            getString(R.string.never)
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
    }

    override fun onResume() {
        super.onResume()
        // 每次回到界面刷新诊断计数
        loadPrefsToUi()
    }

    /** 调度 WorkManager 定时换壁纸（系统级调度，省电） */
    private fun schedulePeriodicWork() {
        val request = androidx.work.PeriodicWorkRequestBuilder<WallpaperWorker>(
            prefs.periodicHours, java.util.concurrent.TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WallpaperWorker.UNIQUE_WORK,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
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
                schedulePeriodicWork()
            } else {
                WorkManager.getInstance(this)
                    .cancelUniqueWork(WallpaperWorker.UNIQUE_WORK)
            }
        }

        binding.spinnerMode.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                prefs.fetchMode = if (p2 == 0) PrefsManager.FetchMode.REDIRECT else PrefsManager.FetchMode.JSON
                binding.etJsonPath.isEnabled = p2 == 1
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        })

        binding.btnSave.setOnClickListener {
            prefs.apiUrl = binding.etUrl.text.toString().trim()
            prefs.jsonPath = binding.etJsonPath.text.toString().trim().ifBlank { "data.url" }
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }

        binding.btnNow.setOnClickListener {
            prefs.apiUrl = binding.etUrl.text.toString().trim()
            prefs.jsonPath = binding.etJsonPath.text.toString().trim().ifBlank { "data.url" }
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
                    Toast.makeText(this@MainActivity, R.string.fetch_failed, Toast.LENGTH_SHORT).show()
                }
                return
            }

            val file = WallpaperFetcher.downloadImage(imageUrl, this)
            if (file == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
                return
            }

            val ok = WallpaperApplier.applyFile(this, file, prefs.lockScreen)
            withContext(Dispatchers.Main) {
                if (ok) {
                    prefs.lastImageUrl = imageUrl
                    prefs.lastAppliedAt = System.currentTimeMillis()
                    loadPrefsToUi()
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
