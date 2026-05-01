package me.jbusdriver.modern.core

import android.app.Activity
import android.app.ActivityManager
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog

/**
 * 职责：二级缓存管理器（内存 LRU + 磁盘 ACache）
 *
 * 使用场景：
 * - Repository 层缓存网络请求结果（MovieRepository、MovieDetailRepository、SearchRepository）
 * - 缓存 key 通常由 URL path + 页码组成
 *
 * 线程：
 * - lru 操作内存缓存，线程安全（LruCache 内部 synchronized）
 * - acache 操作磁盘缓存，应通过 withContext(Dispatchers.IO) 在 IO 线程执行
 */
object CacheLoader {
    private const val TAG = "CacheLoader"

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

    /** 内存 LRU 缓存，应用重启后清空 */
    @JvmStatic
    val lru: LruCache<String, String> by lazy { initMemCache() }

    /** 磁盘缓存，应用重启后仍有效，支持过期时间 */
    @JvmStatic
    val acache: ACache by lazy { ACache.get(JBusManager.context) }

    /**
     * 同时写入内存和磁盘缓存
     *
     * @param pair key → value，value 会被序列化为 JSON
     * @param seconds 磁盘缓存过期秒数，null 表示永不过期
     */
    fun cacheLruAndDisk(pair: Pair<String, Any>, seconds: Int? = null) = with(GSON.toJson(pair.second)) {
        lru.put(pair.first, this)
        seconds?.let { acache.put(pair.first, GSON.toJson(pair.second), seconds) }
            ?: acache.put(pair.first, GSON.toJson(pair.second))
    }

    /**
     * 仅写入内存缓存
     *
     * 使用场景：列表数据，下次启动获取新数据
     */
    fun cacheLru(pair: Pair<String, Any>) = lru.put(pair.first, GSON.toJson(pair.second))

    /**
     * 仅写入磁盘缓存
     *
     * @param seconds 过期秒数，null 表示永不过期
     */
    fun cacheDisk(pair: Pair<String, Any>, seconds: Int? = null) =
        seconds?.let { acache.put(pair.first, v2Str(pair.second), seconds) }
            ?: acache.put(pair.first, v2Str(pair.second))

    /** 将对象转为字符串：CharSequence 直接 toString，其他对象序列化为 JSON */
    private fun v2Str(obj: Any): String = when (obj) {
        is CharSequence -> obj.toString()
        else -> obj.toJsonString()
    }

    /**
     * 从内存缓存同步读取
     *
     * @return 缓存值，不存在时返回 null
     */
    fun getFromLru(key: String): String? = lru[key]

    /**
     * 从磁盘缓存同步读取，命中时可选回填内存缓存
     *
     * 注意：磁盘 I/O，应在 IO 线程调用
     * @param add2Lru 命中时是否回填到内存缓存
     * @return 缓存值，不存在时返回 null
     */
    fun getFromDisk(key: String, add2Lru: Boolean = true): String? {
        val v = acache.getAsString(key)
        if (v != null && add2Lru) lru.put(key, v)
        return v
    }

    /**
     * 按关键词模糊删除缓存（内存 + 磁盘同时清除）
     *
     * 在 IO 线程执行，避免阻塞调用方
     *
     * @param keys 要匹配的关键词
     * @param isRegex true 时用正则匹配，false 时用 contains 匹配
     */
    fun removeCacheLike(vararg keys: String, isRegex: Boolean = false) {
        // 快照当前 key 集合，避免遍历时并发修改
        val cacheCopyKeys = lru.snapshot().keys.toList()
        keys.forEach { removeKey ->
            val filterAction: (String) -> Boolean =
                { s -> if (isRegex) s.contains(removeKey.toRegex()) else s.contains(removeKey) }
            cacheCopyKeys.filter(filterAction).forEach {
                KLog.i("removeCacheLike : $it")
                lru.remove(it)
                acache.remove(it)
            }
        }
    }
}
