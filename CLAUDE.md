# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (ProGuard enabled, APK named jbus_release_v<versionName>.apk)
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Build all variants
./gradlew assemble
```

The project uses Kotlin DSL (`build.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`. AGP 9.2.0 provides built-in Kotlin compilation — no separate `kotlin-android` plugin. KSP is used for annotation processing (Room compiler, Hilt compiler).

## Architecture

**MVVM with Jetpack Compose** and Hilt dependency injection. All code is in the `me.jbusdriver.modern` package.

### Package Structure

```
me.jbusdriver.modern/
  JBusApplication.kt       - @HiltAndroidApp entry point, provides Coil ImageLoader
  AppContext.kt             - Application base, initializes JBus global ref and JBusManager
  KLog.kt                   - Logging utility
  core/
    GsonExt.kt              - GSON instance, generic fromJson/toJson extensions
    BaseExtension.kt        - SharedPreferences, Context extensions
    CacheLoader.kt          - Two-tier cache: LruCache (memory) + FileCache (disk)
    FileCache.kt            - Disk cache implementation (replaces former ACache.java)
    FileUtil.kt             - File size formatting helpers
    JBusManager.kt          - Activity lifecycle tracker, context provider
    C.kt                    - Constants (cache durations, component names)
    http/
      NetClient.kt          - OkHttp singleton, fetchDocument() for HTML→Jsoup parsing
  data/
    db/
      DB.kt                 - Lazy Room database instances (JBusDatabase, CollectDatabase)
      DBTypes.kt            - DB type constants (MovieDBType, ActressDBType)
      LinkMappers.kt        - DB entity ↔ domain model mappers
      JBusDatabase.kt       - Room DB for history tracking
      CollectDatabase.kt    - Room DB for categories/link items (SD card for persistence)
      dao/                  - CategoryDao, HistoryDao, LinkItemDao
      entity/               - Category, History, LinkItem (Room entities)
      SDCardDatabaseContext.kt - SD card database context
    parser/
      HtmlParser.kt         - All HTML→domain parsing (loadMovieFromDoc, parseMovieDetails, etc.)
    magnet/
      MagnetManager.kt      - Magnet link resolution
      IMagnetLoader.kt      - Magnet loader interface
    MovieRepository.kt      - Interface + DefaultMovieRepository (OkHttp + Jsoup + cache)
    MovieDetailRepository.kt - Interface + DefaultMovieDetailRepository
    CollectRepository.kt    - Interface + DefaultCollectRepository (Room-backed)
    SearchRepository.kt     - Interface + DefaultSearchRepository
    di/
      DataModule.kt         - @Binds Repository interfaces → implementations
      DatabaseModule.kt     - @Provides Room DB instances and DAOs
  domain/model/
    Movie.kt, MovieDetail.kt   - Core domain models (Movie, MovieDetail, Header, Genre, etc.)
    ILink.kt, PageLink.kt      - Link abstraction and pagination
    ActressInfo, ImageSample, ActressAttrs  - defined in MovieDetail.kt
    Magnet.kt, SearchType.kt, DataSourceType.kt, Category.kt
    UrlExt.kt               - URL path extension property
    MoviePageResult.kt      - Paginated result wrapper
  ui/
    ModernMainActivity.kt   - Single Activity (@AndroidEntryPoint, edge-to-edge, Compose host)
    MainScreen.kt           - Tab pager: 有碼/無碼/收藏 × 影片/演員
    Navigation.kt           - Compose Navigation graph with iOS-style transitions
    NavigationKeys.kt       - Route constants and URL builders
    UiModels.kt             - Shared UI state models (MovieUiModel, ActressUiModel, GenreUiModel)
    components/
      ActressAvatar.kt       - Actress avatar with placeholder
      ActressGrid.kt         - Grid layout for actress list
      MovieList.kt           - Shared movie list/grid composable
    detail/                 - Movie detail screen + ViewModel
    image/                  - Full-screen image viewer (Telephoto zoomable)
    movielist/              - Movie/actress/genre/collection list screens + ViewModels
    search/                 - Search screen + ViewModel
    theme/                  - Material3 theme (Color, Theme, Type)
```

### Key Patterns

- **Single Activity**: `ModernMainActivity` with `enableEdgeToEdge()` hosts all Compose UI
- **Hilt DI**: ViewModels use `hiltViewModel()` (from `hilt-navigation-compose`), repositories are interface+impl pairs bound via `@Binds` in `DataModule`. Navigation-arg ViewModels use `@AssistedInject` + `@AssistedFactory`.
- **Repository pattern**: Each screen has a ViewModel that delegates to a Hilt-provided repository
- **HTML scraping**: `NetClient.fetchDocument()` (OkHttp → Jsoup) → domain models via top-level functions in `HtmlParser.kt`
- **Two-tier cache**: `CacheLoader.lruCached()` (memory only) for lists; `CacheLoader.persistentCached()` (memory + disk) for details
- **Coroutines throughout**: All repositories use `suspend` functions, no RxJava

## Data Flow

1. **Network**: `NetClient.fetchDocument(url)` fetches HTML via OkHttp and parses to Jsoup `Document`. Base URL (`defaultFastUrl`) is switchable at runtime.
2. **Parsing**: Top-level functions in `HtmlParser.kt` convert `Document` → domain models. All Jsoup CSS selectors are centralized there.
3. **Caching**: `CacheLoader` — `lruCached()` for volatile list data, `persistentCached()` for stable detail/genre data. Both use Gson serialization under the hood.
4. **Database**: Room with two databases:
   - `JBusDatabase`: history tracking
   - `CollectDatabase`: categories and link items (stored on SD card for persistence across reinstalls)

## Navigation Routes (Nav3)

| Route Key | Purpose |
|-----------|---------|
| `RouteMain` | Tab pager (home) |
| `RouteSearch(defaultSearchType)` | Search screen |
| `RouteMovieDetail(movieUrl)` | Movie detail |
| `RouteImageViewer(images, startIndex)` | Full-screen image viewer |
| `RouteLinkMovies(linkUrl, title, type, avatar)` | Actress/genre movie list |

## Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose + Material3 (BOM-managed) |
| DI | Hilt |
| Async | Kotlin Coroutines |
| Network | OkHttp 5.3 |
| HTML Parsing | Jsoup 1.22 |
| Database | Room 2.8 (KSP) |
| Image Loading | Coil 2.7 |
| Image Zoom | Telephoto 0.19 |
| JSON | Gson 2.14 |
| Navigation | Navigation 3 (1.1.1) |
| Debug | LeakCanary 2.14 (debugOnly) |

## Project Configuration

- **Package**: `me.jbusdriver` (namespace) / `me.jbus` (applicationId)
- **Compile SDK**: 37
- **Target SDK**: 36 / **Min SDK**: 28
- **Java target**: 17
- **ProGuard**: enabled for release (`isMinifyEnabled` + `isShrinkResources`)
- **Versioning**: `versionCode = 10000 + gitCommitCount`, `versionName = 1.<yyyyMMdd>`
- **Build variants**: debug (`.debug` suffix) and release (`.release` suffix)
- **Room schemas**: exported to `app/schemas/`
- **APK naming**: `jbus_{buildType}_v{versionName}.apk`

## Global State

- `JBus` (top-level `lateinit var` in `AppContext.kt`): Application context reference.
- `NetClient.defaultFastUrl`: Current base URL for the target site, switchable at runtime.
- `CacheLoader.lru` / `CacheLoader.fileCache`: Global caches (memory LRU + disk FileCache).

Add as a new ## Build & Commit Hygiene section near the top of CLAUDE.md, after any project description.\n\n## Build & Commit Hygiene
- After staging files, always run `git status` before committing to verify no unrelated files (e.g., `.mimicode/`, tool config) are included.
- Never commit directly to `release` branch — always work on `develop` or a feature branch.
- Run a build check (`./gradlew assembleDebug`) before committing to catch compilation errors.
  Add under a new ## Code Quality Rules section, after build/commit instructions.\n\n## Code Quality Rules
- Prefer StateFlow with `.cachedIn()` or `.stateIn()` over raw Flow for ViewModel state holders to avoid recomputation and flickering.
- When refactoring multiple files, keep changes minimal and targeted — do not redesign UI the user didn't ask for.
- After any Gson/ProGuard changes, verify both debug and release builds parse sample JSON correctly.
- When removing or renaming a data field, add backward-compatible @SerializedName aliases for old field names.
  Add under a new ## Android / Kotlin Conventions section.\n\n## Android / Kotlin Conventions
- This is an Android app using Jetpack Compose, Material 3, Kotlin, Gson, and DataStore.
- Verify Compose API calls compile before finalizing — do not guess package paths for inlineContent, SubcomposeAsyncImage, or similar APIs.
- When implementing caching (SWR or similar), test for: scroll position retention, duplicate key safety, and tab-switch refresh behavior.
- ProGuard/R8 keep rules must cover all Gson model classes — add `@Keep` or rules proactively.
  Add under a new ## Workflow Preferences section at the top of CLAUDE.md so Claude sees it immediately.\n\n## Workflow Preferences
- Do NOT start implementing until I've confirmed the design or approach. Wait for explicit approval.
- When I propose a feature, first present a brief plan (affected files, approach, trade-offs) before writing code.
- If a first implementation is complex, proactively suggest a simpler alternative before I have to ask.
- After completing a feature, check for and remove any dead code or unused parameters from the old approach.