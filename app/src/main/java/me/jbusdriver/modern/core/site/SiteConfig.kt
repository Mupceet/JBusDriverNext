package me.jbusdriver.modern.core.site

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.LabSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

interface SiteConfig {
    var baseUrl: String

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

@Singleton
class DefaultSiteConfig @Inject constructor(
    private val labSettingsStore: LabSettingsStore
) : SiteConfig {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _baseUrl: String = LabSettingsStore.DEFAULT_BASE_URL

    init {
        scope.launch {
            val persisted = labSettingsStore.selectedBaseUrl.first()
            if (persisted.isNotBlank()) {
                _baseUrl = persisted.trimEnd('/')
            }
        }
    }

    override var baseUrl: String
        get() = _baseUrl
        set(value) { _baseUrl = value.trimEnd('/') }

    suspend fun updateBaseUrl(url: String) {
        _baseUrl = url.trimEnd('/')
    }

    override fun resolve(pathOrUrl: String): String = resolveUrl(baseUrl, pathOrUrl)
}
