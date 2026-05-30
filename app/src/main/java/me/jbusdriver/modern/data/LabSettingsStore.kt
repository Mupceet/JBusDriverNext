package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import me.jbusdriver.modern.core.http.WebViewHelper.loadUrlAwait
import me.jbusdriver.modern.core.http.WebViewHelper.unescapeJsString
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class MirrorUrl(
    val url: String,
    val isReachable: Boolean = false,
    val latencyMs: Long = -1L
)

data class ScanState(
    val isScanning: Boolean = false,
    val phase: ScanPhase = ScanPhase.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val discoveredUrls: List<MirrorUrl> = emptyList(),
    val error: String? = null
)

enum class ScanPhase {
    IDLE,
    DISCOVERING,
    VERIFYING,
    DONE
}

private val Context.labSettingsDataStore by preferencesDataStore("lab_settings")

@Singleton
class LabSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.labSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val forumEnabled: StateFlow<Boolean> = dataStore.data.map { it[KEY_FORUM_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setForumEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FORUM_ENABLED] = enabled }
    }

    val autoLoadGifs: StateFlow<Boolean> = dataStore.data.map { it[KEY_AUTO_LOAD_GIFS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setAutoLoadGifs(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LOAD_GIFS] = enabled }
    }

    val selectedBaseUrl: StateFlow<String> = dataStore.data.map {
        it[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL
    }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_BASE_URL)

    val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data.map {
        it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS
    }.stateIn(scope, SharingStarted.Eagerly, PRESET_MIRROR_URLS)

    suspend fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        dataStore.edit { it[KEY_SELECTED_BASE_URL] = trimmed }
        NetClient.defaultFastUrl = trimmed
    }

    /**
     * JavaScript to extract mirror URLs from the rendered DOM.
     */
    private val extractMirrorJs = """
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

    /**
     * Scan mirror URLs by loading pages in a WebView (JS-rendered content)
     * and recursively discovering mirror addresses.
     */
    suspend fun scanMirrorUrls(
        state: MutableStateFlow<ScanState>,
        seedUrl: String
    ) {
        val allSeeds = mutableSetOf<String>()
        allSeeds.add(seedUrl.trimEnd('/'))
        val currentCached = cachedMirrorUrls.first()
        for (url in currentCached) {
            allSeeds.add(url.trimEnd('/'))
        }

        state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)

        try {
            // Phase 1: Discover URLs from all seeds in parallel via WebView
            val discovered = mutableSetOf<String>()
            discovered.addAll(allSeeds)

            withContext(Dispatchers.Main) {
                val webView = WebViewHelper.createWebView()
                try {
                    val seeds = allSeeds.toList()
                    val completed = java.util.concurrent.atomic.AtomicInteger(0)
                    // Process seeds sequentially (WebView is single-threaded),
                    // skipping failures gracefully
                    for (url in seeds) {
                        if (!coroutineContext.isActive) break
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
                            KLog.d("[Mirror] Seed $url failed, skipping: ${e.message}")
                        }
                    }
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }

            // Phase 2: Verify reachability in parallel
            val urlList = discovered.toList()
            val verified = verifyUrlsParallel(urlList, state)

            // Cache all discovered URLs (regardless of reachability)
            dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = urlList.toSet() }

            state.value = ScanState(
                isScanning = false,
                phase = ScanPhase.DONE,
                discoveredUrls = sortMirrorUrls(verified)
            )
        } catch (e: CancellationException) {
            // Save whatever was discovered before cancellation
            if (allSeeds.size > 1) {
                dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = allSeeds.toSet() }
                KLog.d("[Mirror] Scan cancelled, saved ${allSeeds.size} seeds")
            }
            state.value = ScanState()
            throw e
        }
    }

    /**
     * Re-verify reachability of cached URLs without re-scanning.
     */
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = cachedMirrorUrls.first()
        if (urls.isEmpty()) return

        state.value = ScanState(isScanning = true, phase = ScanPhase.VERIFYING)
        val verified = verifyUrlsParallel(urls, state)

        state.value = ScanState(
            isScanning = false,
            phase = ScanPhase.DONE,
            discoveredUrls = sortMirrorUrls(verified)
        )
    }

    /**
     * Verify reachability of URLs in parallel (concurrency = 6).
     */
    private suspend fun verifyUrlsParallel(
        urls: List<String>,
        state: MutableStateFlow<ScanState>
    ): List<MirrorUrl> = coroutineScope {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val deferreds = urls.mapIndexed { _, url ->
            async(Dispatchers.IO) {
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

    /**
     * Load a URL in the WebView, wait for JS to render,
     * then extract mirror URLs from the DOM.
     */
    private suspend fun loadAndExtractMirrorUrls(webView: android.webkit.WebView, url: String): List<String> {
        webView.loadUrlAwait(url)

        val result = webView.evaluateJs(extractMirrorJs)
        if (result == null || result == "null") return emptyList()

        return try {
            val jsonStr = unescapeJsString(result)
            val arr = JSONArray(jsonStr)
            val urls = (0 until arr.length()).map { arr.getString(it) }
            KLog.d("Mirror scan found ${urls.size} URLs from $url")
            urls
        } catch (e: Exception) {
            KLog.d("Mirror JS extraction failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val KEY_FORUM_ENABLED = booleanPreferencesKey("forum_enabled")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
