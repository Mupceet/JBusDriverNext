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

    override fun resolve(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http")) return pathOrUrl
        val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
        return baseUrl.trimEnd('/') + prefix + pathOrUrl
    }
}
