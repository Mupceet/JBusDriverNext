package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.core.site.resolveUrl
import me.jbusdriver.modern.domain.model.DataSourceType

internal object MovieRepositoryUrls {
    fun moviePage(baseUrl: String, type: DataSourceType, page: Int): String {
        val basePath = when (type) {
            DataSourceType.UNCENSORED -> "/uncensored"
            DataSourceType.XYZ -> "/xyz"
            else -> ""
        }
        return if (page == 1) {
            "$baseUrl$basePath"
        } else {
            "$baseUrl$basePath${type.prefix}$page"
        }
    }

    fun actressPage(baseUrl: String, type: DataSourceType, page: Int): String {
        val actressBaseUrl = when (type) {
            DataSourceType.UNCENSORED_ACTRESSES -> "$baseUrl/uncensored/actresses"
            else -> "$baseUrl/actresses"
        }
        return if (page == 1) actressBaseUrl else "$actressBaseUrl/$page"
    }

    fun genreCategories(baseUrl: String, type: DataSourceType): String =
        when (type) {
            DataSourceType.UNCENSORED_GENRE -> "$baseUrl/uncensored/genre"
            else -> "$baseUrl/genre"
        }

    fun resolvedPageUrl(baseUrl: String, url: String): String = resolveUrl(baseUrl, url)

    fun pageByUrl(resolvedUrl: String, page: Int): String =
        if (page == 1) resolvedUrl else "$resolvedUrl/$page"
}
