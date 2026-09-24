package com.example.autowallpaper

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 简易 JSON Path 提取器
 *
 * 支持语法：
 *   data.url        -> object 取字段
 *   data[0].url     -> array 按下标取
 *   data.list[1].img
 *   $.data.url      -> 可选 $ 前缀
 */
object JsonPathExtractor {

    /**
     * 根据 path 从原始 JSON 字符串中提取 URL
     * @return URL 或 null（提取失败）
     */
    fun extract(json: String, path: String): String? {
        val root = try {
            com.google.gson.JsonParser.parseString(json)
        } catch (_: Exception) {
            return null
        }
        return extract(root, path)
    }

    /**
     * 从 JsonElement 提取 path 对应的值（字符串）
     */
    fun extract(root: JsonElement, path: String): String? {
        val cleanPath = path.trim().removePrefix("$").trimStart('.')
        if (cleanPath.isEmpty()) return root.asStringSafe()

        var current: JsonElement = root
        val tokens = tokenize(cleanPath)

        for (token in tokens) {
            when (token) {
                is Token.Field -> {
                    if (!current.isJsonObject) return null
                    val obj = current.asJsonObject
                    if (!obj.has(token.name)) return null
                    current = obj.get(token.name)
                }
                is Token.Index -> {
                    if (!current.isJsonArray) return null
                    val arr = current.asJsonArray
                    if (token.index < 0 || token.index >= arr.size()) return null
                    current = arr.get(token.index)
                }
            }
        }

        return current.asStringSafe()
    }

    private fun tokenize(path: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = path.length

        while (i < len) {
            when {
                path[i] == '.' -> {
                    i++
                    // 字段名
                    val start = i
                    while (i < len && path[i] != '.' && path[i] != '[') i++
                    if (i > start) {
                        tokens += Token.Field(path.substring(start, i))
                    }
                }
                path[i] == '[' -> {
                    i++
                    val start = i
                    while (i < len && path[i] != ']') i++
                    val num = path.substring(start, i).toIntOrNull()
                    if (num != null) tokens += Token.Index(num)
                    if (i < len) i++ // 跳过 ']'
                }
                else -> {
                    // 初始字段（path 不以 . 开头）
                    val start = i
                    while (i < len && path[i] != '.' && path[i] != '[') i++
                    tokens += Token.Field(path.substring(start, i))
                }
            }
        }
        return tokens
    }

    private fun JsonElement.asStringSafe(): String? {
        return try {
            if (isJsonNull) null
            else asString
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private sealed class Token {
        data class Field(val name: String) : Token()
        data class Index(val index: Int) : Token()
    }

    // ========== 便捷方法：也可以从 JsonObject/JsonArray 直接取 ==========

    /** 尝试在对象第一层的字段里找 URL（兼容 {data:"..."} 和直接是 URL 字符串） */
    fun findUrlDirectly(obj: JsonObject): String? {
        // 先看 data 字段
        obj.get("data")?.let {
            if (it.isJsonPrimitive) return it.asStringSafe()
            if (it.isJsonObject) {
                // 在 data 对象里找 url
                it.asJsonObject.get("url")?.let { u -> return u.asStringSafe() }
            }
            if (it.isJsonArray && it.asJsonArray.size() > 0) {
                val first = it.asJsonArray[0]
                if (first.isJsonObject) {
                    first.asJsonObject.get("url")?.let { u -> return u.asStringSafe() }
                }
            }
        }
        // 直接顶层 url
        obj.get("url")?.let { return it.asStringSafe() }
        return null
    }
}
