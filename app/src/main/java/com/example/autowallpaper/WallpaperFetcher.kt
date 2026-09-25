package com.example.autowallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 壁纸获取 + 下载 + 应用
 */
object WallpaperFetcher {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)  // 自动跟随 302
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 根据随机选中的图源获取最终图片 URL
     * 图源自带模式：jsonPath 为空 = 302 直连；非空 = JSON 提取
     */
    suspend fun fetchImageUrl(context: Context, prefs: PrefsManager): String? =
        withContext(Dispatchers.IO) {
            // 多图源：每次随机取一个
            val source = prefs.randomSource() ?: return@withContext null
            val url = resolveUrl(context, source.url)

            if (source.jsonPath.isEmpty()) {
                // 302 模式：OkHttp 跟随重定向后，response.request.url 就是最终图片地址
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.close() // 只需要 URL，不需要 body
                    resp.request.url.toString()
                }
            } else {
                // JSON 模式：用该图源自己的路径提取
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val json = resp.body?.string() ?: return@withContext null
                    JsonPathExtractor.extract(json, source.jsonPath)
                }
            }
        }

    /**
     * 把 URL 里的 {w} {h} 替换成屏幕分辨率
     */
    private fun resolveUrl(context: Context, template: String): String {
        var url = template
        if (url.contains("{w}") || url.contains("{h}")) {
            val dm: DisplayMetrics = context.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            url = url.replace("{w}", w.toString()).replace("{h}", h.toString())
        }
        return url
    }

    /**
     * 下载图片到缓存文件
     * @return 缓存文件，下载失败返回 null
     */
    suspend fun downloadImage(imageUrl: String, context: Context): File? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(imageUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null

                // 先下载到 .part，成功后重命名为 .jpg
                val cacheDir = File(context.cacheDir, "wallpaper")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val partFile = File(cacheDir, "current.part")
                val finalFile = File(cacheDir, "current.jpg")

                FileOutputStream(partFile).use { fos ->
                    body.byteStream().use { it.copyTo(fos) }
                }

                // 验证是不是合法图片
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(partFile.absolutePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    partFile.delete()
                    return@withContext null
                }

                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)
                finalFile
            }
        }

    /**
     * 清空缓存目录
     */
    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, "wallpaper")
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
