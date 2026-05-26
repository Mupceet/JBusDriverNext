package me.jbusdriver.modern.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
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

    private val mirrorUrlRegex =
        """(?:防屏蔽地址|永久域名)[：:]\s*</strong>\s*<a\s+href="(https?://[^"]+)"""".toRegex()

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

        // Phase 1: Discover URLs by crawling
        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            val url = queue.removeFirst()
            if (url in scanned) continue
            scanned.add(url)

            state.value = state.value.copy(
                scannedCount = scanned.size,
                totalCount = discovered.size,
                currentUrl = url
            )

            try {
                val html = NetClient.fetchHtml(url)
                val matches = mirrorUrlRegex.findAll(html)
                for (match in matches) {
                    val found = match.groupValues[1].trimEnd('/')
                    if (found !in discovered) {
                        discovered.add(found)
                        queue.add(found)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.d("Scan failed for $url: ${e.message}")
            }
        }

        // Phase 2: Verify reachability
        val urlList = discovered.toList()
        val verified = mutableListOf<MirrorUrl>()

        for ((index, url) in urlList.withIndex()) {
            coroutineContext.ensureActive()
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

    companion object {
        private const val PREFS_NAME = "lab_settings"
        private const val KEY_FORUM_ENABLED = "forum_enabled"
        private const val KEY_SELECTED_BASE_URL = "selected_base_url"
        private const val KEY_CACHED_MIRROR_URLS = "cached_mirror_urls"
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
    }
}
