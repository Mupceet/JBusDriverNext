# Persistence Layer Redesign: SharedPreferences → DataStore

**Date**: 2026-05-30
**Status**: Approved

## Background

The current persistence layer has several issues:

1. **SD card hack**: `CollectDatabase` uses `SDCardDatabaseContext` to store `collect.db` on `/sdcard/me.jbus/collect/`. This breaks on Android 11+ Scoped Storage.
2. **6 scattered SharedPreferences files**: `lab_settings`, `search_history`, `session_cookies`, `ui_prefs`, `gif_loaded_urls`, `cover_stats` — some bypass Hilt entirely via `JBus.getSharedPreferences()`.
3. **Manual StateFlow sync**: `LabSettingsStore` manually mirrors every SP write to a `MutableStateFlow`, creating boilerplate.
4. **Dual-entry reads**: `SiteConfigStore` reads `selected_base_url` directly from the `lab_settings` SP file, bypassing `LabSettingsStore`.

## Scope

- **In scope**: Replace all SharedPreferences with Preferences DataStore; remove SD card hack; centralize SP access points into proper Store classes.
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
| `SearchHistoryStore` (SP + JSON) | DataStore + JSON (same serialization) |
| `SessionCookieStore` (SP + JSON) | DataStore + JSON (same serialization) |
| `CoverStats` (direct `JBus.getSharedPreferences()`) | Self-owned DataStore, no global ref |
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

Interface unchanged. Internal `SharedPreferences` → DataStore. JSON serialization logic stays the same (DataStore only supports primitives).

### §5 SessionCookieStore

Same pattern. Interface unchanged, internal SP → DataStore. JSON serialization for `PersistedCookie` map stays.

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

Extracts the GIF URL set tracking currently in `ForumThreadDetailViewModel` via `@GifPrefs SharedPreferences`.

```kotlin
@Singleton
class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore("gif_loaded_urls")
    fun loadedUrls(): Set<String> // runBlocking for sync callers
    suspend fun markLoaded(url: String)
    suspend fun clearAll()
}
```

### §8 CoverStats

Replace direct `JBus.getSharedPreferences("cover_stats", 0)` with a self-owned DataStore. Remains an `object` singleton since it's a fire-and-forget stats collector.

### §9 Consumer Changes

| File | Change |
|---|---|
| `MainScreen.kt` | Inject `UiPrefsStore`, replace direct SP access |
| `MovieList.kt` | Inject `UiPrefsStore`, replace direct SP access |
| `ForumViewModels.kt` | Inject `GifLoadTracker`, remove `@GifPrefs` parameter |
| `SiteConfigStore.kt` | Inject `LabSettingsStore`, stop reading SP directly |
| All consumers of `LabSettingsStore` | `StateFlow<T>` → `Flow<T>`, add `collectAsStateWithLifecycle()` |

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

### Files to Create

- `app/src/main/java/me/jbusdriver/modern/data/UiPrefsStore.kt`
- `app/src/main/java/me/jbusdriver/modern/data/GifLoadTracker.kt`

### Files to Modify

- `libs.versions.toml` — add datastore dependency
- `app/build.gradle.kts` — add datastore implementation
- `DB.kt` — remove SDCardDatabaseContext usage
- `LabSettingsStore.kt` — SP → DataStore, remove MutableStateFlow boilerplate
- `SearchHistoryStore.kt` — SP → DataStore
- `SessionCookieStore.kt` — SP → DataStore
- `CoverStats.kt` — SP → DataStore
- `SiteConfigStore.kt` — inject LabSettingsStore instead of direct SP read
- `MainScreen.kt` — inject UiPrefsStore
- `MovieList.kt` — inject UiPrefsStore
- `ForumViewModels.kt` — inject GifLoadTracker
- `data/di/DataModule.kt` — remove any SP-related bindings if present
