# Settings Rewrite Implementation Plan

> **Update (2026-06-21):** The backup/restore subsystem (Tasks 3, 6–13 and the backup portions of 4/14/15/17) was **removed** after review — it added too much complexity (WebDAV client, auto-backup coordinator, conflict engine, 9 settings) for its value. Only **Appearance + Network** settings remain. The collections import/export codec (`CollectionBackupCodec`) and the file-picker UI in `CollectCategoryScreen` are pre-existing and were kept; only the backup-specific overlay (`RestoreStrategy`, `CollectionChangeEvent`) was reverted from them.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reimplement the unified Settings screen (appearance / network / backup-restore) from the `dev/setting` DEMO on `main`'s clean baseline, with a clean layered architecture: thin `AppSettingsStore`, theme decoupled via `ThemeRepository`, backup behind a `BackupStorage` interface with event-driven auto-backup, and coarse-grained restore conflict prompting.

**Architecture:** Single `AppSettingsStore` (thin DataStore, pure key-value, implements narrow reader interfaces following the existing `LabSettingsStore` pattern) delegates mirror scanning to the already-existing `MirrorScanner`. `BackupManager` orchestrates serialization (via pure `BackupSerializer`) + delegates to `BackupStorage` (Local SAF / WebDAV). `CollectRepository` emits `CollectionChangeEvent` on a `SharedFlow`; `BackupCoordinator` consumes it → collect↔backup decoupled. `JBusTheme` reads `ThemeViewModel` → `ThemeRepository`, no longer depends on `SettingsViewModel`.

**Tech Stack:** Kotlin, Jetpack Compose + Material3, Hilt, Kotlin Coroutines, DataStore Preferences, OkHttp 5.4 (hand-rolled WebDAV + MockWebServer tests), Gson 2.14, Room 2.8, Navigation 3. JVM-only unit tests (JUnit4 + coroutines-test + MockWebServer).

**Reference spec:** `docs/superpowers/specs/2026-06-20-settings-rewrite-design.md`

---

## Critical context for the implementer

- **Build on `main`.** The `dev/setting` branch is a functional reference ONLY — do not cherry-pick its commits. Read its files with `git show dev/setting:<path>` when porting UI.
- **`MirrorScanner` already exists** at `app/src/main/java/me/jbusdriver/modern/data/mirror/MirrorScanner.kt` with `suspend fun scanAndVerify(state: MutableStateFlow<ScanState>, seedUrl: String, cachedUrls: List<String>): Set<String>` and `suspend fun verifyOnly(state: MutableStateFlow<ScanState>, cachedUrls: List<String>)`. Reuse it; do NOT inline scan logic.
- **`ScanState.error` is `Int?`** (an `R.string` id), NOT `String`. Mirror this for i18n consistency.
- **`LabSettingsStore`** (to be deleted) currently implements `ForumSettingsReader`, `SitePreferenceSource`, `LabSettingsStoreContract`, bound in `DataModule.kt`. `ForumThreadDetailViewModel` depends on `ForumSettingsReader`; `DefaultSiteConfig` depends on `SitePreferenceSource`. `AppSettingsStore` must keep implementing these two so those consumers are untouched.
- **Tests are JVM-only** (no Robolectric, no Hilt test runner). `app/src/test/.../TestFakes.kt` has `StubCollectRepository`. DataStore-backed defaults are verified manually (verification checklist), not by unit test. New code must be testable via interfaces + MockWebServer + pure functions.
- **`History` entity** = `(id, dbType, createTime, jsonStr, isAll)`. `jsonStr` is already serialized JSON. `HistoryDao.queryByLimit` returns a `Flow`; we add a suspend `listAll()` for backup.
- **`CollectionBackupCodec`** produces `{version:1, exportTime, movies[], actresses[]}` (no categories — out of scope). `importCollectionsFromJson(json): Pair<Int,Int>` = (imported, skipped), skip-existing. We extend it with a strategy.
- **No migration.** New DataStore file `app_settings`, clean key names. Existing `lab_settings` values are abandoned (forum/network prefs reset to defaults — acceptable per decision).
- **WebDAV client is simplest-first.** No 401 Authenticator challenge-response, no URL encoding hardening — until real issues surface. PROPFIND parsing uses `xpp3` `XmlPullParser` (added as a dependency) so it is unit-testable in pure JVM.
- **Commit after every task.** Run `git status` before committing to exclude stray files (e.g. `.superpowers/`, `.mimocode/`).

---

## File Structure

### New files — data layer
| File | Responsibility |
|------|----------------|
| `data/settings/ThemeMode.kt` | `enum class ThemeMode` |
| `data/backup/BackupTarget.kt` | `enum class BackupTarget`, `enum class RestoreStrategy` |
| `data/settings/AppSettingsStore.kt` | Thin DataStore; implements `ForumSettingsReader`, `SitePreferenceSource`, `ThemeSettingsReader`, `AppSettingsContract`; delegates scan to `MirrorScanner`. Defines the two new interfaces. |
| `data/settings/ThemeRepository.kt` | `interface ThemeRepository` + `DefaultThemeRepository` (reads `ThemeSettingsReader`) |
| `data/backup/BackupSerializer.kt` | Pure: `buildV2(...)`, `parse(json): BackupPayload`, conflict payload types |
| `data/backup/BackupStorage.kt` | `interface BackupStorage` + `BackupFileInfo` + `BackupStorageFactory` |
| `data/backup/LocalBackupStorage.kt` | SAF tree-based impl (createDocument/list/query/delete) |
| `data/backup/WebDavBackupStorage.kt` | Delegates to `WebDavClient` |
| `data/backup/BackupManager.kt` | Orchestration: backup, restore, conflict precheck, keep-latest |
| `data/backup/BackupCoordinator.kt` | `@Singleton`; subscribes to `CollectRepository.collectionChanges` → `autoBackupIfNeeded` |
| `data/backup/webdav/WebDavClient.kt` | Simplest OkHttp WebDAV (PUT/GET/PROPFIND/DELETE/MKCOL) + Basic Auth |
| `data/backup/webdav/WebDavClientFactory.kt` | Hilt factory reading WebDAV settings |

### New files — UI layer
| File | Responsibility |
|------|----------------|
| `ui/settings/ThemeViewModel.kt` | Thin VM exposing `themeMode`/`dynamicColor` for `JBusTheme` |
| `ui/settings/SettingsViewModel.kt` | Single VM, grouped state, pure delegation |
| `ui/settings/SettingsScreen.kt` | 3 cards (Appearance / Network / Backup) + conflict dialog |
| `res/drawable/settings_24px.xml` | Settings icon |

### Modified files
| File | Change |
|------|--------|
| `data/di/DataModule.kt` | Rebind `ForumSettingsReader`/`SitePreferenceSource` to `AppSettingsStore`; add `ThemeSettingsReader`/`AppSettingsContract`/`ThemeRepository`/`BackupManager`/`BackupCoordinator`/`BackupStorageFactory`/`WebDavClientFactory` bindings |
| `data/db/dao/HistoryDao.kt` | Add `suspend fun listAll(): List<History>` |
| `data/repository/CollectRepository.kt` | Add `collectionChanges: SharedFlow<CollectionChangeEvent>`; add `importCollectionsFromJson(json, strategy)` |
| `data/repository/CollectionBackupCodec.kt` | Add `strategy` param to import |
| `ui/theme/Theme.kt` | Read `ThemeViewModel` |
| `ui/MainScreen.kt` | Tab visibility from `SettingsViewModel`; `onSettingsClick` |
| `ui/Navigation.kt` | `RouteSettings` entry; `onSettingsClick` wiring |
| `ui/NavigationKeys.kt` | Replace `RouteLabSettings` with `RouteSettings` |
| `ui/movielist/CollectCategoryScreen.kt` | "更多設置" menu item; `onSettingsClick` param (replaces `onGoHome`) |
| `JBusApplication.kt` | Inject + start `BackupCoordinator` |
| `app/build.gradle.kts` | Add `mockwebserver` (testImplementation), `xpp3` (implementation) |
| `gradle/libs.versions.toml` | Add `mockwebserver`, `xpp3` entries |

### Deleted files (final cleanup task)
- `data/settings/LabSettingsStore.kt`
- `ui/settings/LabSettingsScreen.kt`
- `ui/settings/LabSettingsViewModel.kt`

---

## Task 1: Add dependencies (mockwebserver, xpp3)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add catalog entries**

In `gradle/libs.versions.toml`, under `[versions]` add:
```toml
mockwebserver = "5.4.0"
xpp3 = "1.1.4c"
```
Under `[libraries]` add:
```toml
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver3-junit4", version.ref = "mockwebserver" }
xpp3 = { group = "xpp3", name = "xpp3", version.ref = "xpp3" }
```
>Note: OkHttp on main is `5.4.0`. The MockWebServer artifact for OkHttp 5.x is `mockwebserver3-junit4` (new package `okhttp3.mockwebserver.MockWebServer` still works via `mockwebserver3`).

- [ ] **Step 2: Wire into app build**

In `app/build.gradle.kts`, in the `dependencies` block add:
```kotlin
implementation(libs.xpp3)
testImplementation(libs.mockwebserver)
```

- [ ] **Step 3: Verify build resolves deps**

Run: `./gradlew :app:dependencies --configuration debugUnitTestRuntimeClasspath | grep -E "mockwebserver|xpp3"`
Expected: both artifacts appear.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add mockwebserver (test) and xpp3 for settings rewrite"
```

---

## Task 2: ThemeMode enum (TDD)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/settings/ThemeMode.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/settings/ThemeModeTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package me.jbusdriver.modern.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun parsesKnownValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPreferenceValue("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPreferenceValue("dark"))
    }
    @Test fun unknownOrNullFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreferenceValue("nonsense"))
    }
    @Test fun preferenceValueRoundTrips() {
        ThemeMode.entries.forEach {
            assertEquals(it, ThemeMode.fromPreferenceValue(it.preferenceValue))
        }
    }
}
```

- [ ] **Step 2: Run test, verify failure**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.settings.ThemeModeTest"`
Expected: FAIL (unresolved reference `ThemeMode`).

- [ ] **Step 3: Implement**

```kotlin
package me.jbusdriver.modern.data.settings

enum class ThemeMode(val preferenceValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromPreferenceValue(value: String?): ThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}
```

- [ ] **Step 4: Run test, verify pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.settings.ThemeModeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/settings/ThemeMode.kt app/src/test/java/me/jbusdriver/modern/data/settings/ThemeModeTest.kt
git commit -m "feat(settings): add ThemeMode enum"
```

---

## Task 3: BackupTarget + RestoreStrategy enums (TDD)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupTarget.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/backup/BackupTargetTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package me.jbusdriver.modern.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTargetTest {
    @Test fun parsesAndIncludes() {
        assertEquals(BackupTarget.LOCAL, BackupTarget.fromPreferenceValue("local"))
        assertEquals(BackupTarget.WEBDAV, BackupTarget.fromPreferenceValue("webdav"))
        assertEquals(BackupTarget.BOTH, BackupTarget.fromPreferenceValue("both"))
        assertEquals(BackupTarget.LOCAL, BackupTarget.fromPreferenceValue(null))

        assertTrue(BackupTarget.BOTH.includesLocal)
        assertTrue(BackupTarget.BOTH.includesWebDav)
        assertTrue(BackupTarget.LOCAL.includesLocal)
        assertFalse(BackupTarget.LOCAL.includesWebDav)
        assertTrue(BackupTarget.WEBDAV.includesWebDav)
        assertFalse(BackupTarget.WEBDAV.includesLocal)
    }
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupTargetTest"`
Expected: FAIL (unresolved).

- [ ] **Step 3: Implement**

```kotlin
package me.jbusdriver.modern.data.backup

enum class BackupTarget(val preferenceValue: String) {
    LOCAL("local"),
    WEBDAV("webdav"),
    BOTH("both");

    val includesWebDav get() = this == WEBDAV || this == BOTH
    val includesLocal get() = this == LOCAL || this == BOTH

    companion object {
        fun fromPreferenceValue(value: String?): BackupTarget =
            entries.firstOrNull { it.preferenceValue == value } ?: LOCAL
    }
}

/** Restore conflict strategy. MERGE skips existing items; OVERWRITE replaces them. */
enum class RestoreStrategy { MERGE, OVERWRITE }
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupTargetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/BackupTarget.kt app/src/test/java/me/jbusdriver/modern/data/backup/BackupTargetTest.kt
git commit -m "feat(backup): add BackupTarget and RestoreStrategy enums"
```

---

## Task 4: AppSettingsStore + new interfaces

This is the thin store replacing `LabSettingsStore`. It implements the two existing reader interfaces (`ForumSettingsReader`, `SitePreferenceSource`) plus two new ones (`ThemeSettingsReader`, `AppSettingsContract`). It is created ALONGSIDE `LabSettingsStore` for now; bindings are swapped in the final cleanup task. It delegates scanning to the existing `MirrorScanner`.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt`

First, read the existing interfaces to match exactly:
- [ ] **Step 1: Inspect existing contracts**

Run:
```bash
sed -n '1,60p' app/src/main/java/me/jbusdriver/modern/core/site/SiteConfig.kt   # SitePreferenceSource
sed -n '1,40p' app/src/main/java/me/jbusdriver/modern/data/settings/LabSettingsStore.kt  # ForumSettingsReader
sed -n '1,80p' app/src/main/java/me/jbusdriver/modern/data/mirror/MirrorScanner.kt       # scanAndVerify / verifyOnly
```
Confirm: `SitePreferenceSource.currentSelectedBaseUrl(): String`; `ForumSettingsReader.autoLoadGifs: StateFlow<Boolean>` + `currentForumFloorOrder(): ForumFloorOrder`; `MirrorScanner.scanAndVerify(state, seedUrl, cachedUrls): Set<String>` + `verifyOnly(state, cachedUrls)`.

- [ ] **Step 2: Create the store**

```kotlin
package me.jbusdriver.modern.data.settings

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.jbusdriver.modern.core.site.SitePreferenceSource
import me.jbusdriver.modern.data.backup.BackupTarget
import me.jbusdriver.modern.data.mirror.MirrorScanner
import me.jbusdriver.modern.data.mirror.ScanState
import javax.inject.Inject
import javax.inject.Singleton

/** Narrow read interface for theme consumers (ThemeViewModel/JBusTheme). */
interface ThemeSettingsReader {
    val themeMode: StateFlow<ThemeMode>
    val dynamicColor: StateFlow<Boolean>
}

/** Full settings read/write contract for SettingsViewModel + BackupManager. */
interface AppSettingsContract : ThemeSettingsReader, ForumSettingsReader, SitePreferenceSource {
    // Appearance
    val showMovieTab: StateFlow<Boolean>
    val showActressTab: StateFlow<Boolean>
    val showForumTab: StateFlow<Boolean>
    val forumFloorOrder: StateFlow<ForumFloorOrder>
    // Network
    val selectedBaseUrl: StateFlow<String>
    val cachedMirrorUrls: StateFlow<List<String>>
    // Backup
    val autoBackupEnabled: StateFlow<Boolean>
    val backupTarget: StateFlow<BackupTarget>
    val keepLatestOnly: StateFlow<Boolean>
    val localBackupUri: StateFlow<String>
    val webdavServerUrl: StateFlow<String>
    val webdavUsername: StateFlow<String>
    val webdavPassword: StateFlow<String>
    val webdavFolder: StateFlow<String>
    val webdavDeviceName: StateFlow<String>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setShowMovieTab(visible: Boolean)
    suspend fun setShowActressTab(visible: Boolean)
    suspend fun setShowForumTab(enabled: Boolean)
    suspend fun setAutoLoadGifs(enabled: Boolean)
    suspend fun setForumFloorOrder(order: ForumFloorOrder)
    suspend fun selectUrl(url: String)
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setBackupTarget(target: BackupTarget)
    suspend fun setKeepLatestOnly(enabled: Boolean)
    suspend fun setLocalBackupUri(uri: String)
    suspend fun setWebdavServerUrl(url: String)
    suspend fun setWebdavUsername(username: String)
    suspend fun setWebdavPassword(password: String)
    suspend fun setWebdavFolder(folder: String)
    suspend fun setWebdavDeviceName(name: String)

    /** A snapshot of non-sensitive settings for inclusion in a backup payload. */
    suspend fun settingsSnapshot(): Map<String, String>
    /** Apply restored settings (keys = preference key names, values = strings). */
    suspend fun applySettings(values: Map<String, String>)

    suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>, seedUrl: String)
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>)
}

private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mirrorScanner: MirrorScanner
) : AppSettingsContract {

    private val dataStore = context.appSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun <T> flowOf(key: androidx.datastore.preferences.core.Preferences.Key<T>, default: T): StateFlow<T> =
        dataStore.data.map { it[key] ?: default }.stateIn(scope, SharingStarted.Eagerly, default)

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    // region Appearance
    override val themeMode get() = mapped(KEY_THEME_MODE, ThemeMode.SYSTEM) { ThemeMode.fromPreferenceValue(it) }
    override val dynamicColor get() = flowOf(KEY_DYNAMIC_COLOR, true)
    override val showMovieTab get() = flowOf(KEY_SHOW_MOVIE_TAB, true)
    override val showActressTab get() = flowOf(KEY_SHOW_ACTRESS_TAB, true)
    override val showForumTab get() = flowOf(KEY_SHOW_FORUM_TAB, false)

    override suspend fun setThemeMode(mode: ThemeMode) = put(KEY_THEME_MODE, mode.preferenceValue)
    override suspend fun setDynamicColor(enabled: Boolean) = put(KEY_DYNAMIC_COLOR, enabled)
    override suspend fun setShowMovieTab(visible: Boolean) = put(KEY_SHOW_MOVIE_TAB, visible)
    override suspend fun setShowActressTab(visible: Boolean) = put(KEY_SHOW_ACTRESS_TAB, visible)
    override suspend fun setShowForumTab(enabled: Boolean) = put(KEY_SHOW_FORUM_TAB, enabled)
    // endregion

    // region Forum
    override val autoLoadGifs get() = flowOf(KEY_AUTO_LOAD_GIFS, false)
    override val forumFloorOrder get() = mapped(KEY_FORUM_FLOOR_ORDER, ForumFloorOrder.REGULAR) { ForumFloorOrder.fromPreferenceValue(it) }
    override suspend fun currentForumFloorOrder(): ForumFloorOrder =
        ForumFloorOrder.fromPreferenceValue(dataStore.data.first()[KEY_FORUM_FLOOR_ORDER])
    override suspend fun setAutoLoadGifs(enabled: Boolean) = put(KEY_AUTO_LOAD_GIFS, enabled)
    override suspend fun setForumFloorOrder(order: ForumFloorOrder) = put(KEY_FORUM_FLOOR_ORDER, order.preferenceValue)
    // endregion

    // region Network
    override val selectedBaseUrl get() = flowOf(KEY_SELECTED_BASE_URL, DEFAULT_BASE_URL)
    override val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS }
        .stateIn(scope, SharingStarted.Eagerly, PRESET_MIRROR_URLS)
    override suspend fun currentSelectedBaseUrl(): String =
        dataStore.data.first()[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL
    override suspend fun selectUrl(url: String) = put(KEY_SELECTED_BASE_URL, url.trimEnd('/'))
    override suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>, seedUrl: String) {
        val cached = cachedMirrorUrls.first()
        val discovered = mirrorScanner.scanAndVerify(state, seedUrl, cached)
        dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = discovered }
    }
    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        mirrorScanner.verifyOnly(state, cachedMirrorUrls.first())
    }
    // endregion

    // region Backup
    override val autoBackupEnabled get() = flowOf(KEY_AUTO_BACKUP_ENABLED, false)
    override val backupTarget get() = mapped(KEY_BACKUP_TARGET, BackupTarget.LOCAL) { BackupTarget.fromPreferenceValue(it) }
    override val keepLatestOnly get() = flowOf(KEY_KEEP_LATEST_ONLY, false)
    override val localBackupUri get() = flowOf(KEY_LOCAL_BACKUP_URI, "")
    override val webdavServerUrl get() = flowOf(KEY_WEBDAV_SERVER_URL, "")
    override val webdavUsername get() = flowOf(KEY_WEBDAV_USERNAME, "")
    override val webdavPassword get() = flowOf(KEY_WEBDAV_PASSWORD, "")
    override val webdavFolder get() = flowOf(KEY_WEBDAV_FOLDER, DEFAULT_WEBDAV_FOLDER)
    override val webdavDeviceName get() = flowOf(KEY_WEBDAV_DEVICE_NAME, Build.MODEL)

    override suspend fun setAutoBackupEnabled(enabled: Boolean) = put(KEY_AUTO_BACKUP_ENABLED, enabled)
    override suspend fun setBackupTarget(target: BackupTarget) = put(KEY_BACKUP_TARGET, target.preferenceValue)
    override suspend fun setKeepLatestOnly(enabled: Boolean) = put(KEY_KEEP_LATEST_ONLY, enabled)
    override suspend fun setLocalBackupUri(uri: String) = put(KEY_LOCAL_BACKUP_URI, uri)
    override suspend fun setWebdavServerUrl(url: String) = put(KEY_WEBDAV_SERVER_URL, url)
    override suspend fun setWebdavUsername(username: String) = put(KEY_WEBDAV_USERNAME, username)
    override suspend fun setWebdavPassword(password: String) = put(KEY_WEBDAV_PASSWORD, password)
    override suspend fun setWebdavFolder(folder: String) = put(KEY_WEBDAV_FOLDER, folder)
    override suspend fun setWebdavDeviceName(name: String) = put(KEY_WEBDAV_DEVICE_NAME, name)

    override suspend fun settingsSnapshot(): Map<String, String> = mapOf(
        "theme_mode" to themeMode.first().preferenceValue,
        "dynamic_color" to dynamicColor.first().toString(),
        "show_movie_tab" to showMovieTab.first().toString(),
        "show_actress_tab" to showActressTab.first().toString(),
        "show_forum_tab" to showForumTab.first().toString(),
        "auto_load_gifs" to autoLoadGifs.first().toString(),
        "forum_floor_order" to forumFloorOrder.first().preferenceValue,
        "selected_base_url" to selectedBaseUrl.first()
        // NOTE: WebDAV credentials intentionally excluded.
    )

    override suspend fun applySettings(values: Map<String, String>) {
        values["theme_mode"]?.let { setThemeMode(ThemeMode.fromPreferenceValue(it)) }
        values["dynamic_color"]?.toBooleanStrictOrNull()?.let { setDynamicColor(it) }
        values["show_movie_tab"]?.toBooleanStrictOrNull()?.let { setShowMovieTab(it) }
        values["show_actress_tab"]?.toBooleanStrictOrNull()?.let { setShowActressTab(it) }
        values["show_forum_tab"]?.toBooleanStrictOrNull()?.let { setShowForumTab(it) }
        values["auto_load_gifs"]?.toBooleanStrictOrNull()?.let { setAutoLoadGifs(it) }
        values["forum_floor_order"]?.let { setForumFloorOrder(ForumFloorOrder.fromPreferenceValue(it)) }
        values["selected_base_url"]?.let { selectUrl(it) }
    }
    // endregion

    private fun <T> mapped(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        default: T,
        decode: (String?) -> T
    ): StateFlow<T> = dataStore.data.map { decode(it[key]) }.stateIn(scope, SharingStarted.Eagerly, default)

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_SHOW_MOVIE_TAB = booleanPreferencesKey("show_movie_tab")
        private val KEY_SHOW_ACTRESS_TAB = booleanPreferencesKey("show_actress_tab")
        private val KEY_SHOW_FORUM_TAB = booleanPreferencesKey("show_forum_tab")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_FORUM_FLOOR_ORDER = stringPreferencesKey("forum_floor_order")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_BACKUP_TARGET = stringPreferencesKey("backup_target")
        private val KEY_KEEP_LATEST_ONLY = booleanPreferencesKey("keep_latest_only")
        private val KEY_LOCAL_BACKUP_URI = stringPreferencesKey("local_backup_uri")
        private val KEY_WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        private val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_WEBDAV_FOLDER = stringPreferencesKey("webdav_folder")
        private val KEY_WEBDAV_DEVICE_NAME = stringPreferencesKey("webdav_device_name")

        const val DEFAULT_BASE_URL = "https://www.javbus.com"
        const val DEFAULT_WEBDAV_FOLDER = "/JBusBackup"
        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
```

- [ ] **Step 3: Bind new interfaces in DataModule (additive — no conflict yet)**

In `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`, add (keep existing `LabSettingsStore` bindings untouched for now):
```kotlin
@Binds @Singleton abstract fun bindThemeSettingsReader(impl: AppSettingsStore): ThemeSettingsReader
@Binds @Singleton abstract fun bindAppSettingsContract(impl: AppSettingsStore): AppSettingsContract
```
Add imports for `me.jbusdriver.modern.data.settings.AppSettingsStore`, `AppSettingsContract`, `ThemeSettingsReader`.

> Note: `AppSettingsStore` also declares it implements `ForumSettingsReader` and `SitePreferenceSource`, but those are still bound to `LabSettingsStore` — do NOT add those bindings yet (Hilt would error on duplicate bindings). They are swapped in Task 16.

- [ ] **Step 4: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (Existing LabSettingsStore + UI still compile; AppSettingsStore is added but not yet consumed.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(settings): add thin AppSettingsStore with reader interfaces"
```

---

## Task 5: ThemeRepository + ThemeViewModel + Theme.kt rewire

Decouples theme from `SettingsViewModel`. `JBusTheme` reads `ThemeViewModel` → `ThemeRepository` → `ThemeSettingsReader` (bound to `AppSettingsStore` in Task 4).

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/settings/ThemeRepository.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/ThemeViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create ThemeRepository**

```kotlin
package me.jbusdriver.modern.data.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

interface ThemeRepository {
    val themeMode: StateFlow<ThemeMode>
    val dynamicColor: StateFlow<Boolean>
}

class DefaultThemeRepository @Inject constructor(
    private val reader: ThemeSettingsReader
) : ThemeRepository {
    override val themeMode: StateFlow<ThemeMode> get() = reader.themeMode
    override val dynamicColor: StateFlow<Boolean> get() = reader.dynamicColor
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    repository: ThemeRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
    val dynamicColor: StateFlow<Boolean> = repository.dynamicColor
}
```

- [ ] **Step 2: Bind ThemeRepository**

In `DataModule.kt` add:
```kotlin
@Binds @Singleton abstract fun bindThemeRepository(impl: DefaultThemeRepository): ThemeRepository
```

- [ ] **Step 3: Rewire Theme.kt**

Read current `Theme.kt`:
```bash
sed -n '1,140p' app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt
```
Replace the `JBusTheme` signature and body so it reads from `ThemeViewModel` and syncs window chrome. The new `JBusTheme`:

```kotlin
@Composable
fun JBusTheme(content: @Composable () -> Unit) {
    val theme = hiltViewModel<ThemeViewModel>()
    val themeMode by theme.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by theme.dynamicColor.collectAsStateWithLifecycle()

    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val bg = colorScheme.background
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.rgb(bg.red, bg.green, bg.blue)
                )
            )
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```
Add imports: `android.app.Activity`, `androidx.compose.runtime.SideEffect`, `androidx.compose.runtime.getValue`, `androidx.compose.ui.platform.LocalView`, `androidx.core.view.WindowCompat`, `androidx.hilt.navigation.compose.hiltViewModel`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `me.jbusdriver.modern.data.settings.ThemeMode`, `me.jbusdriver.modern.ui.settings.ThemeViewModel`. Remove the now-unused `darkTheme`/`dynamicColor` parameters. Keep the existing `if (BuildConfig.DEBUG) colorScheme.dumpToLog()` if present.

- [ ] **Step 4: Verify compile + run existing tests**

Run: `./gradlew assembleDebug test`
Expected: BUILD SUCCESSFUL, all existing tests pass. (`ModernMainActivity` calls `JBusTheme { ... }` with no args — still valid.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/settings/ThemeRepository.kt app/src/main/java/me/jbusdriver/modern/ui/settings/ThemeViewModel.kt app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(theme): decouple JBusTheme via ThemeRepository/ThemeViewModel"
```

---

## Task 6: BackupSerializer (pure, TDD)

Pure functions for the v2 envelope. v1 = a raw collections JSON (array or the codec's v1 object). v2 wraps `{version:2, exportTime, deviceName, collections, settings, history}`.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupSerializer.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/backup/BackupSerializerTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package me.jbusdriver.modern.data.backup

import me.jbusdriver.modern.data.db.entity.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {
    private data class HDTO(val dbType: Int, val jsonStr: String, val isAll: Int)
    private fun hd(d: Int, j: String, a: Int = 0) = HistoryDto(d, j, a)

    @Test fun buildV2HasEnvelopeAndFields() {
        val json = BackupSerializer.buildV2(
            collectionsJson = """{"version":1,"movies":[]}""",
            settings = mapOf("theme_mode" to "dark"),
            history = listOf(hd(1, "{\"id\":1}")),
            deviceName = "Pixel"
        )
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        assertEquals(2, root.get("version").asInt)
        assertEquals("Pixel", root.get("deviceName").asString)
        assertTrue(root.has("exportTime"))
        assertTrue(root.getAsJsonObject("collections").has("movies"))
        assertEquals("dark", root.getAsJsonObject("settings").get("theme_mode").asString)
        assertEquals(1, root.getAsJsonArray("history").size())
    }

    @Test fun parseV2ExtractsSections() {
        val src = BackupSerializer.buildV2(
            collectionsJson = """{"version":1,"movies":[]}""",
            settings = mapOf("show_forum_tab" to "true"),
            history = listOf(hd(2, "{}")),
            deviceName = "D"
        )
        val payload = BackupSerializer.parse(src)
        assertEquals(2, payload.version)
        assertTrue(payload.collectionsJson.contains("movies"))
        assertEquals("true", payload.settings["show_forum_tab"])
        assertEquals(1, payload.history.size)
    }

    @Test fun parseV1TreatsWholeJsonAsCollections() {
        val v1 = """{"version":1,"movies":[],"actresses":[]}"""
        val payload = BackupSerializer.parse(v1)
        assertNull(payload.version) // v1 has no top-level "version" we recognize as 2
        assertEquals(v1, payload.collectionsJson)
        assertTrue(payload.settings.isEmpty())
        assertTrue(payload.history.isEmpty())
    }

    @Test fun parseInvalidThrows() {
        try {
            BackupSerializer.parse("not json")
            org.junit.Assert.fail("expected exception")
        } catch (e: Exception) { /* expected */ }
    }
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupSerializerTest"`
Expected: FAIL (unresolved `BackupSerializer`, `HistoryDto`).

- [ ] **Step 3: Implement**

```kotlin
package me.jbusdriver.modern.data.backup

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A history entry in the backup payload (mirrors the non-auto History columns). */
data class HistoryDto(val dbType: Int, val jsonStr: String, val isAll: Int)

data class BackupPayload(
    val version: Int?,
    val collectionsJson: String,
    val settings: Map<String, String>,
    val history: List<HistoryDto>
)

object BackupSerializer {
    private val gson = Gson()
    private val isoFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

    fun buildV2(
        collectionsJson: String,
        settings: Map<String, String>,
        history: List<HistoryDto>,
        deviceName: String
    ): String {
        val root = JsonObject().apply {
            addProperty("version", 2)
            addProperty("exportTime", isoFormat.format(Date()))
            addProperty("deviceName", deviceName)
            add("collections", JsonParser.parseString(collectionsJson))
            add("settings", gson.toJsonTree(settings))
            add("history", gson.toJsonTree(history))
        }
        return gson.toJson(root)
    }

    fun parse(json: String): BackupPayload {
        val root = JsonParser.parseString(json).asJsonObject
        val version = if (root.has("version")) root.get("version")?.asInt else null
        return if (version == 2) {
            val collections = root.getAsJsonObject("collections").toString()
            val settings = gson.fromJson(
                root.getAsJsonObject("settings"),
                object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            ) ?: emptyMap()
            val history = gson.fromJson(
                root.getAsJsonArray("history"),
                object : com.google.gson.reflect.TypeToken<List<HistoryDto>>() {}.type
            ) ?: emptyList()
            BackupPayload(2, collections, settings, history)
        } else {
            // v1 (or legacy array): the entire document is the collections payload.
            BackupPayload(null, json, emptyMap(), emptyList())
        }
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupSerializerTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/BackupSerializer.kt app/src/test/java/me/jbusdriver/modern/data/backup/BackupSerializerTest.kt
git commit -m "feat(backup): add pure BackupSerializer (v2 envelope, v1 fallback)"
```

---

## Task 7: WebDavClient (simplest-first, TDD with MockWebServer)

Hand-rolled WebDAV over OkHttp with HTTP Basic Auth. No Authenticator challenge-response, no URL encoding hardening (deferred). PROPFIND parsed with xpp3 `XmlPullParser`.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/webdav/WebDavClient.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/backup/webdav/WebDavClientTest.kt`

- [ ] **Step 1: Write failing test (MockWebServer)**

```kotlin
package me.jbusdriver.modern.data.backup.webdav

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebDavClient(
            OkHttpClient(),
            server.url("/").toString().trimEnd('/'),
            "user",
            "pass"
        )
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun testConnectionSendsBasicAuthAndSucceedsOn2xx() {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = client.testConnection()
        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertTrue(recorded.getHeader("Authorization")?.startsWith("Basic ") == true)
    }

    @Test fun testConnectionFailsOn401() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(client.testConnection().isFailure)
    }

    @Test fun uploadPutsBytes() {
        server.enqueue(MockResponse().setResponseCode(201))
        val r = client.upload("folder/file.json", "hello".toByteArray())
        assertTrue(r.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertTrue(recorded.path!!.contains("folder/file.json"))
    }

    @Test fun downloadReturnsBody() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("payload"))
        val bytes = client.download("folder/file.json").getOrThrow()
        assertEquals("payload", String(bytes))
    }

    @Test fun deleteSendsDelete() {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(client.delete("folder/file.json").isSuccess)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test fun ensureDirTreats405AsExisting() {
        server.enqueue(MockResponse().setResponseCode(405)) // dir already exists
        assertTrue(client.ensureDir("folder").isSuccess)
        assertEquals("MKCOL", server.takeRequest().method)
    }

    @Test fun listParsesPropfindMultistatus() {
        val body = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/folder/jbus_backup_a.json</d:href>
                <d:propstat><d:prop><d:getcontentlength>100</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/folder/jbus_backup_b.json</d:href>
                <d:propstat><d:prop><d:getcontentlength>200</d:getcontentlength></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(207).setBody(body))
        val files = client.list("folder").getOrThrow()
        assertEquals(2, files.size)
        assertTrue(files.any { it.name.endsWith("jbus_backup_a.json") })
    }
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.webdav.WebDavClientTest"`
Expected: FAIL (unresolved `WebDavClient`, `RemoteFileInfo`).

- [ ] **Step 3: Implement**

```kotlin
package me.jbusdriver.modern.data.backup.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import kotlin.Result

data class RemoteFileInfo(val name: String, val size: Long)

class WebDavClient(
    private val okHttpClient: OkHttpClient,
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val authHeader = okhttp3.Credentials.basic(username, password)

    private fun url(remotePath: String): String =
        serverUrl.trimEnd('/') + "/" + remotePath.trimStart('/')

    private fun exec(request: Request): Result<Response> = runCatching {
        okHttpClient.newCall(request).execute()
    }

    private fun Response.ensureSuccess(): Result<Unit> = runCatching {
        if (!isSuccessful) throw IllegalStateException("HTTP ${code} for ${request.url}").also { close() }
    }.also { close() }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        // PROPFIND on root with depth 0
        val req = Request.Builder()
            .url(url(""))
            .method("PROPFIND", "".toRequestBody(null))
            .header("Authorization", authHeader)
            .header("Depth", "0")
            .build()
        exec(req).fold(onSuccess = { resp ->
            val ok = resp.isSuccessful || resp.code == 207
            resp.close()
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("HTTP ${resp.code}"))
        }, onFailure = { Result.failure(it) })
    }

    suspend fun upload(remotePath: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val body = data.toRequestBody("application/octet-stream".toMediaType())
        val req = Request.Builder().url(url(remotePath)).put(body).header("Authorization", authHeader).build()
        exec(req).fold(onSuccess = { it.ensureSuccess() }, onFailure = { Result.failure(it) })
    }

    suspend fun download(remotePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(remotePath)).get().header("Authorization", authHeader).build()
        exec(req).fold(onSuccess = { resp ->
            if (!resp.isSuccessful) { val c = resp.code; resp.close(); Result.failure(IllegalStateException("HTTP $c")) }
            else { val bytes = resp.body?.bytes() ?: ByteArray(0); resp.close(); Result.success(bytes) }
        }, onFailure = { Result.failure(it) })
    }

    suspend fun delete(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(remotePath)).delete().header("Authorization", authHeader).build()
        exec(req).fold(onSuccess = { it.ensureSuccess() }, onFailure = { Result.failure(it) })
    }

    /** MKCOL; 405 means the collection already exists (treated as success). */
    suspend fun ensureDir(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(remotePath)).method("MKCOL", null).header("Authorization", authHeader).build()
        exec(req).fold(onSuccess = { resp ->
            val ok = resp.isSuccessful || resp.code == 405
            resp.close()
            if (ok) Result.success(Unit) else Result.failure(IllegalStateException("HTTP ${resp.code}"))
        }, onFailure = { Result.failure(it) })
    }

    suspend fun list(remotePath: String): Result<List<RemoteFileInfo>> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url(remotePath))
            .method("PROPFIND", "".toRequestBody(null))
            .header("Authorization", authHeader)
            .header("Depth", "1")
            .header("Content-Type", "application/xml")
            .build()
        exec(req).fold(onSuccess = { resp ->
            if (resp.code != 207 && !resp.isSuccessful) { val c = resp.code; resp.close(); return@fold Result.failure(IllegalStateException("HTTP $c")) }
            val xml = resp.body?.string() ?: ""
            resp.close()
            Result.success(parseMultiStatus(xml))
        }, onFailure = { Result.failure(it) })
    }

    private fun parseMultiStatus(xml: String): List<RemoteFileInfo> {
        if (xml.isBlank()) return emptyList()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val files = mutableListOf<RemoteFileInfo>()
        var event = parser.eventType
        var currentHref: String? = null
        var currentSize: Long = 0L
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "href") currentHref = parser.nextText()
                    if (parser.name == "getcontentlength") currentSize = parser.nextText().trim().toLongOrNull() ?: 0L
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && currentHref != null) {
                        val name = currentHref.substringAfterLast('/').takeIf { it.isNotBlank() } ?: currentHref
                        // Skip the directory listing itself (href ending with '/')
                        if (!currentHref.endsWith("/")) files.add(RemoteFileInfo(name, currentSize))
                    }
                    currentHref = null; currentSize = 0L
                }
            }
            event = parser.next()
        }
        return files
    }
}
```

- [ ] **Step 4: Run, verify pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.webdav.WebDavClientTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/webdav/WebDavClient.kt app/src/test/java/me/jbusdriver/modern/data/backup/webdav/WebDavClientTest.kt
git commit -m "feat(backup): add simplest-first WebDavClient over OkHttp"
```

---

## Task 8: WebDavClientFactory

Reads WebDAV settings from `AppSettingsContract` and builds a `WebDavClient`. Also a shared OkHttpClient.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/webdav/WebDavClientFactory.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create factory**

```kotlin
package me.jbusdriver.modern.data.backup.webdav

import kotlinx.coroutines.flow.first
import me.jbusdriver.modern.data.settings.AppSettingsContract
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClientFactory @Inject constructor(
    private val settings: AppSettingsContract
) {
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun create(): WebDavClient = WebDavClient(
        okHttpClient = sharedClient,
        serverUrl = settings.webdavServerUrl.first(),
        username = settings.webdavUsername.first(),
        password = settings.webdavPassword.first()
    )
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/webdav/WebDavClientFactory.kt
git commit -m "feat(backup): add WebDavClientFactory"
```

---

## Task 9: BackupStorage interface + HistoryDao.listAll

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupStorage.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/dao/HistoryDao.kt`

- [ ] **Step 1: Define interface**

```kotlin
package me.jbusdriver.modern.data.backup

import kotlin.Result

data class BackupFileInfo(val name: String, val size: Long, val lastModified: Long)

interface BackupStorage {
    /** Write bytes under [name]; return a human-readable location string. */
    suspend fun write(name: String, data: ByteArray): Result<String>
    suspend fun list(): Result<List<BackupFileInfo>>
    suspend fun read(name: String): Result<ByteArray>
    suspend fun delete(name: String): Result<Unit>
    /** Human label for UI, e.g. "本地 / <path>" or "WebDAV / <server>". */
    fun describe(): String
}
```

- [ ] **Step 2: Add HistoryDao.listAll**

In `HistoryDao.kt` add:
```kotlin
@Query("SELECT * FROM t_history ORDER BY id ASC")
suspend fun listAll(): List<History>
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/BackupStorage.kt app/src/main/java/me/jbusdriver/modern/data/db/dao/HistoryDao.kt
git commit -m "feat(backup): add BackupStorage interface and HistoryDao.listAll"
```

---

## Task 10: LocalBackupStorage + WebDavBackupStorage

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/LocalBackupStorage.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/WebDavBackupStorage.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupStorageFactory.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: LocalBackupStorage (SAF tree)**

```kotlin
package me.jbusdriver.modern.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.Result

/** SAF tree-based local backup storage. Requires a persisted tree URI in settings. */
class LocalBackupStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val treeUri: String
) : BackupStorage {

    private val resolver get() = context.contentResolver

    override fun describe(): String = "本地 / ${Uri.parse(treeUri).lastPathSegment ?: treeUri}"

    override suspend fun write(name: String, data: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (treeUri.isBlank()) error("本地備份路徑未選擇")
            val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), docId)
            // Delete existing with same name first (overwrite)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val childId = c.getString(0)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), childId)
                    val displayName = childUri.lastPathSegment ?: ""
                    if (displayName.endsWith(name) || displayName == name) {
                        DocumentsContract.deleteDocument(resolver, childUri)
                    }
                }
            }
            val newDoc = DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), docId),
                "application/json",
                name
            ) ?: error("無法建立備份檔 $name")
            resolver.openOutputStream(newDoc)?.use { it.write(data) } ?: error("無法寫入備份檔")
            newDoc.toString()
        }
    }

    override suspend fun list(): Result<List<BackupFileInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (treeUri.isBlank()) return@runCatching emptyList()
            val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), docId)
            val out = mutableListOf<BackupFileInfo>()
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val modified = c.getLong(1)
                    val size = c.getLong(2)
                    out.add(BackupFileInfo(c.getString(3) ?: id, size, modified))
                }
            }
            out
        }
    }

    override suspend fun read(name: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            findUriByName(name)?.let { uri ->
                resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("無法讀取 $name")
            } ?: error("找不到 $name")
        }
    }

    override suspend fun delete(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            findUriByName(name)?.let { DocumentsContract.deleteDocument(resolver, it) } ?: Unit
        }
    }

    private fun findUriByName(name: String): Uri? {
        if (treeUri.isBlank()) return null
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), docId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val displayName = c.getString(1) ?: ""
                if (displayName == name || displayName.endsWith("/$name")) {
                    return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), c.getString(0))
                }
            }
        }
        return null
    }
}
```

- [ ] **Step 2: WebDavBackupStorage**

```kotlin
package me.jbusdriver.modern.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.data.backup.webdav.WebDavClient
import kotlin.Result

class WebDavBackupStorage(
    private val client: WebDavClient,
    private val remoteFolder: String,
    private val serverLabel: String
) : BackupStorage {

    private fun path(name: String) = remoteFolder.trim('/') + "/" + name

    override fun describe(): String = "WebDAV / $serverLabel"

    override suspend fun write(name: String, data: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        client.ensureDir(remoteFolder).getOrElse { return@withContext Result.failure(it) }
        client.upload(path(name), data).map { path(name) }
    }

    override suspend fun list(): Result<List<BackupFileInfo>> = withContext(Dispatchers.IO) {
        client.list(remoteFolder).map { remote ->
            remote.map { BackupFileInfo(it.name, it.size, 0L) }
        }
    }

    override suspend fun read(name: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        client.download(path(name))
    }

    override suspend fun delete(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        client.delete(path(name))
    }
}
```

- [ ] **Step 3: BackupStorageFactory (Hilt)**

```kotlin
package me.jbusdriver.modern.data.backup

import dagger.Lazy
import kotlinx.coroutines.flow.first
import me.jbusdriver.modern.data.backup.webdav.WebDavClientFactory
import me.jbusdriver.modern.data.settings.AppSettingsContract
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupStorageFactory @Inject constructor(
    private val settings: AppSettingsContract,
    private val webDavClientFactory: WebDavClientFactory
) {
    suspend fun forTarget(target: BackupTarget): List<BackupStorage> {
        val storages = mutableListOf<BackupStorage>()
        if (target.includesLocal) {
            storages += LocalBackupStorage(localTreeUriProvider(), settings.localBackupUri.first())
        }
        if (target.includesWebDav) {
            val client = webDavClientFactory.create()
            storages += WebDavBackupStorage(
                client = client,
                remoteFolder = settings.webdavFolder.first(),
                serverLabel = settings.webdavServerUrl.first()
            )
        }
        return storages
    }

    /** LocalBackupStorage needs Context; provided via a Hilt-bound function. */
    @Inject lateinit var contextProvider: Lazy<android.content.Context>
    private suspend fun localTreeUriProvider(): android.content.Context = contextProvider.get()
}
```

> The factory injects `AppSettingsContract` (already Hilt-bound) and `WebDavClientFactory`. `LocalBackupStorage` needs `@ApplicationContext Context` — to keep `LocalBackupStorage` constructable by the factory, inject `dagger.Lazy<Context>` qualified with `@ApplicationContext`. Simpler alternative below.

Replace the `contextProvider` lines with a clean injected context:

```kotlin
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
// inside class:
@Inject @ApplicationContext lateinit var appContext: android.content.Context
// replace localTreeUriProvider() usage:
//   storages += LocalBackupStorage(appContext, settings.localBackupUri.first())
```
(Final factory body uses `appContext` directly; remove the `Lazy<Context>` and `localTreeUriProvider`.)

- [ ] **Step 4: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/LocalBackupStorage.kt app/src/main/java/me/jbusdriver/modern/data/backup/WebDavBackupStorage.kt app/src/main/java/me/jbusdriver/modern/data/backup/BackupStorageFactory.kt
git commit -m "feat(backup): add Local/WebDav BackupStorage + factory"
```

---

## Task 11: Extend CollectionBackupCodec + CollectRepository with strategy & change events

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/CollectionBackupCodec.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt`

- [ ] **Step 1: Read current codec**

```bash
sed -n '1,150p' app/src/main/java/me/jbusdriver/modern/data/repository/CollectionBackupCodec.kt
```

- [ ] **Step 2: Add strategy-aware import**

In `CollectionBackupCodec.kt`, change `importCollectionsFromJson` to accept a strategy. Keep the existing skip-only path for `MERGE` and add delete-then-insert for `OVERWRITE`:

```kotlin
suspend fun importCollectionsFromJson(json: String, strategy: RestoreStrategy = RestoreStrategy.MERGE): Pair<Int, Int> {
    val element = GSON.fromJson(json, com.google.gson.JsonElement::class.java)
    return if (element.isJsonArray) importLegacyFormat(element.asJsonArray, strategy)
    else importNewFormat(element.asJsonObject, strategy)
}
```
In `importNewFormat` / `importLegacyFormat`, at the existing `if (linkDao.hasByKey(item.dbType, item.key) >= 1)` branch:
- `MERGE`: keep current behavior (`skipped++`).
- `OVERWRITE`: `linkDao.delete(item.dbType, item.key); linkDao.insert(item); imported++`.

Concretely replace the branch body with:
```kotlin
if (exists) {
    if (strategy == RestoreStrategy.OVERWRITE) {
        linkDao.delete(item.dbType, item.key); linkDao.insert(item); imported++
    } else {
        skipped++
    }
} else {
    linkDao.insert(item); imported++
}
```
Apply to both movies and actresses loops. Add import `me.jbusdriver.modern.data.backup.RestoreStrategy`.

- [ ] **Step 3: Add collectionChanges to CollectRepository**

In `CollectRepository.kt`:
- Add to the interface:
```kotlin
val collectionChanges: SharedFlow<CollectionChangeEvent>
```
- Add a top-level (same file or a new tiny file) sealed type:
```kotlin
sealed interface CollectionChangeEvent { data object Changed : CollectionChangeEvent }
```
- In `DefaultCollectRepository`, add a private mutable backing flow and expose it:
```kotlin
private val _collectionChanges = MutableSharedFlow<CollectionChangeEvent>(extraBufferCapacity = 8)
override val collectionChanges: SharedFlow<CollectionChangeEvent> = _collectionChanges.asSharedFlow()
private suspend fun emitChange() { _collectionChanges.tryEmit(CollectionChangeEvent.Changed) }
```
- Call `emitChange()` at the end of `addCollect`, `removeCollect`, `toggleMovieCollect`, `toggleActressCollect` (after the DB op succeeds). Also expose the strategy import:
```kotlin
override suspend fun importCollectionsFromJson(json: String, strategy: RestoreStrategy): Pair<Int, Int> =
    transactionRunner.withTransaction { backupCodec.importCollectionsFromJson(json, strategy) }.also { emitChange() }
```
- Keep the existing `importCollectionsFromJson(json): Pair<Int, Int>` as a `= importCollectionsFromJson(json, RestoreStrategy.MERGE)` overload (so `CollectCategoryViewModel` still compiles).
- Add imports: `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`, `kotlinx.coroutines.flow.asSharedFlow`, `me.jbusdriver.modern.data.backup.RestoreStrategy`.

> Remove the previous `Lazy<BackupManager>`-style coupling — there is none on main. `DefaultCollectRepository` gains NO backup dependency; it only emits events.

- [ ] **Step 4: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (`CollectCategoryViewModel` still compiles via the MERGE overload.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/repository/CollectionBackupCodec.kt app/src/main/java/me/jbusdriver/modern/data/repository/CollectRepository.kt
git commit -m "feat(backup): add restore strategy + collection change events"
```

---

## Task 12: BackupManager + conflict precheck (TDD)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupManager.kt`
- Create: `app/src/test/java/me/jbusdriver/modern/data/backup/BackupManagerTest.kt`
- Create: `app/src/test/java/me/jbusdriver/modern/data/backup/Fakes.kt`

- [ ] **Step 1: Write fakes**

`Fakes.kt`:
```kotlin
package me.jbusdriver.modern.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ThemeMode
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.LinkItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeBackupStorage(val written: MutableList<Pair<String, ByteArray>> = mutableListOf()) : BackupStorage {
    val deleted = mutableListOf<String>()
    var listResult: List<BackupFileInfo> = emptyList()
    override suspend fun write(name: String, data: ByteArray) = runCatching { written.add(name to data); "mem/$name" }
    override suspend fun list() = runCatching { listResult }
    override suspend fun read(name: String) = runCatching { written.first { it.first == name }.second }
    override suspend fun delete(name: String) = runCatching { deleted.add(name) }
    override fun describe() = "fake"
}

class FakeHistoryDao : HistoryDao {
    val rows = mutableListOf<History>()
    override suspend fun insert(history: History) = Long.MIN_VALUE
    override suspend fun insertAll(histories: List<History>) = histories.map { Long.MIN_VALUE }
    override suspend fun update(id: Int, dbType: Int, jsonStr: String, isAll: Int) = 0
    override fun queryByLimit(size: Int, offset: Int) = MutableStateFlow(rows.toList())
    override suspend fun count() = rows.size
    override suspend fun deleteAll() = rows.size.also { rows.clear() }
    override suspend fun resetAutoIncrement() = 0
    override suspend fun listAll() = rows.toList()
}

class FakeCollectRepository(
    override val collectionChanges: SharedFlow<CollectionChangeEvent> = MutableSharedFlow()
) : CollectRepository {
    var exported = """{"version":1,"movies":[]}"""
    var lastStrategy: RestoreStrategy? = null
    override suspend fun isCollected(linkItem: LinkItem) = false
    override suspend fun addCollect(linkItem: LinkItem) = true
    override suspend fun removeCollect(linkItem: LinkItem) = true
    override suspend fun isMovieCollected(movie: Movie) = false
    override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
    override suspend fun isActressCollected(actress: ActressInfo) = false
    override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
    override suspend fun getCollectedMovies() = emptyList<Movie>()
    override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
    override suspend fun getCollectedLinkItems(dbType: Int) = emptyList<LinkItem>()
    override suspend fun exportCollectionsJson() = exported
    override suspend fun importCollectionsFromJson(json: String) = importCollectionsFromJson(json, RestoreStrategy.MERGE)
    override suspend fun importCollectionsFromJson(json: String, strategy: RestoreStrategy): Pair<Int, Int> {
        lastStrategy = strategy; return 1 to 0
    }
}
```
> If `CollectRepository` has additional members not listed, the test compile will flag them — add no-op overrides as needed. Match the exact interface from Task 11.

- [ ] **Step 2: Write failing test**

```kotlin
package me.jbusdriver.modern.data.backup

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.db.entity.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    private fun mgr(storage: FakeBackupStorage, collect: FakeCollectRepository, history: FakeHistoryDao) =
        BackupManagerForTest(storage, collect, history)

    @Test fun backupWritesV2AndDelegates() = runTest {
        val storage = FakeBackupStorage()
        val collect = FakeCollectRepository()
        val history = FakeHistoryDao().apply { rows.add(History(dbType = 1, jsonStr = "{}")) }
        val m = BackupManagerForTest(storage, collect, history)
        val result = m.performBackup().getOrThrow()
        assertEquals(1, storage.written.size)
        assertTrue(storage.written[0].first.startsWith("jbus_backup_"))
        val parsed = BackupSerializer.parse(String(storage.written[0].second))
        assertEquals(2, parsed.version)
        assertEquals(1, parsed.history.size)
        assertTrue(result.contains("mem/"))
    }

    @Test fun keepLatestDeletesOlder() = runTest {
        val storage = FakeBackupStorage()
        val m = BackupManagerForTest(storage, FakeCollectRepository(), FakeHistoryDao(), keepLatestOnly = true)
        // simulate 2 pre-existing files
        storage.listResult = listOf(
            BackupFileInfo("jbus_backup_old.json", 10, 1L),
            BackupFileInfo("jbus_backup_older.json", 10, 0L)
        )
        m.performBackup().getOrThrow()
        assertEquals(2, storage.deleted.size) // both older ones deleted
    }

    @Test fun conflictReportCountsExisting() = runTest {
        val collect = FakeCollectRepository()
        val m = BackupManagerForTest(FakeBackupStorage(), collect, FakeHistoryDao())
        val payload = BackupSerializer.parse(
            BackupSerializer.buildV2(
                """{"version":1,"movies":[]}""",
                mapOf("show_forum_tab" to "true"),
                emptyList(),
                "D"
            )
        )
        // No existing settings differ from defaults → show_forum_tab differs (backup true vs default false)
        val report = m.checkConflicts(payload)
        assertTrue(report.settingsDiffer >= 1)
    }

    @Test fun restoreMergeCallsCodecWithMerge() = runTest {
        val collect = FakeCollectRepository()
        val m = BackupManagerForTest(FakeBackupStorage(), collect, FakeHistoryDao())
        val payload = BackupSerializer.parse(
            BackupSerializer.buildV2("""{"version":1,"movies":[]}""", emptyMap(), emptyList(), "D")
        )
        m.performRestore(payload, RestoreStrategy.MERGE)
        assertEquals(RestoreStrategy.MERGE, collect.lastStrategy)
    }

    @Test fun restoreOverwriteCallsCodecWithOverwrite() = runTest {
        val collect = FakeCollectRepository()
        val m = mgr(FakeBackupStorage(), collect, FakeHistoryDao())
        val payload = BackupSerializer.parse(
            BackupSerializer.buildV2("""{"version":1,"movies":[]}""", emptyMap(), emptyList(), "D")
        )
        m.performRestore(payload, RestoreStrategy.OVERWRITE)
        assertEquals(RestoreStrategy.OVERWRITE, collect.lastStrategy)
    }
}
```

- [ ] **Step 3: Run, verify failure**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupManagerTest"`
Expected: FAIL (unresolved `BackupManager`, `BackupManagerForTest`, `RestoreConflictReport`).

- [ ] **Step 4: Implement BackupManager**

```kotlin
package me.jbusdriver.modern.data.backup

import android.os.Build
import kotlinx.coroutines.flow.first
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.settings.AppSettingsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BackupResult(val locations: List<String>) {
    override fun toString() = locations.joinToString(", ")
}

data class RestoreResult(
    val collectionsImported: Int,
    val collectionsSkipped: Int,
    val settingsRestored: Int,
    val historyRestored: Int
)

data class RestoreConflictReport(
    val collectionsConflict: Int,
    val settingsDiffer: Int,
    val historyDuplicate: Int
) {
    val hasConflicts get() = collectionsConflict > 0 || settingsDiffer > 0 || historyDuplicate > 0
}

@Singleton
class BackupManager @Inject constructor(
    private val settings: AppSettingsContract,
    private val collectRepository: CollectRepository,
    private val historyDao: HistoryDao,
    private val storageFactory: BackupStorageFactory
) {
    private val fileNameFormat by lazy { SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US) }

    suspend fun performBackup(target: BackupTarget = settings.backupTarget.first()): Result<BackupResult> = runCatching {
        val collectionsJson = collectRepository.exportCollectionsJson()
        val settingsMap = settings.settingsSnapshot()
        val history = historyDao.listAll().map { HistoryDto(it.dbType, it.jsonStr, it.isAll) }
        val payload = BackupSerializer.buildV2(collectionsJson, settingsMap, history, Build.MODEL)
        val name = "jbus_backup_${fileNameFormat.format(Date())}.json"
        val bytes = payload.toByteArray(Charsets.UTF_8)

        val storages = storageFactory.forTarget(target)
        require(storages.isNotEmpty()) { "未選擇有效的備份目標" }

        val locations = storages.map { storage ->
            storage.write(name, bytes).getOrThrow()
        }

        if (settings.keepLatestOnly.first()) {
            storages.forEach { cleanupKeepLatest(it, name) }
        }
        BackupResult(locations)
    }

    private suspend fun cleanupKeepLatest(storage: BackupStorage, currentName: String) {
        val files = storage.list().getOrDefault(emptyList())
        files.filter { it.name.startsWith("jbus_backup_") && it.name != currentName }
            .sortedByDescending { it.lastModified }
            .drop(0) // keep newest (which is currentName among jbus_backup_*; delete the rest)
        files.filter { it.name.startsWith("jbus_backup_") && it.name != currentName }
            .forEach { storage.delete(it.name) }
    }

    suspend fun checkConflicts(payload: BackupPayload): RestoreConflictReport {
        // Collections conflict: the codec reports skipped during a dry MERGE run is destructive,
        // so approximate by re-running import in a non-committing way is not available.
        // Pragmatic approach: report conflicts based on settings differences (reliable) and
        // history duplicates; collections conflict count is reported as 0 unless codec exposes it.
        // (Backup scope is debug-only; precise per-collection conflict is a later enhancement.)
        val currentSnapshot = settings.settingsSnapshot()
        val settingsDiffer = payload.settings.count { (k, v) -> currentSnapshot[k] != null && currentSnapshot[k] != v }
        val existingHistoryKeys = historyDao.listAll().map { Triple(it.dbType, it.jsonStr, it.isAll) }.toSet()
        val historyDup = payload.history.count { Triple(it.dbType, it.jsonStr, it.isAll) in existingHistoryKeys }
        return RestoreConflictReport(collectionsConflict = 0, settingsDiffer = settingsDiffer, historyDuplicate = historyDup)
    }

    suspend fun performRestore(payload: BackupPayload, strategy: RestoreStrategy): Result<RestoreResult> = runCatching {
        val (imported, skipped) = collectRepository.importCollectionsFromJson(payload.collectionsJson, strategy)
        settings.applySettings(payload.settings)
        val restored = if (payload.history.isNotEmpty()) {
            historyDao.insertAll(payload.history.map { History(dbType = it.dbType, jsonStr = it.jsonStr, isAll = it.isAll) })
                .size
        } else 0
        RestoreResult(imported, skipped, payload.settings.size, restored)
    }

    suspend fun autoBackupIfNeeded() {
        if (!settings.autoBackupEnabled.first()) return
        performBackup().onFailure { /* silent: background auto-backup */ }
    }
}
```

Also add a test-only subclass `BackupManagerForTest` (in `Fakes.kt`) that injects a fixed storage and overrides keepLatestOnly — but cleaner: make the test construct `BackupManager` with a fake `BackupStorageFactory`. Since `BackupStorageFactory` is concrete, add a tiny test seam:

In `BackupManager.kt`, allow storage injection by extracting the storage list acquisition behind an open function:
```kotlin
protected open suspend fun storagesFor(target: BackupTarget): List<BackupStorage> = storageFactory.forTarget(target)
```
and use `storagesFor(target)` inside `performBackup`. For settings values in tests (keepLatestOnly, autoBackupEnabled, backupTarget), add a second constructor-friendly path: since `AppSettingsContract` is an interface, tests provide a `FakeAppSettings`. Add to `Fakes.kt`:

```kotlin
class FakeAppSettings(
    var keepLatest: Boolean = false,
    var autoBackup: Boolean = false,
    val target: BackupTarget = BackupTarget.LOCAL
) : AppSettingsContract {
    // Minimal: all StateFlow defaults; override only what backup reads.
    private fun <T> sf(t: T) = MutableStateFlow(t)
    override val themeMode = sf(ThemeMode.SYSTEM)
    override val dynamicColor = sf(true)
    override val autoLoadGifs = sf(false)
    override val forumFloorOrder = sf(ForumFloorOrder.REGULAR)
    override val showMovieTab = sf(true)
    override val showActressTab = sf(true)
    override val showForumTab = sf(false)
    override val selectedBaseUrl = sf("https://www.javbus.com")
    override val cachedMirrorUrls = sf(emptyList<String>())
    override val autoBackupEnabled = sf(autoBackup)
    override val backupTarget = sf(target)
    override val keepLatestOnly = sf(keepLatest)
    override val localBackupUri = sf("")
    override val webdavServerUrl = sf("")
    override val webdavUsername = sf("")
    override val webdavPassword = sf("")
    override val webdavFolder = sf("/JBusBackup")
    override val webdavDeviceName = sf("Test")
    override suspend fun currentForumFloorOrder() = forumFloorOrder.value
    override suspend fun currentSelectedBaseUrl() = selectedBaseUrl.value
    override suspend fun setThemeMode(mode: ThemeMode) {}
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setShowMovieTab(visible: Boolean) {}
    override suspend fun setShowActressTab(visible: Boolean) {}
    override suspend fun setShowForumTab(enabled: Boolean) {}
    override suspend fun setAutoLoadGifs(enabled: Boolean) {}
    override suspend fun setForumFloorOrder(order: ForumFloorOrder) {}
    override suspend fun selectUrl(url: String) {}
    override suspend fun setAutoBackupEnabled(enabled: Boolean) {}
    override suspend fun setBackupTarget(t: BackupTarget) {}
    override suspend fun setKeepLatestOnly(enabled: Boolean) {}
    override suspend fun setLocalBackupUri(uri: String) {}
    override suspend fun setWebdavServerUrl(url: String) {}
    override suspend fun setWebdavUsername(u: String) {}
    override suspend fun setWebdavPassword(p: String) {}
    override suspend fun setWebdavFolder(f: String) {}
    override suspend fun setWebdavDeviceName(n: String) {}
    override suspend fun settingsSnapshot() = mapOf(
        "theme_mode" to themeMode.value.preferenceValue,
        "dynamic_color" to dynamicColor.value.toString(),
        "show_movie_tab" to showMovieTab.value.toString(),
        "show_actress_tab" to showActressTab.value.toString(),
        "show_forum_tab" to showForumTab.value.toString(),
        "auto_load_gifs" to autoLoadGifs.value.toString(),
        "forum_floor_order" to forumFloorOrder.value.preferenceValue,
        "selected_base_url" to selectedBaseUrl.value
    )
    var appliedSettings: Map<String, String> = emptyMap()
    override suspend fun applySettings(values: Map<String, String>) { appliedSettings = values }
    override suspend fun scanMirrorUrls(state: kotlinx.coroutines.flow.MutableStateFlow<me.jbusdriver.modern.data.mirror.ScanState>, seedUrl: String) {}
    override suspend fun verifyMirrorUrls(state: kotlinx.coroutines.flow.MutableStateFlow<me.jbusdriver.modern.data.mirror.ScanState>) {}
}
```
And a test subclass `BackupManagerForTest` in `Fakes.kt`:
```kotlin
class BackupManagerForTest(
    private val storage: BackupStorage,
    settings: AppSettingsContract,
    collect: CollectRepository,
    history: HistoryDao
) : BackupManager(settings, collect, history, storageFactory = error("unused")) {
    override suspend fun storagesFor(target: BackupTarget) = listOf(storage)
}
```
> Because `storageFactory` is a non-null constructor param, pass a dummy via a test-only constructor. Cleanest: change `BackupManager`'s `storageFactory` param to `dagger.Lazy<BackupStorageFactory>` and default it in tests, OR add a `protected` secondary constructor. Use the test subclass overriding `storagesFor`; pass `error("unused")` factory is invalid (it's called at construction? No — only inside `storagesFor` which we override). But constructor still requires a non-null arg. Provide a no-op factory:
```kotlin
private object NoopStorageFactory : BackupStorageFactory(/* params */) { ... }
```
`BackupStorageFactory` needs `AppSettingsContract` + `WebDavClientFactory`. Instead, make `BackupManager` constructor take `storageFactory: Lazy<BackupStorageFactory>` and only `.get()` inside the default `storagesFor`. Then tests pass `error("unused")` as the Lazy won't be read. Update the production constructor param type to `dagger.Lazy<BackupStorageFactory>`.

In `BackupManager.kt` change:
```kotlin
class BackupManager @Inject constructor(
    private val settings: AppSettingsContract,
    private val collectRepository: CollectRepository,
    private val historyDao: HistoryDao,
    private val storageFactory: dagger.Lazy<BackupStorageFactory>
) {
    protected open suspend fun storagesFor(target: BackupTarget): List<BackupStorage> = storageFactory.get().forTarget(target)
    ...
}
```
Then `BackupManagerForTest(settings, collect, history, storage)` passes `storageFactory = Lazy { error("unused") }`:
```kotlin
class BackupManagerForTest(
    private val storage: BackupStorage,
    settings: AppSettingsContract,
    collect: CollectRepository,
    history: HistoryDao
) : BackupManager(settings, collect, history, storageFactory = dagger.Lazy { error("unused in test") }) {
    override suspend fun storagesFor(target: BackupTarget) = listOf(storage)
}
```
Update the test factory calls: `BackupManagerForTest(storage, FakeAppSettings(keepLatest = true), collect, history)`.

- [ ] **Step 5: Run, verify pass**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.backup.BackupManagerTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/BackupManager.kt app/src/test/java/me/jbusdriver/modern/data/backup/BackupManagerTest.kt app/src/test/java/me/jbusdriver/modern/data/backup/Fakes.kt
git commit -m "feat(backup): add BackupManager with conflict precheck + keep-latest"
```

---

## Task 13: BackupCoordinator (event-driven auto-backup)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/backup/BackupCoordinator.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/JBusApplication.kt`

- [ ] **Step 1: Create coordinator**

```kotlin
package me.jbusdriver.modern.data.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/** Subscribes to collection changes and triggers background auto-backup when enabled. */
@Singleton
class BackupCoordinator @Inject constructor(
    private val collectRepository: me.jbusdriver.modern.data.repository.CollectRepository,
    private val backupManager: BackupManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        collectRepository.collectionChanges
            .onEach { backupManager.autoBackupIfNeeded() }
            .launchIn(scope)
    }
}
```

- [ ] **Step 2: Start it in JBusApplication**

Read current file:
```bash
sed -n '1,80p' app/src/main/java/me/jbusdriver/modern/JBusApplication.kt
```
Add field + start in `onCreate`:
```kotlin
@Inject lateinit var backupCoordinator: me.jbusdriver.modern.data.backup.BackupCoordinator
// inside onCreate(), after super.onCreate():
backupCoordinator.start()
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/backup/BackupCoordinator.kt app/src/main/java/me/jbusdriver/modern/JBusApplication.kt
git commit -m "feat(backup): add event-driven BackupCoordinator"
```

---

## Task 14: SettingsViewModel

Single VM, pure delegation, grouped state. Also surfaces restore conflict report.

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create ViewModel**

```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.backup.BackupManager
import me.jbusdriver.modern.data.backup.BackupPayload
import me.jbusdriver.modern.data.backup.BackupSerializer
import me.jbusdriver.modern.data.backup.BackupTarget
import me.jbusdriver.modern.data.backup.RestoreConflictReport
import me.jbusdriver.modern.data.backup.RestoreResult
import me.jbusdriver.modern.data.backup.RestoreStrategy
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.settings.AppSettingsContract
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ThemeMode
import me.jbusdriver.modern.data.backup.webdav.WebDavClientFactory
import javax.inject.Inject

data class BackupUiState(
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val lastResult: String? = null,
    val error: String? = null
)

data class WebDavTestState(val isTesting: Boolean = false, val success: Boolean? = null, val error: String? = null)

sealed interface RestoreFlow {
    data object Idle : RestoreFlow
    data class AwaitingChoice(val payload: BackupPayload, val report: RestoreConflictReport) : RestoreFlow
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: AppSettingsContract,
    private val backupManager: BackupManager,
    private val webDavClientFactory: WebDavClientFactory,
    private val siteConfig: SiteConfig
) : ViewModel() {

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _webdavTestState = MutableStateFlow(WebDavTestState())
    val webdavTestState: StateFlow<WebDavTestState> = _webdavTestState.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _restoreFlow = MutableStateFlow<RestoreFlow>(RestoreFlow.Idle)
    val restoreFlow: StateFlow<RestoreFlow> = _restoreFlow.asStateFlow()

    // region Network scan (delegates to store -> MirrorScanner)
    fun startScan() = launchScan {
        store.scanMirrorUrls(_scanState, store.selectedBaseUrl.first())
    }
    fun startVerify() = launchScan { store.verifyMirrorUrls(_scanState) }
    fun cancelScan() { _scanState.value = ScanState() }
    fun selectUrl(url: String) {
        viewModelScope.launch { store.selectUrl(url); siteConfig.baseUrl = url }
    }
    private fun launchScan(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { _scanState.value = ScanState(); block() }
            catch (e: Exception) { _scanState.value = _scanState.value.copy(error = null) }
        }
    }
    // endregion

    // region Setters (pure delegation)
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { store.setDynamicColor(enabled) }
    fun setShowMovieTab(v: Boolean) = viewModelScope.launch { store.setShowMovieTab(v) }
    fun setShowActressTab(v: Boolean) = viewModelScope.launch { store.setShowActressTab(v) }
    fun setShowForumTab(v: Boolean) = viewModelScope.launch { store.setShowForumTab(v) }
    fun setAutoLoadGifs(v: Boolean) = viewModelScope.launch { store.setAutoLoadGifs(v) }
    fun setForumFloorOrder(o: ForumFloorOrder) = viewModelScope.launch { store.setForumFloorOrder(o) }
    fun setAutoBackupEnabled(v: Boolean) = viewModelScope.launch { store.setAutoBackupEnabled(v) }
    fun setBackupTarget(t: BackupTarget) = viewModelScope.launch { store.setBackupTarget(t) }
    fun setKeepLatestOnly(v: Boolean) = viewModelScope.launch { store.setKeepLatestOnly(v) }
    fun setLocalBackupUri(uri: String) = viewModelScope.launch { store.setLocalBackupUri(uri) }
    fun setWebdavServerUrl(v: String) = viewModelScope.launch { store.setWebdavServerUrl(v) }
    fun setWebdavUsername(v: String) = viewModelScope.launch { store.setWebdavUsername(v) }
    fun setWebdavPassword(v: String) = viewModelScope.launch { store.setWebdavPassword(v) }
    fun setWebdavFolder(v: String) = viewModelScope.launch { store.setWebdavFolder(v) }
    fun setWebdavDeviceName(v: String) = viewModelScope.launch { store.setWebdavDeviceName(v) }
    // endregion

    // region Backup / Restore
    fun backup() {
        viewModelScope.launch {
            _backupState.update { it.copy(isBackingUp = true, error = null) }
            val result = backupManager.performBackup()
            _backupState.value = result.fold(
                onSuccess = { BackupUiState(lastResult = it.toString()) },
                onFailure = { BackupUiState(error = it.message ?: "備份失敗") }
            )
        }
    }

    /** Entry point from SAF restore picker. Parses + conflict precheck, then either restores or asks the user. */
    fun restoreFromJson(json: String) {
        viewModelScope.launch {
            _backupState.update { it.copy(isRestoring = true, error = null) }
            val payload = try { BackupSerializer.parse(json) }
            catch (e: Exception) {
                _backupState.update { it.copy(isRestoring = false, error = "備份檔格式無效") }
                return@launch
            }
            val report = backupManager.checkConflicts(payload)
            if (!report.hasConflicts) {
                finishRestore(payload, RestoreStrategy.MERGE)
            } else {
                _backupState.update { it.copy(isRestoring = false) }
                _restoreFlow.value = RestoreFlow.AwaitingChoice(payload, report)
            }
        }
    }

    fun applyRestore(strategy: RestoreStrategy) {
        val pending = (_restoreFlow.value as? RestoreFlow.AwaitingChoice) ?: return
        _restoreFlow.value = RestoreFlow.Idle
        viewModelScope.launch { finishRestore(pending.payload, strategy) }
    }

    fun cancelRestore() { _restoreFlow.value = RestoreFlow.Idle }

    private suspend fun finishRestore(payload: BackupPayload, strategy: RestoreStrategy) {
        _backupState.update { it.copy(isRestoring = true) }
        val result = backupManager.performRestore(payload, strategy)
        _backupState.value = result.fold(
            onSuccess = { BackupUiState(lastResult = "已匯入 ${it.collectionsImported} 項收藏、${it.settingsRestored} 項設定、${it.historyRestored} 筆歷史") },
            onFailure = { BackupUiState(error = it.message ?: "還原失敗") }
        )
    }

    fun testWebDavConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _webdavTestState.value = WebDavTestState(isTesting = true)
            val result = webDavClientFactory.create().testConnection()
            _webdavTestState.value = result.fold(
                onSuccess = { WebDavTestState(success = true) },
                onFailure = { WebDavTestState(success = false, error = it.message ?: "連接失敗") }
            )
        }
    }
    // endregion
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt
git commit -m "feat(settings): add SettingsViewModel (delegation + conflict flow)"
```

---

## Task 15: SettingsScreen + settings icon + navigation wiring

Port the DEMO's 3-card screen from `dev/setting:app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`, adapted to: use `AppSettingsContract` via `SettingsViewModel`, use the `RestoreFlow` conflict dialog, and reuse main's existing `R.string.*` for scan UI.

**Files:**
- Create: `app/src/main/res/drawable/settings_24px.xml`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`

- [ ] **Step 1: Add settings icon**

Create `app/src/main/res/drawable/settings_24px.xml` — copy from dev/setting:
```bash
git show dev/setting:app/src/main/res/drawable/settings_24px.xml > app/src/main/res/drawable/settings_24px.xml
```

- [ ] **Step 2: Replace RouteLabSettings with RouteSettings**

In `NavigationKeys.kt`, replace:
```kotlin
@Serializable
data object RouteLabSettings : NavKey
```
with:
```kotlin
@Serializable
data object RouteSettings : NavKey
```

- [ ] **Step 3: Create SettingsScreen**

Port from dev/setting, but bind to `SettingsViewModel`/`AppSettingsContract` and add the conflict `AlertDialog`. Read the reference:
```bash
git show dev/setting:app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt > /tmp/settings_ref.kt
```
Key adaptations from the reference:
- The composable signature stays `fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel())`.
- Read state from `viewModel.store.*` (each is a `StateFlow`) via `collectAsStateWithLifecycle()` — same field names as the DEMO (`themeMode`, `dynamicColor`, `showMovieTab`, `showActressTab`, `showForumTab`, `autoLoadGifs`, `forumFloorOrder`, `selectedBaseUrl`, `cachedMirrorUrls`, `autoBackupEnabled`, `backupTarget`, `keepLatestOnly`, `localBackupUri`, `webdav*`).
- The `AppearanceCard`, `SwitchRow`, `NetworkCard`, `BackupCard` private composables port essentially verbatim from the DEMO. `NetworkCard` is identical to main's `UrlSelectionCard` (in `LabSettingsScreen.kt`) — port that version (it uses `R.string.*` for scan strings). Use `git show dev/setting:...SettingsScreen.kt` for `AppearanceCard`/`BackupCard` bodies.
- SAF launchers (`OpenDocumentTree` for backup dir, `OpenDocument` for restore file) — keep as in DEMO. On restore file picked: read text via `contentResolver.openInputStream`, then `viewModel.restoreFromJson(json)`.
- Backup card gated by `if (BuildConfig.DEBUG) { ... }` (import `me.jbusdriver.BuildConfig`).
- **NEW — conflict dialog**: collect `viewModel.restoreFlow`; when `RestoreFlow.AwaitingChoice`, show:
```kotlin
val restoreFlow by viewModel.restoreFlow.collectAsStateWithLifecycle()
(restoreFlow as? RestoreFlow.AwaitingChoice)?.let { state ->
    AlertDialog(
        onDismissRequest = { viewModel.cancelRestore() },
        title = { Text("發現衝突") },
        text = { Text("收藏衝突 ${state.report.collectionsConflict} 項、設定不同 ${state.report.settingsDiffer} 項、歷史重複 ${state.report.historyDuplicate} 筆。\n如何處理？") },
        confirmButton = {
            TextButton(onClick = { viewModel.applyRestore(RestoreStrategy.OVERWRITE) }) { Text("用備份覆蓋") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.applyRestore(RestoreStrategy.MERGE) }) { Text("保留現有") }
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("取消") }
            }
        }
    )
}
```
- Add the `BackupState` error/lastResult text rows as in DEMO.

> Implementer note: the DEMO file uses hardcoded Traditional Chinese strings. Keep them as-is (i18n deferred per spec). Do NOT invent new string resources.

- [ ] **Step 4: Wire Navigation**

In `Navigation.kt`:
- Replace `import ...LabSettingsScreen` with `import ...SettingsScreen`.
- Replace the `entry<RouteLabSettings> { LabSettingsScreen(onBack = ...) }` block with:
```kotlin
entry<RouteSettings> {
    SettingsScreen(onBack = { backStack.removeLastOrNull() })
}
```
- Replace any `onLabSettingsClick = { backStack.add(RouteLabSettings) }` with `onSettingsClick = { backStack.add(RouteSettings) }`.

- [ ] **Step 5: CollectCategoryScreen menu entry**

Read current file:
```bash
sed -n '50,185p' app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt
```
- Change the `onGoHome: () -> Unit = {}` parameter to `onSettingsClick: () -> Unit = {}`.
- In the `DropdownMenu`, after the import item, add:
```kotlin
DropdownMenuItem(
    text = { Text("更多設置") },
    onClick = { showMenu = false; onSettingsClick() }
)
```

- [ ] **Step 6: MainScreen tab visibility from SettingsViewModel**

Read current file:
```bash
sed -n '40,135p' app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
```
- Change import `LabSettingsViewModel` → `SettingsViewModel`.
- Replace the `labSettingsViewModel`/`forumEnabled` block with:
```kotlin
val settingsViewModel = hiltViewModel<SettingsViewModel>()
val store = settingsViewModel.store
val showMovieTab by store.showMovieTab.collectAsStateWithLifecycle()
val showActressTab by store.showActressTab.collectAsStateWithLifecycle()
val showForumTab by store.showForumTab.collectAsStateWithLifecycle()
```
- Add `onSettingsClick: () -> Unit = {}` to `MainScreen` params.
- Replace the forum-only filter with the three-tab filter:
```kotlin
if (item.category == BottomNavCategory.MOVIE && !showMovieTab) return@forEach
if (item.category == BottomNavCategory.ACTRESS && !showActressTab) return@forEach
if (item.category == BottomNavCategory.FORUM && !showForumTab) return@forEach
```
- Expand the `LaunchedEffect` to auto-switch away from any hidden tab:
```kotlin
LaunchedEffect(showMovieTab, showActressTab, showForumTab) {
    if (!showForumTab && selectedCategory == BottomNavCategory.FORUM) selectedCategory = BottomNavCategory.MOVIE
    if (!showMovieTab && selectedCategory == BottomNavCategory.MOVIE) selectedCategory = BottomNavCategory.ACTRESS
    if (!showActressTab && selectedCategory == BottomNavCategory.ACTRESS) selectedCategory = BottomNavCategory.MOVIE
}
```
- Change `if (forumEnabled) hiltViewModel<ForumBoardsViewModel>()` → `if (showForumTab) hiltViewModel<ForumBoardsViewModel>()`.
- In the `BottomNavCategory.COLLECT -> CollectCategoryScreen(...)` call, replace `onGoHome = { selectedCategory = BottomNavCategory.MOVIE }` with `onSettingsClick = onSettingsClick`.
- In `Navigation.kt`, pass `onSettingsClick = { backStack.add(RouteSettings) }` into the `MainScreen(...)` invocation.

- [ ] **Step 7: Verify compile**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. (Note: at this point both `LabSettings*` and the new settings exist; `RouteLabSettings` references are gone.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/drawable/settings_24px.xml app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt
git commit -m "feat(settings): add SettingsScreen, navigation, menu entry, tab visibility"
```

---

## Task 16: Swap bindings + remove LabSettings (cleanup)

Now that `SettingsViewModel`/`SettingsScreen`/`MainScreen` no longer reference `LabSettings*`, rebind the existing interfaces to `AppSettingsStore` and delete `LabSettings*`.

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/data/settings/LabSettingsStore.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt`
- Delete: `app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt`

- [ ] **Step 1: Swap bindings**

In `DataModule.kt`, remove:
```kotlin
@Binds @Singleton abstract fun bindSitePreferenceSource(impl: LabSettingsStore): SitePreferenceSource
@Binds @Singleton abstract fun bindLabSettingsStoreContract(impl: LabSettingsStore): LabSettingsStoreContract
@Binds @Singleton abstract fun bindForumSettingsReader(impl: LabSettingsStore): ForumSettingsReader
```
and add:
```kotlin
@Binds @Singleton abstract fun bindSitePreferenceSource(impl: AppSettingsStore): SitePreferenceSource
@Binds @Singleton abstract fun bindAppSettingsContractImpl(impl: AppSettingsStore): AppSettingsContract
@Binds @Singleton abstract fun bindForumSettingsReader(impl: AppSettingsStore): ForumSettingsReader
```
Remove the `LabSettingsStoreContract` interface binding entirely (it's being deleted). Update imports: drop `LabSettingsStore`/`LabSettingsStoreContract`, keep `AppSettingsStore`/`AppSettingsContract`/`ThemeSettingsReader`/`ForumSettingsReader`/`SitePreferenceSource`.

- [ ] **Step 2: Delete LabSettings files**

```bash
git rm app/src/main/java/me/jbusdriver/modern/data/settings/LabSettingsStore.kt
git rm app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsScreen.kt
git rm app/src/main/java/me/jbusdriver/modern/ui/settings/LabSettingsViewModel.kt
```
Also remove the now-orphaned `LabSettingsStoreContract` interface — it lived in `LabSettingsStore.kt` (deleted). Grep for any lingering references:
```bash
grep -rn "LabSettings" app/src/main app/src/test || true
```
Expected: no matches (or only your deliberate deletions). If `LabSettingsStoreContract` was referenced elsewhere, remove those references.

- [ ] **Step 3: Verify build + full tests + release smoke**

Run: `./gradlew assembleDebug test assembleRelease`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git status   # confirm only intended changes, no .superpowers/ or .mimocode/ stray files
git commit -m "refactor(settings): rebind to AppSettingsStore, remove LabSettings"
```

---

## Task 17: File hygiene + final verification

**Files:**
- Verify/remove: `.superpowers/brainstorm/**` if present on main
- Modify: `app/src/main/java/me/jbusdriver/modern/JBusApplication.kt` (only if diagnostic logging was added — main has none, so likely no change)

- [ ] **Step 1: Check for stray brainstorm artifacts**

```bash
git ls-files | grep -E "\.superpowers/|\.mimocode/" || echo "none tracked"
```
If any tracked, remove them:
```bash
git rm -r --cached .superpowers 2>/dev/null || true
```
And add to `.gitignore` if a root `.gitignore` exists:
```bash
grep -q "^.superpowers/" .gitignore 2>/dev/null || echo ".superpowers/" >> .gitignore
```

- [ ] **Step 2: Confirm no debug-only diagnostic logging leaked**

```bash
grep -n "logStoragePaths\|Storage Paths" app/src/main/java/me/jbusdriver/modern/JBusApplication.kt || echo "clean"
```
Expected: `clean` (main never had it; the rewrite did not add it). If present, gate behind `if (BuildConfig.DEBUG)`.

- [ ] **Step 3: ProGuard sanity for new Gson model classes**

Add `@Keep` (or verify existing keep rules) for the new data classes serialized by Gson in the backup payload: `HistoryDto`, `BackupPayload` fields. In `app/proguard-rules.pro` (or the project's keep config), ensure a rule covers `me.jbusdriver.modern.data.backup.**`. Verify with the release build in Step 4.

- [ ] **Step 4: Full quality gate**

Run: `./gradlew clean assembleDebug test lintDebug assembleRelease`
Expected: BUILD SUCCESSFUL, tests pass, no new lint errors, release APK builds.

- [ ] **Step 5: Manual verification (device/emulator)**

Work through the spec's verification checklist (items 4–13): settings entry from collect menu; theme switch immediacy; tab visibility toggles; forum sub-settings visibility; mirror scan; local backup writes a timestamped JSON; restore no-conflict path; restore conflict dialog (merge/overwrite/cancel); keep-latest deletes older; auto-backup on collection change; WebDAV test connection (debug build).

- [ ] **Step 6: Commit any hygiene changes**

```bash
git status
git add -A
git commit -m "chore: settings rewrite file hygiene and proguard keep rules"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- Thin AppSettingsStore (pure key-value, no scan) → Task 4 ✅
- MirrorScanner extraction → already exists on main; Task 4 delegates to it ✅
- ThemeRepository decouples theme from SettingsVM → Task 5 ✅
- BackupStorage interface (Local/WebDAV) → Tasks 9–10 ✅
- BackupSerializer (pure) → Task 6 ✅
- BackupManager orchestration + keep-latest + conflict precheck → Task 12 ✅
- WebDavClient simplest-first → Task 7 ✅
- BackupCoordinator event-driven auto-backup; CollectRepository emits events → Tasks 11, 13 ✅
- Coarse-grained restore conflict (merge/overwrite/cancel) → Tasks 11 (strategy), 12 (precheck), 14 (flow), 15 (dialog) ✅
- SettingsViewModel single, grouped, pure delegation → Task 14 ✅
- SettingsScreen 3 cards + entry from collect menu + RouteSettings → Task 15 ✅
- MainScreen tab visibility from new store → Task 15 ✅
- Theme.kt reads ThemeViewModel → Task 5 ✅
- Remove LabSettings*, rebind → Task 16 ✅
- File hygiene, ProGuard, verification → Task 17 ✅
- Decisions table (no migration, debug-only backup, simplest WebDAV, no per-item conflict) → encoded in tasks ✅

**Placeholder scan:** No TBD/TODO/"add error handling" left. Each code step contains full code or an exact `git show`/`sed` reference to copy from.

**Type consistency:** `RestoreStrategy` (Task 3) used in Tasks 11, 12, 14, 15 ✅. `BackupPayload` (Task 6) used in Tasks 12, 14 ✅. `AppSettingsContract` field names (Task 4) match `SettingsViewModel` usage (Task 14) and `FakeAppSettings` (Task 12) ✅. `BackupStorage` methods (Task 9) match Local/WebDav impls (Task 10) and `BackupManager` usage (Task 12) ✅. `CollectionChangeEvent` (Task 11) consumed by `BackupCoordinator` (Task 13) ✅.

**Known limitation documented:** collection-level conflict count is reported as 0 in `checkConflicts` (precise per-collection dry-run isn't available from the codec without a commit). This is called out inline in Task 12 and is acceptable for debug-only backup (per-item conflict is a deferred phase).

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-20-settings-rewrite.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
