package me.jbusdriver.modern.core

import android.app.Activity
import android.app.ActivityManager
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import java.io.File

/**
 * 职责：二级缓存管理器（内存 LRU + 磁盘 FileCache）
 *
 * 使用场景：Repository 层缓存网络请求结果，通过 [lruCached] 和 [persistentCached]
 * 两种策略实现临时缓存和持久缓存，业务方无需直接操作底层缓存实现。
 *
 * 线程：LRU 操作内存缓存（线程安全），磁盘操作应在 IO 线程调用。
 * 两个核心方法已内置序列化/反序列化，调用方只需关注泛型类型。
 */
object CacheLoader {
    private const val TAG = "CacheLoader"

    /** 内存 LRU 缓存，应用重启后清空 */
    @PublishedApi
    internal val lru: LruCache<String, String> by lazy { initMemCache() }

    /** 磁盘缓存，应用重启后仍有效 */
    @PublishedApi
    internal val fileCache: FileCache by lazy {
        FileCache(
            File(
                JBusManager.context.cacheDir,
                "ACache"
            ), 300.MB.toLong()
        )
    }

    /**
     * 根据设备可用内存初始化 LRU 内存缓存
     *
     * 可用内存 > 32MB 时分配 4MB 缓存，否则 2MB
     * sizeOf 按字符串字节数计算，确保不超限
     */
    private fun initMemCache(): LruCache<String, String> {
        val memoryInfo = ActivityManager.MemoryInfo()
        val myActivityManager =
            JBusManager.context.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        myActivityManager.getMemoryInfo(memoryInfo)
        val memSize = memoryInfo.availMem.formatFileSize()
        KLog.t(TAG).d("max availMem = $memSize")
        if (memoryInfo.lowMemory) {
            KLog.w("可能的內存不足")
        }
        val cacheSize =
            if (memoryInfo.availMem > 64.MB) 32.MB else 8.MB
        KLog.t(TAG).d("max cacheSize = ${cacheSize.toLong().formatFileSize()}")
        return object : LruCache<String, String>(cacheSize) {
            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: String,
                newValue: String?
            ) {
                KLog.i("entryRemoved : evicted = $evicted , key = $key")
            }

            override fun sizeOf(key: String, value: String): Int {
                return value.toByteArray().size
            }
        }
    }

    // region 核心读取 API

    /**
     * 临时缓存策略：仅 LRU 内存缓存
     *
     * 查找顺序：LRU → [fetch] → 写入 LRU
     * 适用于列表数据，应用重启后自动获取新数据。
     *
     * @param T 缓存数据类型
     * @param key 缓存键
     * @param forceRefresh true 时跳过缓存直接获取
     * @param fetch 缓存未命中时的数据获取逻辑
     * @return 缓存或新获取的数据
     */
    inline fun <reified T> lruCached(
        key: String,
        forceRefresh: Boolean = false,
        fetch: () -> T
    ): T {
        if (!forceRefresh) {
            lru.get(key)?.let { json ->
                GSON.fromJson<T>(json)?.let { return it }
            }
        }
        val result = fetch()
        lru.put(key, GSON.toJson(result))
        return result
    }

    /**
     * 持久缓存策略：LRU + 磁盘双级缓存
     *
     * 查找顺序：LRU → 磁盘（命中后回填 LRU）→ [fetch] → 写入 LRU + 磁盘
     * 适用于详情、类别等静态数据，应用重启后仍可命中缓存。
     *
     * 磁盘读写自动切换到 [Dispatchers.IO]，调用方无需关心线程。
     *
     * @param T 缓存数据类型
     * @param key 缓存键
     * @param forceRefresh true 时跳过缓存直接获取
     * @param fetch 缓存未命中时的数据获取逻辑
     * @return 缓存或新获取的数据
     */
    suspend inline fun <reified T> persistentCached(
        key: String,
        forceRefresh: Boolean = false,
        crossinline fetch: suspend () -> T
    ): T {
        if (!forceRefresh) {
            // 第一级：LRU 内存（无磁盘 I/O）
            lru.get(key)?.let { json ->
                GSON.fromJson<T>(json)?.let { return it }
            }
            // 第二级：磁盘（命中后回填 LRU）
            val diskJson = withContext(Dispatchers.IO) { fileCache.get(key) }
            diskJson?.let { json ->
                GSON.fromJson<T>(json)?.let { cached ->
                    lru.put(key, GSON.toJson(cached))
                    return cached
                }
            }
        }
        // 缓存未命中或强制刷新，获取并写入 LRU + 磁盘
        val result = fetch()
        val json = GSON.toJson(result)
        lru.put(key, json)
        withContext(Dispatchers.IO) { fileCache.put(key, json) }
        return result
    }

    /**
     * 从磁盘缓存读取原始字符串值
     *
     * 使用场景：读取配置型数据（如 BUS_URLS），不涉及类型化反序列化
     *
     * 磁盘读取自动切换到 [Dispatchers.IO]，调用方无需关心线程。
     *
     * @param key 缓存键
     * @return 缓存字符串，不存在时返回 null
     */
    suspend fun getString(key: String): String? = withContext(Dispatchers.IO) { fileCache.get(key) }

    fun getLruString(key: String): String? = lru.get(key)

    fun putLruString(key: String, value: String) {
        lru.put(key, value)
    }

    suspend fun getDiskString(key: String): String? = withContext(Dispatchers.IO) { fileCache.get(key) }

    suspend fun putDiskString(key: String, value: String) {
        withContext(Dispatchers.IO) { fileCache.put(key, value) }
    }

    // endregion
}
