package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.LabSettingsStoreContract
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase
import me.jbusdriver.modern.data.mirror.ScanState
import javax.inject.Inject

data class LabSettingsUiState(
    val forumEnabled: Boolean = false,
    val autoLoadGifs: Boolean = false,
    val forumFloorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
    val selectedBaseUrl: String = "",
    val cachedMirrorUrls: List<String> = emptyList(),
    val scanState: ScanState = ScanState(),
    val mirrorUrls: List<MirrorUrl> = emptyList(),
    val hasCachedUrls: Boolean = false
)

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    private val store: LabSettingsStoreContract,
    private val siteConfig: SiteConfig
) : ViewModel() {
    private var scanDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        store: LabSettingsStoreContract,
        siteConfig: SiteConfig,
        scanDispatcher: CoroutineDispatcher
    ) : this(store, siteConfig) {
        this.scanDispatcher = scanDispatcher
    }

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val settingsState = combine(
        store.forumEnabled,
        store.autoLoadGifs,
        store.forumFloorOrder,
        store.selectedBaseUrl,
        store.cachedMirrorUrls
    ) { forumEnabled, autoLoadGifs, forumFloorOrder, selectedBaseUrl, cachedMirrorUrls ->
        val scanState = _scanState.value
        LabSettingsUiState(
            forumEnabled = forumEnabled,
            autoLoadGifs = autoLoadGifs,
            forumFloorOrder = forumFloorOrder,
            selectedBaseUrl = selectedBaseUrl,
            cachedMirrorUrls = cachedMirrorUrls,
            scanState = scanState,
            mirrorUrls = buildMirrorUrls(scanState, cachedMirrorUrls),
            hasCachedUrls = hasCachedUrls(scanState, cachedMirrorUrls)
        )
    }

    val uiState: StateFlow<LabSettingsUiState> = combine(settingsState, _scanState) { settings, scanState ->
        settings.copy(
            scanState = scanState,
            mirrorUrls = buildMirrorUrls(scanState, settings.cachedMirrorUrls),
            hasCachedUrls = hasCachedUrls(scanState, settings.cachedMirrorUrls)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LabSettingsUiState())

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanState.value = ScanState()
        scanJob = viewModelScope.launch(scanDispatcher) {
            try {
                val currentUrl = store.selectedBaseUrl.first()
                store.scanMirrorUrls(_scanState, currentUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanState(error = R.string.scan_failed)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState()
    }

    fun startVerify() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch(scanDispatcher) {
            try {
                store.verifyMirrorUrls(_scanState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanState(error = R.string.verify_failed)
            }
        }
    }

    fun selectUrl(url: String) {
        viewModelScope.launch {
            store.selectUrl(url)
            siteConfig.baseUrl = url
        }
    }

    fun setForumEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setForumEnabled(enabled) }
    }

    fun setAutoLoadGifs(enabled: Boolean) {
        viewModelScope.launch { store.setAutoLoadGifs(enabled) }
    }

    fun setForumFloorOrder(order: ForumFloorOrder) {
        viewModelScope.launch { store.setForumFloorOrder(order) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    private fun buildMirrorUrls(scanState: ScanState, cachedMirrorUrls: List<String>): List<MirrorUrl> {
        return if (scanState.phase == ScanPhase.DONE) {
            scanState.discoveredUrls
        } else if (!scanState.isScanning && cachedMirrorUrls.isNotEmpty()) {
            val defaultHost = "www.javbus.com"
            cachedMirrorUrls.map { MirrorUrl(it, true) }.sortedWith(
                compareBy<MirrorUrl> { it.url.contains(defaultHost, ignoreCase = true).not() }
                    .thenBy { if (it.isReachable) it.latencyMs else Long.MAX_VALUE }
                    .thenBy { it.url }
            )
        } else {
            emptyList()
        }
    }

    private fun hasCachedUrls(scanState: ScanState, cachedMirrorUrls: List<String>): Boolean =
        buildMirrorUrls(scanState, cachedMirrorUrls).isNotEmpty() || cachedMirrorUrls.isNotEmpty()
}
