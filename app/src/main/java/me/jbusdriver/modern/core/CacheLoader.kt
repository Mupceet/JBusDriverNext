package me.jbusdriver.modern.core

import android.app.Activity
import android.app.ActivityManager
import androidx.collection.LruCache
import me.jbusdriver.modern.KLog

/**
 * 职责：二级缓存管理器（内存 LRU + 磁盘 ACache）
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

    /** 磁盘缓存，应用重启后仍有效，支持过期时间 */
    @PublishedApi
    internal val acache: ACache by lazy { ACache.get(JBusManager.context) }

    /**
     * 根据设备可用内存初始化 LRU 内存缓存
     *
     * 可用内存 > 32MB 时分配 4MB 缓存，否则 2MB
     * sizeOf 按字符串字节数计算，确保不超限
     */
    private fun initMemCache(): LruCache<String, String> {
        val memoryInfo = ActivityManager.MemoryInfo()
        val myActivityManager = JBusManager.context.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        myActivityManager.getMemoryInfo(memoryInfo)
        val memSize = memoryInfo.availMem.formatFileSize()
        KLog.t(TAG).d("max availMem = $memSize")
        if (memoryInfo.lowMemory) {
            KLog.w("可能的内存不足")
            toast("当前可用内存:$memSize,请注意释放内存")
        }
        val cacheSize = if (memoryInfo.availMem > 32 * 1024 * 1024) 4 * 1024 * 1024 else 2 * 1024 * 1024
        KLog.t(TAG).d("max cacheSize = ${cacheSize.toLong().formatFileSize()}")
        return object : LruCache<String, String>(cacheSize) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: String, newValue: String?) {
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
    inline fun <reified T> lruCached(key: String, forceRefresh: Boolean = false, fetch: () -> T): T {
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
     * @param T 缓存数据类型
     * @param key 缓存键
     * @param fetch 缓存未命中时的数据获取逻辑
     * @return 缓存或新获取的数据
     */
    inline fun <reified T> persistentCached(key: String, forceRefresh: Boolean = false, fetch: () -> T): T {
        if (!forceRefresh) {
            // 第一级：LRU 内存
            lru.get(key)?.let { json ->
                GSON.fromJson<T>(json)?.let { return it }
            }
            // 第二级：磁盘（命中后回填 LRU）
            acache.getAsString(key)?.let { json ->
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
        acache.put(key, json)
        return result
    }

    /**
     * 从磁盘缓存读取原始字符串值
     *
     * 使用场景：读取配置型数据（如 BUS_URLS），不涉及类型化反序列化
     *
     * @param key 缓存键
     * @return 缓存字符串，不存在时返回 null
     */
    fun getString(key: String): String? = acache.getAsString(key)

    // endregion

    /**
     * 按关键词模糊删除缓存（内存 + 磁盘同时清除）
     *
     * @param keys 要匹配的关键词
     * @param isRegex true 时用正则匹配，false 时用 contains 匹配
     */
    fun removeLike(vararg keys: String, isRegex: Boolean = false) {
        val cacheCopyKeys = lru.snapshot().keys.toList()
        keys.forEach { removeKey ->
            val matches: (String) -> Boolean =
                { s -> if (isRegex) s.contains(removeKey.toRegex()) else s.contains(removeKey) }
            cacheCopyKeys.filter(matches).forEach {
                KLog.i("removeLike : $it")
                lru.remove(it)
                acache.remove(it)
            }
        }
    }
}
