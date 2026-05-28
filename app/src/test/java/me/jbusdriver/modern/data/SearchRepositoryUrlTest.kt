package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.SearchType
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepositoryUrlTest {
    @Test
    fun searchMovies_encodesQueryBeforeBuildingUrl() = runTest {
        var capturedUrl = ""
        val repository = DefaultSearchRepository(
            htmlClient = object : HtmlClient {
                override val imageOkHttpClient: OkHttpClient = OkHttpClient()
                override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?) = ""
                override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
                    capturedUrl = url
                    return Jsoup.parse("""<html><body></body></html>""", url)
                }
            },
            cacheStore = memoryCacheStore(),
            siteConfig = object : SiteConfig {
                override var baseUrl: String = "https://example.test"
                override fun resolve(pathOrUrl: String) = pathOrUrl
            }
        )

        repository.searchMovies(SearchType.CENSORED, "演員 1", page = 1, forceRefresh = true)

        assertEquals("https://example.test/search/%E6%BC%94%E5%93%A1%201", capturedUrl)
    }

    private fun memoryCacheStore(): CacheStore {
        val memory = mutableMapOf<String, String>()
        return object : CacheStore {
            override fun readMemory(key: String) = memory[key]
            override fun writeMemory(key: String, value: String) {
                memory[key] = value
            }
            override suspend fun readDisk(key: String): String? = null
            override suspend fun writeDisk(key: String, value: String) = Unit
        }
    }
}
