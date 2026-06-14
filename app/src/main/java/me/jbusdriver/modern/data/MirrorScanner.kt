package me.jbusdriver.modern.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import me.jbusdriver.modern.core.http.WebViewHelper.loadUrlAwait
import me.jbusdriver.modern.core.http.WebViewHelper.unescapeJsString
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MirrorScanner"

private val EXTRACT_MIRROR_JS = """
    (function() {
        var urls = [];
        document.querySelectorAll('strong').forEach(function(el) {
            var text = el.textContent;
            if (text.indexOf('防屏蔽地址') !== -1 || text.indexOf('永久域名') !== -1) {
                var parent = el.parentElement;
                var link = parent ? parent.querySelector('a[href]') : null;
                if (link && link.href && link.href.indexOf('http') === 0) {
                    urls.push(link.href);
                }
            }
        });
        return JSON.stringify(urls);
    })()
"""

@Singleton
class MirrorScanner @Inject constructor() {

    suspend fun scanAndVerify(
        state: MutableStateFlow<ScanState>,
        seedUrl: String,
        cachedUrls: List<String>
    ): Set<String> {
        val allSeeds = mutableSetOf<String>()
        allSeeds.add(seedUrl.trimEnd('/'))
        for (url in cachedUrls) {
            allSeeds.add(url.trimEnd('/'))
        }

        state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)

        try {
            val discovered = mutableSetOf<String>()
            discovered.addAll(allSeeds)

            withContext(Dispatchers.Main) {
                val webView = WebViewHelper.createWebView()
                try {
                    val seeds = allSeeds.toList()
                    val completed = java.util.concurrent.atomic.AtomicInteger(0)
                    for (url in seeds) {
                        if (!currentCoroutineContext().isActive) break
                        state.value = ScanState(
                            isScanning = true,
                            phase = ScanPhase.DISCOVERING,
                            scannedCount = completed.incrementAndGet(),
                            totalCount = seeds.size,
                            currentUrl = url
                        )
                        try {
                            val mirrorUrls = loadAndExtractMirrorUrls(webView, url)
                            for (found in mirrorUrls) {
                                val trimmed = found.trimEnd('/')
                                if (trimmed !in discovered) {
                                    discovered.add(trimmed)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            KLog.d("[$TAG] Seed $url failed, skipping: ${e.message}")
                        }
                    }
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }

            val urlList = discovered.toList()
            val verified = verifyUrlsParallel(urlList, state)

            state.value = ScanState(
                isScanning = false,
                phase = ScanPhase.DONE,
                discoveredUrls = sortMirrorUrls(verified)
            )
            return urlList.toSet()
        } catch (e: CancellationException) {
            state.value = ScanState()
            throw e
        }
    }

    suspend fun verifyOnly(
        state: MutableStateFlow<ScanState>,
        cachedUrls: List<String>
    ) {
        if (cachedUrls.isEmpty()) return

        state.value = ScanState(isScanning = true, phase = ScanPhase.VERIFYING)
        val verified = verifyUrlsParallel(cachedUrls, state)

        state.value = ScanState(
            isScanning = false,
            phase = ScanPhase.DONE,
            discoveredUrls = sortMirrorUrls(verified)
        )
    }

    private suspend fun verifyUrlsParallel(
        urls: List<String>,
        state: MutableStateFlow<ScanState>
    ): List<MirrorUrl> = coroutineScope {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = Semaphore(6)
        val deferreds = urls.map { url ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val latency = NetClient.checkReachable(url)
                    val done = completed.incrementAndGet()
                    state.value = ScanState(
                        isScanning = true,
                        phase = ScanPhase.VERIFYING,
                        scannedCount = done,
                        totalCount = urls.size,
                        currentUrl = url
                    )
                    MirrorUrl(url, latency >= 0, latency)
                }
            }
        }
        deferreds.awaitAll()
    }

    private fun sortMirrorUrls(urls: List<MirrorUrl>): List<MirrorUrl> {
        val defaultHost = "www.javbus.com"
        return urls.sortedWith(
            compareBy<MirrorUrl> { it.url.contains(defaultHost, ignoreCase = true).not() }
                .thenBy { if (it.isReachable) it.latencyMs else Long.MAX_VALUE }
                .thenBy { it.url }
        )
    }

    private suspend fun loadAndExtractMirrorUrls(
        webView: android.webkit.WebView,
        url: String
    ): List<String> {
        webView.loadUrlAwait(url)

        val result = webView.evaluateJs(EXTRACT_MIRROR_JS)
        if (result == null || result == "null") return emptyList()

        return try {
            val jsonStr = unescapeJsString(result)
            val arr = JSONArray(jsonStr)
            val urls = (0 until arr.length()).map { arr.getString(it) }
            KLog.d("[$TAG] Found ${urls.size} URLs from $url")
            urls
        } catch (e: Exception) {
            KLog.d("[$TAG] JS extraction failed: ${e.message}")
            emptyList()
        }
    }
}
