package me.jbusdriver.modern.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.LabSettingsStoreContract
import me.jbusdriver.modern.data.mirror.ScanState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LabSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cancelScanDoesNotPublishFailure() = runTest(dispatcher) {
        val blocked = CompletableDeferred<Unit>()
        val store = FakeLabSettingsStore(
            scan = {
                blocked.await()
            }
        )
        val viewModel = LabSettingsViewModel(store, FakeSiteConfig(), dispatcher)

        viewModel.startScan()
        runCurrent()

        viewModel.cancelScan()
        advanceUntilIdle()

        assertNull(viewModel.scanState.value.error)
    }

    @Test
    fun selectUrlUpdatesStoreAndRuntimeSiteConfig() = runTest(dispatcher) {
        val store = FakeLabSettingsStore()
        val siteConfig = FakeSiteConfig("https://old.example.test")
        val viewModel = LabSettingsViewModel(store, siteConfig, dispatcher)

        viewModel.selectUrl("https://new.example.test/")
        advanceUntilIdle()

        assertEquals("https://new.example.test/", store.selectedUrl)
        assertEquals("https://new.example.test/", siteConfig.baseUrl)
    }

    @Test
    fun cancelledVerifyDoesNotPublishFailure() = runTest(dispatcher) {
        val blocked = CompletableDeferred<Unit>()
        val store = FakeLabSettingsStore(
            verify = {
                blocked.await()
            }
        )
        val viewModel = LabSettingsViewModel(store, FakeSiteConfig(), dispatcher)

        viewModel.startVerify()
        runCurrent()

        viewModel.cancelScan()
        advanceUntilIdle()

        assertNull(viewModel.scanState.value.error)
    }

    @Test
    fun realScanFailureStillPublishesFailure() = runTest(dispatcher) {
        val store = FakeLabSettingsStore(
            scan = {
                error("network")
            }
        )
        val viewModel = LabSettingsViewModel(store, FakeSiteConfig(), dispatcher)

        viewModel.startScan()
        advanceUntilIdle()

        org.junit.Assert.assertEquals(R.string.scan_failed, viewModel.scanState.value.error)
    }

    @Test
    fun uiStateReflectsStoreAndSettingsIntents() = runTest(dispatcher) {
        val store = FakeLabSettingsStore()
        val viewModel = LabSettingsViewModel(store, FakeSiteConfig(), dispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.forumEnabled)
        assertFalse(viewModel.uiState.value.autoLoadGifs)
        assertEquals(ForumFloorOrder.REGULAR, viewModel.uiState.value.forumFloorOrder)

        viewModel.setForumEnabled(true)
        viewModel.setAutoLoadGifs(true)
        viewModel.setForumFloorOrder(ForumFloorOrder.REVERSE)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.forumEnabled)
        assertTrue(viewModel.uiState.value.autoLoadGifs)
        assertEquals(ForumFloorOrder.REVERSE, viewModel.uiState.value.forumFloorOrder)
    }

    private class FakeLabSettingsStore(
        private val scan: suspend () -> Unit = {},
        private val verify: suspend () -> Unit = {}
    ) : LabSettingsStoreContract {
        override val forumEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
        override val autoLoadGifs: MutableStateFlow<Boolean> = MutableStateFlow(false)
        override val forumFloorOrder: MutableStateFlow<ForumFloorOrder> =
            MutableStateFlow(ForumFloorOrder.REGULAR)
        override val selectedBaseUrl: StateFlow<String> = MutableStateFlow("https://example.test")
        override val cachedMirrorUrls: StateFlow<List<String>> = MutableStateFlow(emptyList())
        var selectedUrl: String? = null

        override suspend fun setForumEnabled(enabled: Boolean) {
            forumEnabled.value = enabled
        }

        override suspend fun setAutoLoadGifs(enabled: Boolean) {
            autoLoadGifs.value = enabled
        }

        override suspend fun setForumFloorOrder(order: ForumFloorOrder) {
            forumFloorOrder.value = order
        }

        override suspend fun selectUrl(url: String) {
            selectedUrl = url
        }

        override suspend fun scanMirrorUrls(
            state: MutableStateFlow<ScanState>,
            seedUrl: String
        ) {
            scan()
        }

        override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
            verify()
        }
    }

    private class FakeSiteConfig(initialBaseUrl: String = "https://example.test") : SiteConfig {
        override var baseUrl: String = initialBaseUrl
        override fun resolve(pathOrUrl: String): String =
            baseUrl.trimEnd('/') + if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
    }
}
