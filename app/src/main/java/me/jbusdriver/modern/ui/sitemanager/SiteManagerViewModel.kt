package me.jbusdriver.modern.ui.sitemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.site.DEFAULT_SITE_URL
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.site.normalizeBaseUrl
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.SiteSettingsSource
import me.jbusdriver.modern.ui.UserMessage
import javax.inject.Inject

enum class SiteGroup { PRESET, SCANNED, CUSTOM }

/** 正在运行的后台操作：区分「取消」入口归属——扫描槽只在 SCAN 时、测速按钮只在 VERIFY 时显示取消。 */
enum class ScanOp { NONE, SCAN, VERIFY }

data class SiteEntry(val url: String, val group: SiteGroup)

data class SiteManagerUiState(
    val entries: List<SiteEntry> = emptyList(),
    val selectedUrl: String = DEFAULT_SITE_URL,
    val isScanning: Boolean = false,
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val measurements: Map<String, MirrorUrl> = emptyMap(),
    val runningOp: ScanOp = ScanOp.NONE,
)

/**
 * 分组派生站点列表：去重优先级 官方 > 自定义 > 扫描；
 * 组内排序 可达(延迟升序) → 未测量(按 URL) → 不可达。
 */
internal fun deriveSiteEntries(
    presetUrl: String,
    scannedUrls: List<String>,
    customUrls: List<String>,
    measurements: Map<String, MirrorUrl>
): List<SiteEntry> {
    val claimed = mutableSetOf<String>()
    fun claim(urls: List<String>, group: SiteGroup): List<SiteEntry> =
        urls.filter { claimed.add(it) }.map { SiteEntry(it, group) }

    fun sortKey(url: String): Long {
        val measured = measurements[url] ?: return Long.MAX_VALUE - 1
        return if (measured.isReachable) measured.latencyMs else Long.MAX_VALUE
    }

    fun sorted(entries: List<SiteEntry>) =
        entries.sortedWith(compareBy({ sortKey(it.url) }, { it.url }))

    // 认领顺序 = 去重优先级：官方 > 自定义 > 扫描；输出分区顺序为 官方 + 自定义 + 扫描（自定义卡常显在第二位）。
    val preset = claim(listOf(presetUrl), SiteGroup.PRESET)
    val custom = claim(customUrls, SiteGroup.CUSTOM)
    val scanned = claim(scannedUrls, SiteGroup.SCANNED)
    return preset + sorted(custom) + sorted(scanned)
}

@HiltViewModel
class SiteManagerViewModel @Inject constructor(
    private val store: SiteSettingsSource,
    private val siteConfig: SiteConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** 保存自定义网址后的可达性探测（仅提示）；测试可替换以避免真实网络。 */
    internal var reachabilityProbe: suspend (String) -> Long = NetClient::checkReachable

    private val _scanState = MutableStateFlow(ScanState())
    private val _runningOp = MutableStateFlow(ScanOp.NONE)
    private val _measurements = MutableStateFlow<Map<String, MirrorUrl>>(emptyMap())

    /** 扫描状态与运行操作捆绑（combine 最多 5 路合流的内层包装）。 */
    private data class ScanProgress(val state: ScanState, val op: ScanOp)

    val uiState: StateFlow<SiteManagerUiState> = combine(
        store.selectedBaseUrl,
        store.cachedMirrorUrls,
        store.customBaseUrls,
        _measurements,
        combine(_scanState, _runningOp) { state, op -> ScanProgress(state, op) }
    ) { selected, cached, custom, measurements, progress ->
        SiteManagerUiState(
            entries = deriveSiteEntries(DEFAULT_SITE_URL, cached, custom, measurements),
            selectedUrl = selected,
            isScanning = progress.state.isScanning,
            scanPhase = progress.state.phase,
            scannedCount = progress.state.scannedCount,
            totalCount = progress.state.totalCount,
            currentUrl = progress.state.currentUrl,
            measurements = measurements,
            runningOp = progress.op
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteManagerUiState())

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun selectSite(url: String) {
        viewModelScope.launch { store.selectUrl(url); siteConfig.baseUrl = url }
    }

    fun deleteEntry(entry: SiteEntry) {
        if (entry.url == store.selectedBaseUrl.value) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.site_delete_selected_first)) }
            return
        }
        viewModelScope.launch {
            when (entry.group) {
                SiteGroup.PRESET -> Unit
                SiteGroup.SCANNED -> store.removeScannedUrl(entry.url)
                SiteGroup.CUSTOM -> store.removeCustomUrl(entry.url)
            }
        }
    }

    /** 保存自定义网址；校验仅格式+跨组重复（可达性不作保存条件），保存后探测仅提示。 */
    fun addCustomUrl(rawUrl: String) {
        viewModelScope.launch {
            if (!store.addCustomUrl(rawUrl)) {
                _messages.emit(UserMessage(R.string.site_add_invalid_or_duplicate))
                return@launch
            }
            val latency = reachabilityProbe(normalizeBaseUrl(rawUrl))
            _messages.emit(
                if (latency >= 0) UserMessage(R.string.site_add_saved_reachable, listOf(latency))
                else UserMessage(R.string.site_add_saved_unreachable)
            )
        }
    }

    fun startScan() = launchScan(ScanOp.SCAN) { store.scanMirrorUrls(_scanState) }
    fun startVerify() = launchScan(ScanOp.VERIFY) { store.verifyMirrorUrls(_scanState) }

    /** 取消進行中的掃描：取消後台 Job 並重置掃描狀態。 */
    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanState()
    }

    private var scanJob: Job? = null

    private fun launchScan(op: ScanOp, block: suspend () -> Unit) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch(ioDispatcher) {
            _runningOp.value = op
            try {
                _scanState.value = ScanState()
                block()
                _measurements.value = _scanState.value.discoveredUrls.associateBy { it.url }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.t(TAG).e("mirror scan failed", e)
                _scanState.value = ScanState()
            } finally {
                _runningOp.value = ScanOp.NONE
            }
        }
    }

    companion object {
        private const val TAG = "SiteManagerViewModel"
    }
}
