package me.jbusdriver.modern.ui.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createViewModel(repository: SettingsRepository): SettingsViewModel {
        return SettingsViewModel(repository)
    }

    @Test
    fun initialState_loadsFromRepository() {
        val repository = object : SettingsRepository {
            override fun getCurrentUrl() = "https://example.com"
            override fun getAvailableUrls() = listOf("https://example.com", "https://example.org")
            override suspend fun updateUrl(url: String) {}
        }

        val viewModel = createViewModel(repository)

        assertEquals("https://example.com", viewModel.uiState.value.baseUrl)
        assertEquals(listOf("https://example.com", "https://example.org"), viewModel.uiState.value.availableUrls)
    }

    @Test
    fun updateUrl_updatesState() = runTest(testDispatcher) {
        var currentUrl = "https://old.com"
        val repository = object : SettingsRepository {
            override fun getCurrentUrl() = currentUrl
            override fun getAvailableUrls() = listOf("https://old.com", "https://new.com")
            override suspend fun updateUrl(url: String) { currentUrl = url }
        }

        val viewModel = createViewModel(repository)
        viewModel.updateUrl("https://new.com")
        advanceUntilIdle()

        assertEquals("https://new.com", viewModel.uiState.value.baseUrl)
        assertFalse(viewModel.uiState.value.isUpdating)
    }
}
