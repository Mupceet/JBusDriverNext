package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.ScanState
import me.jbusdriver.modern.data.UiPrefsStore
import javax.inject.Inject

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore,
    val uiPrefsStore: UiPrefsStore
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanState.value = ScanState()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUrl = store.selectedBaseUrl.first()
                store.scanMirrorUrls(_scanState, currentUrl)
            } catch (e: Exception) {
                _scanState.value = ScanState(error = e.message ?: "掃描失敗")
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
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                store.verifyMirrorUrls(_scanState)
            } catch (e: Exception) {
                _scanState.value = ScanState(error = e.message ?: "檢測失敗")
            }
        }
    }

    fun selectUrl(url: String) {
        viewModelScope.launch { store.selectUrl(url) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
