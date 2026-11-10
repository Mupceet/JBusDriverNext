package me.jbusdriver.modern.ui.sitemanager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.site.DEFAULT_SITE_URL
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.SiteSettingsSource
import me.jbusdriver.modern.ui.UserMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SiteManagerViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region deriveSiteEntries (pure)

    @Test
    fun `derive entries dedups with preset and custom priority over scanned`() {
        val entries = deriveSiteEntries(
            presetUrl = DEFAULT_SITE_URL,
            scannedUrls = listOf(DEFAULT_SITE_URL, "https://dup.example.com", "https://scan.example.com"),
            customUrls = listOf("https://dup.example.com", "https://custom.example.com"),
            measurements = emptyMap()
        )
        // 输出分区顺序：官方 + 自定义 + 扫描（自定义卡常显在第二位）
        assertEquals(
            listOf(
                SiteEntry(DEFAULT_SITE_URL, SiteGroup.PRESET),
                SiteEntry("https://custom.example.com", SiteGroup.CUSTOM),
                SiteEntry("https://dup.example.com", SiteGroup.CUSTOM),
                SiteEntry("https://scan.example.com", SiteGroup.SCANNED)
            ),
            entries
        )
    }

    @Test
    fun `derive entries sorts measured reachable first then unmeasured then unreachable`() {
        val measurements = mapOf(
            "https://slow.example.com" to MirrorUrl("https://slow.example.com", true, 200L),
            "https://fast.example.com" to MirrorUrl("https://fast.example.com", true, 20L),
            "https://down.example.com" to MirrorUrl("https://down.example.com", false)
        )
        val entries = deriveSiteEntries(
            presetUrl = DEFAULT_SITE_URL,
            scannedUrls = listOf(
                "https://down.example.com",
                "https://unmeasured.example.com",
                "https://slow.example.com",
                "https://fast.example.com"
            ),
            customUrls = emptyList(),
            measurements = measurements
        )
        assertEquals(
            listOf(
                DEFAULT_SITE_URL,
                "https://fast.example.com",
                "https://slow.example.com",
                "https://unmeasured.example.com",
                "https://down.example.com"
            ),
            entries.map { it.url }
        )
    }

    // endregion

    private fun viewModel(
        fake: FakeSiteSettingsSource = FakeSiteSettingsSource(),
        siteConfig: FakeSiteConfig = FakeSiteConfig()
    ) = SiteManagerViewModel(fake, siteConfig, UnconfinedTestDispatcher())

    @Test
    fun `uiState groups preset custom scanned entries`() = runTest {
        val fake = FakeSiteSettingsSource(
            cached = listOf("https://a.test"),
            custom = listOf("https://b.test")
        )
        val state = viewModel(fake).uiState.first()
        assertEquals(
            listOf(
                SiteEntry(DEFAULT_SITE_URL, SiteGroup.PRESET),
                SiteEntry("https://b.test", SiteGroup.CUSTOM),
                SiteEntry("https://a.test", SiteGroup.SCANNED)
            ),
            state.entries
        )
    }

    @Test
    fun `selecting a site updates store and site config`() = runTest {
        val fake = FakeSiteSettingsSource()
        val siteConfig = FakeSiteConfig()
        viewModel(fake, siteConfig).selectSite("https://proxy.example.com")
        assertEquals("https://proxy.example.com", fake.selectedBaseUrl.value)
        assertEquals("https://proxy.example.com", siteConfig.current)
    }

    @Test
    fun `deleting the selected entry is blocked with a message`() = runTest {
        val fake = FakeSiteSettingsSource(
            selected = "https://a.test",
            cached = listOf("https://a.test")
        )
        val vm = viewModel(fake)
        val received = mutableListOf<UserMessage>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received.add(it) } }

        vm.deleteEntry(SiteEntry("https://a.test", SiteGroup.SCANNED))

        assertEquals(R.string.site_delete_selected_first, received.first().resId)
        assertEquals(listOf("https://a.test"), fake.cachedMirrorUrls.value)
        assertNull(fake.removedScanned)
        job.cancel()
    }

    @Test
    fun `deleting an unselected custom entry removes it via store`() = runTest {
        val fake = FakeSiteSettingsSource(custom = listOf("https://b.test"))
        viewModel(fake).deleteEntry(SiteEntry("https://b.test", SiteGroup.CUSTOM))
        assertEquals(emptyList<String>(), fake.customBaseUrls.value)
        assertEquals("https://b.test", fake.removedCustom)
    }

    @Test
    fun `add custom url invalid emits message without probe`() = runTest {
        val fake = FakeSiteSettingsSource().apply { addResult = false }
        val vm = viewModel(fake)
        var probed: String? = null
        vm.reachabilityProbe = { probed = it; -1L }
        val received = mutableListOf<UserMessage>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received.add(it) } }

        vm.addCustomUrl("not a url")

        assertEquals(R.string.site_add_invalid_or_duplicate, received.first().resId)
        assertNull(probed)
        job.cancel()
    }

    @Test
    fun `add custom url saves then reports advisory reachability`() = runTest {
        val fake = FakeSiteSettingsSource()
        val vm = viewModel(fake)
        vm.reachabilityProbe = { 123L }
        val received = mutableListOf<UserMessage>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received.add(it) } }

        vm.addCustomUrl("https://proxy.example.com")

        assertEquals(listOf("https://proxy.example.com"), fake.customBaseUrls.value)
        val message = received.first()
        assertEquals(R.string.site_add_saved_reachable, message.resId)
        assertEquals(listOf<Any>(123L), message.args)
        job.cancel()
    }

    @Test
    fun `verify stores measurements from scan state`() = runTest {
        val fake = FakeSiteSettingsSource().apply {
            scanImpl = { state ->
                state.value = ScanState(
                    isScanning = false,
                    phase = ScanPhase.DONE,
                    discoveredUrls = listOf(
                        MirrorUrl("https://a.test", true, 120L),
                        MirrorUrl("https://b.test", false)
                    )
                )
            }
        }
        val vm = viewModel(fake)
        vm.startVerify()
        val state = vm.uiState.first()
        assertEquals(120L, state.measurements["https://a.test"]?.latencyMs)
        assertFalse(state.measurements["https://b.test"]?.isReachable ?: true)
    }

    @Test
    fun `cancelScan cancels in-flight scan and resets state`() = runTest {
        val fake = FakeSiteSettingsSource().apply {
            scanImpl = { state ->
                state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)
                awaitCancellation()
            }
        }
        val vm = viewModel(fake)
        vm.startScan()
        assertEquals(true, vm.uiState.first().isScanning)
        vm.cancelScan()
        val state = vm.uiState.first()
        assertEquals(false, state.isScanning)
        // job actually cancelled: a second scan can start immediately
        var secondRan = false
        fake.scanImpl = { secondRan = true }
        vm.startScan()
        assertTrue(secondRan)
    }

    @Test
    fun `cancel affordance follows running op not scan phase`() = runTest {
        // 测速运行中 phase 也是 VERIFYING，取消入口必须归属测速按钮而非扫描槽。
        val fake = FakeSiteSettingsSource().apply {
            scanImpl = { state ->
                state.value = ScanState(isScanning = true, phase = ScanPhase.VERIFYING)
                awaitCancellation()
            }
        }
        val vm = viewModel(fake)
        vm.startVerify()
        val running = vm.uiState.first()
        assertEquals(true, running.isScanning)
        assertEquals(ScanPhase.VERIFYING, running.scanPhase)
        assertEquals(ScanOp.VERIFY, running.runningOp)
        vm.cancelScan()
        val cancelled = vm.uiState.first()
        assertEquals(false, cancelled.isScanning)
        assertEquals(ScanOp.NONE, cancelled.runningOp)
    }

    @Test
    fun `scan run reports SCAN op even in its verifying phase`() = runTest {
        val fake = FakeSiteSettingsSource().apply {
            scanImpl = { state ->
                state.value = ScanState(isScanning = true, phase = ScanPhase.VERIFYING)
                awaitCancellation()
            }
        }
        val vm = viewModel(fake)
        vm.startScan()
        val running = vm.uiState.first()
        assertEquals(ScanPhase.VERIFYING, running.scanPhase)
        assertEquals(ScanOp.SCAN, running.runningOp)
        vm.cancelScan()
    }
}

private class FakeSiteSettingsSource(
    selected: String = DEFAULT_SITE_URL,
    cached: List<String> = emptyList(),
    custom: List<String> = emptyList()
) : SiteSettingsSource {
    override val selectedBaseUrl = MutableStateFlow(selected)
    override val cachedMirrorUrls = MutableStateFlow(cached)
    override val customBaseUrls = MutableStateFlow(custom)
    var addResult = true
    var removedScanned: String? = null
    var removedCustom: String? = null
    var scanImpl: suspend (MutableStateFlow<ScanState>) -> Unit = { }

    override suspend fun currentSelectedBaseUrl(): String = selectedBaseUrl.value
    override suspend fun selectUrl(url: String) { selectedBaseUrl.value = url }
    override suspend fun addCustomUrl(url: String): Boolean {
        if (!addResult) return false
        customBaseUrls.value = customBaseUrls.value + url
        return true
    }
    override suspend fun removeCustomUrl(url: String) {
        removedCustom = url
        customBaseUrls.value = customBaseUrls.value - url
    }
    override suspend fun removeScannedUrl(url: String) {
        removedScanned = url
        cachedMirrorUrls.value = cachedMirrorUrls.value - url
    }
    override suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>) = scanImpl(state)
    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) = scanImpl(state)
}

private class FakeSiteConfig : SiteConfig {
    var current = DEFAULT_SITE_URL
    override var baseUrl: String
        get() = current
        set(value) { current = value }
    override fun resolve(pathOrUrl: String): String = pathOrUrl
}
