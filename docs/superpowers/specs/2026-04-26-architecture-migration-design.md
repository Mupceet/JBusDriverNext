# Architecture Migration Design: MVP to Modern Android Architecture

**Date:** 2026-04-26
**Status:** Approved
**Scope:** Incremental migration from MVP + RxJava to ViewModel + Compose + Hilt + Coroutines/Flow

---

## Overview

Migrate the JBus Android app from its current MVP (Model-View-Presenter) architecture with RxJava 3 to the modern Android architecture recommended by the official Android architecture guide, using the [architecture-templates/base](https://github.com/android/architecture-templates/tree/base) as the reference template.

**Strategy:** Strangler Fig pattern — new architecture code lives in a `modern/` package namespace, coexisting with existing MVP code. Features are migrated one screen at a time, starting with the simplest.

---

## 1. New Package Structure

New architecture code is organized under `me.jbusdriver.modern/`, following the architecture-templates pattern:

```
me.jbusdriver/
├── (existing packages: base/, common/, db/, http/, magnet/, mvp/, ui/)
│
└── modern/
    ├── JBusApplication.kt                     # @HiltAndroidApp
    │
    ├── data/
    │   ├── SettingsRepository.kt              # interface + DefaultSettingsRepository
    │   ├── di/
    │   │   └── DataModule.kt                  # binds repository interfaces
    │   ├── local/
    │   │   ├── database/                      # Room DB wrappers
    │   │   └── di/
    │   │       └── DatabaseModule.kt          # provides DBs and DAOs
    │   └── remote/
    │       └── di/
    │           └── NetworkModule.kt           # provides OkHttpClient, Retrofit services
    │
    └── ui/
        ├── ModernMainActivity.kt              # @AndroidEntryPoint, Compose host
        ├── Navigation.kt                      # NavHost + route graph
        ├── NavigationKeys.kt                  # Route constants
        ├── settings/                          # Per-feature package
        │   ├── SettingsScreen.kt              # Compose UI
        │   └── SettingsViewModel.kt           # @HiltViewModel
        └── theme/
            ├── Color.kt
            ├── Theme.kt
            └── Type.kt
```

**Key decisions:**
- `modern/` namespace for coexistence — old code stays untouched
- DI modules colocated with the layer they serve (matching architecture-templates)
- No separate `domain/` layer initially — add only if shared complex logic emerges
- Each feature is a folder under `ui/` with Screen + ViewModel pair
- `data/remote/` added (not in template) because this app uses Retrofit + Jsoup

---

## 2. Hilt Foundation

### 2.1 Application Class

`modern/JBusApplication.kt`:
- Annotated `@HiltAndroidApp`
- Initializes existing `JBus` singleton and global state (backward compatibility)
- AndroidManifest references this as `android:name`

### 2.2 Network Module

`data/remote/di/NetworkModule.kt`:
- Provides `OkHttpClient` (wraps existing `NetClient` setup)
- Provides `Gson` instance
- Provides per-base-URL `JAVBusService` Retrofit instances (wraps `JBus.JBusServices`)
- All scoped `@Singleton`

### 2.3 Database Module

`data/local/di/DatabaseModule.kt`:
- Provides `JBusDatabase` and `CollectDatabase` instances
- Provides DAOs (`HistoryDao`, `CategoryDao`, `LinkItemDao`)
- All scoped `@Singleton`

### 2.4 Data Module

`data/di/DataModule.kt`:
- Binds repository interfaces to their default implementations
- e.g., `SettingsRepository` → `DefaultSettingsRepository`

---

## 3. First Feature: Settings Screen

### 3.1 UI State

```kotlin
data class SettingsUiState(
    val baseUrl: String,
    val availableUrls: List<String>,
    val isUpdating: Boolean = false
)
```

### 3.2 ViewModel

`ui/settings/SettingsViewModel.kt`:
- `@HiltViewModel` with `@Inject constructor`
- Exposes `StateFlow<SettingsUiState>`
- Delegates to `SettingsRepository` for URL read/write
- Uses coroutines for async operations

### 3.3 Repository

```kotlin
interface SettingsRepository {
    fun getCurrentUrl(): String
    fun getAvailableUrls(): List<String>
    suspend fun updateUrl(url: String)
}
```

`DefaultSettingsRepository` delegates to existing URL management: `JBus.currentUrl`, the URL list from `AppContext`, and URL persistence via `ACache`.

### 3.4 Compose Screen

`ui/settings/SettingsScreen.kt`:
- Material 3 components
- URL selector (DropdownMenu)
- Simple layout for first migration

### 3.5 Navigation

- `ModernMainActivity` hosts Compose content with NavHost
- Single route: Settings
- **Entry point:** The old `SettingActivity` intent is redirected to `ModernMainActivity` by updating the navigation menu click handler in `MainActivity`. This is the only change to existing code — a single intent target swap.

---

## 4. Coexistence Rules

1. **Old code is untouched** — no modifications to existing MVP code during migration
2. **New code wraps old code** — repositories delegate to existing services/DAOs initially
3. **Both DI systems coexist** — Hilt for new code, manual singletons for old code
4. **Both async frameworks coexist** — Coroutines/Flow for new code, RxJava for old code
5. **New Application class initializes both** — `JBusApplication` calls old init code + Hilt setup

---

## 5. Migration Roadmap

### Phase 1: Foundation + Simple Screen (Current)
1. Hilt infrastructure (Application, NetworkModule, DatabaseModule, DataModule)
2. Settings screen (ViewModel + Repository + Compose + Navigation)

### Phase 2: Paginated Content Lists
3. Movie list screen (Home — pagination, Jsoup parsing, caching)
4. Actress list / Genre list (reuse pagination pattern from #5)

### Phase 3: Detail & Search
5. Movie detail screen (complex layout, linked data, magnets)
6. Search screen (cross-source search)

### Phase 4: Data Layer Screens
7. History screen (simple list + Room DB, Repository → Flow pattern)
8. Collection screen (My Collection — multi-DAO, validates database module)

### Phase 5: Navigation & Cleanup
9. Replace DrawerLayout + fragment show/hide with Compose Navigation
10. Remove old base classes, RxBus, manual DI, unused RxJava code

---

## 6. Technology Transition Summary

| Component | Current | Target | When |
|-----------|---------|--------|------|
| DI | Manual singletons | Hilt | Phase 1 |
| Architecture | Presenter + Contract | ViewModel + UiState | Per screen, Phases 1-4 |
| Async | RxJava 3 | Coroutines/Flow | New code only; old keeps RxJava |
| UI | XML + ViewBinding | Compose + Material 3 | Per screen, Phases 1-4 |
| Lists | BRVAH adapters | LazyColumn | Per list screen, Phases 2-4 |
| Events | RxBus | SharedFlow / direct calls | Phase 5 |
| Navigation | DrawerLayout + fragments | Navigation 3 | Phase 5 |
| Images | Glide | Coil (optional) | As needed |
| Testing | None | Unit + UI tests with Hilt | Per screen |

---

## 7. Naming Conventions

Following Android architecture recommendations:
- `Default` prefix for implementations: `DefaultSettingsRepository`
- `Fake` prefix for test doubles: `FakeSettingsRepository`
- `UiState` suffix for screen state: `SettingsUiState`
- `Screen` suffix for composables: `SettingsScreen`
- `ViewModel` suffix: `SettingsViewModel`
- `Repository` suffix for data interfaces: `SettingsRepository`

---

## References

- [Android Official Architecture Guide](https://developer.android.com/topic/architecture?hl=zh-cn)
- [Android Architecture Recommendations](https://developer.android.com/topic/architecture/recommendations?hl=zh-cn)
- [Architecture Templates (base branch)](https://github.com/android/architecture-templates/tree/base)
