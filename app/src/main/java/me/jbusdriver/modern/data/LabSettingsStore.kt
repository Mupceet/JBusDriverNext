package me.jbusdriver.modern.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.jbusdriver.modern.JBus
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
    val isReachable: Boolean = false
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

@Singleton
class LabSettingsStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences(PREFS_NAME, 0)

    private val _forumEnabled = MutableStateFlow(prefs.getBoolean(KEY_FORUM_ENABLED, false))
    val forumEnabled: StateFlow<Boolean> = _forumEnabled.asStateFlow()

    fun setForumEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FORUM_ENABLED, enabled) }
        _forumEnabled.value = enabled
    }

    private val _selectedBaseUrl = MutableStateFlow(
        prefs.getString(KEY_SELECTED_BASE_URL, null) ?: DEFAULT_BASE_URL
    )
    val selectedBaseUrl: StateFlow<String> = _selectedBaseUrl.asStateFlow()

    private val _cachedMirrorUrls = MutableStateFlow(
        prefs.getStringSet(KEY_CACHED_MIRROR_URLS, null)?.toList() ?: emptyList()
    )
    val cachedMirrorUrls: StateFlow<List<String>> = _cachedMirrorUrls.asStateFlow()

    fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        prefs.edit { putString(KEY_SELECTED_BASE_URL, trimmed) }
        _selectedBaseUrl.value = trimmed
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
        val discovered = mutableSetOf<String>()
        val scanned = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(seedUrl.trimEnd('/'))
        discovered.add(seedUrl.trimEnd('/'))

        state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)

        // Phase 1: Discover URLs via WebView
        withContext(Dispatchers.Main) {
            val webView = WebViewHelper.createWebView()
            try {
                while (queue.isNotEmpty()) {
                    if (!coroutineContext.isActive) break
                    val url = queue.removeFirst()
                    if (url in scanned) continue
                    scanned.add(url)

                    state.value = state.value.copy(
                        scannedCount = scanned.size,
                        totalCount = discovered.size,
                        currentUrl = url
                    )

                    try {
                        val mirrorUrls = loadAndExtractMirrorUrls(webView, url)
                        for (found in mirrorUrls) {
                            val trimmed = found.trimEnd('/')
                            if (trimmed !in discovered) {
                                discovered.add(trimmed)
                                queue.add(trimmed)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        KLog.d("Scan failed for $url: ${e.message}")
                    }
                }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }

        // Phase 2: Verify reachability
        val urlList = discovered.toList()
        val verified = mutableListOf<MirrorUrl>()

        for ((index, url) in urlList.withIndex()) {
            if (!coroutineContext.isActive) break
            state.value = state.value.copy(
                phase = ScanPhase.VERIFYING,
                scannedCount = index + 1,
                totalCount = urlList.size,
                currentUrl = url
            )
            val reachable = NetClient.checkReachable(url)
            verified.add(MirrorUrl(url, reachable))
        }

        // Cache and complete
        val reachableUrls = verified.filter { it.isReachable }.map { it.url }.toSet()
        prefs.edit { putStringSet(KEY_CACHED_MIRROR_URLS, reachableUrls) }
        _cachedMirrorUrls.value = reachableUrls.toList()

        state.value = ScanState(
            isScanning = false,
            phase = ScanPhase.DONE,
            discoveredUrls = verified
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
        private const val PREFS_NAME = "lab_settings"
        private const val KEY_FORUM_ENABLED = "forum_enabled"
        private const val KEY_SELECTED_BASE_URL = "selected_base_url"
        private const val KEY_CACHED_MIRROR_URLS = "cached_mirror_urls"
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
    }
}
