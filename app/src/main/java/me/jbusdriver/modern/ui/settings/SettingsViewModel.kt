package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.AppSettingsContract
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: AppSettingsContract,
    private val siteConfig: SiteConfig
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

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
