package me.jbusdriver.modern.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.jbusdriver.modern.data.settings.ThemeMode
import me.jbusdriver.modern.data.settings.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ThemeViewModel].
 *
 * [ThemeViewModel] is a thin read-only wrapper over [ThemeRepository], exposing `themeMode` and
 * `dynamicColor` as [StateFlow]s so the Compose host can scope theme appearance to its lifecycle.
 * We assert it (1) forwards the repository's current values on first read and (2) keeps reflecting
 * subsequent repository updates (StateFlow hot propagation).
 */
class ThemeViewModelTest {

    @Test
    fun `exposes theme mode and dynamic color from repository`() {
        val repo = FakeThemeRepository(themeMode = ThemeMode.DARK, dynamicColor = true)

        val viewModel = ThemeViewModel(repo)

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals(true, viewModel.dynamicColor.value)
    }

    @Test
    fun `reflects updates when repository changes`() {
        val repo = FakeThemeRepository(themeMode = ThemeMode.LIGHT, dynamicColor = false)
        val viewModel = ThemeViewModel(repo)

        // Sanity check the initial forwarded values.
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
        assertEquals(false, viewModel.dynamicColor.value)

        // Mutate the repository's backing flows; the VM must observe the new values.
        repo.themeModeValue = ThemeMode.DARK
        repo.dynamicColorValue = true

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        assertEquals(true, viewModel.dynamicColor.value)
    }
}

/**
 * Minimal [ThemeRepository] backed by writable [MutableStateFlow]s so tests can mutate the emitted
 * values via [themeModeValue]/[dynamicColorValue] and assert the VM observes the change.
 */
private class FakeThemeRepository(
    themeMode: ThemeMode,
    dynamicColor: Boolean
) : ThemeRepository {
    private val themeModeFlow = MutableStateFlow(themeMode)
    private val dynamicColorFlow = MutableStateFlow(dynamicColor)

    var themeModeValue: ThemeMode
        get() = themeModeFlow.value
        set(value) { themeModeFlow.value = value }

    var dynamicColorValue: Boolean
        get() = dynamicColorFlow.value
        set(value) { dynamicColorFlow.value = value }

    override val themeMode: StateFlow<ThemeMode> = themeModeFlow.asStateFlow()
    override val dynamicColor: StateFlow<Boolean> = dynamicColorFlow.asStateFlow()
}
