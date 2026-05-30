package me.jbusdriver.modern.core.site

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    @Volatile
    override var baseUrl: String = runBlocking {
        labSettingsStore.selectedBaseUrl.first()
    }
        private set

    suspend fun updateBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    override fun resolve(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http")) return pathOrUrl
        val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
        return baseUrl.trimEnd('/') + prefix + pathOrUrl
    }
}
