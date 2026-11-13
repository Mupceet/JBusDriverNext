package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.cache.CacheEnvelope
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.LtxWebFetch
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.http.LtxWebViewGateway
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.data.ltx.LtxChallengeException
import me.jbusdriver.modern.data.ltx.isLtxCloudflareChallenge
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
 *
 * 站点开启人机验证时（Worker 层 Turnstile 门禁，403 + `reason=verify`），纯
 * OkHttp 请求会被拒绝：此时改用隐形 WebView 加载站点让 SPA 自行完成验证并
 * 直接抓取数据（[LtxWebViewGateway]），同时导出 Cookie 与 UA 供后续 OkHttp
 * 快路径复用；Cookie 经系统 CookieManager 持久化，有效期内无需重复验证。
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

    /** 任意全库拉取进行中（缓存过期自检、下拉强刷、验证自动尝试均涵盖）；界面据此显示加载指示。 */
    val isFetching: StateFlow<Boolean>

    /**
     * 人机验证需人工介入时发射（隐形 WebView 自动通过失败后）。
     * 调用方据此弹出可见验证界面；完成验证后调 [adoptWebSession] 并刷新。
     */
    val verificationRequests: SharedFlow<Unit>

    /** 注入人工验证流程获取的会话，使后续拉取走 OkHttp 快路径。 */
    fun adoptWebSession(session: LtxWebSession)

    /** 当前 LTX 站点首页地址（人工验证 WebView 的加载目标）。 */
    suspend fun currentOrigin(): String

    /** 最近缓存的全库载荷大小（字节，取自序列化 JSON）；无缓存返回 0。用于刷新确认提示。 */
    suspend fun cachedPayloadBytes(): Long
}

/** 全库缓存新鲜度阈值：下拉刷新据此决定直接强刷还是先询问确认。 */
internal const val LTX_CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L

@Singleton
class DefaultLtxRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cacheStore: CacheStore,
    private val settings: LtxSettingsSource,
    private val webGateway: LtxWebViewGateway,
) : LtxRepository {

    /** 最近一次成功加载的全库快照，供 findByCode 免重复读缓存。 */
    private val libraryState = MutableStateFlow<LtxLibrary?>(null)

    private val _isFetching = MutableStateFlow(false)
    override val isFetching: StateFlow<Boolean> = _isFetching

    /** WebView 过 Cloudflare 挑战后的会话；进程内复用，Cookie/UA 必须成对生效。 */
    @Volatile
    private var webSession: LtxWebSession? = null

    private val _verificationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val verificationRequests: SharedFlow<Unit> = _verificationRequests

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
                is CachedLoadEvent.Failure -> KLog.t(TAG).w(
                    "LTX fetch failed (hadCached=${event.hadCachedValue}): ${event.throwable.message}"
                )
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
     * 不复用 NetClient 的 CookieJar 与磁力拦截器。过挑战的 Cookie 以请求头
     * 手动携带（仅 LTX 域 Cookie），保持无 CookieJar 的隔离语义。
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** 拉取全程（含验证自动尝试）置位 [isFetching]，供界面显示加载指示。 */
    private suspend fun fetchLibrary(): LtxLibrary {
        _isFetching.value = true
        try {
            return fetchLibraryInternal()
        } finally {
            _isFetching.value = false
        }
    }

    private suspend fun fetchLibraryInternal(): LtxLibrary = withContext(ioDispatcher) {
        val base = settings.currentLtxBaseUrl().trimEnd('/')
        val url = base + LTX_ITEMS_PATH
        KLog.t(TAG).i("LTX fetch start: $url (session=${if (webSession != null) "reused" else "none"})")
        val json = try {
            executeJsonRequest(url)
        } catch (e: LtxChallengeException) {
            // 人机验证门：隐形 WebView 让 SPA 过 Turnstile 并在页面内直接抓取（无循环）
            KLog.t(TAG).w("LTX verification required, fetching via WebView gateway: $url")
            val fetched = webGateway.fetchViaWebView(url)
                ?: run {
                    // 自动通过失败（如交互式 Turnstile）：请求人工介入
                    KLog.t(TAG).w("LTX auto verification failed; requesting manual verification")
                    _verificationRequests.tryEmit(Unit)
                    throw IOException("LTX human verification not passed for $url", e)
                }
            webSession = fetched.session
            fetched.body
        }
        parseLtxLibrary(json) ?: throw IOException("Malformed LTX payload from $url")
    }

    private fun executeJsonRequest(url: String): String {
        val session = webSession
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            // 验证凭证与 UA/IP 绑定：带会话时必须沿用求解 WebView 的 UA
            .header("User-Agent", session?.userAgent ?: NetClient.USER_AGENT)
            .apply {
                // Worker 以浏览器特征过滤自动化客户端：模拟 SPA 同源 fetch 的请求形态
                runCatching {
                    val uri = java.net.URI(url)
                    val referer = "${uri.scheme}://${uri.host}/"
                    header("Referer", referer)
                    header("Sec-Fetch-Dest", "empty")
                    header("Sec-Fetch-Mode", "cors")
                    header("Sec-Fetch-Site", "same-origin")
                }
                session?.let { header("Cookie", it.cookieHeader) }
            }
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val challenged = isLtxCloudflareChallenge(body)
            KLog.t(TAG).i(
                "LTX response: http=${response.code} type=${response.header("Content-Type")} " +
                        "len=${body.length} challenge=$challenged"
            )
            if (challenged) throw LtxChallengeException(url)
            if (!response.isSuccessful) {
                // 鉴权/业务类失败：响应体（JSON 错误说明）是定位问题的关键证据
                KLog.t(TAG).w("LTX error body: ${body.take(300)}")
                throw IOException("HTTP ${response.code} from $url")
            }
            if (body.isEmpty()) throw IOException("Empty body from $url")
            return body
        }
    }

    override fun adoptWebSession(session: LtxWebSession) {
        webSession = session
    }

    override suspend fun currentOrigin(): String = settings.currentLtxBaseUrl().trimEnd('/')

    override suspend fun cachedPayloadBytes(): Long {
        val json = cacheStore.readMemory(CACHE_KEY) ?: cacheStore.readDisk(CACHE_KEY) ?: return 0L
        // 缓存外壳为 CacheEnvelope(payloadJson=全库 JSON)，内层载荷字节数 ≈ 实际拉取流量
        val envelope = runCatching { GSON.fromJson<CacheEnvelope>(json) }.getOrNull() ?: return 0L
        return envelope.payloadJson.toByteArray(Charsets.UTF_8).size.toLong()
    }

    // endregion

    companion object {
        private const val TAG = "LtxRepository"
        private const val CACHE_KEY = "ltx-library-v1"
        private val TTL_MILLIS = LTX_CACHE_TTL_MILLIS
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
    }
}
