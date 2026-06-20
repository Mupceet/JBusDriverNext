# AGENTS.md

This file is the shared source of truth for coding agents working in this repository.
Claude Code reads `CLAUDE.md`, which imports this file with `@AGENTS.md`.

## Workflow Preferences

- Do not start implementing broad feature work until the design or approach is confirmed. First present a brief plan with affected files, approach, and trade-offs.
- If a first implementation looks complex, proactively suggest a simpler alternative before coding.
- After completing a feature, check for and remove dead code or unused parameters from the old approach.
- When the user explicitly asks for a concrete edit, cleanup, or maintenance task, make a reasonable scoped change and verify it.

## Build & Commit Hygiene

- After staging files, always run `git status` before committing to verify no unrelated files (for example `.mimocode/` or tool config) are included.
- Never commit directly to `release`; work on `develop` or a feature branch.
- Run a build check (`./gradlew assembleDebug`) before committing to catch compilation errors.

## Code Quality Rules

- ViewModels must not expose callbacks to the UI; expose state and one-shot events as Flow/StateFlow/SharedFlow instead.
- Prefer StateFlow with `.cachedIn()` or `.stateIn()` over raw Flow for ViewModel state holders when it avoids recomputation and flickering.
- When refactoring multiple files, keep changes minimal and targeted; do not redesign UI the user did not ask for.
- After any Gson, ProGuard, or R8 changes, verify debug and release behavior for representative JSON payloads.
- When removing or renaming a data field, add backward-compatible `@SerializedName` aliases for old field names where Gson compatibility matters.

## Android / Kotlin Conventions

- This is an Android app using Jetpack Compose, Material 3, Kotlin, Gson, and DataStore.
- Verify Compose API calls compile before finalizing; do not guess package paths for `inlineContent`, `SubcomposeAsyncImage`, or similar APIs.
- When implementing caching or stale-while-revalidate behavior, test scroll position retention, duplicate key safety, and tab-switch refresh behavior.
- ProGuard/R8 keep rules must cover all Gson model classes; add `@Keep` or rules proactively.

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
  KLog.kt                   - Logging utility
  core/
    GsonExt.kt              - GSON instance, generic fromJson/toJson extensions
    BaseExtension.kt        - SharedPreferences, Context extensions
    cache/CacheStore.kt     - Hilt-backed two-tier cache: LruCache (memory) + FileCache (disk)
    FileCache.kt            - Disk cache implementation (replaces former ACache.java)
    FileUtil.kt             - File size formatting helpers
    C.kt                    - Constants (cache durations, component names)
    http/
      NetClient.kt          - OkHttp singleton, fetchDocument() for HTML→Jsoup parsing
  data/
    db/
      DBTypes.kt            - DB type constants (MovieDBType, ActressDBType)
      LinkMappers.kt        - DB entity ↔ domain model mappers
      JBusDatabase.kt       - Room DB for history tracking
      CollectDatabase.kt    - Room DB for categories/link items
      dao/                  - CategoryDao, HistoryDao, LinkItemDao
      entity/               - Category, History, LinkItem (Room entities)
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
- **Two-tier cache**: `CacheStore.lruCached()` (memory only) for lists; `CacheStore.persistentCached()` (memory + disk) for details
- **Coroutines throughout**: All repositories use `suspend` functions, no RxJava

## Data Flow

1. **Network**: `HtmlClient` / `NetClient` fetch HTML via OkHttp and parse to Jsoup `Document`. Runtime base URL is owned by `SiteConfig`.
2. **Parsing**: Top-level functions in `HtmlParser.kt` convert `Document` → domain models. All Jsoup CSS selectors are centralized there.
3. **Caching**: `CacheStore` — `lruCached()` for volatile list data, `persistentCached()` for stable detail/genre data. Both use Gson serialization under the hood.
4. **Database**: Room with two databases:
   - `JBusDatabase`: history tracking
   - `CollectDatabase`: categories and link items, built through the Hilt database module

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
| Network | OkHttp 5.4 |
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

- `SiteConfig`: Hilt-managed runtime base URL for the target site.
- `DefaultCacheStore`: Hilt-managed memory + disk cache using `@ApplicationContext`.

## Testing

```bash
# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Run a specific test class
./gradlew test --tests "me.jbusdriver.modern.ui.movielist.MovieListViewModelTest"
```

Test files are in `app/src/test/` (unit) and `app/src/androidTest/` (instrumented). Tests use JUnit4 + kotlinx-coroutines-test. ViewModel tests inject fake repositories via Hilt test modules.

## Code Review Notes

See `docs/CODE_REVIEW.md` for the full code review report. Key findings:

### Current Status
- No current P0/P1 correctness issue is known from the latest review.
- Phase A/B/C remediation is closed for: `SiteConfig.awaitReady()`, site-aware cache keys, request identity/race guards, forum WebView session synchronization, collection transaction boundaries, JVM URL parsing, Hilt database entry points, platform IO gateway boundaries, and movielist/forum SWR reducers.
- The latest quality gates used were `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleRelease`.

### Remaining Non-Blocking Technical Debt
- The former `JBus`, `JBusManager`, `NetClient.defaultFastUrl`, and `CacheLoader` global entry points have been removed from production code. Prefer Hilt `@ApplicationContext`, `SiteConfig`, `WebViewFactory`, and `CacheStore` for new code.
- UI i18n is only partially complete. New visible UI strings, Toast messages, dialog labels, and content descriptions should use resources; count labels should use plurals.
- Several files remain large, including `MovieList.kt`, `ForumPostContent.kt`, `LinkMovieListViewModel.kt`, `LinkMovieListScreen.kt`, `MovieDetailScreen.kt`, `MovieListViewModel.kt`, `LabSettingsScreen.kt`, `MovieRepository.kt`, `ForumBoardsScreen.kt`, and `ForumThreadDetailViewModel.kt`. Prefer small section/helper extraction when touching those files.
- ViewModel `loadFirstPage/revalidate/loadMore/refresh` orchestration still repeats across list-style screens. Reducers already cover state transitions; avoid broad abstraction until a stable shared shape is obvious.
- If release minify/Gson/forum rich-text code changes, add or run a release smoke test for JSON deserialization and `ContentBlock` payloads.

### Data Flow (Stale-While-Revalidate)
List and forum screens use the same stale-while-revalidate pattern via `CacheStore.observeCached()`:
1. Emit `CachedLoadEvent.Cached` from memory/disk cache (immediate)
2. Background fetch emits `CachedLoadEvent.Fresh` (new data)
3. Reducer/ViewModel logic applies fresh data immediately when appropriate, or stores pending fresh data while the user is away from the top

### Navigation Routes (Nav3)

| Route Key | Purpose |
|-----------|---------|
| `RouteMain` | Tab pager (home) |
| `RouteSearch(defaultSearchType)` | Search screen |
| `RouteMovieDetail(movieUrl, censorType)` | Movie detail |
| `RouteImageViewer(images, startIndex)` | Full-screen image viewer |
| `RouteLinkMovies(linkUrl, title, type, avatar, censorType)` | Actress/genre movie list |
| `RouteForumThreadList(fid, title, typeId)` | Forum thread list |
| `RouteForumThreadDetail(tid)` | Forum thread detail |
| `RouteLabSettings` | Lab/experimental settings |

### Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose + Material3 (BOM-managed) |
| DI | Hilt |
| Async | Kotlin Coroutines |
| Network | OkHttp 5.4 |
| HTML Parsing | Jsoup 1.22 |
| Database | Room 2.8 (KSP) |
| Image Loading | Coil 2.7 |
| Image Zoom | Telephoto 0.19 |
| JSON | Gson 2.14 |
| Navigation | Navigation 3 (1.1.1) |
| Preferences | DataStore Preferences |
| Debug | LeakCanary 2.14 (debugOnly) |
