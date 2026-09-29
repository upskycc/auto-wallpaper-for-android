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
 * 壁纸获取 + 下载
 */
object WallpaperFetcher {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)  // 自动跟随 302
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 部分图床/接口（Cloudflare 等）会拦截 OkHttp 默认 UA 返回 403，
     * 统一伪装成浏览器 UA
     */
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun request(url: String): Request =
        Request.Builder().url(url).header("User-Agent", UA).build()

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
                client.newCall(request(url)).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    resp.body?.close() // 只需要 URL，不需要 body
                    resp.request.url.toString()
                }
            } else {
                // JSON 模式：用该图源自己的路径提取
                client.newCall(request(url)).execute().use { resp ->
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
     * 临时文件和最终文件都带时间戳唯一命名：多个触发源并发下载时互不干扰，
     * 也不会写坏彼此的文件
     * @return 下载好的缓存文件，失败返回 null
     */
    suspend fun downloadImage(imageUrl: String, context: Context): File? =
        withContext(Dispatchers.IO) {
            client.newCall(request(imageUrl)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body ?: return@withContext null

                val cacheDir = File(context.cacheDir, "wallpaper")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                // 唯一命名：并发下载不会互相覆盖
                val stamp = System.currentTimeMillis()
                val partFile = File(cacheDir, "wall_$stamp.part")
                val finalFile = File(cacheDir, "current_$stamp.jpg")

                try {
                    FileOutputStream(partFile).use { fos ->
                        body.byteStream().use { it.copyTo(fos) }
                    }

                    // 验证是不是合法图片
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(partFile.absolutePath, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        return@withContext null
                    }

                    // 改名失败（如存储满）时不能返回不存在的文件
                    if (!partFile.renameTo(finalFile)) {
                        return@withContext null
                    }
                    finalFile
                } finally {
                    // 改名成功后 partFile 已不存在；失败/异常时清掉残留
                    if (partFile.exists()) partFile.delete()
                }
            }
        }

    /**
     * 清理缓存目录里除 keep 之外的旧壁纸（含旧版本遗留的 current.jpg / current.part），
     * 只保留最近应用的一张；进行中的 wall_*.part 不受影响。
     */
    suspend fun cleanupOldWallpapers(context: Context, keep: File) =
        withContext(Dispatchers.IO) {
            val keepPath = keep.absolutePath
            val cacheDir = File(context.cacheDir, "wallpaper")
            cacheDir.listFiles()?.forEach { f ->
                val name = f.name
                val isCurrent = name.startsWith("current_") && name.endsWith(".jpg")
                val isLegacy = name == "current.jpg" || name == "current.part"
                if (f.isFile && (isCurrent || isLegacy) && f.absolutePath != keepPath) {
                    f.delete()
                }
            }
        }
}
