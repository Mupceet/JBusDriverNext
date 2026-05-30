# Persistence Layer Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all SharedPreferences with Preferences DataStore, remove the SD card hack, and migrate all Store interfaces to suspend functions.

**Architecture:** Each Store creates and owns its `DataStore<Preferences>` instance via `Context.dataStore(name)`. The `PreferencesModule`, `AppPreferences`, `SDCardDatabaseContext`, and `CoverStats` files are deleted. Two new Store classes (`UiPrefsStore`, `GifLoadTracker`) centralize previously-scattered SP access.

**Tech Stack:** AndroidX DataStore Preferences 1.1.4, Kotlin Coroutines, Hilt DI

---

## File Structure

### Files to Delete
- `app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt` — SP provider module, replaced by Store-owned DataStore
- `app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt` — SP name constants, move into Store companion objects
- `app/src/main/java/me/jbusdriver/modern/data/db/SDCardDatabaseContext.kt` — SD card hack, no longer needed
- `app/src/main/java/me/jbusdriver/modern/core/CoverStats.kt` — dead code, zero consumers

### Files to Create
- `app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt` — encapsulates `is_grid` pref (was direct SP access in MainScreen/MovieList)
- `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt` — encapsulates GIF URL set (was `@GifPrefs` SP in ForumViewModels)

### Files to Modify
- `gradle/libs.versions.toml` — add datastore version + library
- `app/build.gradle.kts` — add datastore dependency
- `app/src/main/java/me/jbusdriver/modern/data/db/DB.kt` — remove SDCardDatabaseContext
- `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt` — SP → DataStore, MutableStateFlow → Flow
- `app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt` — SP → DataStore, interface → suspend
- `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt` — SP → DataStore, methods → suspend
- `app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt` — inject LabSettingsStore
- `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt` — inject UiPrefsStore
- `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt` — remove global `gridPrefs`, accept `isGrid` parameter only
- `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt` — inject GifLoadTracker, remove `@GifPrefs` SP
- `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt` — adapt to suspend Store
- `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt` — adapt to suspend Store
- `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt` — adapt to Flow + suspend
- `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt` — test fake → suspend

---

### Task 1: Add DataStore dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version and library entry to libs.versions.toml**

In `gradle/libs.versions.toml`, add after the `lottie = "6.7.1"` line in `[versions]`:

```toml
datastore = "1.1.4"
```

Add in `[libraries]` section after the `lottie-compose` entry:

```toml
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Add implementation to app/build.gradle.kts**

In `app/build.gradle.kts`, add after the `implementation(libs.lottie.compose)` line in the `dependencies` block:

```kotlin
    // DataStore
    implementation(libs.datastore.preferences)
```

- [ ] **Step 3: Verify Gradle sync succeeds**

Run: `./gradlew assembleDebug --dry-run`
Expected: BUILD SUCCESSFUL (configuration resolved)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add DataStore Preferences dependency"
```

---

### Task 2: Remove SD card hack

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/DB.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/data/db/SDCardDatabaseContext.kt`

- [ ] **Step 1: Simplify collectDatabase in DB.kt**

In `app/src/main/java/me/jbusdriver/modern/data/db/DB.kt`, replace the `collectDatabase` lazy block (lines 57-66) with:

```kotlin
    val collectDatabase: CollectDatabase by lazy {
        Room.databaseBuilder(
            JBus,
            CollectDatabase::class.java,
            COLLECT_DB_NAME
        ).addMigrations(COLLECT_MIGRATION_1_2).build()
    }
```

Remove the `import java.io.File` and the `import me.jbusdriver.modern.data.db.SDCardDatabaseContext` (if present) — the only File import needed is already used elsewhere. Actually, check: remove the `File` import only if nothing else in the file uses it. The `JBus.packageName + File.separator` line is removed, so the `File` import may no longer be needed.

- [ ] **Step 2: Delete SDCardDatabaseContext.kt**

```bash
rm app/src/main/java/me/jbusdriver/modern/data/db/SDCardDatabaseContext.kt
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: remove SD card hack, CollectDatabase uses internal storage"
```

---

### Task 3: Delete dead code (PreferencesModule, AppPreferences, CoverStats)

**Files:**
- Delete: `app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/core/CoverStats.kt`

- [ ] **Step 1: Delete the three files**

```bash
rm app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt
rm app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt
rm app/src/main/java/me/jbusdriver/modern/core/CoverStats.kt
```

- [ ] **Step 2: Build will fail — that's expected. Note the compilation errors for the next tasks.**

Run: `./gradlew assembleDebug 2>&1 | grep "^e:" `
Expected: Multiple unresolved reference errors for `AppPreferences`, `@LabSettingsPrefs`, `@SearchHistoryPrefs`, `@SessionCookiePrefs`, `@UiPrefs`, `@GifPrefs`, `CoverStats`. These are all expected and will be fixed in subsequent tasks.

- [ ] **Step 3: Do NOT commit yet — the project is intentionally broken. The following tasks will fix each consumer.**

---

### Task 4: Create UiPrefsStore and GifLoadTracker

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`

- [ ] **Step 1: Create UiPrefsStore.kt**

Write `app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt`:

```kotlin
package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPrefsDataStore by preferencesDataStore("ui_prefs")

@Singleton
class UiPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.uiPrefsDataStore

    val isGrid: Flow<Boolean> = dataStore.data.map { it[IS_GRID] ?: false }

    suspend fun setGrid(isGrid: Boolean) {
        dataStore.edit { it[IS_GRID] = isGrid }
    }

    companion object {
        private val IS_GRID = booleanPreferencesKey("is_grid")
    }
}
```

- [ ] **Step 2: Create GifLoadTracker.kt**

Write `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`:

```kotlin
package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gifDataStore by preferencesDataStore("gif_loaded_urls")

@Singleton
class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.gifDataStore

    suspend fun loadedUrls(): Set<String> {
        return dataStore.data.map { it[URLS] ?: emptySet() }.first()
    }

    suspend fun markLoaded(url: String) {
        dataStore.edit { prefs ->
            val current = prefs[URLS] ?: emptySet()
            val updated = if (current.size >= MAX_CACHE) {
                current.toList().takeLast(MAX_CACHE - 1).toSet() + url
            } else {
                current + url
            }
            prefs[URLS] = updated
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val URLS = stringSetPreferencesKey("urls")
        private const val MAX_CACHE = 500
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt
git commit -m "feat: add UiPrefsStore and GifLoadTracker backed by DataStore"
```

---

### Task 5: Migrate LabSettingsStore to DataStore

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt`

- [ ] **Step 1: Rewrite LabSettingsStore.kt**

Replace the full content of `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`. The scan/verify methods remain unchanged; only the persistence layer changes:

```kotlin
package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import me.jbusdriver.modern.core.http.WebViewHelper.loadUrlAwait
import me.jbusdriver.modern.core.http.WebViewHelper.unescapeJsString
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.labSettingsDataStore by preferencesDataStore("lab_settings")

data class MirrorUrl(
    val url: String,
    val isReachable: Boolean = false,
    val latencyMs: Long = -1L
)

data class ScanState(
    val isScanning: Boolean = false,
    val phase: ScanPhase = ScanPhase.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val discoveredUrls: List<MirrorUrl> = emptyList(),
    val error: String? = null
)

enum class ScanPhase {
    IDLE,
    DISCOVERING,
    VERIFYING,
    DONE
}

@Singleton
class LabSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.labSettingsDataStore

    val forumEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_FORUM_ENABLED] ?: false }
    val autoLoadGifs: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_LOAD_GIFS] ?: false }
    val selectedBaseUrl: Flow<String> = dataStore.data.map { it[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL }
    val cachedMirrorUrls: Flow<List<String>> = dataStore.data.map {
        it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS
    }

    suspend fun setForumEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FORUM_ENABLED] = enabled }
    }

    suspend fun setAutoLoadGifs(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LOAD_GIFS] = enabled }
    }

    suspend fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        dataStore.edit { it[KEY_SELECTED_BASE_URL] = trimmed }
        NetClient.defaultFastUrl = trimmed
    }

    /**
     * JavaScript to extract mirror URLs from the rendered DOM.
     */
    private val extractMirrorJs = """
        (function() {
            var urls = [];
            document.querySelectorAll('strong').forEach(function(el) {
                var text = el.textContent;
                if (text.indexOf('防屏蔽地址') !== -1 || text.indexOf('永久域名') !== -1) {
                    var parent = el.parentElement;
                    var link = parent ? parent.querySelector('a[href]') : null;
                    if (link && link.href && link.href.indexOf('http') === 0) {
                        urls.push(link.href);
                    }
                }
            });
            return JSON.stringify(urls);
        })()
    """

    /**
     * Scan mirror URLs by loading pages in a WebView (JS-rendered content)
     * and recursively discovering mirror addresses.
     */
    suspend fun scanMirrorUrls(
        state: MutableStateFlow<ScanState>,
        seedUrl: String
    ) {
        val allSeeds = mutableSetOf<String>()
        allSeeds.add(seedUrl.trimEnd('/'))
        // Read current cached URLs from DataStore flow
        for (url in cachedMirrorUrls.first()) {
            allSeeds.add(url.trimEnd('/'))
        }

        state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)

        try {
            // Phase 1: Discover URLs from all seeds in parallel via WebView
            val discovered = mutableSetOf<String>()
            discovered.addAll(allSeeds)

            withContext(Dispatchers.Main) {
                val webView = WebViewHelper.createWebView()
                try {
                    val seeds = allSeeds.toList()
                    val completed = java.util.concurrent.atomic.AtomicInteger(0)
                    for (url in seeds) {
                        if (!coroutineContext.isActive) break
                        state.value = ScanState(
                            isScanning = true,
                            phase = ScanPhase.DISCOVERING,
                            scannedCount = completed.incrementAndGet(),
                            totalCount = seeds.size,
                            currentUrl = url
                        )
                        try {
                            val mirrorUrls = loadAndExtractMirrorUrls(webView, url)
                            for (found in mirrorUrls) {
                                val trimmed = found.trimEnd('/')
                                if (trimmed !in discovered) {
                                    discovered.add(trimmed)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            KLog.d("[Mirror] Seed $url failed, skipping: ${e.message}")
                        }
                    }
                } finally {
                    webView.stopLoading()
                    webView.destroy()
                }
            }

            // Phase 2: Verify reachability in parallel
            val urlList = discovered.toList()
            val verified = verifyUrlsParallel(urlList, state)

            // Cache all discovered URLs
            dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = urlList.toSet() }

            state.value = ScanState(
                isScanning = false,
                phase = ScanPhase.DONE,
                discoveredUrls = sortMirrorUrls(verified)
            )
        } catch (e: CancellationException) {
            if (allSeeds.size > 1) {
                dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = allSeeds.toSet() }
                KLog.d("[Mirror] Scan cancelled, saved ${allSeeds.size} seeds")
            }
            state.value = ScanState()
            throw e
        }
    }

    /**
     * Re-verify reachability of cached URLs without re-scanning.
     */
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = cachedMirrorUrls.first()
        if (urls.isEmpty()) return

        state.value = ScanState(isScanning = true, phase = ScanPhase.VERIFYING)
        val verified = verifyUrlsParallel(urls, state)

        state.value = ScanState(
            isScanning = false,
            phase = ScanPhase.DONE,
            discoveredUrls = sortMirrorUrls(verified)
        )
    }

    /**
     * Verify reachability of URLs in parallel (concurrency = 6).
     */
    private suspend fun verifyUrlsParallel(
        urls: List<String>,
        state: MutableStateFlow<ScanState>
    ): List<MirrorUrl> = coroutineScope {
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val deferreds = urls.mapIndexed { _, url ->
            async(Dispatchers.IO) {
                val latency = NetClient.checkReachable(url)
                val done = completed.incrementAndGet()
                state.value = ScanState(
                    isScanning = true,
                    phase = ScanPhase.VERIFYING,
                    scannedCount = done,
                    totalCount = urls.size,
                    currentUrl = url
                )
                MirrorUrl(url, latency >= 0, latency)
            }
        }
        deferreds.awaitAll()
    }

    private fun sortMirrorUrls(urls: List<MirrorUrl>): List<MirrorUrl> {
        val defaultHost = "www.javbus.com"
        return urls.sortedWith(
            compareBy<MirrorUrl> { it.url.contains(defaultHost, ignoreCase = true).not() }
                .thenBy { if (it.isReachable) it.latencyMs else Long.MAX_VALUE }
                .thenBy { it.url }
        )
    }

    private suspend fun loadAndExtractMirrorUrls(webView: android.webkit.WebView, url: String): List<String> {
        webView.loadUrlAwait(url)

        val result = webView.evaluateJs(extractMirrorJs)
        if (result == null || result == "null") return emptyList()

        return try {
            val jsonStr = unescapeJsString(result)
            val arr = JSONArray(jsonStr)
            val urls = (0 until arr.length()).map { arr.getString(it) }
            KLog.d("Mirror scan found ${urls.size} URLs from $url")
            urls
        } catch (e: Exception) {
            KLog.d("Mirror JS extraction failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private val KEY_FORUM_ENABLED = booleanPreferencesKey("forum_enabled")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
```

Note: `firstSuspend()` uses `kotlinx.coroutines.flow.first(this)` to read the current value from the DataStore Flow. Import `kotlinx.coroutines.flow.first` at the top.

- [ ] **Step 2: Update SiteConfigStore to use LabSettingsStore**

Replace the full content of `app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt`:

```kotlin
package me.jbusdriver.modern.core.site

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.jbusdriver.modern.data.LabSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

interface SiteConfig {
    var baseUrl: String

    fun resolve(pathOrUrl: String): String

    fun referer(): String = "${baseUrl.trimEnd('/')}/"
}

@Singleton
class DefaultSiteConfig @Inject constructor(
    private val labSettingsStore: LabSettingsStore
) : SiteConfig {
    @Volatile
    override var baseUrl: String = runBlocking {
        labSettingsStore.selectedBaseUrl.first()
    }
        private set

    suspend fun updateBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    override fun resolve(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http")) return pathOrUrl
        val prefix = if (pathOrUrl.startsWith("/")) "" else "/"
        return baseUrl.trimEnd('/') + prefix + pathOrUrl
    }
}
```

Note: `runBlocking` is used here only once during singleton initialization to get the persisted URL. All subsequent URL changes go through `LabSettingsStore.selectUrl()` which is `suspend`. The `SiteConfigStore` singleton object is removed.

- [ ] **Step 3: Update LabSettingsViewModel for suspend selectUrl**

In `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt`, change the `selectUrl` method:

```kotlin
    fun selectUrl(url: String) {
        viewModelScope.launch { store.selectUrl(url) }
    }
```

Also change `scanMirrorUrls` to collect the current baseUrl from the Flow instead of `.value`:

```kotlin
    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanState.value = ScanState()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentUrl = store.selectedBaseUrl.first()
                store.scanMirrorUrls(_scanState, currentUrl)
            } catch (e: Exception) {
                _scanState.value = ScanState(error = e.message ?: "掃描失敗")
            }
        }
    }
```

Add import: `import kotlinx.coroutines.flow.first`

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: LabSettingsStore, SiteConfig, LabSettingsViewModel compile. Other files may still fail (expected).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
git commit -m "refactor: migrate LabSettingsStore to DataStore, fix SiteConfig injection"
```

---

### Task 6: Migrate SearchHistoryStore to DataStore + suspend

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`

- [ ] **Step 1: Rewrite SearchHistoryStore.kt**

Replace the full content of `app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt`:

```kotlin
package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject
import javax.inject.Singleton

interface SearchHistoryStore {
    suspend fun getHistory(): List<String>
    suspend fun addQuery(query: String)
    suspend fun removeQuery(query: String)
    suspend fun clearHistory()
}

private val Context.searchHistoryDataStore by preferencesDataStore("search_history")

@Singleton
class DefaultSearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SearchHistoryStore {

    private val dataStore = context.searchHistoryDataStore

    override suspend fun getHistory(): List<String> {
        val json = dataStore.data.map { it[KEY_HISTORY] }.first() ?: return emptyList()
        return GSON.fromJson<List<String>>(json) ?: emptyList()
    }

    override suspend fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        dataStore.edit { it[KEY_HISTORY] = GSON.toJson(current) }
    }

    override suspend fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        if (current.remove(query)) {
            dataStore.edit { it[KEY_HISTORY] = GSON.toJson(current) }
        }
    }

    override suspend fun clearHistory() {
        dataStore.edit { it.remove(KEY_HISTORY) }
    }

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("search_history_queries")
        private const val MAX_HISTORY = 20
    }
}
```

- [ ] **Step 2: Update SearchViewModel.kt for suspend Store**

In `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`:

Change the `_searchHistory` initialization (line 77) from:
```kotlin
    private val _searchHistory = MutableStateFlow(historyStore.getHistory())
```
to:
```kotlin
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
```

Add an init block to load history asynchronously:
```kotlin
    init {
        viewModelScope.launch { _searchHistory.value = historyStore.getHistory() }
    }
```

Change `clearHistory()` (lines 81-84):
```kotlin
    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clearHistory()
            _searchHistory.value = emptyList()
        }
    }
```

Change `removeHistoryItem()` (lines 87-90):
```kotlin
    fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            historyStore.removeQuery(query)
            _searchHistory.value = historyStore.getHistory()
        }
    }
```

Change `search()` — move the history calls inside the `viewModelScope.launch` block (lines 120-121):
```kotlin
    fun search(query: String, type: SearchType? = null) {
        if (query.isBlank()) return
        val searchType = type ?: _uiState.value.searchType
        viewModelScope.launch {
            historyStore.addQuery(query)
            _searchHistory.value = historyStore.getHistory()
        }
        viewModelScope.launch {
```
(The existing `viewModelScope.launch` block for the actual search follows as before.)

- [ ] **Step 3: Update SearchViewModelTest.kt for suspend interface**

In `app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt`, change the `fakeHistoryStore()` method (lines 34-47):

```kotlin
    private fun fakeHistoryStore() = object : SearchHistoryStore {
        private val history = mutableListOf<String>()
        override suspend fun getHistory(): List<String> = history.toList()
        override suspend fun addQuery(query: String) {
            history.remove(query)
            history.add(0, query)
        }
        override suspend fun removeQuery(query: String) {
            history.remove(query)
        }
        override suspend fun clearHistory() {
            history.clear()
        }
    }
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: SearchHistoryStore, SearchViewModel, and test compile.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt app/src/test/java/me/jbusdriver/modern/ui/search/SearchViewModelTest.kt
git commit -m "refactor: migrate SearchHistoryStore to DataStore, interface → suspend"
```

---

### Task 7: Migrate SessionCookieStore to DataStore + suspend

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`

- [ ] **Step 1: Rewrite SessionCookieStore.kt**

Replace the full content of `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt`:

```kotlin
package me.jbusdriver.modern.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject

private val Context.sessionCookieDataStore by preferencesDataStore("session_cookies")

/**
 * Persists session cookies from CookieManager to DataStore.
 *
 * Stores critical cookies (age verification, Discuz! session) with their expiry
 * timestamps so they can be restored on next app launch without creating a WebView.
 */
class SessionCookieStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.sessionCookieDataStore

    suspend fun saveCookies(url: String) {
        val cookieString = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieString(cookieString)
        val entries = mutableMapOf<String, PersistedCookie>()

        for ((name, value) in cookies) {
            if (name in TRACKED_COOKIES) {
                val ttlSeconds = COOKIE_TTL[name] ?: 0L
                val expiresAt = if (ttlSeconds > 0) {
                    System.currentTimeMillis() / 1000 + ttlSeconds
                } else {
                    0L
                }
                entries[name] = PersistedCookie(value, expiresAt)
            }
        }

        if (entries.isNotEmpty()) {
            val json = GSON.toJson(entries)
            dataStore.edit { it[prefsKey(url)] = json }
            KLog.d("[SessionCookieStore] Saved ${entries.size} cookies for $url", TAG)
        } else {
            KLog.d("[SessionCookieStore] No tracked cookies found for $url", TAG)
        }
    }

    suspend fun restoreCookies(url: String) {
        val key = prefsKey(url)
        val json = dataStore.data.map { it[key] }.first() ?: return
        val entries = tryParse(json) ?: return
        val now = System.currentTimeMillis() / 1000
        val cookieManager = CookieManager.getInstance()
        var restored = 0

        for ((name, cookie) in entries) {
            if (cookie.expiresAt == 0L || cookie.expiresAt > now) {
                cookieManager.setCookie(url, "$name=${cookie.value}; path=/")
                restored++
            }
        }
        if (restored > 0) cookieManager.flush()
        KLog.d("[SessionCookieStore] Restored $restored/${entries.size} cookies for $url", TAG)
    }

    suspend fun isSessionValid(url: String): Boolean {
        val key = prefsKey(url)
        val json = dataStore.data.map { it[key] }.first() ?: return false
        val entries = tryParse(json) ?: return false
        val now = System.currentTimeMillis() / 1000

        for (name in CRITICAL_COOKIES) {
            val cookie = entries[name] ?: return false
            if (cookie.expiresAt != 0L && cookie.expiresAt <= now) {
                KLog.d("[SessionCookieStore] Cookie $name expired", TAG)
                return false
            }
        }
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
        KLog.d("[SessionCookieStore] Cleared all persisted cookies", TAG)
    }

    private fun prefsKey(url: String): androidx.datastore.preferences.core.Preferences.Key<String> {
        val host = url.substringAfter("://").substringBefore("/")
        return stringPreferencesKey("session_cookies_$host")
    }

    private fun tryParse(json: String): Map<String, PersistedCookie>? {
        return try { GSON.fromJson<Map<String, PersistedCookie>>(json) } catch (_: Exception) { null }
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        return cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    internal data class PersistedCookie(
        val value: String,
        val expiresAt: Long
    )

    companion object {
        private const val TAG = "SessionCookie"
        private val TRACKED_COOKIES = setOf(
            "age", "PHPSESSID",
            "4fJN_2132_saltkey", "4fJN_2132_sid",
            "4fJN_2132_lastvisit", "4fJN_2132_lastact"
        )
        private val CRITICAL_COOKIES = setOf("age", "4fJN_2132_saltkey")
        private val COOKIE_TTL = mapOf(
            "age" to 30 * 24 * 3600L,
            "4fJN_2132_saltkey" to 30 * 24 * 3600L,
            "4fJN_2132_sid" to 24 * 3600L,
            "4fJN_2132_lastvisit" to 30 * 24 * 3600L,
            "4fJN_2132_lastact" to 24 * 3600L
        )
    }
}
```

- [ ] **Step 2: Update ForumSessionManager.kt**

In `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`:

The `persistCookies()` method (line 154-156) calls `cookieStore.saveCookies()` which is now `suspend`. Change it to:

```kotlin
    suspend fun persistCookies() {
        cookieStore.saveCookies(siteConfig.referer())
    }
```

All other calls to `cookieStore` in this file (`isSessionValid`, `restoreCookies`, `saveCookies`) are already inside `suspend` functions (`ensureSession`, `initWebView`), so they compile naturally.

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: SessionCookieStore and ForumSessionManager compile.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt
git commit -m "refactor: migrate SessionCookieStore to DataStore, methods → suspend"
```

---

### Task 8: Update consumers (MainScreen, MovieList, ForumViewModels)

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`

- [ ] **Step 1: Update MainScreen.kt**

In `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`:

Remove imports:
```
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.data.AppPreferences
import androidx.core.content.edit
```

Add imports:
```
import androidx.hilt.navigation.compose.hiltViewModel
import me.jbusdriver.modern.data.UiPrefsStore
```

Note: `hiltViewModel` is already used elsewhere in the file. Check if the import for `UiPrefsStore` is needed.

Replace the `uiPrefs` + `isGrid` block (lines 89-96):
```kotlin
    val uiPrefsStore = hiltViewModel<UiPrefsViewModel>().store
    val isGrid by uiPrefsStore.isGrid.collectAsStateWithLifecycle(false)
    val toggleGrid = {
        coroutineScope.launch { uiPrefsStore.setGrid(!isGrid) }
    }
```

Wait — `UiPrefsStore` is a singleton, not a ViewModel. Since we're in a Composable, we should inject it directly. However, Compose can't directly inject a Hilt singleton without a ViewModel. The simplest approach: create a thin ViewModel wrapper.

Actually, looking at the existing pattern, `LabSettingsStore` is accessed via `LabSettingsViewModel` which exposes `val store: LabSettingsStore`. Follow the same pattern for `UiPrefsStore`.

Create a thin ViewModel: but that adds another file. Simpler: just inject `UiPrefsStore` directly into the Composable using `LocalContext`. Actually, the cleanest way is to use the existing pattern:

Since MainScreen already uses `hiltViewModel<LabSettingsViewModel>().store` to access LabSettingsStore, follow the same pattern. But UiPrefsStore is simple enough to not need its own ViewModel. Instead, we can inject it through a ViewModel that already exists in the scope.

**Simplest approach**: Add `UiPrefsStore` as a constructor parameter to the already-created `LabSettingsViewModel`, since they're used in the same screen scope. But that couples unrelated concerns.

**Better approach**: Accept that `MainScreen` needs a small ViewModel for `UiPrefsStore`. But creating a whole ViewModel for one boolean is overkill.

**Pragmatic approach**: Since `UiPrefsStore` is a `@Singleton`, we can access it through `EntryPointAccessors` in the Composable, or create a minimal ViewModel.

Let me use the simplest correct approach — create a minimal `UiPrefsViewModel`:

Actually, the simplest approach is: since `MainScreen` already uses `LabSettingsViewModel`, just add `UiPrefsStore` to `LabSettingsViewModel`:

In `LabSettingsViewModel.kt`:
```kotlin
@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore,
    val uiPrefsStore: UiPrefsStore
) : ViewModel() {
```

Then in `MainScreen.kt`:
```kotlin
    val labVm = hiltViewModel<LabSettingsViewModel>()
    val labSettingsStore = labVm.store
    val uiPrefsStore = labVm.uiPrefsStore
    val isGrid by uiPrefsStore.isGrid.collectAsStateWithLifecycle(false)
    val coroutineScope = rememberCoroutineScope()
    val toggleGrid: () -> Unit = {
        coroutineScope.launch { uiPrefsStore.setGrid(!isGrid) }
    }
```

Remove the old `uiPrefs`/`isGrid`/`toggleGrid` block. Add `import kotlinx.coroutines.launch` and `import androidx.compose.runtime.rememberCoroutineScope`.

Replace lines 89-96 with:
```kotlin
    val labVm = hiltViewModel<LabSettingsViewModel>()
    val labSettingsStore = labVm.store
    val uiPrefsStore = labVm.uiPrefsStore
    val forumEnabled by labSettingsStore.forumEnabled.collectAsStateWithLifecycle(false)
    val isGrid by uiPrefsStore.isGrid.collectAsStateWithLifecycle(false)
    val coroutineScope = rememberCoroutineScope()
    val toggleGrid: () -> Unit = {
        coroutineScope.launch { uiPrefsStore.setGrid(!isGrid) }
    }
```

And remove the old line 98-99 that got `labSettingsStore` and `forumEnabled` separately:
```kotlin
    val labSettingsStore = hiltViewModel<LabSettingsViewModel>().store
    val forumEnabled by labSettingsStore.forumEnabled.collectAsStateWithLifecycle()
```

- [ ] **Step 2: Update LabSettingsViewModel to include UiPrefsStore**

In `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt`, add `UiPrefsStore` to the constructor:

```kotlin
@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore,
    val uiPrefsStore: UiPrefsStore
) : ViewModel() {
```

Add import: `import me.jbusdriver.modern.data.UiPrefsStore`

- [ ] **Step 3: Update MovieList.kt**

In `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`:

Delete the `gridPrefs` lazy val (lines 55-57):
```kotlin
private val gridPrefs by lazy {
    me.jbusdriver.modern.JBus.getSharedPreferences(me.jbusdriver.modern.data.AppPreferences.UI_PREFS, 0)
}
```

Change line 78:
```kotlin
    val useGrid = isGrid ?: remember { gridPrefs.getBoolean("is_grid", false) }
```
to:
```kotlin
    val useGrid = isGrid ?: false
```

Since all callers of `MovieList` now pass `isGrid` from `UiPrefsStore` (via `MainScreen`), the fallback is just `false`.

- [ ] **Step 4: Update ForumViewModels.kt — replace @GifPrefs with GifLoadTracker**

In `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt`:

Change the `ForumThreadDetailViewModel` constructor parameter from `@GifPrefs SharedPreferences` to `GifLoadTracker`:

Replace (line 219):
```kotlin
    @me.jbusdriver.modern.data.di.GifPrefs private val gifPrefs: SharedPreferences,
```
with:
```kotlin
    private val gifLoadTracker: me.jbusdriver.modern.data.GifLoadTracker,
```

Replace `loadPersistedGifUrls()` (lines 238-240):
```kotlin
    private fun loadPersistedGifUrls(): Set<String> {
        return gifPrefs.getStringSet("urls", emptySet()) ?: emptySet()
    }
```
with:
```kotlin
    private suspend fun loadPersistedGifUrls(): Set<String> {
        return gifLoadTracker.loadedUrls()
    }
```

Replace `persistGifUrls()` (lines 242-245):
```kotlin
    private fun persistGifUrls(urls: Set<String>) {
        val trimmed = if (urls.size > MAX_GIF_CACHE) urls.toList().takeLast(MAX_GIF_CACHE).toSet() else urls
        gifPrefs.edit { putStringSet("urls", trimmed) }
    }
```
with:
```kotlin
    private suspend fun persistGifUrls(urls: Set<String>) {
        // GifLoadTracker handles cache size internally
        for (url in urls) gifLoadTracker.markLoaded(url)
    }
```

Update `_loadedGifUrls` init (line 228) — since `loadPersistedGifUrls` is now suspend, load asynchronously:
```kotlin
    private val _loadedGifUrls = MutableStateFlow<Set<String>>(emptySet())
```

Add in the `init` block (after `loadDetail()`):
```kotlin
    init {
        KLog.d("[Forum] ForumThreadDetailViewModel init: tid=$tid", TAG)
        viewModelScope.launch { _loadedGifUrls.value = loadPersistedGifUrls() }
        loadDetail()
    }
```

Update `onLoadGif` (lines 233-236):
```kotlin
    fun onLoadGif(url: String) {
        _loadedGifUrls.update { it + url }
        viewModelScope.launch { persistGifUrls(setOf(url)) }
    }
```

Remove the `MAX_GIF_CACHE` constant if it was local — `GifLoadTracker` handles this internally now.

Remove the `import android.content.SharedPreferences` and `import androidx.core.content.edit` imports if present.

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
git commit -m "refactor: wire consumers to DataStore-backed stores"
```

---

### Task 9: Find and fix any remaining references to deleted files

**Files:**
- Search: `AppPreferences`, `PreferencesModule`, `SDCardDatabaseContext`, `CoverStats`, `@GifPrefs`, `@LabSettingsPrefs`, etc.

- [ ] **Step 1: Search for remaining broken imports**

Run: `./gradlew assembleDebug 2>&1 | grep "^e:" `
Expected: No errors. If there are errors, grep the codebase for the deleted class names and fix each reference.

- [ ] **Step 2: Search for any remaining direct SP access**

Run: `grep -rn "getSharedPreferences" app/src/main/java/ --include="*.kt"`
Expected: Zero results. If any remain, they need to be migrated to a Store.

- [ ] **Step 3: Run tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 4: Final commit if any fixes were needed**

```bash
git add -A
git commit -m "fix: clean up remaining references after persistence migration"
```

---

### Task 10: Build release variant and final verification

**Files:**
- None (verification only)

- [ ] **Step 1: Build release**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 3: Verify no SharedPreferences files remain in the codebase**

Run: `grep -rn "SharedPreferences" app/src/main/java/ --include="*.kt"`
Expected: Zero results (all SP usage replaced by DataStore).

Run: `grep -rn "getSharedPreferences" app/src/main/ --include="*.kt"`
Expected: Zero results.

- [ ] **Step 4: Verify deleted files are gone**

Run: `ls app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt app/src/main/java/me/jbusdriver/modern/data/db/SDCardDatabaseContext.kt app/src/main/java/me/jbusdriver/modern/core/CoverStats.kt 2>&1`
Expected: All four files report "No such file or directory".

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "chore: persistence layer migration complete — SP → DataStore"
```
