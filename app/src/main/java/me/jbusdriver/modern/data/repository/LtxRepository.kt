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
import me.jbusdriver.modern.core.http.LtxWebViewSession
import me.jbusdriver.modern.data.ltx.DEFAULT_LTX_BASE_URL
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.data.ltx.LTX_SESSION_PATH
import me.jbusdriver.modern.data.ltx.LtxChallengeException
import me.jbusdriver.modern.data.ltx.LtxCryptoSession
import me.jbusdriver.modern.data.ltx.LtxEncryptedEnvelopeDto
import me.jbusdriver.modern.data.ltx.LtxMigratedException
import me.jbusdriver.modern.data.ltx.LtxProtocolException
import me.jbusdriver.modern.data.ltx.LtxSessionHandshakeDto
import me.jbusdriver.modern.data.ltx.LtxSessionRequestBodyDto
import me.jbusdriver.modern.data.ltx.base64UrlDecode
import me.jbusdriver.modern.data.ltx.decryptLtxEnvelope
import me.jbusdriver.modern.data.ltx.establishLtxSession
import me.jbusdriver.modern.data.ltx.isLtxCloudflareChallenge
import me.jbusdriver.modern.data.ltx.isLtxCryptoSessionRequired
import me.jbusdriver.modern.data.ltx.ltxPublicKeyRawBase64Url
import me.jbusdriver.modern.data.ltx.newLtxClientKeyPair
import me.jbusdriver.modern.data.ltx.newLtxRequestId
import me.jbusdriver.modern.data.ltx.parseLtxLibrary
import me.jbusdriver.modern.data.ltx.parseLtxMigratedBaseUrl
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec
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
 *
 * 2026-11-15 起站点迁移至 `ltxlib.dpdns.org` 并升级为加密会话协议
 * （Turnstile 后还需 `POST /api/crypto/session` 做 ECDH+HKDF 握手，请求带
 * `X-LTX-*` 会话头、响应 AES-GCM 解密）：当前 OkHttp 与页面注入 XHR 通道均
 * 无法满足，命中 428 `cryptoSession` 时快速失败；旧域 410 迁移公告自动采纳
 * 新址并重试。通道重建（原生复刻握手或 SPA 代抓）另行适配。
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

    /** 加密会话（迁移站协议）；进程内复用，密钥不落盘。 */
    @Volatile
    private var cryptoSession: LtxCryptoSession? = null

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
        val json = fetchLtxLibraryWithMigration(
            initialBase = base,
            settings = settings,
            fetchJson = { fetchLibraryJsonOnce(it) },
        )
        parseLtxLibrary(json) ?: throw IOException("Malformed LTX payload from $base$LTX_ITEMS_PATH")
    }

    /**
     * 对指定基址单次拉取全库 JSON 原文。
     *
     * 迁移站（默认域）走加密会话通道（[fetchViaCryptoSession]）；自定义镜像域
     * 维持旧协议（明文 + Turnstile Cookie，WebView 抓取兜底）。
     */
    private suspend fun fetchLibraryJsonOnce(base: String): String {
        val url = base + LTX_ITEMS_PATH
        KLog.t(TAG).i(
            "LTX fetch start: $url (mode=${if (base == DEFAULT_LTX_BASE_URL) "crypto" else "legacy"}, " +
                    "web=${if (webSession != null) "reused" else "none"}, " +
                    "session=${if (cryptoSession != null) "reused" else "none"})"
        )
        return if (base == DEFAULT_LTX_BASE_URL) fetchViaCryptoSession(url) else fetchViaLegacy(url)
    }

    /** 旧协议（自定义镜像域）：明文 GET + Turnstile 门禁走 WebView 页内抓取。 */
    private suspend fun fetchViaLegacy(url: String): String = try {
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

    /**
     * 迁移站加密会话通道：会话就绪则直接请求；428（会话失效）或挑战（Cookie 失效）
     * 时重建会话并重试，总尝试数受 [MAX_SESSION_ATTEMPTS] 约束。
     */
    private suspend fun fetchViaCryptoSession(url: String): String {
        var session = cryptoSession?.takeIf { !it.isExpired() && webSession != null }
            ?: establishCryptoSession(url, allowManualVerification = true)
        var attempt = 0
        while (true) {
            attempt++
            try {
                val web = webSession ?: error("web session missing after establishment")
                return executeCryptoRequest(url, session, web)
            } catch (e: LtxSessionInvalidException) {
                // 428：会话过期/服务端丢弃——重建一次
                cryptoSession = null
                if (attempt >= MAX_SESSION_ATTEMPTS) throw LtxProtocolException(url)
                KLog.t(TAG).w("LTX crypto session invalid, re-establishing (attempt=$attempt)")
                session = establishCryptoSession(url, allowManualVerification = false)
            } catch (e: LtxChallengeException) {
                // Cookie 失效（边缘质询/Turnstile）：全量重建（WebView 过门禁+握手）
                cryptoSession = null
                webSession = null
                if (attempt >= MAX_SESSION_ATTEMPTS) throw LtxProtocolException(url)
                KLog.t(TAG).w("LTX challenge during crypto request, re-establishing (attempt=$attempt)")
                session = establishCryptoSession(url, allowManualVerification = false)
            }
        }
    }

    /**
     * 建立加密会话：优先以现有 Cookie/UA 走 OkHttp 原生握手（ECDH+HKDF 全在
     * 本进程完成）；无 Cookie 或被拒时由 WebView 网关过门禁并在页内握手。
     * 两条路都不通且允许人工介入时发射验证请求（弹窗只负责过门禁拿 Cookie，
     * 会话仍由本方法在下次拉取时原生建立）。
     */
    private suspend fun establishCryptoSession(
        itemsUrl: String,
        allowManualVerification: Boolean,
    ): LtxCryptoSession {
        val existing = webSession
        if (existing != null) {
            val negotiated = runCatching { negotiateSessionViaOkHttp(itemsUrl, existing) }.getOrNull()
            if (negotiated != null) {
                cryptoSession = negotiated
                return negotiated
            }
            // 握手被拒（Cookie 过期）或握手响应非法：清空走 WebView 全流程
            webSession = null
        }
        val fromWeb = webGateway.establishSessionViaWebView(itemsUrl)
        if (fromWeb != null) {
            val keyBytes = base64UrlDecode(fromWeb.aesKeyBase64Url)
            if (keyBytes != null && keyBytes.size == 32) {
                webSession = fromWeb.webSession
                val session = LtxCryptoSession(
                    sid = fromWeb.sid,
                    aesKey = SecretKeySpec(keyBytes, "AES"),
                    expiresAtMillis = fromWeb.expiresAtMillis,
                )
                cryptoSession = session
                return session
            }
            KLog.t(TAG).w("LTX web handshake returned bad key material")
        }
        // 不可见 WebView 未通过（交互式 Turnstile 等）：请求人工介入
        if (allowManualVerification) {
            KLog.t(TAG).w("LTX auto session establishment failed; requesting manual verification")
            _verificationRequests.tryEmit(Unit)
        }
        throw LtxProtocolException(itemsUrl)
    }

    /** OkHttp 原生会话握手；被门禁拒绝或响应非法返回 null。 */
    private fun negotiateSessionViaOkHttp(itemsUrl: String, web: LtxWebSession): LtxCryptoSession? {
        val base = itemsUrl.removeSuffix(LTX_ITEMS_PATH)
        val url = base + LTX_SESSION_PATH
        val keyPair = newLtxClientKeyPair()
        val bodyJson = GSON.toJson(LtxSessionRequestBodyDto(publicKey = ltxPublicKeyRawBase64Url(keyPair)))
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Content-Type", "application/json")
            .header("User-Agent", web.userAgent)
            .header("Cookie", web.cookieHeader)
            .browserLikeHeaders(url)
            .post(bodyJson.toRequestBody(null))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val challenged = isLtxCloudflareChallenge(body)
            KLog.t(TAG).i(
                "LTX session negotiate: http=${response.code} len=${body.length} " +
                        "challenge=$challenged head=${body.take(80)}"
            )
            if (challenged || !response.isSuccessful) return null
            val dto = runCatching { GSON.fromJson<LtxSessionHandshakeDto>(body) }.getOrNull() ?: return null
            return runCatching { establishLtxSession(keyPair, dto) }.onFailure {
                KLog.t(TAG).w("LTX session establish failed: ${it.message}")
            }.getOrNull()
        }
    }

    /**
     * 加密通道数据请求：带 `X-LTX-Session`/`X-LTX-Request` 头 GET [url]，
     * 响应按 v1 信封 AES-GCM 解密（AAD 绑定 method/path/status/encoding）。
     * 非信封响应按明文载荷回退（协议回滚容错）。
     */
    private fun executeCryptoRequest(url: String, session: LtxCryptoSession, web: LtxWebSession): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("User-Agent", web.userAgent)
            .header("Cookie", web.cookieHeader)
            .header(LTX_SESSION_HEADER, session.sid)
            .header(LTX_REQUEST_HEADER, newLtxRequestId())
            .browserLikeHeaders(url)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val challenged = isLtxCloudflareChallenge(body)
            KLog.t(TAG).i(
                "LTX crypto response: http=${response.code} type=${response.header("Content-Type")} " +
                        "len=${body.length} challenge=$challenged head=${body.take(80)}"
            )
            if (challenged) throw LtxChallengeException(url)
            if (response.code == 428 && isLtxCryptoSessionRequired(body)) throw LtxSessionInvalidException()
            if (!response.isSuccessful) {
                KLog.t(TAG).w("LTX crypto error body: ${body.take(300)}")
                throw IOException("HTTP ${response.code} from $url")
            }
            if (body.isEmpty()) throw IOException("Empty body from $url")
            val envelope = runCatching { GSON.fromJson<LtxEncryptedEnvelopeDto>(body) }.getOrNull()
            if (envelope != null && envelope.looksLikeEnvelope()) {
                return try {
                    decryptLtxEnvelope(envelope, session, "GET", pathAndSearch(url), response.code)
                } catch (e: Exception) {
                    KLog.t(TAG).w("LTX envelope decrypt failed: ${e.message}")
                    throw IOException("LTX envelope decrypt failed for $url", e)
                }
            }
            // 明文回退：协议回滚/变体时直接作为载荷
            KLog.t(TAG).i("LTX crypto response is plaintext, using as payload")
            return body
        }
    }

    /** URL 的 path+query（信封 AAD 绑定值；无 query 时仅 path）。 */
    private fun pathAndSearch(url: String): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return LTX_ITEMS_PATH
        return uri.path.orEmpty().ifEmpty { LTX_ITEMS_PATH } +
                (uri.rawQuery?.let { "?$it" } ?: "")
    }

    /** 模拟 SPA 同源 fetch 的请求形态（站点以此过滤自动化客户端）。 */
    private fun Request.Builder.browserLikeHeaders(url: String): Request.Builder = apply {
        runCatching {
            val uri = java.net.URI(url)
            val referer = "${uri.scheme}://${uri.host}/"
            header("Referer", referer)
            header("Sec-Fetch-Dest", "empty")
            header("Sec-Fetch-Mode", "cors")
            header("Sec-Fetch-Site", "same-origin")
        }
    }

    private fun executeJsonRequest(url: String): String {
        val session = webSession
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            // 验证凭证与 UA/IP 绑定：带会话时必须沿用求解 WebView 的 UA
            .header("User-Agent", session?.userAgent ?: NetClient.USER_AGENT)
            .browserLikeHeaders(url)
            .apply { session?.let { header("Cookie", it.cookieHeader) } }
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val challenged = isLtxCloudflareChallenge(body)
            // 410 迁移公告仅按状态码+公告体联合判定，普通响应里的特征词不触发
            val migratedUrl = if (response.code == 410) parseLtxMigratedBaseUrl(body) else null
            val cryptoSession = isLtxCryptoSessionRequired(body)
            KLog.t(TAG).i(
                "LTX response: http=${response.code} type=${response.header("Content-Type")} " +
                        "len=${body.length} challenge=$challenged migrated=${migratedUrl != null} cryptoSession=$cryptoSession"
            )
            migratedUrl?.let { throw LtxMigratedException(it) }
            if (challenged) throw LtxChallengeException(url)
            if (cryptoSession) throw LtxProtocolException(url)
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
        private const val LTX_SESSION_HEADER = "X-LTX-Session"
        private const val LTX_REQUEST_HEADER = "X-LTX-Request"

        /** 加密会话通道总尝试数（首请求 + 会话失效/挑战后各一次重建）。 */
        private const val MAX_SESSION_ATTEMPTS = 2
    }
}

/** 加密会话失效（428 cryptoSession）：由 [DefaultLtxRepository] 重建会话后重试。 */
private class LtxSessionInvalidException : IOException("LTX crypto session invalid")

/**
 * 全库拉取的迁移自愈编排：首拉命中旧域 410 迁移公告时，采纳公告新址写回
 * 偏好并重试一次（避免存量配置锁死已停用的旧域）；其余异常原样传播。
 * 公告新址与当前地址相同视为服务端公告异常，退化为普通失败。
 */
internal suspend fun fetchLtxLibraryWithMigration(
    initialBase: String,
    settings: LtxSettingsSource,
    fetchJson: suspend (base: String) -> String,
): String {
    try {
        return fetchJson(initialBase)
    } catch (e: LtxMigratedException) {
        val target = e.targetUrl
        if (target == initialBase) {
            throw IOException("LTX site announced migration to its own url: $initialBase", e)
        }
        KLog.t("LtxRepository").w("LTX site migrated: $initialBase -> $target (retrying once)")
        settings.setLtxBaseUrl(target)
        return fetchJson(target)
    }
}
