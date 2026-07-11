package me.jbusdriver.modern.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.settings.AppSettingsContract
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: AppSettingsContract,
    private val siteConfig: SiteConfig,
    private val localVideoRepository: LocalVideoRepository,
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val localVideoSummary: StateFlow<LocalVideoSummary> =
        localVideoRepository.observeSummary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalVideoSummary())

    val showUncollectedLocal: StateFlow<Boolean> =
        localVideoRepository.observeShowUncollectedLocal()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLocalVideoFolder(uri: Uri) {
        viewModelScope.launch { localVideoRepository.setFolder(uri) }
    }

    fun clearLocalVideoFolder() {
        viewModelScope.launch { localVideoRepository.clearFolder() }
    }

    fun setShowUncollectedLocal(value: Boolean) {
        viewModelScope.launch { localVideoRepository.setShowUncollectedLocal(value) }
    }

    // region Network scan (delegates to store -> MirrorScanner)
    fun startScan() = launchScan {
        store.scanMirrorUrls(_scanState, store.selectedBaseUrl.first())
    }
    fun startVerify() = launchScan { store.verifyMirrorUrls(_scanState) }
    fun cancelScan() { _scanState.value = ScanState() }
    fun selectUrl(url: String) {
        viewModelScope.launch { store.selectUrl(url); siteConfig.baseUrl = url }
    }
    private fun launchScan(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState()
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.t(TAG).e("mirror scan failed", e)
                _scanState.value = ScanState()
            }
        }
    }
    // endregion

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
