package me.jbusdriver.modern.data.settings

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to theme preferences for theme consumers.
 *
 * Decouples [me.jbusdriver.modern.ui.settings.ThemeViewModel] / `JBusTheme`
 * from the full [AppSettingsContract], exposing only the appearance state
 * needed to render the Compose tree.
 */
interface ThemeRepository {
    val themeMode: StateFlow<ThemeMode>
    val dynamicColor: StateFlow<Boolean>
}

@Singleton
class DefaultThemeRepository @Inject constructor(
    private val reader: ThemeSettingsReader
) : ThemeRepository {
    override val themeMode: StateFlow<ThemeMode> get() = reader.themeMode
    override val dynamicColor: StateFlow<Boolean> get() = reader.dynamicColor
}
