package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.data.cache.siteCacheKey
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.urlPath

internal object MovieRepositoryCacheKeys {
    fun moviePage(baseUrl: String, type: DataSourceType, showAll: Boolean, page: Int): String =
        siteCacheKey(baseUrl, "movie-${type.key}", "${showAll}_$page")

    fun actressPage(baseUrl: String, type: DataSourceType, page: Int): String =
        siteCacheKey(baseUrl, "actresses-${type.key}", page.toString())

    fun genreCategories(baseUrl: String, type: DataSourceType): String =
        siteCacheKey(baseUrl, "genres-v2", type.key)

    fun pageByUrl(baseUrl: String, resolvedUrl: String, showAll: Boolean, page: Int): String =
        siteCacheKey(baseUrl, "page", "${resolvedUrl.urlPath}_${showAll}_$page")

    fun actressDetail(baseUrl: String, url: String): String =
        siteCacheKey(baseUrl, "actress-detail", url.urlPath)
}
