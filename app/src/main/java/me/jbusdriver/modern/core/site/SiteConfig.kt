package me.jbusdriver.modern.core.site

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_SITE_URL = "https://www.javbus.com"

interface SitePreferenceSource {
    suspend fun currentSelectedBaseUrl(): String
}

interface SiteConfig {
    var baseUrl: String

    suspend fun awaitReady() = Unit

    fun resolve(pathOrUrl: String): String

    fun referer(): String = "${baseUrl.trimEnd('/')}/"
}

/**
 * 将相对路径或完整 URL 解析为基于 [baseUrl] 的绝对 URL。
 *
 * - 已是 http(s) 开头的完整 URL：原样返回。
 * - 以 "/" 开头的绝对路径：拼到 baseUrl 根下。
 * - 其余相对路径：补一个 "/" 再拼接。
 * - baseUrl 末尾的 "/" 会被裁剪，避免出现双斜杠。
 */
internal fun resolveUrl(baseUrl: String, pathOrUrl: String): String {
    if (pathOrUrl.startsWith("http")) return pathOrUrl
    val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
    return baseUrl.trimEnd('/') + prefix + pathOrUrl
}

internal fun normalizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase()
    val host = uri?.host?.lowercase()
    if (scheme == null || host == null) return trimmed

    val port = uri.port.takeIf { it != -1 }?.let { ":$it" }.orEmpty()
    val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotEmpty() }.orEmpty()
    return "$scheme://$host$port$path"
}

@Singleton
class DefaultSiteConfig(
    private val preferenceSource: SitePreferenceSource,
    scope: CoroutineScope,
    private val cancelScopeOnReady: Boolean = false
) : SiteConfig {
    private val ready = CompletableDeferred<Unit>()

    @Volatile
    private var _baseUrl: String = DEFAULT_SITE_URL

    init {
        scope.launch {
            try {
                val persisted = preferenceSource.currentSelectedBaseUrl()
                _baseUrl = persisted.takeIf { it.isNotBlank() }
                    ?.let(::normalizeBaseUrl)
                    ?: DEFAULT_SITE_URL
            } catch (_: Exception) {
                _baseUrl = DEFAULT_SITE_URL
            } finally {
                ready.complete(Unit)
                if (cancelScopeOnReady) {
                    scope.cancel()
                }
            }
        }
    }

    @Inject
    constructor(
        preferenceSource: SitePreferenceSource
    ) : this(
        preferenceSource = preferenceSource,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        cancelScopeOnReady = true
    )

    override var baseUrl: String
        get() = _baseUrl
        set(value) {
            _baseUrl = normalizeBaseUrl(value)
        }

    suspend fun updateBaseUrl(url: String) {
        _baseUrl = normalizeBaseUrl(url)
    }

    override suspend fun awaitReady() {
        ready.await()
    }

    override fun resolve(pathOrUrl: String): String = resolveUrl(baseUrl, pathOrUrl)
}
