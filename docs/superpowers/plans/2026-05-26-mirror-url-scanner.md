# Mirror URL Scanner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add URL scanning and selection to Lab Settings, letting users discover mirror/anti-blocking addresses and switch between them.

**Architecture:** Recursive web-scraping scanner fetches pages, extracts mirror URLs via regex, validates reachability with HEAD requests. Results persisted in SharedPreferences. UI integrated into existing LabSettingsScreen as a new Card. `SiteConfigStore` initializes from persisted selection.

**Tech Stack:** Kotlin Coroutines, OkHttp (HEAD requests), Jsoup/Regex (HTML parsing), SharedPreferences, Jetpack Compose (Material3).

---

### Task 1: Data models + NetClient reachability check

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/http/NetClient.kt`

- [ ] **Step 1: Add MirrorUrl and ScanState to LabSettingsStore.kt**

Add these data classes at the top of `LabSettingsStore.kt` (before the class definition):

```kotlin
package me.jbusdriver.modern.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import javax.inject.Inject
import javax.inject.Singleton

data class MirrorUrl(
    val url: String,
    val isReachable: Boolean = false
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
```

- [ ] **Step 2: Add checkReachable method to NetClient.kt**

Add this method inside `NetClient` object, after `fetchDocument`:

```kotlin
    /**
     * Check if a URL is reachable via HEAD request.
     * Returns true if the server responds with a successful status code.
     */
    suspend fun checkReachable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
```

- [ ] **Step 3: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt app/src/main/java/me/jbusdriver/modern/core/http/NetClient.kt
git commit -m "feat: add MirrorUrl/ScanState models and NetClient.checkReachable"
```

---

### Task 2: LabSettingsStore — scan logic + URL persistence

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`

- [ ] **Step 1: Add persistence fields for selectedBaseUrl and cachedMirrorUrls**

Add these fields inside `LabSettingsStore` class, after `_forumEnabled`:

```kotlin
    private val _selectedBaseUrl = MutableStateFlow(
        prefs.getString(KEY_SELECTED_BASE_URL, null) ?: DEFAULT_BASE_URL
    )
    val selectedBaseUrl: StateFlow<String> = _selectedBaseUrl.asStateFlow()

    private val _cachedMirrorUrls = MutableStateFlow(
        prefs.getStringSet(KEY_CACHED_MIRROR_URLS, null)?.toList() ?: emptyList()
    )
    val cachedMirrorUrls: StateFlow<List<String>> = _cachedMirrorUrls.asStateFlow()

    fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        prefs.edit { putString(KEY_SELECTED_BASE_URL, trimmed) }
        _selectedBaseUrl.value = trimmed
        NetClient.defaultFastUrl = trimmed
    }
```

Add companion object constants:

```kotlin
    companion object {
        private const val PREFS_NAME = "lab_settings"
        private const val KEY_FORUM_ENABLED = "forum_enabled"
        private const val KEY_SELECTED_BASE_URL = "selected_base_url"
        private const val KEY_CACHED_MIRROR_URLS = "cached_mirror_urls"
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
    }
```

- [ ] **Step 2: Add scanMirrorUrls suspend function**

Add this function inside `LabSettingsStore` class:

```kotlin
    private val mirrorUrlRegex =
        """(?:防屏蔽地址|永久域名)[：:]\s*</strong>\s*<a\s+href="(https?://[^"]+)"""".toRegex()

    suspend fun scanMirrorUrls(
        state: MutableStateFlow<ScanState>,
        seedUrl: String
    ) {
        val discovered = mutableSetOf<String>()
        val scanned = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(seedUrl.trimEnd('/'))
        discovered.add(seedUrl.trimEnd('/'))

        state.value = ScanState(isScanning = true, phase = ScanPhase.DISCOVERING)

        // Phase 1: Discover URLs by crawling
        while (queue.isNotEmpty() && isActive) {
            val url = queue.removeFirst()
            if (url in scanned) continue
            scanned.add(url)

            state.value = state.value.copy(
                scannedCount = scanned.size,
                totalCount = discovered.size,
                currentUrl = url
            )

            try {
                val html = NetClient.fetchHtml(url)
                val matches = mirrorUrlRegex.findAll(html)
                for (match in matches) {
                    val found = match.groupValues[1].trimEnd('/')
                    if (found !in discovered) {
                        discovered.add(found)
                        queue.add(found)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.d("Scan failed for $url: ${e.message}")
            }
        }

        // Phase 2: Verify reachability
        val urlList = discovered.toList()
        val verified = mutableListOf<MirrorUrl>()

        for ((index, url) in urlList.withIndex()) {
            if (!isActive) break
            state.value = state.value.copy(
                phase = ScanPhase.VERIFYING,
                scannedCount = index + 1,
                totalCount = urlList.size,
                currentUrl = url
            )
            val reachable = NetClient.checkReachable(url)
            verified.add(MirrorUrl(url, reachable))
        }

        // Cache and complete
        val reachableUrls = verified.filter { it.isReachable }.map { it.url }.toSet()
        prefs.edit { putStringSet(KEY_CACHED_MIRROR_URLS, reachableUrls) }
        _cachedMirrorUrls.value = reachableUrls.toList()

        state.value = ScanState(
            isScanning = false,
            phase = ScanPhase.DONE,
            discoveredUrls = verified
        )
    }
```

Note: `isActive` refers to `kotlinx.coroutines.isActive` — it checks if the coroutine has been cancelled.

- [ ] **Step 3: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt
git commit -m "feat: add mirror URL scan logic and persistence to LabSettingsStore"
```

---

### Task 3: SiteConfigStore — read persisted URL on init

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt`

- [ ] **Step 1: Read persisted URL from SharedPreferences**

Replace the `SiteConfigStore` object initialization:

```kotlin
internal object SiteConfigStore {
    @Volatile
    var baseUrl: String = loadPersistedUrl()
        private set

    private fun loadPersistedUrl(): String {
        return try {
            val prefs = JBus.getSharedPreferences("lab_settings", 0)
            prefs.getString("selected_base_url", null) ?: "https://www.javbus.com"
        } catch (_: Exception) {
            "https://www.javbus.com"
        }
    }
}
```

Add the `JBus` import if not present: `import me.jbusdriver.modern.JBus`

The `set` is removed from `SiteConfigStore` — all writes go through `LabSettingsStore.selectUrl()` which updates both the pref and calls `NetClient.defaultFastUrl = ...` (which sets `SiteConfigStore.baseUrl` via the existing setter on `DefaultSiteConfig`).

Wait — `DefaultSiteConfig` still has a public setter that writes to `SiteConfigStore.baseUrl`. But `SiteConfigStore.baseUrl` now has `private set`. We need `DefaultSiteConfig` to still be able to set it. Change `SiteConfigStore` to use `internal set`:

```kotlin
internal object SiteConfigStore {
    @Volatile
    var baseUrl: String = loadPersistedUrl()
        internal set

    private fun loadPersistedUrl(): String {
        return try {
            val prefs = JBus.getSharedPreferences("lab_settings", 0)
            prefs.getString("selected_base_url", null) ?: "https://www.javbus.com"
        } catch (_: Exception) {
            "https://www.javbus.com"
        }
    }
}
```

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt
git commit -m "feat: SiteConfigStore reads persisted URL from SharedPreferences"
```

---

### Task 4: LabSettingsViewModel — scan orchestration

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt`

- [ ] **Step 1: Rewrite ViewModel with scan state management**

Replace the entire file:

```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.ScanState
import javax.inject.Inject

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        _scanState.value = ScanState()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                store.scanMirrorUrls(_scanState, store.selectedBaseUrl.value)
            } catch (e: Exception) {
                _scanState.value = ScanState(error = e.message ?: "掃描失敗")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState()
    }

    fun selectUrl(url: String) {
        store.selectUrl(url)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
```

- [ ] **Step 2: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
git commit -m "feat: LabSettingsViewModel with scan orchestration and cancellation"
```

---

### Task 5: LabSettingsScreen — URL selection UI

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt`

- [ ] **Step 1: Rewrite LabSettingsScreen with URL selection Card**

Replace the entire file:

```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.ScanPhase
import me.jbusdriver.modern.data.ScanState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    onBack: () -> Unit,
    viewModel: LabSettingsViewModel = hiltViewModel()
) {
    val forumEnabled by viewModel.store.forumEnabled.collectAsStateWithLifecycle()
    val selectedBaseUrl by viewModel.store.selectedBaseUrl.collectAsStateWithLifecycle()
    val cachedMirrorUrls by viewModel.store.cachedMirrorUrls.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("實驗室") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "返回"
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "實驗性功能可能不穩定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Forum card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.forum_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "論壇功能",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "瀏覽論壇版塊、閱讀和參與帖子討論",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("啟用", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = forumEnabled,
                            onCheckedChange = { viewModel.store.setForumEnabled(it) }
                        )
                    }
                }
            }

            // URL selection card
            UrlSelectionCard(
                selectedBaseUrl = selectedBaseUrl,
                cachedUrls = cachedMirrorUrls,
                scanState = scanState,
                onScan = { viewModel.startScan() },
                onCancel = { viewModel.cancelScan() },
                onSelect = { viewModel.selectUrl(it) }
            )
        }
    }
}

@Composable
private fun UrlSelectionCard(
    selectedBaseUrl: String,
    cachedUrls: List<String>,
    scanState: ScanState,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onSelect: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "網址選擇",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "當前：${selectedBaseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            // Scan button
            if (scanState.isScanning) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("取消掃描")
                }
            } else {
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanState.phase == ScanPhase.DONE) "重新掃描" else "掃描網址")
                }
            }

            // Progress
            if (scanState.isScanning) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val phaseText = when (scanState.phase) {
                    ScanPhase.DISCOVERING -> "正在掃描 ${scanState.scannedCount}/${scanState.totalCount}…"
                    ScanPhase.VERIFYING -> "正在驗證 ${scanState.scannedCount}/${scanState.totalCount}…"
                    else -> ""
                }
                Text(
                    phaseText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scanState.currentUrl.isNotBlank()) {
                    Text(
                        scanState.currentUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Error
            if (scanState.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    scanState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // URL list
            val urls = if (scanState.phase == ScanPhase.DONE) {
                scanState.discoveredUrls
            } else if (!scanState.isScanning && cachedUrls.isNotEmpty()) {
                cachedUrls.map { me.jbusdriver.modern.data.MirrorUrl(it, true) }
            } else {
                emptyList()
            }

            if (urls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                urls.forEach { mirror ->
                    val isSelected = mirror.url == selectedBaseUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = mirror.isReachable) { onSelect(mirror.url) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(
                                if (isSelected) R.drawable.radio_button_checked_24px
                                else R.drawable.radio_button_unchecked_24px
                            ),
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            mirror.url,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (!mirror.isReachable) Modifier.alpha(0.4f) else Modifier),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!mirror.isReachable) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "不可達",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add required drawable resources**

Create `app/src/main/res/drawable/radio_button_checked_24px.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M480,640Q546,640 593,593Q640,546 640,480Q640,414 593,367Q546,320 480,320Q414,320 367,367Q320,414 320,480Q320,546 367,593Q414,640 480,640ZM480,880Q397,880 324,848.5Q251,817 197,763Q143,709 111.5,636Q80,563 80,480Q80,397 111.5,324Q143,251 197,197Q251,143 324,111.5Q397,80 480,80Q563,80 636,111.5Q709,143 763,197Q817,251 848.5,324Q880,397 880,480Q880,563 848.5,636Q817,709 763,763Q709,817 636,848.5Q563,880 480,880ZM480,800Q614,800 707,707Q800,614 800,480Q800,346 707,253Q614,160 480,160Q346,160 253,253Q160,346 160,480Q160,614 253,707Q346,800 480,800Z"/>
</vector>
```

Create `app/src/main/res/drawable/radio_button_unchecked_24px.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M480,880Q397,880 324,848.5Q251,817 197,763Q143,709 111.5,636Q80,563 80,480Q80,397 111.5,324Q143,251 197,197Q251,143 324,111.5Q397,80 480,80Q563,80 636,111.5Q709,143 763,197Q817,251 848.5,324Q880,397 880,480Q880,563 848.5,636Q817,709 763,763Q709,817 636,848.5Q563,880 480,880ZM480,800Q614,800 707,707Q800,614 800,480Q800,346 707,253Q614,160 480,160Q346,160 253,253Q160,346 160,480Q160,614 253,707Q346,800 480,800Z"/>
</vector>
```

- [ ] **Step 3: Build verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt app/src/main/res/drawable/radio_button_checked_24px.xml app/src/main/res/drawable/radio_button_unchecked_24px.xml
git commit -m "feat: add URL selection Card with scan/progress/radio UI to LabSettings"
```

---

## Self-Review Checklist

- **Spec coverage:** All sections covered — data model (Task 1), scan flow (Task 2), persistence (Tasks 2+3), UI (Task 5), ViewModel (Task 4).
- **Placeholder scan:** No TBDs, TODOs, or "implement later" — all code is concrete.
- **Type consistency:** `MirrorUrl`, `ScanState`, `ScanPhase` defined in Task 1, used consistently in Tasks 2-5. `LabSettingsStore.selectUrl()` defined in Task 2, called from ViewModel in Task 4 and UI in Task 5.
- **Missing pieces:** The `LabSettingsScreen` now uses `LabSettingsViewModel` directly (via `hiltViewModel()`). The old pattern passed `labSettingsStore` as a parameter from the ViewModel — the new pattern uses `viewModel.store` instead. All call sites updated in Task 5.
