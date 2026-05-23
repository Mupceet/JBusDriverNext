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
        val first = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!first.isDriverVerify()) return first.body

        KLog.w("HtmlClient hit driver verification for $url; warming browser session")
        browserSessionClient.warmUp()
        val retry = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!retry.isDriverVerify()) return retry.body

        KLog.w("HtmlClient retry still hit verification for $url; falling back to browser fetch")
        return browserSessionClient.fetchDocument(url).html()
    }

    override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
        val first = NetClient.fetchHtmlResponse(url, showAll)
        if (!first.isDriverVerify()) {
            return withContext(Dispatchers.Default) { Jsoup.parse(first.body, first.finalUrl) }
        }

        KLog.w("HtmlClient hit driver verification for $url; warming browser session")
        browserSessionClient.warmUp()
        val retry = NetClient.fetchHtmlResponse(url, showAll)
        if (!retry.isDriverVerify()) {
            return withContext(Dispatchers.Default) { Jsoup.parse(retry.body, retry.finalUrl) }
        }

        KLog.w("HtmlClient retry still hit verification for $url; falling back to browser fetch")
        return browserSessionClient.fetchDocument(url)
    }

    private fun NetClient.HtmlResponse.isDriverVerify(): Boolean =
        finalUrl.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("driver-verify", ignoreCase = true)
}
