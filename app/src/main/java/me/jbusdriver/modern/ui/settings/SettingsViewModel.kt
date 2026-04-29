package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.SettingsRepository
import javax.inject.Inject

data class SettingsUiState(
    val baseUrl: String = "",
    val availableUrls: List<String> = emptyList(),
    val isUpdating: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            baseUrl = repository.getCurrentUrl(),
            availableUrls = repository.getAvailableUrls()
        )
    }

    fun updateUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true)
            repository.updateUrl(url)
            _uiState.value = _uiState.value.copy(
                baseUrl = url,
                isUpdating = false
            )
        }
    }

    fun refreshUrls() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true)
            repository.fetchAnnounce()
            _uiState.value = SettingsUiState(
                baseUrl = repository.getCurrentUrl(),
                availableUrls = repository.getAvailableUrls()
            )
        }
    }
}
