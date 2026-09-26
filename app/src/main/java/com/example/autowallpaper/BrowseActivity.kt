package com.example.autowallpaper

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 浏览 6 张图片，点击切换壁纸
 */
class BrowseActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var recycler: RecyclerView
    private val urls = mutableListOf<String>()
    private lateinit var adapter: ImageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.browse_title)

        prefs = PrefsManager(this)
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = ImageAdapter(urls, ::applyImage)
        recycler.adapter = adapter

        loadImages()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadImages() {
        findViewById<View>(R.id.progress).visibility = View.VISIBLE
        recycler.visibility = View.GONE

        lifecycleScope.launch {
            val fetched = mutableListOf<String>()
            repeat(6) {
                val url = try {
                    WallpaperFetcher.fetchImageUrl(this@BrowseActivity, prefs)
                } catch (_: Exception) {
                    null
                }
                if (url != null) fetched.add(url)
            }

            withContext(Dispatchers.Main) {
                findViewById<View>(R.id.progress).visibility = View.GONE
                if (fetched.isEmpty()) {
                    Toast.makeText(this@BrowseActivity, R.string.fetch_failed, Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                urls.clear()
                urls.addAll(fetched)
                adapter.notifyDataSetChanged()
                recycler.visibility = View.VISIBLE
            }
        }
    }

    private fun applyImage(url: String) {
        lifecycleScope.launch {
            val file = WallpaperFetcher.downloadImage(url, this@BrowseActivity)
            if (file == null) {
                Toast.makeText(this@BrowseActivity, R.string.download_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ok = WallpaperApplier.applyFile(this@BrowseActivity, file, prefs.lockScreen)
            if (ok) {
                prefs.lastImageUrl = url
                prefs.lastAppliedAt = System.currentTimeMillis()
                prefs.pendingChange = false
                Toast.makeText(this@BrowseActivity, R.string.apply_ok, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@BrowseActivity, R.string.apply_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== Adapter =====
    private inner class ImageAdapter(
        private val data: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ImageAdapter.VH>() {

        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    setPadding(8, 8, 8, 8)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val url = data[position]
            Glide.with(this@BrowseActivity)
                .load(url)
                .centerCrop()
                .into(holder.iv)
            holder.iv.setOnClickListener { onClick(url) }
        }

        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)
    }
}
