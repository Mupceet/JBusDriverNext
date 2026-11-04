package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.data.ltx.parseLtxLibrary
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LTX 库数据仓库接口。
 *
 * 数据源为 `GET {ltxBaseUrl}/api/items` 单包全量 JSON。所有方法为 main-safe 的
 * suspend/Flow，内部网络与解析在 IO 调度器执行；失败时静默降级返回空值，
 * 不影响调用方（详情页查询、收藏页列表）的主流程。
 */
interface LtxRepository {
    /**
     * 观察 LTX 全库（stale-while-revalidate）。
     *
     * 先发射磁盘/内存缓存（[CachedLoadEvent.Cached]），缓存过期（TTL 6 小时）
     * 时后台拉取新数据并发射 [CachedLoadEvent.Fresh]。
     */
    fun observeLibrary(forceRefresh: Boolean = false): Flow<CachedLoadEvent<LtxLibrary>>

    /** 获取当前可用的全库快照；无缓存且网络失败时返回 null。 */
    suspend fun currentLibrary(): LtxLibrary?

    /** 按番号归一化查找条目；库不可用或未收录时返回 null。 */
    suspend fun findByCode(code: String): LtxEntry?

    /** 强制刷新全库；失败时返回 null（调用方据此提示刷新失败）。 */
    suspend fun refresh(): LtxLibrary?
}

@Singleton
class DefaultLtxRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cacheStore: CacheStore,
    private val settings: LtxSettingsSource,
) : LtxRepository {

    /** 最近一次成功加载的全库快照，供 findByCode 免重复读缓存。 */
    private val libraryState = MutableStateFlow<LtxLibrary?>(null)

    override fun observeLibrary(forceRefresh: Boolean): Flow<CachedLoadEvent<LtxLibrary>> =
        cacheStore.observeCached(
            key = CACHE_KEY,
            ttlMillis = TTL_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = false,
            isCacheable = { it.entries.isNotEmpty() },
            fetch = { fetchLibrary() },
        ).onEach { event ->
            when (event) {
                is CachedLoadEvent.Cached -> libraryState.value = event.entry.value
                is CachedLoadEvent.Fresh -> libraryState.value = event.entry.value
                is CachedLoadEvent.Failure -> Unit
            }
        }

    override suspend fun currentLibrary(): LtxLibrary? {
        libraryState.value?.let { return it }
        return try {
            observeLibrary().first()
            libraryState.value
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            KLog.t(TAG).e("LTX library load failed", e)
            null
        }
    }

    override suspend fun findByCode(code: String): LtxEntry? {
        val normalized = normalizeLtxCode(code)
        if (normalized.isEmpty()) return null
        return currentLibrary()?.find(normalized)
    }

    override suspend fun refresh(): LtxLibrary? = try {
        when (val event = observeLibrary(forceRefresh = true).first()) {
            is CachedLoadEvent.Fresh -> event.entry.value
            // forceRefresh 下不会出现 Cached；网络失败时回退内存中最近快照
            is CachedLoadEvent.Cached -> event.entry.value
            is CachedLoadEvent.Failure -> libraryState.value
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        KLog.t(TAG).e("LTX library refresh failed", e)
        null
    }

    // region network

    /**
     * 独立 OkHttpClient：LTX 为第三方站点，必须隔离 JavBus 会话 Cookie，
     * 不复用 NetClient 的 CookieJar 与磁力拦截器。
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun fetchLibrary(): LtxLibrary = withContext(ioDispatcher) {
        val base = settings.currentLtxBaseUrl().trimEnd('/')
        val url = base + LTX_ITEMS_PATH
        val json = executeJsonRequest(url)
        parseLtxLibrary(json) ?: throw IOException("Malformed LTX payload from $url")
    }

    private fun executeJsonRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", NetClient.USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url")
            }
            return response.body?.string() ?: throw IOException("Empty body from $url")
        }
    }

    // endregion

    companion object {
        private const val TAG = "LtxRepository"
        private const val CACHE_KEY = "ltx-library-v1"
        private val TTL_MILLIS = 6 * 60 * 60 * 1000L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
    }
}
