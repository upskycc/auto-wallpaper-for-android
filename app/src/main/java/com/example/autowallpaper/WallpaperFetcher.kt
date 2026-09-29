package com.example.autowallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

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
     * WebView 兜底解析 302 图源：
     * 部分 API 前置 WAF 通过 TLS 指纹（JA3）识别并拦截 OkHttp（403），
     * 换 UA 无效；系统 WebView 是真实 Chrome TLS 栈 + JS 引擎，可正常通过。
     * 只用它拿"最终图片地址"，图片下载仍走 OkHttp（图床 CDN 一般无 WAF）。
     * @return 最终跳转到的图片 URL；失败返回 null
     */
    private suspend fun resolveRedirectViaWebView(context: Context, apiUrl: String): String? =
        withContext(Dispatchers.Main) {
            try {
                suspendCancellableCoroutine { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    var finished = false

                    fun done(result: String?, view: WebView?) {
                        if (finished) return
                        finished = true
                        view?.stopLoading()
                        view?.destroy()
                        if (cont.isActive) cont.resume(result)
                    }

                    val webView = WebView(context.applicationContext)
                    webView.settings.javaScriptEnabled = true // 允许 WAF 的 JS 质询自动通过
                    webView.settings.userAgentString = UA
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            if (finished) return
                            if (url != apiUrl) {
                                // 已 302 跳转到图片地址
                                done(url, view)
                            } else {
                                // 停留在原地址（可能是 JS 延迟跳转），稍等再取
                                handler.postDelayed({
                                    done(view.url?.takeIf { it != apiUrl }, view)
                                }, 2000)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            // 只关心主文档失败（子资源失败不影响）
                            if (request.isForMainFrame) done(null, view)
                        }
                    }

                    // 总超时保护
                    handler.postDelayed({ done(webView.url?.takeIf { it != apiUrl }, webView) }, 20_000)
                    // 协程被取消时释放 WebView
                    cont.invokeOnCancellation { done(null, webView) }

                    webView.loadUrl(apiUrl)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 根据随机选中的图源获取最终图片 URL
     * 图源自带模式：jsonPath 为空 = 302 直连；非空 = JSON 提取
     * 失败原因会写入 prefs.lastFetchError 供诊断
     */
    suspend fun fetchImageUrl(context: Context, prefs: PrefsManager): String? =
        withContext(Dispatchers.IO) {
            try {
                val source = prefs.randomSource()
                if (source == null) {
                    prefs.lastFetchError = "没有可用的图源（检查图源配置是否为空）"
                    return@withContext null
                }
                val url = resolveUrl(context, source.url)

                if (source.jsonPath.isEmpty()) {
                    // 302 模式：OkHttp 跟随重定向后，response.request.url 就是最终图片地址
                    client.newCall(request(url)).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            // WAF（如 Cloudflare TLS 指纹识别）对 OkHttp 返回 403：
                            // 用系统 WebView（真实 Chrome TLS 栈）兜底解析重定向
                            if (resp.code == 403) {
                                val viaWebView = resolveRedirectViaWebView(context, url)
                                if (viaWebView != null) return@withContext viaWebView
                                prefs.lastFetchError =
                                    "HTTP 403（WAF 拦截，WebView 兜底也失败，图源：${source.url.take(60)}…）"
                            } else {
                                prefs.lastFetchError = "HTTP ${resp.code}（图源：${source.url.take(60)}…）"
                            }
                            return@withContext null
                        }
                        resp.body?.close() // 只需要 URL，不需要 body
                        resp.request.url.toString()
                    }
                } else {
                    // JSON 模式：用该图源自己的路径提取
                    client.newCall(request(url)).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            prefs.lastFetchError = "HTTP ${resp.code}（图源：${source.url.take(60)}…）"
                            return@withContext null
                        }
                        val json = resp.body?.string()
                        if (json == null) {
                            prefs.lastFetchError = "响应体为空"
                            return@withContext null
                        }
                        val result = JsonPathExtractor.extract(json, source.jsonPath)
                        if (result == null) {
                            prefs.lastFetchError = "JSON 提取失败（路径 ${source.jsonPath}），响应开头：${json.take(120)}"
                        }
                        result
                    }
                }
            } catch (e: Exception) {
                prefs.lastFetchError = "请求异常：${e.javaClass.simpleName}: ${e.message}"
                null
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
     * @return 下载好的缓存文件，失败返回 null（原因写入 prefs.lastFetchError）
     */
    suspend fun downloadImage(imageUrl: String, context: Context, prefs: PrefsManager): File? =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request(imageUrl)).execute().use { resp ->
                if (!resp.isSuccessful) {
                    prefs.lastFetchError = "下载失败 HTTP ${resp.code}"
                    return@withContext null
                }
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
                        val mime = options.outMimeType ?: "未知格式"
                        prefs.lastFetchError =
                            "下载的内容无法解码为图片（$mime，可能是动态 WebP 或 HTML）"
                        return@withContext null
                    }

                    // 改名失败（如存储满）时不能返回不存在的文件
                    if (!partFile.renameTo(finalFile)) {
                        prefs.lastFetchError = "缓存文件改名失败（存储空间不足？）"
                        return@withContext null
                    }
                    finalFile
                } finally {
                    // 改名成功后 partFile 已不存在；失败/异常时清掉残留
                    if (partFile.exists()) partFile.delete()
                }
                }
            } catch (e: Exception) {
                prefs.lastFetchError = "下载异常：${e.javaClass.simpleName}: ${e.message}"
                null
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
