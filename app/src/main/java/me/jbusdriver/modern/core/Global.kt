package me.jbusdriver.modern.core

import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import androidx.core.net.toUri
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import java.io.File
import java.lang.reflect.Modifier.STATIC
import java.lang.reflect.Modifier.TRANSIENT
import java.util.Date

private const val TAG = "Global"

/**
 * 全局 Gson 实例
 *
 * 配置：
 * - 排除 transient 和 static 字段（与 Room @Transient 配合）
 * - Int 类型空安全：JSON 中为空或非法时返回 null 而非抛异常
 * - Date 类型：尝试字符串解析，失败时返回当前时间
 * - 序列化时包含 null 值
 *
 * 使用场景：全项目统一的 JSON 序列化/反序列化，包括缓存存取、数据库 Entity 转换
 */
val GSON by lazy {
    GsonBuilder().excludeFieldsWithModifiers(TRANSIENT, STATIC)
        .registerTypeAdapter(Int::class.java, JsonDeserializer<Int> { json, _, _ ->
            if (json.isJsonNull || json.asString.isEmpty()) {
                return@JsonDeserializer null
            }
            try {
                return@JsonDeserializer json.asInt
            } catch (e: NumberFormatException) {
                return@JsonDeserializer null
            }
        }).registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
            try {
                return@JsonDeserializer Date(json.asJsonPrimitive.asString)
            } catch (e: Exception) {
                return@JsonDeserializer Date()
            }
        }).serializeNulls().create()
}

/**
 * 安全创建目录
 *
 * @param collectDir 目标目录路径
 * @return 创建成功返回路径，失败返回 null
 */
fun createDir(collectDir: String): String? {
    File(collectDir.trim()).let {
        try {
            if (!it.exists() && it.mkdirs()) return collectDir
            if (it.exists()) {
                if (it.isDirectory) {
                    return collectDir
                } else {
                    // 同名文件存在时先删除再重建
                    it.delete()
                    createDir(collectDir)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createDir error", e)
        }
    }
    return null
}

/** URL 解析结果缓存，避免重复 Uri.parse() 调用 */
private val urlCache by lazy { LruCache<String, Uri>(512) }

/**
 * 从 URL 字符串提取 host 部分（scheme://host）
 *
 * 结果通过 LruCache 缓存，同一 URL 不会重复解析
 * 使用场景：MovieDetail.checkUrl() 中对比 host 是否匹配
 */
val String.urlHost: String
    get() = (urlCache.get(this) ?: let {
        val uri = Uri.parse(this)
        urlCache.put(this, uri)
        uri
    }).let {
        checkNotNull(it)
        "${it.scheme}://${it.host}"
    }

/**
 * 从 URL 字符串提取路径部分（不含 scheme 和 host）
 *
 * 结果通过 LruCache 缓存
 * 使用场景：CacheLoader 的缓存 key 生成、Repository 的 cacheKey 拼接
 */
val String.urlPath: String
    get() = (urlCache[this] ?: let {
        val uri = this.toUri()
        urlCache.put(this, uri)
        uri
    }).path ?: ""
