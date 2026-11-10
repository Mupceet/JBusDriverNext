# Site Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 4-URL preset dropdown with a dedicated Site Manager page: preset main site + scanned mirrors + user-defined custom URLs, grouped, with unified speed testing.

**Architecture:** Data layer first — a narrow `SiteSettingsSource` interface on `AppSettingsStore` adds custom-URL persistence and composes scan seeds (main site + selected + custom + cached). `MirrorScanner` takes a plain seed list. A new `SiteManagerViewModel` derives grouped entries (dedup priority PRESET > CUSTOM > SCANNED) and owns the scan state moved out of `SettingsViewModel`. A new `RouteSiteManager` screen renders three groups; the Settings NetworkCard shrinks to a navigation row.

**Tech Stack:** Jetpack Compose + Material3, Hilt, DataStore Preferences, Nav3, kotlinx-coroutines-test (no Robolectric — test via pure functions + interface fakes).

**Spec:** `docs/superpowers/specs/2026-11-10-site-manager-design.md`

## Global Constraints

- Package root `me.jbusdriver.modern`; work on branch `feat/site-manager`.
- Every gradle command needs first: `export JAVA_HOME="C:/Users/laiguizhong/AppData/Local/Programs/Android Studio 2/jbr"`
- All new visible strings as resources in `res/values/strings.xml` (繁體 default) **and** `res/values-en/strings.xml`.
- UDF: ViewModels expose one immutable `UiState` via `StateFlow` and one-shot events via `SharedFlow<UserMessage>`; no callbacks to UI; no hardcoded `Dispatchers.*` in ViewModels (inject `@IoDispatcher`).
- Data-layer suspend functions are main-safe (DataStore/NetClient already are).
- Before every commit: `git status` must show only the intended files staged — never stage `.idea/`, `.codex/`, `.zcode/`, `docs/superpowers/plans/`, or unrelated `gradle/` changes that predate this work.
- No Gson/ProGuard/R8 changes → no release smoke test required.
- Focused test runs use `./gradlew testDebugUnitTest --tests "<fqcn>"`.

---

### Task 1: Data layer — custom URLs + scan seed composition

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/mirror/MirrorScanner.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt` (temporary call-site fix; fully removed in Task 4)
- Test: `app/src/test/java/me/jbusdriver/modern/data/settings/SiteSettingsLogicTest.kt` (new)
- Test: `app/src/test/java/me/jbusdriver/modern/data/mirror/MirrorScannerTest.kt` (modify)

**Interfaces:**
- Consumes: `SitePreferenceSource` (exists), `normalizeBaseUrl` (`core/site/SiteConfig.kt`, internal — same module).
- Produces (used by Task 2):
  - `interface SiteSettingsSource : SitePreferenceSource` with: `selectedBaseUrl: StateFlow<String>`, `cachedMirrorUrls: StateFlow<List<String>>`, `customBaseUrls: StateFlow<List<String>>`, `suspend selectUrl(url: String)`, `suspend addCustomUrl(url: String): Boolean`, `suspend removeCustomUrl(url: String)`, `suspend removeScannedUrl(url: String)`, `suspend scanMirrorUrls(state: MutableStateFlow<ScanState>)`, `suspend verifyMirrorUrls(state: MutableStateFlow<ScanState>)`
  - `internal fun normalizeCustomUrlInput(rawUrl: String): String?`
  - `internal fun composeScanSeeds(presetUrl: String, selectedUrl: String, customUrls: List<String>, cachedUrls: List<String>): List<String>`
  - `internal fun filterScannedForPersistence(discovered: Set<String>, presetUrl: String, customUrls: List<String>): Set<String>`
  - `MirrorScanner.scanAndVerify(state: MutableStateFlow<ScanState>, seeds: List<String>): Set<String>` (replaces `(state, seedUrl, cachedUrls)`)

- [ ] **Step 1: Write failing tests for the pure helpers**

Create `app/src/test/java/me/jbusdriver/modern/data/settings/SiteSettingsLogicTest.kt`:

```kotlin
package me.jbusdriver.modern.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteSettingsLogicTest {

    // region normalizeCustomUrlInput

    @Test
    fun `custom url input normalizes scheme host case and trailing slash`() {
        assertEquals(
            "https://mirror.example.com/PATH",
            normalizeCustomUrlInput(" HTTPS://Mirror.Example.com/PATH/ ")
        )
    }

    @Test
    fun `custom url input rejects blank non-http and hostless input`() {
        assertNull(normalizeCustomUrlInput(""))
        assertNull(normalizeCustomUrlInput("   "))
        assertNull(normalizeCustomUrlInput("example.com"))        // no scheme
        assertNull(normalizeCustomUrlInput("ftp://example.com"))  // non-http scheme
        assertNull(normalizeCustomUrlInput("https:///nohost"))    // no host
    }

    // endregion

    // region composeScanSeeds

    @Test
    fun `scan seeds combine preset selected custom and cached with dedup`() {
        val seeds = composeScanSeeds(
            presetUrl = "https://www.javbus.com/",
            selectedUrl = "https://mirror.example.com",
            customUrls = listOf("https://proxy.example.com/", "https://www.javbus.com"),
            cachedUrls = listOf("https://mirror.example.com/", "https://old.example.com")
        )
        assertEquals(
            listOf(
                "https://www.javbus.com",
                "https://mirror.example.com",
                "https://proxy.example.com",
                "https://old.example.com"
            ),
            seeds
        )
    }

    // endregion

    // region filterScannedForPersistence

    @Test
    fun `persistence filter drops preset and custom urls from discovered set`() {
        val persisted = filterScannedForPersistence(
            discovered = setOf(
                "https://www.javbus.com",
                "https://proxy.example.com",
                "https://found.example.com",
                "https://found2.example.com/"
            ),
            presetUrl = "https://www.javbus.com/",
            customUrls = listOf("https://proxy.example.com")
        )
        assertEquals(setOf("https://found.example.com", "https://found2.example.com"), persisted)
    }

    // endregion
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME="C:/Users/laiguizhong/AppData/Local/Programs/Android Studio 2/jbr"
cd D:/Projects/AndroidProjects/JBusDriver
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.settings.SiteSettingsLogicTest"
```

Expected: compilation FAILURE — `normalizeCustomUrlInput`, `composeScanSeeds`, `filterScannedForPersistence` unresolved.

- [ ] **Step 3: Add the pure helpers and SiteSettingsSource interface to AppSettingsStore.kt**

3a. Add imports (top of file, with existing imports):

```kotlin
import me.jbusdriver.modern.core.site.normalizeBaseUrl
import java.net.URI
```

3b. Insert above `/** Full settings read/write contract for SettingsViewModel. */`:

```kotlin
/** Narrow read/write interface for site selection consumers (SiteManagerViewModel). */
interface SiteSettingsSource : SitePreferenceSource {
    val selectedBaseUrl: StateFlow<String>
    val cachedMirrorUrls: StateFlow<List<String>>
    val customBaseUrls: StateFlow<List<String>>

    suspend fun selectUrl(url: String)
    /** 归一并校验后保存自定义网址；非法或跨组重复返回 false。 */
    suspend fun addCustomUrl(url: String): Boolean
    suspend fun removeCustomUrl(url: String)
    suspend fun removeScannedUrl(url: String)

    /** 种子 = 主站 + 当前选中 + 自定义 + 缓存，由实现方统一组装。 */
    suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>)
    /** 验证 preset + custom + cached 的并集。 */
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>)
}

/** 校验并归一化用户输入的自定义网址；非法（空白/非 http(s)/无主机）返回 null。 */
internal fun normalizeCustomUrlInput(rawUrl: String): String? {
    val normalized = normalizeBaseUrl(rawUrl)
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return null
    val host = runCatching { URI(normalized).host }.getOrNull() ?: return null
    if (host.isBlank() || !host.contains('.')) return null
    return normalized
}

/** 扫描种子 = 主站 + 当前选中 + 自定义 + 缓存（归一去重，保持首次出现顺序）。 */
internal fun composeScanSeeds(
    presetUrl: String,
    selectedUrl: String,
    customUrls: List<String>,
    cachedUrls: List<String>
): List<String> = (listOf(presetUrl, selectedUrl) + customUrls + cachedUrls)
    .map { normalizeBaseUrl(it) }
    .distinct()

/** 扫描结果持久化前剔除官方/自定义 URL，缓存只存"扫描发现"。 */
internal fun filterScannedForPersistence(
    discovered: Set<String>,
    presetUrl: String,
    customUrls: List<String>
): Set<String> {
    val excluded = buildSet {
        add(normalizeBaseUrl(presetUrl))
        customUrls.forEach { add(normalizeBaseUrl(it)) }
    }
    return discovered.map { normalizeBaseUrl(it) }.filterNot { it in excluded }.toSet()
}
```

3c. In `AppSettingsContract`: change the extends clause to include `SiteSettingsSource`, and **delete** these now-inherited members from the interface body (they move to `SiteSettingsSource`): `val selectedBaseUrl`, `val cachedMirrorUrls`, `suspend fun selectUrl(url: String)`, `suspend fun scanMirrorUrls(...)`, `suspend fun verifyMirrorUrls(...)`. Result:

```kotlin
/** Full settings read/write contract for SettingsViewModel. */
interface AppSettingsContract : ThemeSettingsReader, ForumSettingsReader, MovieListSettings,
    SiteSettingsSource, TranslationSettingsReader, LtxSettingsSource {
    // Appearance
    val showMovieTab: StateFlow<Boolean>
    // ... (all other existing members unchanged; the five site members above are gone)
}
```

3d. Replace the `// region Network` implementation body in `AppSettingsStore` with:

```kotlin
    // region Network (SiteSettingsSource)
    override val selectedBaseUrl: StateFlow<String> = flowOf(KEY_SELECTED_BASE_URL, DEFAULT_SITE_URL)
    override val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CACHED_MIRROR_URLS]?.toList() ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val customBaseUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CUSTOM_BASE_URLS]?.toList()?.sorted() ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override suspend fun currentSelectedBaseUrl(): String =
        dataStore.data.first()[KEY_SELECTED_BASE_URL] ?: DEFAULT_SITE_URL
    override suspend fun selectUrl(url: String) = put(KEY_SELECTED_BASE_URL, url.trimEnd('/'))
    override suspend fun addCustomUrl(url: String): Boolean {
        val normalized = normalizeCustomUrlInput(url) ?: return false
        val existing = buildList {
            add(DEFAULT_SITE_URL)
            addAll(cachedMirrorUrls.first())
            addAll(customBaseUrls.first())
        }
        if (existing.any { normalizeBaseUrl(it) == normalized }) return false
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BASE_URLS] = (prefs[KEY_CUSTOM_BASE_URLS] ?: emptySet()) + normalized
        }
        return true
    }
    override suspend fun removeCustomUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BASE_URLS] = (prefs[KEY_CUSTOM_BASE_URLS] ?: emptySet()) - url
        }
    }
    override suspend fun removeScannedUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_MIRROR_URLS] = (prefs[KEY_CACHED_MIRROR_URLS] ?: emptySet()) - url
        }
    }
    override suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>) {
        val seeds = composeScanSeeds(
            presetUrl = DEFAULT_SITE_URL,
            selectedUrl = selectedBaseUrl.first(),
            customUrls = customBaseUrls.first(),
            cachedUrls = cachedMirrorUrls.first()
        )
        val discovered = mirrorScanner.scanAndVerify(state, seeds)
        dataStore.edit {
            it[KEY_CACHED_MIRROR_URLS] =
                filterScannedForPersistence(discovered, DEFAULT_SITE_URL, customBaseUrls.first())
        }
    }
    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = (listOf(DEFAULT_SITE_URL) + customBaseUrls.first() + cachedMirrorUrls.first())
            .map { normalizeBaseUrl(it) }
            .distinct()
        mirrorScanner.verifyOnly(state, urls)
    }
    // endregion
```

3e. In the `companion object`: add the new key and **delete** `PRESET_MIRROR_URLS` entirely:

```kotlin
        private val KEY_CUSTOM_BASE_URLS = stringSetPreferencesKey("custom_base_urls")
```

- [ ] **Step 4: Change MirrorScanner.scanAndVerify to take a seed list; drop host-pinned sort**

In `MirrorScanner.kt`, replace the signature and seed assembly (rest of the function body unchanged — `seeds` local now comes from the parameter):

```kotlin
    suspend fun scanAndVerify(
        state: MutableStateFlow<ScanState>,
        seeds: List<String>
    ): Set<String> {
        val allSeeds = seeds.map { it.trimEnd('/') }.distinct()
        // ... body identical to before, iterating `allSeeds.toList()`
```

Replace `sortMirrorUrls` (no more `www.javbus.com` preference — grouping now carries that distinction):

```kotlin
internal fun sortMirrorUrls(urls: List<MirrorUrl>): List<MirrorUrl> =
    urls.sortedWith(
        compareBy<MirrorUrl> { if (it.isReachable) it.latencyMs else Long.MAX_VALUE }
            .thenBy { it.url }
    )
```

Delete the now-unused `private val defaultHost = "www.javbus.com"` line if present.

- [ ] **Step 5: Update MirrorScannerTest.kt**

Replace the host-preference test (first test) with a latency-only ordering test; keep the second test:

```kotlin
    @Test
    fun `sort mirror urls has no host preference and orders purely by latency`() {
        val sorted = sortMirrorUrls(
            listOf(
                MirrorUrl("https://www.javbus.com", isReachable = true, latencyMs = 500),
                MirrorUrl("https://fast.example.test", isReachable = true, latencyMs = 10),
                MirrorUrl("https://slow.example.test", isReachable = true, latencyMs = 100)
            )
        )
        assertEquals("https://fast.example.test", sorted.first().url)
    }
```

- [ ] **Step 6: Fix the SettingsViewModel call site (temporary until Task 4)**

In `SettingsViewModel.kt`, replace:

```kotlin
    fun startScan() = launchScan {
        store.scanMirrorUrls(_scanState, store.selectedBaseUrl.first())
    }
```

with:

```kotlin
    fun startScan() = launchScan {
        store.scanMirrorUrls(_scanState)
    }
```

(`first` import stays — still used by `restoreAllExcludedVideos`.)

- [ ] **Step 7: Register the Hilt binding in DataModule.kt**

Next to `bindSitePreferenceSource`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindSiteSettingsSource(impl: AppSettingsStore): SiteSettingsSource
```

Add import `me.jbusdriver.modern.data.settings.SiteSettingsSource`.

- [ ] **Step 8: Run all touched tests**

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.settings.SiteSettingsLogicTest" --tests "me.jbusdriver.modern.data.mirror.MirrorScannerTest"
```

Expected: PASS (4 new + 2 scanner tests).

- [ ] **Step 9: Compile check**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt \
        app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt \
        app/src/main/java/me/jbusdriver/modern/data/mirror/MirrorScanner.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/data/settings/SiteSettingsLogicTest.kt \
        app/src/test/java/me/jbusdriver/modern/data/mirror/MirrorScannerTest.kt
git status   # verify: only the 6 files staged
git commit -m "feat(site): custom urls, unified scan seeds, preset trimmed to main site"
```

---

### Task 2: Strings + SiteManagerViewModel

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (~line 162, after the network block)
- Modify: `app/src/main/res/values-en/strings.xml` (~line 167, same block)
- Create: `app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModel.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModelTest.kt` (new)

**Interfaces:**
- Consumes: `SiteSettingsSource` (Task 1), `SiteConfig`, `NetClient.checkReachable(url): Long` (main-safe), `normalizeBaseUrl`, `UserMessage(resId, args)`.
- Produces (used by Task 3):
  - `enum class SiteGroup { PRESET, SCANNED, CUSTOM }`
  - `data class SiteEntry(val url: String, val group: SiteGroup)`
  - `data class SiteManagerUiState(entries, selectedUrl, isScanning, scanPhase, scannedCount, totalCount, currentUrl, measurements: Map<String, MirrorUrl>)`
  - `internal fun deriveSiteEntries(presetUrl: String, scannedUrls: List<String>, customUrls: List<String>, measurements: Map<String, MirrorUrl>): List<SiteEntry>`
  - `SiteManagerViewModel(store: SiteSettingsSource, siteConfig: SiteConfig, @IoDispatcher ioDispatcher)` exposing `uiState: StateFlow<SiteManagerUiState>`, `messages: SharedFlow<UserMessage>`, `internal var reachabilityProbe: suspend (String) -> Long`, and methods `selectSite(url)`, `deleteEntry(entry)`, `addCustomUrl(rawUrl)`, `startScan()`, `startVerify()`, `cancelScan()`

- [ ] **Step 1: Add string resources**

`res/values/strings.xml` (繁體 default), after the `unreachable` line:

```xml
    <string name="site_manager_title">站點管理</string>
    <string name="site_group_preset">官方</string>
    <string name="site_group_scanned">掃描發現</string>
    <string name="site_group_custom">自定義</string>
    <string name="site_test_all">全部測速</string>
    <string name="site_scan">掃描</string>
    <string name="site_scan_empty">暫無掃描結果，點擊「掃描」發現可用鏡像</string>
    <string name="site_add_title">添加自定義網址</string>
    <string name="site_add_hint">輸入你能訪問的網址（含 http/https）</string>
    <string name="site_add_invalid_or_duplicate">網址無效或已存在</string>
    <string name="site_add_saved_reachable">已保存，看起來可達（%1$d ms）</string>
    <string name="site_add_saved_unreachable">已保存，但暫時不可達（仍可選用）</string>
    <string name="site_delete_selected_first">請先切換到其他站點再刪除</string>
```

`res/values-en/strings.xml`:

```xml
    <string name="site_manager_title">Site manager</string>
    <string name="site_group_preset">Official</string>
    <string name="site_group_scanned">Scanned</string>
    <string name="site_group_custom">Custom</string>
    <string name="site_test_all">Test all</string>
    <string name="site_scan">Scan</string>
    <string name="site_scan_empty">No scan results yet — tap Scan to discover mirrors</string>
    <string name="site_add_title">Add custom URL</string>
    <string name="site_add_hint">Enter a URL you can reach (with http/https)</string>
    <string name="site_add_invalid_or_duplicate">Invalid or duplicate URL</string>
    <string name="site_add_saved_reachable">Saved; reachable (%1$d ms)</string>
    <string name="site_add_saved_unreachable">Saved; currently unreachable (still selectable)</string>
    <string name="site_delete_selected_first">Switch to another site before deleting</string>
```

- [ ] **Step 2: Write failing ViewModel tests**

Create `app/src/test/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.sitemanager

import kotlinx.coroutines.Dispatchers
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
import org.junit.Before
import org.junit.Test

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
        assertEquals(
            listOf(
                SiteEntry(DEFAULT_SITE_URL, SiteGroup.PRESET),
                SiteEntry("https://scan.example.com", SiteGroup.SCANNED),
                SiteEntry("https://custom.example.com", SiteGroup.CUSTOM),
                SiteEntry("https://dup.example.com", SiteGroup.CUSTOM)
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
    fun `uiState groups preset scanned custom entries`() = runTest {
        val fake = FakeSiteSettingsSource(
            cached = listOf("https://a.test"),
            custom = listOf("https://b.test")
        )
        val state = viewModel(fake).uiState.first()
        assertEquals(
            listOf(
                SiteEntry(DEFAULT_SITE_URL, SiteGroup.PRESET),
                SiteEntry("https://a.test", SiteGroup.SCANNED),
                SiteEntry("https://b.test", SiteGroup.CUSTOM)
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
        val job = launch { vm.messages.collect { received.add(it) } }

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
        val job = launch { vm.messages.collect { received.add(it) } }

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
        val job = launch { vm.messages.collect { received.add(it) } }

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
    var scanImpl: (MutableStateFlow<ScanState>) -> Unit = { }

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
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.sitemanager.SiteManagerViewModelTest"
```

Expected: compilation FAILURE — `SiteManagerViewModel` unresolved.

- [ ] **Step 4: Implement SiteManagerViewModel.kt**

Create `app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.sitemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.site.DEFAULT_SITE_URL
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.site.normalizeBaseUrl
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.SiteSettingsSource
import me.jbusdriver.modern.ui.UserMessage
import javax.inject.Inject

enum class SiteGroup { PRESET, SCANNED, CUSTOM }

data class SiteEntry(val url: String, val group: SiteGroup)

data class SiteManagerUiState(
    val entries: List<SiteEntry> = emptyList(),
    val selectedUrl: String = DEFAULT_SITE_URL,
    val isScanning: Boolean = false,
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val measurements: Map<String, MirrorUrl> = emptyMap(),
)

/**
 * 分组派生站点列表：去重优先级 官方 > 自定义 > 扫描；
 * 组内排序 可达(延迟升序) → 未测量(按 URL) → 不可达。
 */
internal fun deriveSiteEntries(
    presetUrl: String,
    scannedUrls: List<String>,
    customUrls: List<String>,
    measurements: Map<String, MirrorUrl>
): List<SiteEntry> {
    val claimed = mutableSetOf<String>()
    fun claim(urls: List<String>, group: SiteGroup): List<SiteEntry> =
        urls.filter { claimed.add(it) }.map { SiteEntry(it, group) }

    fun sortKey(url: String): Long {
        val measured = measurements[url] ?: return Long.MAX_VALUE - 1
        return if (measured.isReachable) measured.latencyMs else Long.MAX_VALUE
    }

    fun sorted(entries: List<SiteEntry>) =
        entries.sortedWith(compareBy({ sortKey(it.url) }, { it.url }))

    return claim(listOf(presetUrl), SiteGroup.PRESET) +
        sorted(claim(scannedUrls, SiteGroup.SCANNED)) +
        sorted(claim(customUrls, SiteGroup.CUSTOM))
}

@HiltViewModel
class SiteManagerViewModel @Inject constructor(
    private val store: SiteSettingsSource,
    private val siteConfig: SiteConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    /** 保存自定义网址后的可达性探测（仅提示）；测试可替换以避免真实网络。 */
    internal var reachabilityProbe: suspend (String) -> Long = NetClient::checkReachable

    private val _scanState = MutableStateFlow(ScanState())
    private val _measurements = MutableStateFlow<Map<String, MirrorUrl>>(emptyMap())

    val uiState: StateFlow<SiteManagerUiState> = combine(
        store.selectedBaseUrl,
        store.cachedMirrorUrls,
        store.customBaseUrls,
        _measurements,
        _scanState
    ) { selected, cached, custom, measurements, scan ->
        SiteManagerUiState(
            entries = deriveSiteEntries(DEFAULT_SITE_URL, cached, custom, measurements),
            selectedUrl = selected,
            isScanning = scan.isScanning,
            scanPhase = scan.phase,
            scannedCount = scan.scannedCount,
            totalCount = scan.totalCount,
            currentUrl = scan.currentUrl,
            measurements = measurements
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SiteManagerUiState())

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun selectSite(url: String) {
        viewModelScope.launch { store.selectUrl(url); siteConfig.baseUrl = url }
    }

    fun deleteEntry(entry: SiteEntry) {
        if (entry.url == store.selectedBaseUrl.value) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.site_delete_selected_first)) }
            return
        }
        viewModelScope.launch {
            when (entry.group) {
                SiteGroup.PRESET -> Unit
                SiteGroup.SCANNED -> store.removeScannedUrl(entry.url)
                SiteGroup.CUSTOM -> store.removeCustomUrl(entry.url)
            }
        }
    }

    /** 保存自定义网址；校验仅格式+跨组重复（可达性不作保存条件），保存后探测仅提示。 */
    fun addCustomUrl(rawUrl: String) {
        viewModelScope.launch {
            if (!store.addCustomUrl(rawUrl)) {
                _messages.emit(UserMessage(R.string.site_add_invalid_or_duplicate))
                return@launch
            }
            val latency = reachabilityProbe(normalizeBaseUrl(rawUrl))
            _messages.emit(
                if (latency >= 0) UserMessage(R.string.site_add_saved_reachable, listOf(latency))
                else UserMessage(R.string.site_add_saved_unreachable)
            )
        }
    }

    fun startScan() = launchScan { store.scanMirrorUrls(_scanState) }
    fun startVerify() = launchScan { store.verifyMirrorUrls(_scanState) }
    fun cancelScan() { _scanState.value = ScanState() }

    private fun launchScan(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _scanState.value = ScanState()
                block()
                _measurements.value = _scanState.value.discoveredUrls.associateBy { it.url }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.t(TAG).e("mirror scan failed", e)
                _scanState.value = ScanState()
            }
        }
    }

    companion object {
        private const val TAG = "SiteManagerViewModel"
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.sitemanager.SiteManagerViewModelTest"
```

Expected: PASS (9 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerViewModelTest.kt
git status   # verify: only the 4 files staged
git commit -m "feat(site): site manager strings and view model with grouped entries"
```

---

### Task 3: SiteManagerScreen + route + navigation + icon

**Files:**
- Create: `app/src/main/res/drawable/add_24px.xml`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` (add entry after `entry<RouteSettings>` block, ~line 337)
- Create: `app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerScreen.kt`
- Modify: `AGENTS.md` (package structure)

**Interfaces:**
- Consumes: `SiteManagerViewModel` (Task 2), strings (Task 2), existing drawables `arrow_back_24px` / `delete_24px`, existing strings `back` / `rescan` / `unreachable` / `delete` / `save` / `cancel` / `scanning` / `verifying`.
- Produces: `SiteManagerScreen(onBack: () -> Unit)` composable; `RouteSiteManager` NavKey.

- [ ] **Step 1: Add the add icon**

Create `app/src/main/res/drawable/add_24px.xml` (Material Symbols "add", 960 viewport — same family as the existing icons):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorControlNormal"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M440,680L440,520L280,520L280,440L440,440L440,280L520,280L520,440L680,440L680,520L520,520L520,680L440,680Z" />
</vector>
```

- [ ] **Step 2: Add the route key**

In `NavigationKeys.kt`, after `RouteSettings`:

```kotlin
@Serializable
data object RouteSiteManager : NavKey
```

- [ ] **Step 3: Create SiteManagerScreen.kt**

Create `app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.sitemanager

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import me.jbusdriver.R
import me.jbusdriver.modern.data.mirror.MirrorUrl
import me.jbusdriver.modern.data.mirror.ScanPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteManagerScreen(
    onBack: () -> Unit,
    viewModel: SiteManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.format(context), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::startVerify,
                        enabled = !uiState.isScanning
                    ) {
                        Text(stringResource(R.string.site_test_all))
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            painterResource(R.drawable.add_24px),
                            contentDescription = stringResource(R.string.site_add_title)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SiteSection(
                title = stringResource(R.string.site_group_preset),
                entries = uiState.entries.filter { it.group == SiteGroup.PRESET },
                selectedUrl = uiState.selectedUrl,
                measurements = uiState.measurements,
                onSelect = viewModel::selectSite
            )

            SiteSection(
                title = stringResource(R.string.site_group_scanned),
                entries = uiState.entries.filter { it.group == SiteGroup.SCANNED },
                selectedUrl = uiState.selectedUrl,
                measurements = uiState.measurements,
                onSelect = viewModel::selectSite,
                onDelete = viewModel::deleteEntry,
                action = {
                    TextButton(
                        onClick = viewModel::startScan,
                        enabled = !uiState.isScanning
                    ) {
                        Text(
                            if (uiState.scanPhase == ScanPhase.DONE) stringResource(R.string.rescan)
                            else stringResource(R.string.site_scan)
                        )
                    }
                },
                emptyHint = stringResource(R.string.site_scan_empty)
            )

            val customEntries = uiState.entries.filter { it.group == SiteGroup.CUSTOM }
            if (customEntries.isNotEmpty()) {
                SiteSection(
                    title = stringResource(R.string.site_group_custom),
                    entries = customEntries,
                    selectedUrl = uiState.selectedUrl,
                    measurements = uiState.measurements,
                    onSelect = viewModel::selectSite,
                    onDelete = viewModel::deleteEntry
                )
            }

            if (uiState.isScanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                val phaseText = when (uiState.scanPhase) {
                    ScanPhase.DISCOVERING -> stringResource(
                        R.string.scanning, uiState.scannedCount, uiState.totalCount
                    )
                    ScanPhase.VERIFYING -> stringResource(
                        R.string.verifying, uiState.scannedCount, uiState.totalCount
                    )
                    else -> ""
                }
                if (phaseText.isNotEmpty()) {
                    Text(
                        phaseText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.currentUrl.isNotBlank()) {
                    Text(
                        uiState.currentUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddCustomUrlDialog(
            onDismiss = { showAddDialog = false },
            onSave = { url ->
                showAddDialog = false
                viewModel.addCustomUrl(url)
            }
        )
    }
}

@Composable
private fun SiteSection(
    title: String,
    entries: List<SiteEntry>,
    selectedUrl: String,
    measurements: Map<String, MirrorUrl>,
    onSelect: (String) -> Unit,
    onDelete: ((SiteEntry) -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    emptyHint: String? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            if (entries.isEmpty()) {
                if (emptyHint != null) {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            } else {
                entries.forEach { entry ->
                    SiteEntryRow(
                        entry = entry,
                        selected = entry.url == selectedUrl,
                        measured = measurements[entry.url],
                        onClick = { onSelect(entry.url) },
                        onDelete = onDelete?.let { cb -> { cb(entry) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteEntryRow(
    entry: SiteEntry,
    selected: Boolean,
    measured: MirrorUrl?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(40.dp))
        Text(
            entry.url,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (measured != null) {
            if (!measured.isReachable) {
                Text(
                    stringResource(R.string.unreachable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "${measured.latencyMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.delete_24px),
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddCustomUrlDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.site_add_title)) },
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                singleLine = true,
                supportingText = { Text(stringResource(R.string.site_add_hint)) },
                placeholder = { Text("https://…") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (urlText.isNotBlank()) onSave(urlText) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
```

- [ ] **Step 4: Register the navigation entry**

In `Navigation.kt`, after the `entry<RouteSettings> { ... }` block (~line 337), add (transition metadata mirrors `RouteSettings` — iOS-style slide-up):

```kotlin
            entry<RouteSiteManager>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        (slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(ANIM_DURATION_SEARCH)
                        ) + fadeIn(tween(ANIM_DURATION_SEARCH))) togetherWith
                            (scaleOut(
                                targetScale = 0.9f,
                                animationSpec = tween(ANIM_DURATION)
                            ) + fadeOut(tween(ANIM_DURATION)))
                    }
                    put(NavDisplay.PopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(ANIM_DURATION)
                        ) + fadeIn(tween(ANIM_DURATION))) togetherWith
                            (slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(ANIM_DURATION_SEARCH)
                            ) + fadeOut(tween(ANIM_DURATION_SEARCH)))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(ANIM_DURATION)
                        ) + fadeIn(tween(ANIM_DURATION))) togetherWith
                            (slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(ANIM_DURATION_SEARCH)
                            ) + fadeOut(tween(ANIM_DURATION_SEARCH)))
                    }
                }
            ) {
                SiteManagerScreen(onBack = { backStack.removeLastOrNull() })
            }
```

Add import: `import me.jbusdriver.modern.ui.sitemanager.SiteManagerScreen`.

- [ ] **Step 5: Update AGENTS.md package structure**

In the `ui/` tree, after the `search/` line, add:

```
    sitemanager/              - Site manager screen + ViewModel (preset/scanned/custom site groups)
```

- [ ] **Step 6: Compile check**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable/add_24px.xml \
        app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt \
        app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt \
        app/src/main/java/me/jbusdriver/modern/ui/sitemanager/SiteManagerScreen.kt \
        AGENTS.md
git status   # verify: only the 5 files staged
git commit -m "feat(site): site manager screen with grouped entries and unified speed test"
```

---

### Task 4: Settings NetworkCard slim + wiring

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` (line ~336)

**Interfaces:**
- Consumes: `RouteSiteManager` (Task 3).
- Produces: `SettingsScreen(onBack: () -> Unit, onOpenSiteManager: () -> Unit, ...)`.

- [ ] **Step 1: Slim the NetworkCard composable**

In `SettingsScreen.kt`, replace the whole `NetworkCard` signature and the block from the `// Dropdown spinner for URL selection` comment through the end of the `// Error` block (the dropdown, action buttons, progress, and error sections — roughly lines 688-898 of the current file) so the composable becomes:

```kotlin
@Composable
private fun NetworkCard(
    selectedBaseUrl: String,
    ltxEnabled: Boolean,
    ltxBaseUrl: String,
    onLtxEnabledChange: (Boolean) -> Unit,
    onLtxBaseUrlSave: (String) -> Unit,
    onOpenSiteManager: () -> Unit
) {
    var showLtxUrlDialog by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.public_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.select_url),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            // 当前站点：点击进入站点管理页
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSiteManager)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedBaseUrl,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // === LTX 資料庫（保持不变） ===
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                stringResource(R.string.ltx_enable),
                ltxEnabled,
                onLtxEnabledChange,
            )
            // ... LTX URL row + dialog unchanged ...
        }
    }
}
```

Keep the LTX URL row and dialog code exactly as it is today (from `if (ltxEnabled) {` through the end of the `showLtxUrlDialog` block).

- [ ] **Step 2: Update SettingsScreen call sites**

2a. Signature gains the callback (second parameter):

```kotlin
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSiteManager: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
)
```

2b. Delete these state collections and computations:

```kotlin
    val cachedMirrorUrls by store.cachedMirrorUrls.collectAsStateWithLifecycle()   // delete
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()            // delete
    // the whole "Mirror URL list for network card" block (mirrorUrls + hasCachedUrls)  // delete
```

2c. Replace the `NetworkCard(...)` call:

```kotlin
            NetworkCard(
                selectedBaseUrl = selectedBaseUrl,
                ltxEnabled = ltxEnabled,
                ltxBaseUrl = ltxBaseUrl,
                onLtxEnabledChange = { viewModel.setLtxEnabled(it) },
                onLtxBaseUrlSave = { viewModel.setLtxBaseUrl(it) },
                onOpenSiteManager = onOpenSiteManager
            )
```

2d. Delete now-unused imports at the top of the file: `me.jbusdriver.modern.data.mirror.MirrorUrl`, `me.jbusdriver.modern.data.mirror.ScanPhase`, `me.jbusdriver.modern.data.mirror.ScanState`, plus `androidx.compose.material3.ExposedDropdownMenuBox`, `androidx.compose.material3.ExposedDropdownMenuDefaults`, `androidx.compose.material3.DropdownMenuItem`, `androidx.compose.material3.Button`, `androidx.compose.material3.ButtonDefaults`, `androidx.compose.material3.OutlinedButton` **only if** the editor/lint reports them unused after the deletion (other cards may still use `OutlinedButton`/`Button`). The three `data.mirror` imports are definitely dead — remove unconditionally.

- [ ] **Step 3: Remove the scan region from SettingsViewModel**

Delete from `SettingsViewModel.kt`:

- The `// region Network scan` block entirely: `_scanState`, `scanState`, `startScan()`, `startVerify()`, `cancelScan()`, `selectUrl()`, `launchScan()`.
- Constructor params now unused: `private val siteConfig: SiteConfig` and `@IoDispatcher private val ioDispatcher: CoroutineDispatcher` (verify with a project-wide search that `ioDispatcher`/`siteConfig` have no other use in this file — they don't as of this plan).
- Imports now unused: `me.jbusdriver.modern.core.site.SiteConfig`, `me.jbusdriver.modern.core.coroutine.IoDispatcher`, `kotlinx.coroutines.CoroutineDispatcher`, `me.jbusdriver.modern.data.mirror.ScanState`, `kotlinx.coroutines.flow.MutableStateFlow` (still used by `UpdateCheckState`/`translationTestRunning` — keep), `kotlinx.coroutines.flow.asStateFlow` (still used — keep). Concretely remove the first four listed; keep the rest.
- `KLog`, `CancellationException`, `MutableStateFlow`, `asStateFlow`, `first`, `combine`, `update` stay (other regions use them).

- [ ] **Step 4: Wire the navigation call**

In `Navigation.kt` (~line 336), change:

```kotlin
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
```

to:

```kotlin
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenSiteManager = { backStack.add(RouteSiteManager) }
                )
```

- [ ] **Step 5: Compile + full unit tests**

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all unit tests pass (existing `SettingsViewModel` tests, if any reference removed members, must be updated in this step — as of this plan there is no SettingsViewModelTest).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt \
        app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt
git status   # verify: only the 3 files staged
git commit -m "refactor(settings): network card hands off to site manager"
```

---

### Task 5: Quality gates + manual smoke checklist

**Files:** none created/modified (verification only).

- [ ] **Step 1: Full gates**

```bash
export JAVA_HOME="C:/Users/laiguizhong/AppData/Local/Programs/Android Studio 2/jbr"
cd D:/Projects/AndroidProjects/JBusDriver
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Expected: all three BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke checklist (device/emulator)**

- Settings → 網址 row shows current URL; tapping opens 站點管理.
- 官方 group lists only `www.javbus.com`; legacy cached mirrors (from an old install) appear under 掃描發現 with delete buttons.
- ＋ adds a custom URL; invalid input (no scheme) and duplicates (incl. main site) toast 網址無效或已存在; a valid save toasts the advisory reachability result.
- 全部測速 fills latency badges across all three groups; rows remain clickable even when 不可達.
- 掃描 discovers mirrors; re-scan relabels to 重新掃描; empty scanned group shows the hint + still offers 掃描.
- Selecting a row updates the Settings row value and the app actually loads from the new base URL (open the movie list).
- Deleting the currently selected row toasts 請先切換到其他站點再刪除; deleting another row removes it.

- [ ] **Step 3: Report results**

Report gate outputs and smoke results to the user. No commit (nothing changed). If gates fail, fix in the owning task's files and commit the fix with `fix(site): ...` before re-running.

---

## Self-Review Notes (resolved during planning)

- Spec coverage: preset trim (T1), custom URLs + dedup + advisory check (T1/T2), scan seeds incl. custom (T1), persistence filter (T1), migration-free legacy cache (T1 default change), unreachable selectable (T3 rows always clickable), delete-selected guard (T2), unified speed test (T1 verifyMirrorUrls union + T3 全部測速), Settings slim (T4), strings i18n (T2), AGENTS.md structure (T3).
- Spec amendment made during planning: the 掃描發現 header always renders (empty group shows a hint) — otherwise the 掃描 action becomes unreachable when the group is empty. Spec file updated accordingly.
- `combine` with exactly 5 flows is within the stdlib's 5-arity overload.
- `normalizeBaseUrl`/`deriveSiteEntries`/helpers are `internal` — same-module test access is intentional.
- Hilt: `SiteSettingsSource` gets its own `@Binds` (Task 1 Step 7); `SiteManagerViewModel` injects the narrow interface, not the full contract.
