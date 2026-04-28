# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (ProGuard enabled)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Run unit tests (currently empty test directory)
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Build all variants
./gradlew assemble
```

The project uses Kotlin DSL (`build.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`. KSP is used for annotation processing (Room compiler, Glide compiler).

## Architecture

**MVP (Model-View-Presenter)** pattern with RxJava 3 for asynchronous operations.

### Layer Structure

- **View layer** (`ui/`): Activities and Fragments implement presenter-defined View interfaces. Base classes are in `base/common/`.
- **Presenter layer** (`mvp/presenter/`): Each screen has a Contract interface in `mvp/Contract.kt` defining View and Presenter interfaces. Presenters handle lifecycle, pagination, and data flow via RxJava.
- **Model layer**: HTML parsing with Jsoup (`base/mvp/model/`), Room databases for persistence, and Retrofit for network requests.

### Key Base Classes

- `AppBaseActivity<P, V>` / `AppBaseFragment<P, V>`: Generic base that wires presenter lifecycle (onViewAttached → onStart → onResume → onPause → onStop → onViewDetached). Activities declare `layoutId` and `createPresenter()`.
- `AbstractRefreshLoadMorePresenterImpl<V, T>`: Core pagination presenter that handles page loading, pull-to-refresh, load-more, error handling (404, timeout), and pagination state via `PageInfo`.
- `AppBaseRecycleFragment`: Base fragment for list UIs with refresh/load-more support.

### MVP Contract Pattern

All screen contracts are defined in `mvp/Contract.kt`. Each contract has a `*View` interface (extending `BaseView`) and a `*Presenter` interface (extending `BasePresenter`). Presenters are implemented in `mvp/presenter/` with the `*Impl` naming convention (e.g., `MainPresenterImpl`).

### Navigation

`SplashActivity` → `MainActivity` (DrawerLayout + NavigationView). Menu items are driven by `MenuOp` enum which maps to dynamically created Fragments. Fragment switching uses show/hide transactions with tag-based lookup.

## Data Flow

1. **Network**: `JAVBusService` (Retrofit interface) fetches HTML pages. `NetClient` provides a configured OkHttp client with cookie management. Base URLs are switchable at runtime.
2. **Parsing**: Jsoup `Document` objects are parsed in presenter `stringMap()` methods to extract domain beans.
3. **Caching**: Two-tier `CacheLoader` — in-memory `LruCache` + disk `ACache`. First page loads check cache; subsequent pages go direct to network.
4. **Database**: Room with two databases:
   - `JBusDatabase`: history tracking
   - `CollectDatabase`: categories and link items (stored on SD card for persistence across installs)
5. **Events**: `RxBus` for inter-component communication (e.g., `MenuChangeEvent`, `CategoryChangeEvent`).

## Key Libraries

| Purpose | Library |
|---------|---------|
| Async | RxJava 3 + RxAndroid + RxKotlin |
| Network | Retrofit 2.11 + OkHttp 4.12 |
| HTML Parsing | Jsoup 1.18 |
| Database | Room 2.7 (KSP) |
| Image Loading | Glide 4.16 (KSP) |
| List Adapters | BRVAH 3.0.14 |
| Dialogs | Material Dialogs 3.3 |
| JSON | Gson 2.12 |
| Debug | LeakCanary (debugOnly) |

## Project Configuration

- **Package**: `me.jbusdriver`
- **Compile SDK / Target SDK**: 36
- **Min SDK**: 28
- **Java target**: 17
- **ViewBinding + DataBinding**: enabled
- **ProGuard**: enabled for release builds with custom rules in `app/proguard-rules.pro`
- **Room schemas**: exported to `app/schemas/`

## Global State

- `JBus` (top-level `lateinit var` in `common/AppContext.kt`): Application context reference used throughout the app.
- `JBus.JBusServices`: Map of base URL → `JAVBusService` Retrofit instances, cleared on low memory.
- `CacheLoader.lru` / `CacheLoader.acache`: Global caches.
- `RxBus`: Event bus singleton in `common/RxBus.kt`.
