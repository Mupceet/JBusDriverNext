package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.ScanState
import javax.inject.Inject

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanState.value = ScanState()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                store.scanMirrorUrls(_scanState, store.selectedBaseUrl.value)
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

    fun selectUrl(url: String) {
        store.selectUrl(url)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
