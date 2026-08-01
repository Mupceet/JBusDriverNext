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
- Data-layer `suspend` functions must be main-safe: the data layer owns thread switching (`withContext(Dispatchers.IO/Default)` or main-safe libraries such as Room/DataStore/OkHttp), so callers can invoke them from `viewModelScope.launch` on the Main thread without extra `withContext`.
- ViewModels must not hardcode `Dispatchers.IO`/`Dispatchers.Default`; when background work is needed (e.g. Gson deserialization, list filter/sort), inject the `@IoDispatcher`-qualified dispatcher (or a test-injectable dispatcher) so tests can substitute a `TestDispatcher`.

## Android / Kotlin Conventions

- This is an Android app using Jetpack Compose, Material 3, Kotlin, Gson, DataStore, and kotlinx-serialization (for Nav3 route keys).
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

The project uses Kotlin DSL (`build.gradle.kts`) with a version catalog at `gradle/libs.versions.toml`. AGP 9.2.1 provides built-in Kotlin compilation — no separate `kotlin-android` plugin. KSP is used for annotation processing (Room compiler, Hilt compiler).

## Architecture

**MVVM with Jetpack Compose** and Hilt dependency injection. All code is in the `me.jbusdriver.modern` package.

### Package Structure

```
me.jbusdriver.modern/
  JBusApplication.kt          - @HiltAndroidApp entry point, provides Coil ImageLoader
  KLog.kt                      - Logging utility
  core/
    GsonExt.kt                 - GSON instance + generic fromJson/toJson extensions
    BaseExtension.kt           - SharedPreferences, Context extensions
    FileCache.kt               - Disk cache implementation (replaces former ACache.java)
    FileUtil.kt                - File size formatting helpers
    LogDiff.kt                 - Logging diff helpers
    cache/
      CacheStore.kt            - Interface + DefaultCacheStore (LruCache memory + FileCache disk);
                                 lruCached()/persistentCached()/observeCached() SWR extensions
      CacheModels.kt           - CacheEntry/CacheEnvelope/CachedLoadEvent models
      PagedSwrState.kt         - Reusable paged stale-while-revalidate state holder
    http/
      NetClient.kt             - OkHttp client singleton + cookie/auth handling
      HtmlClient.kt            - HTML fetch (OkHttp, WebView session fallback) -> Jsoup Document
      WebViewFactory.kt        - WebView creation for anti-bot page bypass
      WebViewHelper.kt         - WebView session/eval helpers
      BrowserSessionManager.kt - Shared browser session lifecycle
      BrowserSessionClient.kt  - Session cookie/token client
      ExistMag.kt              - Magnet existence checks
    serialization/
      ContentBlockJsonAdapter.kt - Gson adapter for forum rich-text ContentBlocks
    site/
      SiteConfig.kt            - Hilt-managed runtime base URL for the target site
  data/
    cache/
      SiteCacheKey.kt          - Site-aware cache key helpers
    db/
      DBTypes.kt               - DB type constants (MovieDBType, ActressDBType)
      LinkMappers.kt           - DB entity <-> domain model mappers
      JBusDatabase.kt          - Room DB for history tracking
      CollectDatabase.kt       - Room DB for categories/link items
      dao/                     - CategoryDao, HistoryDao, LinkItemDao
      entity/                  - Category, History, LinkItem (Room entities)
    parser/
      MovieHtmlParser.kt       - Movie list/detail parsing (loadMovieFromDoc, parseMovieDetails)
      ActressHtmlParser.kt     - Actress list parsing
      GenreHtmlParser.kt       - Genre parsing
      ForumHomeParser.kt       - Forum home/boards parsing
      ForumThreadParser.kt     - Forum thread list parsing
      ForumPostParser.kt       - Forum post/floor parsing
      InlineParagraphParser.kt - Inline rich-text paragraph parsing
      InlineStyle.kt           - Inline style model
      UrlParserExt.kt          - URL/anchor parsing helpers
    gateway/
      CollectionDocumentGateway.kt - Document gateway for collection pages
      ImageMediaGateway.kt         - Image/media gateway
    mirror/
      MirrorScanner.kt         - Mirror site availability scanning
    repository/
      MovieRepository.kt          - Interface + DefaultMovieRepository (OkHttp + Jsoup + cache)
      MovieDetailRepository.kt    - Interface + DefaultMovieDetailRepository
      MoviePageFetcher.kt         - Paged movie page fetching
      MovieRepositoryCacheKeys.kt - Cache key builders for movie data
      MovieRepositoryUrls.kt      - URL builders for movie pages
      CollectRepository.kt        - Interface + DefaultCollectRepository (Room-backed)
      CollectTransactionRunner.kt - Transactional collection writes
      CollectionBackupCodec.kt    - Import/export codec for collections
      SearchRepository.kt         - Interface + DefaultSearchRepository
      MagnetRepository.kt         - Magnet link resolution (interface + impl)
      ForumRepository.kt          - Forum data repository
    session/
      GifLoadTracker.kt       - Tracks GIF load progress/state
    settings/
      AppSettingsStore.kt     - DataStore-backed app settings
      UiPrefsStore.kt         - DataStore-backed UI preferences
      ThemeRepository.kt      - Theme mode repository
      ThemeMode.kt            - Theme mode enum
      MovieListStyle.kt       - List style enum
      MovieLoadMode.kt        - Load mode enum
      SearchHistoryStore.kt   - DataStore-backed search history
      ForumFloorOrder.kt      - Forum floor ordering preference
    di/
      DataModule.kt           - @Binds Repository interfaces -> implementations
      DatabaseModule.kt       - @Provides Room DB instances and DAOs
  domain/model/
    Movie.kt, MovieDetail.kt     - Core models (Movie, MovieDetail; Header, Genre, ActressInfo,
                                   ImageSample, ActressAttrs are all defined in MovieDetail.kt)
    ILink.kt, PageLink.kt        - Link abstraction and pagination
    Magnet.kt, SearchType.kt, DataSourceType.kt, Category.kt
    UrlExt.kt                    - URL path extension property
    MoviePageResult.kt           - Paginated result wrapper
    ForumModels.kt               - Forum domain models
    GenreGroup.kt                - Grouped genre model
  ui/
    ModernMainActivity.kt     - Single Activity (@AndroidEntryPoint, edge-to-edge, Compose host)
    MainScreen.kt             - Bottom navigation: 影片(Movie) / 演员(Actress) / 论坛(Forum) / 收藏(Collect)
    MainTabContent.kt         - Per-tab content host
    Navigation.kt             - Nav3 graph with iOS-style transitions
    NavigationKeys.kt         - Route NavKey objects (RouteMain, RouteSearch, ...)
    UiModels.kt               - Shared UI state models (MovieUiModel, ActressUiModel, GenreUiModel)
    UserMessage.kt            - One-shot UI message model
    components/               - Reusable composables (MovieList, MovieListItems, ActressGrid,
                                ActressAvatar, AppAsyncImage, SearchBar, MovieFilterBar,
                                CollectButton, ShareButton, CategoryBottomSheet, etc.)
    detail/                   - Movie detail screen + ViewModel (MovieDetailScreen/ViewModel,
                                MovieDetailSections, MagnetBottomSheet)
    image/                    - Full-screen image viewer (ImageViewScreen, ImageActionsViewModel;
                                Telephoto zoomable)
    movielist/                - Movie/actress/genre/collection list screens + ViewModels +
                                StateReducers (MovieList, ActressList, GenreList, Collection,
                                LinkMovieList, CollectCategory)
    forum/                    - Forum boards/thread list/detail screens + ViewModels + StateReducers
                                (ForumBoards, ForumThreadList, ForumThreadDetail) + ForumPostContent
    search/                   - Search screen + ViewModel
    settings/                 - Settings screen + ViewModel (SettingsScreen, SettingsViewModel,
                                ThemeViewModel, UiPrefsViewModel)
    debug/                    - Color scheme debug screen
    localvideo/               - Local video sheet (SAF DocumentFile)
    theme/                    - Material3 theme (Theme.kt, Type.kt)
```

### Key Patterns

- **Single Activity**: `ModernMainActivity` with `enableEdgeToEdge()` hosts all Compose UI
- **Hilt DI**: ViewModels use `hiltViewModel()` (from `hilt-navigation-compose`), repositories are interface+impl pairs bound via `@Binds` in `DataModule`. Navigation-arg ViewModels use `@AssistedInject` + `@AssistedFactory`.
- **Repository pattern**: Each screen has a ViewModel that delegates to a Hilt-provided repository
- **HTML scraping**: `HtmlClient` / `NetClient` fetch HTML (OkHttp, with a WebView session fallback for anti-bot pages) into a Jsoup `Document`, which top-level functions split across `data/parser/*HtmlParser.kt` convert into domain models
- **Two-tier cache**: `CacheStore.lruCached()` (memory only) for lists; `CacheStore.persistentCached()` (memory + disk) for details; `CacheStore.observeCached()` for stale-while-revalidate flows
- **Coroutines throughout**: All repositories use `suspend` functions, no RxJava

## Data Flow

1. **Network**: `HtmlClient` / `NetClient` fetch HTML via OkHttp and parse to Jsoup `Document`. Runtime base URL is owned by `SiteConfig`.
2. **Parsing**: Top-level functions in the `data/parser/` files (`*HtmlParser.kt`) convert `Document` -> domain models; Jsoup CSS selectors live in those per-domain parser files.
3. **Caching**: `CacheStore` — `lruCached()` for volatile list data, `persistentCached()` for stable detail/genre data, `observeCached()` for SWR flows. All use Gson serialization under the hood.
4. **Database**: Room with two databases:
   - `JBusDatabase`: history tracking
   - `CollectDatabase`: categories and link items, built through the Hilt database module

## Navigation Routes (Nav3)

Routes are `@Serializable NavKey` objects defined in `NavigationKeys.kt`.

| Route Key | Purpose |
|-----------|---------|
| `RouteMain` | Home (bottom navigation) |
| `RouteSearch(defaultSearchType)` | Search screen |
| `RouteMovieDetail(movieUrl, censorType)` | Movie detail |
| `RouteImageViewer(images, startIndex)` | Full-screen image viewer |
| `RouteLinkMovies(linkUrl, title, type, avatar, censorType)` | Actress/genre movie list |
| `RouteForumThreadList(fid, title, typeId)` | Forum thread list |
| `RouteForumThreadDetail(tid)` | Forum thread detail |
| `RouteSettings` | Settings screen |

## Key Libraries

| Purpose | Library |
|---------|---------|
| UI | Jetpack Compose + Material3 (BOM-managed) |
| DI | Hilt |
| Async | Kotlin Coroutines |
| Network | OkHttp 5.4.0 |
| HTML Parsing | Jsoup 1.22.2 |
| Database | Room 2.8.4 (KSP) |
| Image Loading | Coil 2.7.0 |
| Image Zoom | Telephoto 0.19.0 |
| Animation | Lottie Compose 6.7.1 |
| JSON | Gson 2.14.0 |
| Serialization | kotlinx-serialization (pinned to 1.8.1; used for Nav3 route keys) |
| Navigation | Navigation 3 (1.1.4) |
| Preferences | DataStore Preferences 1.2.1 |
| Storage Access | DocumentFile 1.1.0 |
| Debug | LeakCanary 2.14 (debugOnly) |

## Project Configuration

- **Package**: `me.jbusdriver` (namespace) / `me.jbus` (applicationId)
- **Compile SDK**: 37
- **Min SDK**: 28 / **Target SDK**: not pinned (uses compileSdk)
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

Test files are in `app/src/test/` (unit) and `app/src/androidTest/` (instrumented). Tests use JUnit4 + kotlinx-coroutines-test. ViewModel tests inject fake repositories via Hilt test modules. Connected (instrumented) tests require a running emulator or device.

## Code Review Notes

See `docs/CODE_REVIEW.md` for the full code review report. Key findings:

### Current Status
- No current P0/P1 correctness issue is known from the latest review.
- Phase A/B/C remediation is closed for: `SiteConfig.awaitReady()`, site-aware cache keys, request identity/race guards, forum WebView session synchronization, collection transaction boundaries, JVM URL parsing, Hilt database entry points, platform IO gateway boundaries, and movielist/forum SWR reducers.
- Phase D (2026-07-31) closed: `JAVBUS_AUTH_COOKIE` build support removed (no longer embeds a session cookie in release APKs), Forum/Magnet repositories await `SiteConfig` before building URLs, remaining hardcoded UI strings migrated to resources, `CancellationException` rethrow made consistent, `loadMore` race guarded, `logListDiff`/`FileCache`/deep-link hardening, and collection observation queries pushed into SQL.
- The latest quality gates used were `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleRelease`.

### Remaining Non-Blocking Technical Debt
- The former `JBus`, `JBusManager`, `NetClient.defaultFastUrl`, and `CacheLoader` global entry points have been removed from production code. Prefer Hilt `@ApplicationContext`, `SiteConfig`, `WebViewFactory`, and `CacheStore` for new code.
- UI i18n is complete for existing screens. Keep using string resources for new visible UI strings, Toast messages, dialog labels, and content descriptions; use plurals for count labels. Server/domain-provided titles and category names stay as-is.
- Several files remain large, including `MovieList.kt`, `ForumPostContent.kt`, `LinkMovieListViewModel.kt`, `LinkMovieListScreen.kt`, `MovieDetailScreen.kt`, `MovieListViewModel.kt`, `SettingsScreen.kt`, `MovieRepository.kt`, `ForumBoardsScreen.kt`, and `ForumThreadDetailViewModel.kt`. Prefer small section/helper extraction when touching those files.
- ViewModel `loadFirstPage/revalidate/loadMore/refresh` orchestration still repeats across list-style screens. Reducers already cover state transitions; avoid broad abstraction until a stable shared shape is obvious.
- If release minify/Gson/forum rich-text code changes, add or run a release smoke test for JSON deserialization and `ContentBlock` payloads.

### Data Flow (Stale-While-Revalidate)
List and forum screens use the same stale-while-revalidate pattern via `CacheStore.observeCached()`:
1. Emit `CachedLoadEvent.Cached` from memory/disk cache (immediate)
2. Background fetch emits `CachedLoadEvent.Fresh` (new data)
3. Reducer/ViewModel logic applies fresh data immediately when appropriate, or stores pending fresh data while the user is away from the top
