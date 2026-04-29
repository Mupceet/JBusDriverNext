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

The project uses Kotlin DSL (`build.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`. KSP is used for annotation processing (Room compiler).

## Architecture

**MVVM with Jetpack Compose** and Hilt dependency injection. All code is in the `me.jbusdriver.modern` package.

### Package Structure

```
me.jbusdriver.modern/
  JBusApplication.kt     - Hilt application entry point
  AppContext.kt           - Base Application class (JBus context holder)
  KLog.kt                - Logging utility
  core/
    Gobal.kt             - GSON instance, URL extensions, toast
    BaseExtension.kt     - Gson helpers, SharedPreferences, Context extensions
    CacheLoader.kt       - Two-tier cache (LruCache + disk ACache)
    JBusManager.kt       - Activity lifecycle tracker, context provider
    ACache.java          - Disk cache implementation
    C.kt                 - Constants (cache durations, component names)
    db/SDCardDatabaseContext.kt  - SD card database context
    http/
      NetClient.kt           - OkHttp/Retrofit client factory
      LoggerInterceptor.java - HTTP logging interceptor
  data/
    db/
      DB.kt, CollectDatabase.kt, JBusDatabase.kt
      dao/     - CategoryDao, HistoryDao, LinkItemDao
      entity/  - Category, History, LinkItem (Room entities)
      service/ - CategoryService, LinkService
    remote/
      JAVBusService.kt  - Retrofit interface for HTML fetching
      GitHub.kt          - Retrofit interface for announce JSON
      di/NetworkModule.kt - Hilt network providers
    magnet/
      Magnet.kt, MagnetManager.kt, IMagnetLoader.kt, Configuration.kt
      loaders/ - DefaultLoaderImpl, MagnetLoaders, WebViewHtmlContentLoader
    CollectRepository.kt, MovieRepository.kt, MovieDetailRepository.kt
    SearchRepository.kt, SettingsRepository.kt
    di/DataModule.kt       - Hilt repository bindings
    local/di/DatabaseModule.kt - Hilt database providers
    model/MoviePageResult.kt
  domain/model/
    Movie.kt, MovieDetail.kt, ILink.kt, ICollectCategory.kt
    Header, Genre, ActressInfo, ImageSample, ActressAttrs
    Bean.kt (DB type constants, convertDBItem, PageLink, SearchLink)
    BeanTransform.kt (HTML parsing: loadMovieFromDoc, parseMovieDetails, etc.)
    Category.kt (default category defaults)
    DataSourceType.kt, SearchType.kt
  ui/
    ModernMainActivity.kt  - Single Activity (Compose)
    MainScreen.kt          - Top-level scaffold with category dropdown
    Navigation.kt          - Compose Navigation graph
    NavigationKeys.kt      - Route constants
    UiModels.kt            - Shared UI state models
    components/ActressAvatar.kt
    detail/                - Movie detail screen + ViewModel
    image/                 - Full-screen image viewer
    movielist/             - Movie/actress/genre/collection list screens + ViewModels
    search/                - Search screen + ViewModel
    settings/              - Settings screen (URL selector) + ViewModel
    theme/                 - Material3 theme (Color, Theme, Type)
```

### Key Patterns

- **Single Activity**: `ModernMainActivity` hosts all Compose UI via navigation
- **Hilt DI**: ViewModels use `hiltViewModel()`, repositories are Hilt-provided
- **Repository pattern**: Each screen has a ViewModel that delegates to a repository
- **HTML parsing**: Jsoup parses fetched HTML into domain models (in BeanTransform.kt)
- **Two-tier cache**: `CacheLoader` uses in-memory LruCache + disk ACache

## Data Flow

1. **Network**: `JAVBusService` (Retrofit) fetches HTML pages via `NetClient`'s OkHttp. Base URLs switchable at runtime.
2. **Parsing**: Jsoup `Document` → domain models via `loadMovieFromDoc()`, `parseMovieDetails()`, etc.
3. **Caching**: `CacheLoader` — LruCache (memory) + ACache (disk).
4. **Database**: Room with two databases:
   - `JBusDatabase`: history tracking
   - `CollectDatabase`: categories and link items (SD card for persistence)

## Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Async | RxJava 3 + Kotlin Coroutines (migration in progress) |
| Network | Retrofit 2.11 + OkHttp 4.12 |
| HTML Parsing | Jsoup 1.18 |
| Database | Room 2.7 (KSP) |
| Image Loading | Coil |
| JSON | Gson 2.12 |
| Navigation | Compose Navigation |

## Project Configuration

- **Package**: `me.jbusdriver.modern`
- **Compile SDK / Target SDK**: 36
- **Min SDK**: 28
- **Java target**: 17
- **ProGuard**: enabled for release builds
- **Room schemas**: exported to `app/schemas/`

## Global State

- `JBus` (top-level `lateinit var` in `AppContext.kt`): Application context reference.
- `JBus.JBusServices`: Map of base URL → `JAVBusService` Retrofit instances.
- `CacheLoader.lru` / `CacheLoader.acache`: Global caches.
