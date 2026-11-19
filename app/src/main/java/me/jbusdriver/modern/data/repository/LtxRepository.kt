package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.http.LtxWebViewGateway
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.ltx.DEFAULT_LTX_BASE_URL
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.data.ltx.LTX_QUERY_PATH
import me.jbusdriver.modern.data.ltx.LTX_SESSION_PATH
import me.jbusdriver.modern.data.ltx.LtxChallengeException
import me.jbusdriver.modern.data.ltx.LtxCryptoSession
import me.jbusdriver.modern.data.ltx.LtxEncryptedEnvelopeDto
import me.jbusdriver.modern.data.ltx.LtxListQuery
import me.jbusdriver.modern.data.ltx.LtxMigratedException
import me.jbusdriver.modern.data.ltx.LtxProtocolException
import me.jbusdriver.modern.data.ltx.LtxSessionHandshakeDto
import me.jbusdriver.modern.data.ltx.LtxSessionRequestBodyDto
import me.jbusdriver.modern.data.ltx.LtxItemsPayloadDto
import me.jbusdriver.modern.data.ltx.LtxSnapshotExpiredException
import me.jbusdriver.modern.data.ltx.base64UrlDecode
import me.jbusdriver.modern.data.ltx.buildLtxQueryBody
import me.jbusdriver.modern.data.ltx.decryptLtxEnvelope
import me.jbusdriver.modern.data.ltx.establishLtxSession
import me.jbusdriver.modern.data.ltx.isLtxCloudflareChallenge
import me.jbusdriver.modern.data.ltx.isLtxCryptoSessionRequired
import me.jbusdriver.modern.data.ltx.ltxPublicKeyRawBase64Url
import me.jbusdriver.modern.data.ltx.newLtxClientKeyPair
import me.jbusdriver.modern.data.ltx.newLtxRequestId
import me.jbusdriver.modern.data.ltx.parseLtxItems
import me.jbusdriver.modern.data.ltx.parseLtxMigratedBaseUrl
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LTX 分页目录数据仓库接口。
 *
 * 2026-11 起站点（`ltxlib.dpdns.org`）为分页协议：列表数据经
 * `POST /api/items/query` 按页获取（服务端承担排序/状态筛选，页大小上限
 * 100），仓库按页转发、不做全量同步；详情页按番号即时查询（`q` 搜索）。
 * 所有方法为 main-safe 的 suspend，内部网络与解析在 IO 调度器执行。
 *
 * 会话一致性：[loadPage] 的 [LtxPage.snapshot] 由调用方保存并在后续翻页/
 * 换排序请求中回传，保证同一浏览会话内数据集一致；快照过期（站点报
 * `snapshotExpired`）抛 [LtxSnapshotExpiredException]，调用方丢弃快照从第
 * 0 页重查即可。首查传 `snapshot=null` 由服务端新建快照。
 *
 * 站点开启人机验证时（Turnstile 门禁，403 + `reason=verify`），改用隐形
 * WebView 加载站点让 SPA 自行完成验证，导出 Cookie 与 UA 供后续 OkHttp 快
 * 路径复用；Cookie 经系统 CookieManager 持久化，有效期内无需重复验证。
 * 加密会话协议：`POST /api/crypto/session` ECDH+HKDF 握手，请求带
 * `X-LTX-*` 会话头、响应 AES-GCM 解密（错误响应同为加密信封，按 reason 分型）。
 */
interface LtxRepository {
    /**
     * 拉取一页目录。[snapshot] 为 null 时服务端新建快照（首查），否则在既有
     * 快照内取 [offset] 起的一页。快照过期抛 [LtxSnapshotExpiredException]。
     */
    suspend fun loadPage(query: LtxListQuery, snapshot: String?, offset: Int): LtxPage

    /**
     * 按番号查找条目：优先命中本会话已加载过的条目，未命中走站点 `q` 搜索
     * （单请求即时查询，供详情页磁力弹窗）。失败静默返回 null，不打断详情主流程。
     */
    suspend fun findByCode(code: String): LtxEntry?

    /**
     * 人机验证需人工介入时发射（隐形 WebView 自动通过失败后）。
     * 调用方据此弹出可见验证界面；完成验证后调 [adoptWebSession] 并重试。
     */
    val verificationRequests: SharedFlow<Unit>

    /** 注入人工验证流程获取的会话，使后续拉取走 OkHttp 快路径。 */
    fun adoptWebSession(session: LtxWebSession)

    /** 当前 LTX 站点首页地址（人工验证 WebView 的加载目标）。 */
    suspend fun currentOrigin(): String
}

/** 一页目录：条目（同码多行已聚合）+ 当前查询总数 + 会话快照 + 库更新时间。 */
data class LtxPage(
    val entries: List<LtxEntry>,
    /** 当前查询（排序/状态筛选后）的服务端总数。 */
    val total: Int,
    /** 会话快照 id；旧协议镜像无快照为 null（整库单页，无翻页）。 */
    val snapshot: String?,
    /** 库更新时间（毫秒）；载荷缺失为 0。 */
    val updatedAtMillis: Long,
    /** 本页起始偏移（服务端回显）。 */
    val offset: Int,
)

@Singleton
class DefaultLtxRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settings: LtxSettingsSource,
    private val webGateway: LtxWebViewGateway,
) : LtxRepository {

    /** 本会话已加载过的条目（番号 → 条目），供 findByCode 免重复网络查询。 */
    private val seenEntries = ConcurrentHashMap<String, LtxEntry>()

    /** WebView 过 Cloudflare 挑战后的会话；进程内复用，Cookie/UA 必须成对生效。 */
    @Volatile
    private var webSession: LtxWebSession? = null

    /** 加密会话（迁移站协议）；进程内复用，密钥不落盘。 */
    @Volatile
    private var cryptoSession: LtxCryptoSession? = null

    private val _verificationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val verificationRequests: SharedFlow<Unit> = _verificationRequests

    override suspend fun loadPage(query: LtxListQuery, snapshot: String?, offset: Int): LtxPage =
        withContext(ioDispatcher) {
            val initialBase = settings.currentLtxBaseUrl().trimEnd('/')
            var base = initialBase
            val json = fetchLtxWithMigration(initialBase, settings) { adopted ->
                base = adopted
                if (adopted == DEFAULT_LTX_BASE_URL) {
                    fetchViaCryptoSession(
                        method = "POST",
                        url = adopted + LTX_QUERY_PATH,
                        bodyJson = buildLtxQueryBody(query = query, snapshot = snapshot, offset = offset),
                        // 首查/重查允许请求人工验证；翻页失败由首页入口统一处理，避免重复弹窗
                        allowManualVerification = offset == 0,
                    )
                } else {
                    // 旧协议镜像：单包全量载荷降级为「一大页」（无快照、无翻页）
                    fetchViaLegacy(adopted + LTX_ITEMS_PATH)
                }
            }
            parsePage(json, base, offset)
        }

    /** 载荷 JSON → [LtxPage]；分页载荷与旧单包载荷共用（total/snapshot 缺省即无翻页）。 */
    private fun parsePage(json: String, base: String, requestedOffset: Int): LtxPage {
        val payload = GSON.fromJson<LtxItemsPayloadDto>(json)
            ?.takeIf { it.items != null }
            ?: throw IOException("Malformed LTX page payload from $base (offset=$requestedOffset)")
        val entries = parseLtxItems(payload)?.entries.orEmpty()
        entries.forEach(::rememberEntry)
        return LtxPage(
            entries = entries,
            total = payload.total ?: entries.size,
            snapshot = payload.snapshot,
            updatedAtMillis = payload.updatedAt ?: 0L,
            offset = requestedOffset,
        )
    }

    private fun rememberEntry(entry: LtxEntry) {
        seenEntries[normalizeLtxCode(entry.code)] = entry
    }

    override suspend fun findByCode(code: String): LtxEntry? = withContext(ioDispatcher) {
        val normalized = normalizeLtxCode(code)
        if (normalized.isEmpty()) return@withContext null
        seenEntries[normalized]?.let { return@withContext it }
        try {
            val initialBase = settings.currentLtxBaseUrl().trimEnd('/')
            if (initialBase != DEFAULT_LTX_BASE_URL) {
                // 旧协议镜像无按码查询：整库拉一次在本地找
                val json = fetchLtxWithMigration(initialBase, settings) { b ->
                    fetchViaLegacy(b + LTX_ITEMS_PATH)
                }
                return@withContext parsePage(json, initialBase, 0).entries
                    .firstOrNull { normalizeLtxCode(it.code) == normalized }
            }
            val body = buildLtxQueryBody(
                query = LtxListQuery(q = code.trim()),
                snapshot = null,
                offset = 0,
                limit = FIND_BY_CODE_LIMIT,
            )
            val json = fetchViaCryptoSession(
                method = "POST",
                url = initialBase + LTX_QUERY_PATH,
                bodyJson = body,
                allowManualVerification = true,
            )
            val entries = parsePage(json, initialBase, 0).entries
            entries.firstOrNull { normalizeLtxCode(it.code) == normalized }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 详情页磁力查询失败静默降级（LTX 分组缺省），不打断详情主流程
            KLog.t(TAG).w("LTX findByCode failed for $normalized: ${e.message}")
            null
        }
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
     * [allowManualVerification] 仅首查/重查置位：翻页复用会话，失败由首页
     * 入口统一请求人工验证，避免重复弹窗。
     */
    private suspend fun fetchViaCryptoSession(
        method: String,
        url: String,
        bodyJson: String? = null,
        allowManualVerification: Boolean = true,
    ): String {
        var session = cryptoSession?.takeIf { !it.isExpired() && webSession != null }
            ?: establishCryptoSession(url, allowManualVerification)
        var attempt = 0
        while (true) {
            attempt++
            try {
                val web = webSession ?: error("web session missing after establishment")
                return executeCryptoRequest(method, url, bodyJson, session, web)
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
     * 加密通道数据请求：带 `X-LTX-Session`/`X-LTX-Request` 头请求 [url]
     * （GET/POST，[bodyJson] 仅 POST 携带），响应按 v1 信封 AES-GCM 解密
     * （AAD 绑定 method/path/status/encoding）。非信封响应按明文载荷回退
     * （协议回滚容错）。
     */
    private fun executeCryptoRequest(
        method: String,
        url: String,
        bodyJson: String?,
        session: LtxCryptoSession,
        web: LtxWebSession,
    ): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("User-Agent", web.userAgent)
            .header("Cookie", web.cookieHeader)
            .header(LTX_SESSION_HEADER, session.sid)
            .header(LTX_REQUEST_HEADER, newLtxRequestId())
            .apply { if (bodyJson != null) header("Content-Type", "application/json") }
            .method(method, bodyJson?.toRequestBody(null))
            .browserLikeHeaders(url)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val challenged = isLtxCloudflareChallenge(body)
            KLog.t(TAG).i(
                "LTX crypto response: method=$method http=${response.code} type=${response.header("Content-Type")} " +
                        "len=${body.length} challenge=$challenged head=${body.take(80)}"
            )
            if (challenged) throw LtxChallengeException(url)
            if (response.code == 428 && isLtxCryptoSessionRequired(body)) throw LtxSessionInvalidException()
            if (!response.isSuccessful) {
                throw cryptoRequestError(method, url, body, session, response.code)
            }
            if (body.isEmpty()) throw IOException("Empty body from $url")
            val envelope = runCatching { GSON.fromJson<LtxEncryptedEnvelopeDto>(body) }.getOrNull()
            if (envelope != null && envelope.looksLikeEnvelope()) {
                return try {
                    decryptLtxEnvelope(envelope, session, method, pathAndSearch(url), response.code)
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

    /**
     * 业务错误分型：错误响应同为加密信封（解密后读 `reason`），明文 JSON 直接
     * 读。`verify` → 挑战（会话全量重建）；`snapshotExpired` → 快照失效（调用
     * 方丢快照重查）；其余按 HTTP 状态失败。
     */
    private fun cryptoRequestError(
        method: String,
        url: String,
        body: String,
        session: LtxCryptoSession,
        httpStatus: Int,
    ): Exception {
        val reason = runCatching {
            val envelope = GSON.fromJson<LtxEncryptedEnvelopeDto>(body)
            if (envelope != null && envelope.looksLikeEnvelope()) {
                decryptLtxEnvelope(envelope, session, method, pathAndSearch(url), httpStatus)
            } else {
                body
            }
        }.getOrNull()
            ?.let { json -> runCatching { GSON.fromJson<Map<String, String>>(json) }.getOrNull()?.get("reason") }
        KLog.t(TAG).w("LTX crypto error: http=$httpStatus reason=$reason body=${body.take(200)}")
        return when (reason) {
            "verify" -> LtxChallengeException(url)
            "snapshotExpired" -> LtxSnapshotExpiredException()
            else -> IOException("HTTP $httpStatus from $url (reason=$reason)")
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

    // endregion

    companion object {
        private const val TAG = "LtxRepository"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val LTX_SESSION_HEADER = "X-LTX-Session"
        private const val LTX_REQUEST_HEADER = "X-LTX-Request"

        /** 加密会话通道总尝试数（首请求 + 会话失效/挑战后各一次重建）。 */
        private const val MAX_SESSION_ATTEMPTS = 2

        /** 详情页按码查询返回上限（番号查询实测至多命中数条，取整页足够）。 */
        private const val FIND_BY_CODE_LIMIT = 20
    }
}

/** 加密会话失效（428 cryptoSession）：由 [DefaultLtxRepository] 重建会话后重试。 */
private class LtxSessionInvalidException : IOException("LTX crypto session invalid")

/**
 * 拉取的迁移自愈编排：请求命中旧域 410 迁移公告时，采纳公告新址写回偏好并
 * 重试一次（避免存量配置锁死已停用的旧域）；其余异常原样传播。公告新址与
 * 当前地址相同视为服务端公告异常，退化为普通失败。
 */
internal suspend fun fetchLtxWithMigration(
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
