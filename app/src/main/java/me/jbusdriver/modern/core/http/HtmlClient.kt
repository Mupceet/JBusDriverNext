package me.jbusdriver.modern.core.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

interface HtmlClient {
    val imageOkHttpClient: OkHttpClient

    suspend fun fetchHtml(url: String, showAll: Boolean = false, referer: String? = null): String

    suspend fun fetchDocument(url: String, showAll: Boolean = false): Document
}

@Singleton
class DefaultHtmlClient @Inject constructor(
    private val browserSessionClient: BrowserSessionClient
) : HtmlClient {
    override val imageOkHttpClient: OkHttpClient
        get() = NetClient.glideOkHttpClient

    override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?): String {
        val response = fetchWithVerifyFallback(url, showAll, referer)
        return response.body
    }

    override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
        val response = fetchWithVerifyFallback(url, showAll, referer = null)
        return withContext(Dispatchers.Default) { Jsoup.parse(response.body, response.finalUrl) }
    }

    private suspend fun fetchWithVerifyFallback(
        url: String,
        showAll: Boolean,
        referer: String?
    ): NetClient.HtmlResponse {
        val first = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!first.isDriverVerify()) return first

        KLog.w("HtmlClient hit driver verification for $url; warming browser session")
        browserSessionClient.warmUp()
        val retry = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!retry.isDriverVerify()) return retry

        KLog.w("HtmlClient retry still hit verification for $url; falling back to browser fetch")
        val doc = browserSessionClient.fetchDocument(url)
        return NetClient.HtmlResponse(doc.location(), doc.html())
    }

    private fun NetClient.HtmlResponse.isDriverVerify(): Boolean =
        finalUrl.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("driver-verify", ignoreCase = true)
}
