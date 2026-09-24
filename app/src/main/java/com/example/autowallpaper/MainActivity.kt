package com.example.autowallpaper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.autowallpaper.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startUnlockService()
        } else {
            Toast.makeText(this, "需要通知权限才能运行自动换壁纸服务", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)
        loadPrefsToUi()
        setupListeners()
        requestNotificationPermission()
    }

    private fun loadPrefsToUi() {
        binding.switchEnable.isChecked = prefs.enabled
        binding.switchLock.isChecked = prefs.lockScreen
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
    }

    private fun setupListeners() {
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
        }

        binding.switchLock.setOnCheckedChangeListener { _, checked ->
            prefs.lockScreen = checked
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startUnlockService()
            } else {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startUnlockService()
        }
    }

    private fun startUnlockService() {
        val serviceIntent = Intent(this, UnlockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
