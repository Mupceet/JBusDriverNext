package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import me.jbusdriver.modern.data.settings.ThemeMode
import me.jbusdriver.modern.data.settings.ThemeRepository
import javax.inject.Inject

/**
 * Exposes the current theme appearance state to `JBusTheme`.
 *
 * Thin wrapper over [ThemeRepository]; kept as a ViewModel so the Compose
 * host can scope theme state to its local lifecycle via `hiltViewModel()`.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    repository: ThemeRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val dynamicColor: StateFlow<Boolean> = repository.dynamicColor
}
