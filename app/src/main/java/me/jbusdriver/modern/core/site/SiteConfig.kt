package me.jbusdriver.modern.core.site

import me.jbusdriver.modern.JBus
import javax.inject.Inject
import javax.inject.Singleton

interface SiteConfig {
    var baseUrl: String

    fun resolve(pathOrUrl: String): String

    fun referer(): String = "${baseUrl.trimEnd('/')}/"
}

internal object SiteConfigStore {
    @Volatile
    var baseUrl: String = loadPersistedUrl()
        internal set

    private fun loadPersistedUrl(): String {
        return try {
            val prefs = JBus.getSharedPreferences("lab_settings", 0)
            prefs.getString("selected_base_url", null) ?: "https://www.javbus.com"
        } catch (_: Exception) {
            "https://www.javbus.com"
        }
    }
}

@Singleton
class DefaultSiteConfig @Inject constructor() : SiteConfig {
    override var baseUrl: String
        get() = SiteConfigStore.baseUrl
        set(value) {
            SiteConfigStore.baseUrl = value.trimEnd('/')
        }

    override fun resolve(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http")) return pathOrUrl
        val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
        return baseUrl.trimEnd('/') + prefix + pathOrUrl
    }
}
