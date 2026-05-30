# Persistence Layer Redesign: SharedPreferences → DataStore

**Date**: 2026-05-30
**Status**: Approved

## Background

The current persistence layer has several issues:

1. **SD card hack**: `CollectDatabase` uses `SDCardDatabaseContext` to store `collect.db` on `/sdcard/me.jbus/collect/`. This breaks on Android 11+ Scoped Storage.
2. **6 scattered SharedPreferences files**: `lab_settings`, `search_history`, `session_cookies`, `ui_prefs`, `gif_loaded_urls`, `cover_stats` — some bypass Hilt entirely via `JBus.getSharedPreferences()`.
3. **Manual StateFlow sync**: `LabSettingsStore` manually mirrors every SP write to a `MutableStateFlow`, creating boilerplate.
4. **Dual-entry reads**: `SiteConfigStore` reads `selected_base_url` directly from the `lab_settings` SP file, bypassing `LabSettingsStore`.
5. **Synchronous blocking I/O**: All Store methods are synchronous, forcing `runBlocking` or blocking the main thread when backed by DataStore.

## Scope

- **In scope**: Replace all SharedPreferences with Preferences DataStore; remove SD card hack; centralize SP access points into proper Store classes; migrate all Store interfaces to `suspend` functions.
- **Out of scope**: Room databases (`JBusDatabase`, `CollectDatabase`) remain unchanged — they are the right tool for structured data. No uninstall-survival requirement since the project has not launched.

## Decision: Preferences DataStore (not Proto)

All current SP data is simple key-value (booleans, strings, string sets). No complex nested structures. Preferences DataStore provides Flow APIs, coroutine-friendliness, and no ANR risk with minimal migration cost.

## Approach: Each Store Owns Its DataStore

Each Store creates and holds its own `DataStore<Preferences>` instance via a `Context.dataStore(name)` extension. This eliminates the need for `PreferencesModule`, `@Qualifier` annotations, and `AppPreferences` constants file.

## Design

### §1 SD Card Hack Removal

- Delete `SDCardDatabaseContext.kt` entirely.
- In `DB.kt`, change `collectDatabase` to use standard Room construction:

```kotlin
val collectDatabase: CollectDatabase by lazy {
    Room.databaseBuilder(JBus, CollectDatabase::class.java, COLLECT_DB_NAME)
        .addMigrations(COLLECT_MIGRATION_1_2)
        .build()
}
```

No data migration needed — project has not launched.

### §2 Store Migration Overview

| Current | After |
|---|---|
| `PreferencesModule` (5 `@Qualifier` SP providers) | **Deleted** |
| `AppPreferences` (string constants) | **Deleted** — constants move into Store companion objects |
| `LabSettingsStore` (manual MutableStateFlow sync) | DataStore-backed Flow properties |
| `SearchHistoryStore` (SP + JSON, sync interface) | DataStore + JSON, **all methods → `suspend`** |
| `SessionCookieStore` (SP + JSON, sync interface) | DataStore + JSON, **all methods → `suspend`** |
| `CoverStats` (direct `JBus.getSharedPreferences()`) | **Deleted** — dead code, no consumers |
| `MainScreen.kt` / `MovieList.kt` (direct SP read) | New `UiPrefsStore` injected |
| `ForumThreadDetailViewModel` (`@GifPrefs` SP) | New `GifLoadTracker` injected |

### §3 LabSettingsStore

Before: each field has `_field = MutableStateFlow(prefs.getX())` + setter does `prefs.edit` + `_field.value = ...`.

After:

```kotlin
@Singleton
class LabSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore("lab_settings")

    val forumEnabled: Flow<Boolean> = dataStore.data.map { it[FORUM_ENABLED] ?: false }
    val autoLoadGifs: Flow<Boolean> = dataStore.data.map { it[AUTO_LOAD_GIFS] ?: false }
    val selectedBaseUrl: Flow<String> = dataStore.data.map { it[SELECTED_BASE_URL] ?: DEFAULT_BASE_URL }
    val cachedMirrorUrls: Flow<List<String>> = dataStore.data.map {
        it[CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS
    }

    suspend fun setForumEnabled(enabled: Boolean) = dataStore.edit { it[FORUM_ENABLED] = enabled }
    suspend fun setAutoLoadGifs(enabled: Boolean) = dataStore.edit { it[AUTO_LOAD_GIFS] = enabled }
    suspend fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        dataStore.edit { it[SELECTED_BASE_URL] = trimmed }
        NetClient.defaultFastUrl = trimmed
    }
    // scanMirrorUrls / verifyMirrorUrls: logic unchanged, only result persistence uses dataStore.edit
}
```

`SiteConfigStore` will be updated to receive `LabSettingsStore` via injection instead of reading SP directly.

### §4 SearchHistoryStore

Interface changes — all methods become `suspend`:

```kotlin
interface SearchHistoryStore {
    suspend fun getHistory(): List<String>
    suspend fun addQuery(query: String)
    suspend fun removeQuery(query: String)
    suspend fun clearHistory()
}
```

Internal `SharedPreferences` → DataStore. JSON serialization logic stays the same (DataStore only supports primitives).

**Consumer**: `SearchViewModel` already operates in `viewModelScope`, so callers just add `suspend` naturally.

### §5 SessionCookieStore

All public methods become `suspend`:

```kotlin
suspend fun saveCookies(url: String)
suspend fun restoreCookies(url: String)
suspend fun isSessionValid(url: String): Boolean
suspend fun clear()
```

**Consumer**: `ForumSessionManager` already operates in coroutine scope, so callers just add `suspend` naturally.

### §6 UiPrefsStore (new)

Extracts the `is_grid` boolean currently accessed directly in `MainScreen.kt` and `MovieList.kt` via `JBus.getSharedPreferences(UI_PREFS, 0)`.

```kotlin
@Singleton
class UiPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore("ui_prefs")
    val isGrid: Flow<Boolean> = dataStore.data.map { it[IS_GRID] ?: false }
    suspend fun setGrid(isGrid: Boolean) = dataStore.edit { it[IS_GRID] = isGrid }
}
```

### §7 GifLoadTracker (new)

Extracts the GIF URL set tracking currently in `ForumThreadDetailViewModel` via `@GifPrefs SharedPreferences`. All methods are `suspend`.

```kotlin
@Singleton
class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore("gif_loaded_urls")
    suspend fun loadedUrls(): Set<String>
    suspend fun markLoaded(url: String)
    suspend fun clearAll()
}
```

### §8 CoverStats — Deleted

`CoverStats` has zero consumers (no import references found). Delete `CoverStats.kt` entirely.

### §9 Consumer Changes

| File | Change |
|---|---|
| `MainScreen.kt` | Inject `UiPrefsStore`, replace direct SP access |
| `MovieList.kt` | Inject `UiPrefsStore`, replace direct SP access |
| `ForumViewModels.kt` | Inject `GifLoadTracker`, remove `@GifPrefs` parameter |
| `SiteConfigStore.kt` | Inject `LabSettingsStore`, stop reading SP directly |
| `SearchViewModel.kt` | `historyStore.xxx()` calls now suspend — already in `viewModelScope` |
| `ForumSessionManager.kt` | `cookieStore.xxx()` calls now suspend — already in coroutine scope |
| `SearchViewModelTest.kt` | Test fake's methods add `suspend` |
| All consumers of `LabSettingsStore` | `StateFlow<T>` → `Flow<T>`, use `collectAsStateWithLifecycle()` |

### §10 Dependency Changes

`libs.versions.toml`:
```toml
[versions]
datastore = "1.1.4"

[libraries]
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

`app/build.gradle.kts`:
```kotlin
implementation(libs.datastore.preferences)
```

### Files to Delete

- `app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt`
- `app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt`
- `app/src/main/java/me/jbusdriver/modern/data/db/SDCardDatabaseContext.kt`
- `app/src/main/java/me/jbusdriver/modern/core/CoverStats.kt`

### Files to Create

- `app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt`
- `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`

### Files to Modify

- `libs.versions.toml` — add datastore dependency
- `app/build.gradle.kts` — add datastore implementation
- `DB.kt` — remove SDCardDatabaseContext usage
- `LabSettingsStore.kt` — SP → DataStore, remove MutableStateFlow boilerplate
- `SearchHistoryStore.kt` — SP → DataStore, all methods → suspend
- `SessionCookieStore.kt` — SP → DataStore, all methods → suspend
- `SiteConfigStore.kt` — inject LabSettingsStore instead of direct SP read
- `MainScreen.kt` — inject UiPrefsStore
- `MovieList.kt` — inject UiPrefsStore
- `ForumViewModels.kt` — inject GifLoadTracker
- `SearchViewModel.kt` — adapt to suspend Store methods
- `ForumSessionManager.kt` — adapt to suspend Store methods
- `SearchViewModelTest.kt` — test fake methods → suspend
