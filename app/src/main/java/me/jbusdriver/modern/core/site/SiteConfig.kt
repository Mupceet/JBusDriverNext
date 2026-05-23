package me.jbusdriver.modern.core.site

import javax.inject.Inject
import javax.inject.Singleton

interface SiteConfig {
    var baseUrl: String

    fun resolve(pathOrUrl: String): String

    fun referer(): String = "${baseUrl.trimEnd('/')}/"
}

internal object SiteConfigStore {
    @Volatile
    var baseUrl: String = "https://www.javbus.com"
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
