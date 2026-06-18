package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.UiPrefsStoreContract
import javax.inject.Inject

data class UiPrefsUiState(
    val isGrid: Boolean = false
)

@HiltViewModel
class UiPrefsViewModel @Inject constructor(
    private val store: UiPrefsStoreContract
) : ViewModel() {
    val uiState: StateFlow<UiPrefsUiState> = store.isGrid
        .map { UiPrefsUiState(isGrid = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefsUiState())

    fun setGrid(isGrid: Boolean) {
        viewModelScope.launch { store.setGrid(isGrid) }
    }

    fun toggleGrid() {
        setGrid(!uiState.value.isGrid)
    }
}
