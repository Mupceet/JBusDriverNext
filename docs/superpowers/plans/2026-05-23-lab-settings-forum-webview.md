# Lab Settings & Forum WebView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a hidden lab settings screen (search-triggered), hide the Forum Tab by default, and implement a dual-mode forum (native render + WebView with injected mobile CSS/JS).

**Architecture:** `LabSettingsStore` (SharedPreferences-backed StateFlow) drives `MainScreen` tab visibility and forum mode selection. A new `ForumWebViewScreen` composable wraps Android WebView with injected CSS/JS for mobile adaptation. Cookie sharing uses the existing `SessionCookieStore`.

**Tech Stack:** Jetpack Compose, Hilt DI, Android WebView, SharedPreferences, Navigation 3

---

## File Structure

### New files
```
app/src/main/java/me/jbusdriver/modern/
  data/
    LabSettingsStore.kt              — Lab settings state (forumEnabled, forumMode)
  ui/
    settings/
      LabSettingsScreen.kt           — Lab settings UI (grouped cards)
    forum/
      ForumWebViewScreen.kt          — WebView composable + injection logic

app/src/main/assets/
  forum_mobile.css                   — Injected dark theme + mobile layout styles
  forum_mobile.js                    — Injected DOM manipulation script
```

### Modified files
```
app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt   — Add RouteLabSettings
app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt        — Add LabSettings route
app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt        — Dynamic tabs + forum mode switching
app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt  — Lab entry card on keyword match
app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt — Lab entry detection
app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt   — Bind LabSettingsStore
```

---

### Task 1: Create LabSettingsStore

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create LabSettingsStore**

```kotlin
// app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt
package me.jbusdriver.modern.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.jbusdriver.modern.JBus
import javax.inject.Inject
import javax.inject.Singleton

enum class ForumMode { NATIVE, WEBVIEW }

@Singleton
class LabSettingsStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences(PREFS_NAME, 0)

    private val _forumEnabled = MutableStateFlow(prefs.getBoolean(KEY_FORUM_ENABLED, false))
    val forumEnabled: StateFlow<Boolean> = _forumEnabled.asStateFlow()

    private val _forumMode = MutableStateFlow(
        try { ForumMode.valueOf(prefs.getString(KEY_FORUM_MODE, null) ?: ForumMode.NATIVE.name) }
        catch (_: Exception) { ForumMode.NATIVE }
    )
    val forumMode: StateFlow<ForumMode> = _forumMode.asStateFlow()

    fun setForumEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FORUM_ENABLED, enabled) }
        _forumEnabled.value = enabled
    }

    fun setForumMode(mode: ForumMode) {
        prefs.edit { putString(KEY_FORUM_MODE, mode.name) }
        _forumMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "lab_settings"
        private const val KEY_FORUM_ENABLED = "forum_enabled"
        private const val KEY_FORUM_MODE = "forum_mode"
    }
}
```

- [ ] **Step 2: Build to verify compilation**

No DataModule changes needed — `LabSettingsStore` is a concrete `@Singleton` class with `@Inject constructor()`, so Hilt provides it automatically.

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt
git commit -m "feat: add LabSettingsStore with forum toggle and mode state"
```

---

### Task 2: Add RouteLabSettings and Navigation Entry

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`

- [ ] **Step 1: Add RouteLabSettings to NavigationKeys.kt**

Append after `RouteForumThreadDetail` in `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`:

```kotlin
@Serializable
data object RouteLabSettings : NavKey
```

- [ ] **Step 2: Add LabSettings route to Navigation.kt**

In `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`, add import at the top:

```kotlin
import me.jbusdriver.modern.ui.settings.LabSettingsScreen
```

Then add a new `entry<RouteLabSettings>` block after the `entry<RouteForumThreadDetail>` block (after line 268), still inside `entryProvider`:

```kotlin
            entry<RouteLabSettings> {
                LabSettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: Build will fail because `LabSettingsScreen` doesn't exist yet. This is expected — we'll create it in Task 3. For now, **skip this build check** and proceed to Task 3.

- [ ] **Step 4: Commit (together with Task 3)**

We'll commit Tasks 2+3 together after the screen is created.

---

### Task 3: Create LabSettingsScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt`

- [ ] **Step 1: Create the settings screen**

```kotlin
// app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt
package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.ForumMode
import me.jbusdriver.modern.data.LabSettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    onBack: () -> Unit,
    labSettingsStore: LabSettingsStore = hiltViewModel<LabSettingsViewModel>().store
) {
    val forumEnabled by labSettingsStore.forumEnabled.collectAsStateWithLifecycle()
    val forumMode by labSettingsStore.forumMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "返回"
                )
            }
            Text(
                "实验室",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "实验性功能可能不稳定",
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
                    // Card header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.forum_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "论坛功能",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "浏览论坛版块、阅读和参与帖子讨论",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    // Enable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = forumEnabled,
                            onCheckedChange = { labSettingsStore.setForumEnabled(it) }
                        )
                    }

                    if (forumEnabled) {
                        Spacer(Modifier.height(12.dp))

                        // Mode selector
                        Text(
                            "渲染模式",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModeChip(
                                label = "原生渲染",
                                subtitle = if (forumMode == ForumMode.NATIVE) "✓ 当前" else "",
                                selected = forumMode == ForumMode.NATIVE,
                                onClick = { labSettingsStore.setForumMode(ForumMode.NATIVE) },
                                modifier = Modifier.weight(1f)
                            )
                            ModeChip(
                                label = "WebView",
                                subtitle = if (forumMode == ForumMode.WEBVIEW) "✓ 当前" else "网页模式",
                                selected = forumMode == ForumMode.WEBVIEW,
                                onClick = { labSettingsStore.setForumMode(ForumMode.WEBVIEW) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Placeholder for future features
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Text(
                    "更多实验功能即将推出…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 2: Create LabSettingsViewModel (thin wrapper for Hilt injection)**

```kotlin
// Add to the same file or create a separate file
// app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.jbusdriver.modern.data.LabSettingsStore
import javax.inject.Inject

@HiltViewModel
class LabSettingsViewModel @Inject constructor(
    val store: LabSettingsStore
) : ViewModel()
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit Tasks 2+3 together**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt \
        app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
git commit -m "feat: add LabSettingsScreen with forum toggle and mode selector"
```

---

### Task 4: Add Lab Entry to SearchScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`

- [ ] **Step 1: Add lab entry detection to SearchViewModel**

In `app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt`:

Add import:
```kotlin
import me.jbusdriver.modern.data.LabSettingsStore
```

Add `LabSettingsStore` to the constructor:
```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val historyStore: SearchHistoryStore,
    private val labSettingsStore: LabSettingsStore
) : ViewModel() {
```

Add a computed property after `searchHistory`:
```kotlin
    /** Whether to show the lab settings entry card */
    val showLabEntry: StateFlow<Boolean> = _uiState.map { query ->
        query.query.trim().lowercase().let { q ->
            q == "setting" || q == "settings" || q == "设置"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```

Add the required imports:
```kotlin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Add lab entry card to SearchScreen**

In `app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt`:

Add imports:
```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import me.jbusdriver.modern.ui.NavigationKeys.RouteLabSettings
```

Actually, `SearchScreen` doesn't have a navigation controller. We need to add an `onLabSettingsClick` callback. Change the function signature:

```kotlin
fun SearchScreen(
    modifier: Modifier = Modifier,
    defaultSearchType: String = "",
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    onLabSettingsClick: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
)
```

Add state collection after `val searchHistory`:
```kotlin
    val showLabEntry by viewModel.showLabEntry.collectAsStateWithLifecycle()
```

In the `!hasResults && uiState.query.isBlank()` branch (around line 212), add the lab entry card **before** the search history section. Insert right after `if (searchHistory.isNotEmpty()) {`:

```kotlin
                    if (showLabEntry) {
                        Card(
                            onClick = {
                                focusManager.clearFocus()
                                onLabSettingsClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.science_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "实验室",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "实验性功能设置",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
```

Note: If `science_24px` drawable doesn't exist, use `R.drawable.forum_24px` as a fallback and create the proper icon later.

- [ ] **Step 3: Wire onLabSettingsClick in Navigation.kt**

In `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`, update the `SearchScreen` call (around line 174):

```kotlin
            ) { key ->
                SearchScreen(
                    defaultSearchType = key.defaultSearchType,
                    onMovieClick = { movie ->
                        backStack.add(RouteMovieDetail(movie.link))
                    },
                    onActressClick = { actress ->
                        backStack.add(
                            RouteLinkMovies(
                                actress.link,
                                actress.name,
                                type = "actress",
                                avatar = actress.avatar
                            )
                        )
                    },
                    onBack = { backStack.removeLastOrNull() },
                    onLabSettingsClick = { backStack.add(RouteLabSettings) }
                )
            }
```

- [ ] **Step 4: Add science icon drawable (or use existing)**

Check if `R.drawable.science_24px` exists. If not, use `R.drawable.forum_24px` temporarily.

Run: `ls app/src/main/res/drawable/science_24px* 2>/dev/null || echo "not found"`

If not found, create a simple vector drawable at `app/src/main/res/drawable/science_24px.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorControlNormal">
  <path
      android:fillColor="@android:color/white"
      android:pathData="M200,840q-33,0 -46.5,-30t6.5,-55l230,-272v-263h-30q-17,0 -28.5,-11.5T320,180q0,-17 11.5,-28.5T360,140h240q17,0 28.5,11.5T640,180q0,17 -11.5,28.5T600,220h-30v263l230,272q20,25 6.5,55T760,840L200,840Z"/>
</vector>
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/search/SearchViewModel.kt \
        app/src/main/java/me/jbusdriver/modern/ui/search/SearchScreen.kt \
        app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt \
        app/src/main/res/drawable/science_24px.xml
git commit -m "feat: add lab settings entry in search screen via keyword trigger"
```

---

### Task 5: Dynamic Tab Visibility in MainScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

- [ ] **Step 1: Inject LabSettingsStore and make tabs dynamic**

In `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`:

Add import:
```kotlin
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.data.ForumMode
import me.jbusdriver.modern.ui.forum.ForumWebViewScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

At the start of the `MainScreen` composable function body (after `val toggleGrid`), add:
```kotlin
    val labSettingsStore = remember { LabSettingsStore(JBus) }
```

Actually, since `LabSettingsStore` is a Hilt singleton, let's use the ViewModel approach. Add a thin ViewModel or just access it via Hilt. Since the `MainScreen` doesn't use a ViewModel, we'll use a simpler approach — get it from the `LabSettingsViewModel`:

Inside `MainScreen`, add after the `val uiPrefs` block:
```kotlin
    val labSettingsStore: LabSettingsStore = hiltViewModel<LabSettingsViewModel>().store
    val forumEnabled by labSettingsStore.forumEnabled.collectAsStateWithLifecycle()
    val forumMode by labSettingsStore.forumMode.collectAsStateWithLifecycle()
```

Add the import:
```kotlin
import me.jbusdriver.modern.ui.settings.LabSettingsViewModel
```

Replace the static `BottomNavItems` usage. Change the `NavigationBar` block:

```kotlin
            NavigationBar(modifier = Modifier.height(64.dp)) {
                BottomNavItems.forEach { item ->
                    if (item.category == BottomNavCategory.FORUM && !forumEnabled) return@forEach
                    NavigationBarItem(
                        selected = selectedCategory == item.category,
                        onClick = { selectedCategory = item.category },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
```

Also add auto-switch to Movie tab when forum is disabled while selected:
```kotlin
    // Auto-switch away from Forum tab when disabled
    LaunchedEffect(forumEnabled) {
        if (!forumEnabled && selectedCategory == BottomNavCategory.FORUM) {
            selectedCategory = BottomNavCategory.MOVIE
        }
    }
```

- [ ] **Step 2: Switch forum content based on mode**

Replace the `BottomNavCategory.FORUM` branch:

```kotlin
                    BottomNavCategory.FORUM -> {
                        // Search bar
                        SearchBar(onClick = { onSearchClick("") }, modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp))

                        when (forumMode) {
                            ForumMode.NATIVE -> ForumBoardsScreen(
                                onBoardClick = onForumBoardClick,
                                onThreadClick = onForumThreadClick
                            )
                            ForumMode.WEBVIEW -> ForumWebViewScreen(
                                onThreadClick = onForumThreadClick
                            )
                        }
                    }
```

- [ ] **Step 3: Remove forum ViewModel preloading when disabled**

Change the preload line:
```kotlin
    // Preload forum data — creating the ViewModel triggers init → loadBoards()
    if (forumEnabled) hiltViewModel<ForumBoardsViewModel>()
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: Build will fail because `ForumWebViewScreen` doesn't exist yet. Proceed to Task 6.

- [ ] **Step 5: Commit (together with Task 6)**

---

### Task 6: Create ForumWebViewScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumWebViewScreen.kt`

- [ ] **Step 1: Create the WebView composable**

```kotlin
// app/src/main/java/me/jbusdriver/modern/ui/forum/ForumWebViewScreen.kt
package me.jbusdriver.modern.ui.forum

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.jbusdriver.modern.core.site.SiteConfigStore
import me.jbusdriver.modern.data.SessionCookieStore
import me.jbusdriver.modern.JBus

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ForumWebViewScreen(
    onThreadClick: (Int) -> Unit = {},
    sessionCookieStore: SessionCookieStore = SessionCookieStore()
) {
    val baseUrl = SiteConfigStore.baseUrl
    val forumUrl = "${baseUrl}/forum/"
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false

                    // Sync cookies from SessionCookieStore
                    sessionCookieStore.restoreCookies(forumUrl)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()

                            // Forum thread links → native detail screen
                            val tidRegex = Regex("""[?&]tid=(\d+)""")
                            tidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()?.let { tid ->
                                if (url.contains("mod=viewthread")) {
                                    onThreadClick(tid)
                                    return true
                                }
                            }

                            // External URLs → system browser (handled by default)
                            if (url.startsWith(baseUrl) || url.contains("buscdn") || url.contains("busfan") || url.contains("cdnbus") || url.contains("dmmbus") || url.contains("dmmsee") || url.contains("javsee") || url.contains("seejav")) {
                                return false // Load in WebView
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            progress = 0
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            isLoading = false
                            progress = 100

                            // Inject CSS
                            val css = context.assets.open("forum_mobile.css")
                                .bufferedReader().use { it.readText() }
                            view.evaluateJavascript(
                                "(function(){var s=document.createElement('style');s.textContent='$css';document.head.appendChild(s);})();",
                                null
                            )

                            // Inject JS
                            val js = context.assets.open("forum_mobile.js")
                                .bufferedReader().use { it.readText() }
                            view.evaluateJavascript(js, null)

                            // Save cookies back
                            url?.let { sessionCookieStore.saveCookies(it) }
                        }
                    }

                    loadUrl(forumUrl)
                }
            },
            update = { webView ->
                // No-op: WebView manages its own navigation
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: Build will fail because assets `forum_mobile.css` and `forum_mobile.js` don't exist yet. Proceed to Task 7.

- [ ] **Step 3: Commit (together with Tasks 5+7)**

---

### Task 7: Create Injected CSS and JS Assets

**Files:**
- Create: `app/src/main/assets/forum_mobile.css`
- Create: `app/src/main/assets/forum_mobile.js`

- [ ] **Step 1: Create forum_mobile.css**

```css
/* app/src/main/assets/forum_mobile.css */

/* === Viewport & Base === */
html, body {
    max-width: 100% !important;
    overflow-x: hidden !important;
    -webkit-text-size-adjust: 100%;
}

/* === Dark Theme Override === */
body {
    background-color: #1a1a2e !important;
    color: #e0e0e0 !important;
}

a { color: #c0b0e0 !important; }
a:visited { color: #9a8abf !important; }

/* === Remove Ads === */
.bcpic2, .a_pt, .a_pb { display: none !important; }

/* === Remove Hidden Elements === */
div.wp.cl { display: none !important; }

/* === Full-Width Layout === */
#wp, .mn, #ct, div.wp {
    width: 100% !important;
    min-width: 0 !important;
    padding: 0 !important;
    box-sizing: border-box !important;
}

/* === Top Bar Cleanup === */
#toptb {
    min-width: unset !important;
    width: 100% !important;
    padding: 0 !important;
}
#toptb a.jav-logo { display: none !important; }
#toptb div.wp { display: none !important; }

/* Login area */
#toptb .login-wrap.y { float: left !important; }
#toptb .login-wrap .member-name { display: none !important; }

/* === Footer === */
.jav-footer {
    width: 100% !important;
    min-width: 0 !important;
    padding: 0 !important;
}

/* === Breadcrumb === */
div.z {
    width: 100% !important;
    padding: 8px !important;
}

/* === Images === */
img {
    max-width: 100% !important;
    height: auto !important;
}

.t_f img {
    width: 100% !important;
    height: auto !important;
}

/* === Forum Index Specifics === */
.biaoqicn_show, .biaoqicn_show * {
    max-width: 100% !important;
}

.sideMenu .item {
    width: 100% !important;
}

/* Table cleanup for board lists */
table.fl_tb {
    width: 100% !important;
}
table.fl_tb td {
    display: block !important;
    width: 100% !important;
    padding: 4px 0 !important;
}

/* === Thread List === */
#threadlist {
    width: 100% !important;
}

#threadlisttableid {
    width: 100% !important;
}

#threadlisttableid tbody {
    width: 100% !important;
    display: block !important;
    padding: 8px 0 !important;
    border-bottom: 1px solid #2a2a4a !important;
}

#threadlisttableid tbody th {
    width: 100% !important;
    display: block !important;
}

.post_inforight {
    display: flex !important;
    flex-direction: column !important;
    width: 100% !important;
}

/* === Sidebar (Hot topics, Featured) === */
.sd, .sd_allbox {
    width: 100% !important;
    min-width: 0 !important;
    float: none !important;
}

.main-right-box.cl {
    width: 100% !important;
    min-width: 0 !important;
    padding: 0 !important;
}

.main-right-kuaixu.cl li,
.main-right-zuixin li {
    width: 100% !important;
}

/* === Post Detail === */
.t_f {
    font-size: 15px !important;
    line-height: 1.6 !important;
    color: #e0e0e0 !important;
    padding: 8px !important;
}

/* Quote blocks */
.blockquote, .quote {
    background-color: #252540 !important;
    border-left: 3px solid #7c6aae !important;
    color: #ccc !important;
    padding: 8px 12px !important;
    margin: 8px 0 !important;
    border-radius: 4px !important;
}

/* Author info card */
.biaoqi_pls {
    width: 100% !important;
}

/* === Touch Optimization === */
a, button, input, .item, li {
    min-height: 44px !important;
}

/* === Code blocks === */
pre, code {
    background-color: #252540 !important;
    color: #c0b0e0 !important;
    border-radius: 4px !important;
    padding: 8px !important;
    overflow-x: auto !important;
}

/* === Scrollbar === */
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-track { background: #1a1a2e; }
::-webkit-scrollbar-thumb { background: #333; border-radius: 2px; }
```

- [ ] **Step 2: Create forum_mobile.js**

```javascript
// app/src/main/assets/forum_mobile.js
(function() {
    'use strict';

    // 1. Set viewport meta for mobile
    var viewport = document.querySelector('meta[name="viewport"]');
    if (!viewport) {
        viewport = document.createElement('meta');
        viewport.name = 'viewport';
        document.head.appendChild(viewport);
    }
    viewport.content = 'width=device-width,initial-scale=1.0,maximum-scale=1.0,minimum-scale=1.0,user-scalable=no';

    // 2. Remove ads
    document.querySelectorAll('.bcpic2, .a_pt, .a_pb').forEach(function(el) { el.remove(); });

    // 3. Remove hidden container
    var hiddenDiv = document.querySelector('div.wp.cl');
    if (hiddenDiv) hiddenDiv.remove();

    // 4. Clean top bar
    var nav = document.getElementById('toptb');
    if (nav) {
        // Remove logo
        var logo = nav.querySelector('a.jav-logo');
        if (logo) logo.remove();
        // Remove search/nav wrapper
        var wp = nav.querySelector('div.wp');
        if (wp) wp.remove();
        // Simplify login area
        var member = nav.querySelector('div.login-wrap.y');
        if (member) {
            var memberName = member.querySelector('span.member-name');
            if (memberName) memberName.remove();
            var angle = member.querySelector('span.angle');
            if (angle) angle.remove();
        }
    }

    // 5. Flatten login menu (if present)
    var member = nav ? nav.querySelector('div.login-wrap.y') : null;
    if (member) {
        var menuBody = member.querySelector('div.menu-body');
        if (menuBody) {
            var menu = document.createElement('ul');
            menu.style.cssText = 'display:flex;justify-content:left;align-items:center;list-style:none;padding:0;margin:0;';
            menuBody.querySelectorAll('div.item a').forEach(function(a) {
                var li = document.createElement('li');
                li.style.cssText = 'display:inline-block;margin:0 8px;';
                a.style.cssText = 'font-size:14px;padding:10px 0;text-align:center;color:#c0b0e0;';
                li.appendChild(a);
                menu.appendChild(li);
            });
            nav.appendChild(menu);
            // Remove first item (quick nav)
            if (menu.firstElementChild) menu.firstElementChild.remove();
            // Remove dropdown button and body
            member.querySelector('span.angle')?.remove();
            menuBody.remove();
        }
    }

    // 6. Adjust back-to-top button
    var backBtn = document.getElementsByClassName('biaoqi-fix-area');
    if (backBtn[0]) {
        backBtn[0].style.cssText = 'left:0;margin-left:80%;';
        if (backBtn[0].firstElementChild) {
            backBtn[0].firstElementChild.style.bottom = '10%';
        }
    }

    // 7. Enlarge touch targets for thread list items
    document.querySelectorAll('#threadlisttableid tbody').forEach(function(tbody) {
        tbody.querySelectorAll('th a, .post_infolist a').forEach(function(a) {
            a.style.padding = '8px 4px';
            a.style.display = 'inline-block';
        });
    });

    // 8. Post images: skip smileys
    document.querySelectorAll('.t_f img').forEach(function(img) {
        if (!img.src.includes('/static/image/smiley/')) {
            img.style.width = '100%';
            img.style.height = 'auto';
        }
    });

})();
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit Tasks 5+6+7 together**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt \
        app/src/main/java/me/jbusdriver/modern/ui/forum/ForumWebViewScreen.kt \
        app/src/main/assets/forum_mobile.css \
        app/src/main/assets/forum_mobile.js
git commit -m "feat: dynamic forum tab with WebView mode and mobile injection"
```

---

### Task 8: Integration Test & Final Build

**Files:** None new — verification only

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**

Install the debug APK and verify:
1. Bottom nav shows 3 tabs (影片/演員/收藏) — no Forum tab
2. Search for "设置" — lab entry card appears at top
3. Tap lab entry — LabSettingsScreen opens
4. Toggle "启用" ON — back to main screen, Forum tab now appears (4 tabs)
5. Forum tab shows native `ForumBoardsScreen` (existing behavior)
6. Go back to Lab Settings, switch to "WebView" mode
7. Forum tab now shows WebView with injected dark theme
8. Disable forum — auto-switches to Movie tab, Forum tab disappears

- [ ] **Step 3: Commit any fixes found during testing**

```bash
git add -A
git commit -m "fix: address issues found during integration testing"
```
