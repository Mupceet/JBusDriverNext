package me.jbusdriver.modern.core.cache

import android.app.ActivityManager
import android.content.Context
import android.text.format.Formatter
import androidx.collection.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.FileCache
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.MB
import me.jbusdriver.modern.core.fromJson
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface CacheStore {
    fun readMemory(key: String): String?
    fun writeMemory(key: String, value: String)
    suspend fun readDisk(key: String): String?
    suspend fun writeDisk(key: String, value: String)
}

@Singleton
class DefaultCacheStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CacheStore {
    private val appContext = context.applicationContext

    private val memoryCache: LruCache<String, String> by lazy { initMemoryCache() }

    private val diskCache: FileCache by lazy {
        FileCache(
            File(appContext.cacheDir, "ACache"),
            300.MB.toLong()
        )
    }

    override fun readMemory(key: String): String? = memoryCache.get(key)

    override fun writeMemory(key: String, value: String) {
        memoryCache.put(key, value)
    }

    override suspend fun readDisk(key: String): String? =
        withContext(Dispatchers.IO) { diskCache.get(key) }

    override suspend fun writeDisk(key: String, value: String) {
        withContext(Dispatchers.IO) { diskCache.put(key, value) }
    }

    private fun initMemoryCache(): LruCache<String, String> {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        KLog.t(TAG).d("max availMem = ${Formatter.formatFileSize(appContext, memoryInfo.availMem)}")
        if (memoryInfo.lowMemory) {
            KLog.w("Possible low memory when initializing cache")
        }
        val cacheSize = if (memoryInfo.availMem > 64.MB) 32.MB else 8.MB
        KLog.t(TAG).d("max cacheSize = ${Formatter.formatFileSize(appContext, cacheSize.toLong())}")
        return object : LruCache<String, String>(cacheSize) {
            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: String,
                newValue: String?
            ) {
                KLog.i("entryRemoved : evicted = $evicted , key = $key")
            }

            override fun sizeOf(key: String, value: String): Int = value.toByteArray().size
        }
    }

    private companion object {
        const val TAG = "CacheStore"
    }
}

suspend inline fun <reified T> CacheStore.lruCached(
    key: String,
    forceRefresh: Boolean = false,
    crossinline fetch: suspend () -> T
): T {
    if (!forceRefresh) {
        readMemory(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { return it }
        }
    }
    val result = fetch()
    writeMemory(key, GSON.toJson(result))
    return result
}

suspend inline fun <reified T> CacheStore.persistentCached(
    key: String,
    forceRefresh: Boolean = false,
    crossinline fetch: suspend () -> T
): T {
    if (!forceRefresh) {
        readMemory(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { return it }
        }
        readDisk(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { cached ->
                writeMemory(key, GSON.toJson(cached))
                return cached
            }
        }
    }
    val result = fetch()
    val json = GSON.toJson(result)
    writeMemory(key, json)
    writeDisk(key, json)
    return result
}

suspend inline fun <reified T> CacheStore.readCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T>? {
    readMemory(key)?.let { json ->
        parseCacheEntry<T>(json, CacheSource.Memory, ttlMillis, nowMillis)?.let { return it }
    }

    if (disk) {
        readDisk(key)?.let { json ->
            parseCacheEntry<T>(json, CacheSource.Disk, ttlMillis, nowMillis)?.let { entry ->
                writeMemory(key, json)
                return entry
            }
        }
    }

    return null
}

suspend inline fun <reified T> CacheStore.writeCached(
    key: String,
    value: T,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T> {
    val storedAtMillis = nowMillis()
    val json = GSON.toJson(CacheEnvelope(storedAtMillis, GSON.toJson(value)))
    writeMemory(key, json)
    if (disk) {
        writeDisk(key, json)
    }
    return CacheEntry(
        value = value,
        storedAtMillis = storedAtMillis,
        source = CacheSource.Network,
        isExpired = false
    )
}

@PublishedApi
internal inline fun <reified T> parseCacheEntry(
    json: String,
    source: CacheSource,
    ttlMillis: Long,
    noinline nowMillis: () -> Long
): CacheEntry<T>? {
    val envelope = runCatching { GSON.fromJson<CacheEnvelope>(json) }.getOrNull()
    val payloadJson = envelope?.payloadJson
    if (!payloadJson.isNullOrBlank()) {
        val payload = runCatching { GSON.fromJson<T>(payloadJson) }.getOrNull() ?: return null
        return CacheEntry(
            value = payload,
            storedAtMillis = envelope.storedAtMillis,
            source = source,
            isExpired = nowMillis() - envelope.storedAtMillis >= ttlMillis
        )
    }

    val legacy = runCatching { GSON.fromJson<T>(json) }.getOrNull() ?: return null
    return CacheEntry(
        value = legacy,
        storedAtMillis = 0L,
        source = source,
        isExpired = true
    )
}

/**
 * Stale-while-revalidate 缓存策略的核心实现。
 *
 * 先尝试读取缓存并立即发射 [CachedLoadEvent.Cached]，然后在后台发起网络请求，
 * 成功后发射 [CachedLoadEvent.Fresh]，失败时发射 [CachedLoadEvent.Failure]。
 *
 * @param key 缓存键
 * @param ttlMillis 缓存存活时间（毫秒）
 * @param disk 是否使用磁盘缓存
 * @param forceRefresh 强制跳过缓存直接网络请求
 * @param revalidate 即使缓存未过期也发起后台刷新
 * @param nowMillis 当前时间戳提供者（用于测试注入）
 * @param fetch 网络数据获取逻辑
 */
inline fun <reified T> CacheStore.observeCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true,
    forceRefresh: Boolean = false,
    revalidate: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() },
    crossinline isCacheable: (T) -> Boolean = { true },
    crossinline fetch: suspend () -> T
): Flow<CachedLoadEvent<T>> = flow {
    var emittedCache = false
    val cached = if (forceRefresh) {
        null
    } else {
        readCached<T>(key, ttlMillis, disk, nowMillis)
    }

    if (cached != null) {
        emittedCache = true
        emit(CachedLoadEvent.Cached(cached))
    }

    val shouldFetch = forceRefresh || cached == null || cached.isExpired || revalidate
    if (!shouldFetch) return@flow

    try {
        var fresh = fetch()
        // 退化结果（如年龄验证/反爬中间页解析为 0 条）：重试一次；仍退化则不落缓存。
        if (!isCacheable(fresh)) {
            KLog.d("[Cache] fresh result not cacheable, retrying once: key=$key", "CacheSWR")
            fresh = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                fresh
            }
        }

        if (isCacheable(fresh)) {
            val entry = writeCached(key, fresh, disk, nowMillis)
            emit(CachedLoadEvent.Fresh(entry))
        } else if (!emittedCache) {
            // 无缓存可用时，仍把（空）结果交给 UI 以脱离加载态，但不持久化，避免毒化缓存。
            emit(CachedLoadEvent.Fresh(ephemeralEntry(fresh, nowMillis())))
        }
        // 否则（已有缓存且结果退化）：保留缓存，既不发空 Fresh 也不落盘。
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        emit(CachedLoadEvent.Failure(throwable, emittedCache))
    }
}

/**
 * 构造一个不持久化的 [CacheEntry]：仅在无缓存可用、且网络返回退化结果时，
 * 临时把结果交给 UI，避免停留在加载态。
 */
fun <T> ephemeralEntry(value: T, now: Long): CacheEntry<T> = CacheEntry(
    value = value,
    storedAtMillis = now,
    source = CacheSource.Network,
    isExpired = false
)

/**
 * 从 [CachedLoadEvent] Flow 中获取最终值。
 *
 * 等待第一个有效结果：非过期的缓存值，或网络返回的新鲜/失败值。
 * 如果网络失败但有缓存值（即使过期），返回缓存的值。
 */
suspend fun <T> Flow<CachedLoadEvent<T>>.firstCachedOrFresh(): T {
    var expiredCached: CacheEntry<T>? = null
    return when (val event = first { event ->
        when (event) {
            is CachedLoadEvent.Cached -> {
                if (event.entry.isExpired) {
                    expiredCached = event.entry
                    false
                } else {
                    true
                }
            }

            is CachedLoadEvent.Fresh,
            is CachedLoadEvent.Failure -> true
        }
    }) {
        is CachedLoadEvent.Cached -> event.entry.value
        is CachedLoadEvent.Fresh -> event.entry.value
        is CachedLoadEvent.Failure -> expiredCached?.value ?: throw event.throwable
    }
}
