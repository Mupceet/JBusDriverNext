# Code Audit & Annotation Design

**Date**: 2026-05-01
**Status**: Approved
**Scope**: All 74 source files under `me.jbusdriver.modern`

## Goals

1. Add comprehensive documentation: KDoc class comments, method docs, and inline comments
2. Remove dead code and simplify unnecessary abstractions
3. Migrate all RxJava usage to Kotlin Coroutines
4. Ensure correct thread dispatching (IO/Main/Default)

## Approach

**Method A: File-by-file deep refactoring, bottom-up by dependency layer.**

Process each file: full documentation + thread correction + dead code cleanup + RxJava→Coroutines migration.

## Processing Order

| Phase | Package | Files | Focus |
|-------|---------|-------|-------|
| 1 | `core/` | 11 | Infrastructure: cache, network, logging, constants |
| 2 | `domain/model/` | 9 | Pure data models, no async logic |
| 3 | `data/` (db, magnet, remote, di, repositories) | 26 | Data layer: DAO, service, network, DI, repositories |
| 4 | `ui/` | 25 | UI layer: Screen + ViewModel |
| 5 | Top-level files | 3 | `ModernMainActivity`, `Navigation`, `MainScreen` |

## Per-File Actions

Every file receives these actions in order:

1. **KDoc class comment**: responsibility, usage scenario, thread requirements
2. **Method documentation**: parameters, return values, side effects
3. **Inline comments**: only the "why" of non-obvious logic
4. **Thread correction**: ensure IO operations use `Dispatchers.IO`, UI uses `Main`, computation uses `Default`
5. **RxJava→Coroutines migration**: replace all RxJava types and operators
6. **Dead code removal**: delete unreferenced functions, obsolete logic, redundant wrappers

### What We Don't Do

- Change public API semantics (inputs/outputs stay the same)
- Add new features
- Change UI layout or styling

## Threading Model

Standard three-dispatcher model:

| Operation Type | Dispatcher | Specific Scenarios |
|---------------|-----------|-------------------|
| IO | `Dispatchers.IO` | Network (Retrofit), database (Room DAO), disk (ACache), HTML parsing (Jsoup) |
| Main | `Dispatchers.Main` | Compose UI updates, ViewModel state emission (`_state.value = ...`) |
| Default | `Dispatchers.Default` | None (no compute-heavy operations in this project) |

### Key Constraints

- Repository methods are `suspend` — they don't switch threads internally (caller controls dispatching)
- Room DAO methods returning `Flow` are not `suspend` (Room handles threading)
- `CacheLoader` disk read/write wrapped in `withContext(Dispatchers.IO)`
- Remove all RxJava dependencies from `build.gradle.kts`

## RxJava → Coroutines Migration Mapping

| RxJava Original | Coroutines Target | Notes |
|----------------|-------------------|-------|
| `Observable<T>` | `Flow<T>` | Streaming data (DB change observation) |
| `Flowable<T>` | `Flow<T>` | Same, Room natively supports |
| `Single<T>` | `suspend fun(): T` | One-shot requests (network, single query) |
| `Completable` | `suspend fun()` | No-return operations (insert, delete) |
| `Disposable` | `Job` / `CoroutineScope` | Lifecycle management |
| `subscribeOn(io())` | `withContext(Dispatchers.IO)` | Thread switching |
| `observeOn(mainThread())` | Automatic (Room/Flow guarantee) | Room Flow collected on Main by default |

### DAO Migration Example

```kotlin
// Before: RxJava
@Query("SELECT * FROM history ORDER BY createTime DESC")
fun getAll(): Flowable<List<History>>

// After: Coroutines + Flow
@Query("SELECT * FROM history ORDER BY createTime DESC")
fun getAll(): Flow<List<History>>
```

### Repository Migration Example

```kotlin
// Before: RxJava chain
fun loadMovies(url: String): Single<MoviePageResult> =
    Single.fromCallable { fetchAndParse(url) }
        .subscribeOn(Schedulers.io())

// After: Coroutines
suspend fun loadMovies(url: String): MoviePageResult =
    withContext(Dispatchers.IO) { fetchAndParse(url) }
```

### ViewModel Unified Pattern

```kotlin
@HiltViewModel
class XxxViewModel @Inject constructor(
    private val repository: XxxRepository
) : ViewModel() {

    private val _state = MutableStateFlow<XxxUiState>(XxxUiState.Loading)
    val state: StateFlow<XxxUiState> = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = XxxUiState.Loading
            try {
                val result = withContext(Dispatchers.IO) { repository.fetch() }
                _state.value = XxxUiState.Success(result)
            } catch (e: Exception) {
                _state.value = XxxUiState.Error(e.message)
            }
        }
    }
}
```

## Documentation Standards

### Class-Level KDoc (required for every class)

```kotlin
/**
 * 职责：One-line description of what this class does
 *
 * 使用场景：When to use, who references it
 * 线程：Which Dispatcher it runs on (if async)
 *
 * @param xxx Constructor parameter descriptions (if any)
 */
class XxxRepository @Inject constructor(...) {
```

### Method-Level KDoc (required for public methods, optional for private)

```kotlin
/**
 * Fetch movie list from remote and parse HTML
 *
 * Note: suspend function, must be called from coroutine scope
 * @param url Target page URL
 * @return Parsed paginated result
 * @throws IOException When network request fails
 */
suspend fun loadMovies(url: String): MoviePageResult
```

### Inline Comments (only "why", never "what")

```kotlin
// Jsoup selectors depend on page structure — update if website changes
val items = doc.select("div.item")

// Two-tier cache: check memory first, on miss read disk and backfill memory
val cached = lruCache.get(key) ?: acache.get(key)?.also { lruCache.put(key, it) }
```

## Dead Code Criteria

A code element is removed if **any** condition is met:

| Condition | Verification Method | Example |
|-----------|-------------------|---------|
| Unreferenced public function | IDE "Find Usages" returns empty | Unused extension function |
| Commented-out code blocks | Search for `// fun`, `// val` | Legacy implementation remnants |
| Empty implementation/interface | Body is only `TODO()` or empty | `SettingsRepository` empty interface |
| RxJava remnants after migration | No longer needed imports/utilities | `Disposable` management, `CompositeDisposable` |
| Obsolete delegation wrapper | Internally calls one method with no extra logic | `CategoryService` meaningless wrapper |
| Redundant type cast | `as` cast to same actual type | Unnecessary forced cast |

### Code We Don't Touch

- `ACache.java` — stable disk cache implementation, add KDoc only
- `LoggerInterceptor.java` — OkHttp interceptor, clear logic, add KDoc only
- `theme/*.kt` — pure declarative theme files, no changes needed

## File-Level Processing Plan

### Phase 1: core/ (11 files)

| File | Actions | Details |
|------|---------|---------|
| `ACache.java` | KDoc only | Stable implementation, no internal changes |
| `LoggerInterceptor.java` | KDoc only | Same |
| `AppContext.kt` | Docs + cleanup | Remove RxJava error handler, replace with CoroutineExceptionHandler |
| `JBusApplication.kt` | Docs | Already uses Coroutines, documentation only |
| `KLog.kt` | Docs | Simple utility class |
| `BaseExtension.kt` | Docs + split | Mixed extensions — split into `ContextExt.kt`, `GsonExt.kt`, `NetExt.kt` |
| `C.kt` | Docs + cleanup | Remove unused constants |
| `CacheLoader.kt` | Docs + rewrite | RxJava→Coroutines, wrap disk ops in `withContext(IO)` |
| `Gobal.kt` | Docs + rename | Fix spelling (Gobal→Global), content already uses Coroutines |
| `JBusManager.kt` | Docs | Activity lifecycle management |
| `SDCardDatabaseContext.kt` | Docs | SD card database path |
| `NetClient.kt` | Docs + cleanup | Remove RxJava-related config, pure OkHttp/Retrofit |

### Phase 2: domain/model/ (9 files)

| File | Actions | Details |
|------|---------|---------|
| `Bean.kt` | Docs + split | Split into `Bean.kt`(data classes) + `BeanTransform.kt`(parsing) + `Converters.kt`(converters) |
| `Category.kt` | Docs | Default category definitions |
| `DataSourceType.kt` | Docs | Enum |
| `ICollectCategory.kt` | Docs | Interface |
| `ILink.kt` | Docs | Interface |
| `Movie.kt` | Docs | Data model |
| `MovieDetail.kt` | Docs | Data model |
| `SearchType.kt` | Docs | Enum |
| `BeanTransform.kt` | Docs | HTML parsing core, add Jsoup selector documentation |

### Phase 3: data/ (26 files)

| File | Actions | Details |
|------|---------|---------|
| **DAO** | | |
| `dao/CategoryDao.kt` | Docs + migrate | `Flowable`→`Flow`, `Single`→`suspend` |
| `dao/HistoryDao.kt` | Docs + migrate | Same |
| `dao/LinkItemDao.kt` | Docs + migrate | Same |
| **Entity** | | |
| `entity/Category.kt` | Docs | Room entity |
| `entity/History.kt` | Docs | Room entity |
| `entity/LinkItem.kt` | Docs | Room entity |
| **Service** | | |
| `service/CategoryService.kt` | Evaluate | If only delegates to DAO, inline into Repository |
| `service/LinkService.kt` | Evaluate | Same |
| **Database** | | |
| `DB.kt` | Docs | Database entry point |
| `CollectDatabase.kt` | Docs | Room DB definition |
| `JBusDatabase.kt` | Docs | Room DB definition |
| **Remote** | | |
| `JAVBusService.kt` | Docs + migrate | Return types from `Single`→`suspend` |
| **DI** | | |
| `DataModule.kt` | Docs | Hilt bindings |
| `DatabaseModule.kt` | Docs | Hilt database providers |
| `di/NetworkModule.kt` | Docs + cleanup | Remove RxJava adapter |
| **Magnet** | | |
| `Magnet.kt` | Docs | Data model |
| `MagnetManager.kt` | Docs | Loader registry |
| `IMagnetLoader.kt` | Docs + migrate | Interface methods to `suspend` |
| `Configuration.kt` | Docs | Configuration |
| `loaders/DefaultLoaderImpl.kt` | Docs + migrate | Java Thread→`suspend` + `withContext(IO)` |
| `loaders/MagnetLoaders.kt` | Docs | Loader enum |
| `loaders/WebViewHtmlContentLoader.kt` | Docs + migrate | WebView callback→`suspendCancellableCoroutine` |
| **Repositories** | | |
| `CollectRepository.kt` | Docs + migrate | RxJava→Coroutines |
| `MovieRepository.kt` | Docs + migrate + split | 227 lines too large, split into `MovieRepository` + `ActressRepository` + `GenreRepository` |
| `MovieDetailRepository.kt` | Docs + migrate | RxJava→Coroutines |
| `SearchRepository.kt` | Docs + migrate | RxJava→Coroutines |
| `SettingsRepository.kt` | Evaluate | Empty interface — consider deleting or merging |
| `MoviePageResult.kt` | Docs | Data model |

### Phase 4: ui/ (25 files)

| File | Actions | Details |
|------|---------|---------|
| **ViewModels** (8 files) | Docs + unify | Standardize to `StateFlow` + `viewModelScope.launch` pattern, remove RxJava remnants |
| `detail/MovieDetailScreen.kt` | Docs + split | 502 lines too large, split into `MovieDetailScreen` + `MagnetBottomSheet` + `MovieInfoSection` |
| Other Screen files | Docs | UI component documentation |
| `components/ActressAvatar.kt` | Docs | Compose component |
| `theme/*.kt` | No change | Pure declarative, no changes needed |
| `Navigation.kt` | Docs | Route graph documentation |
| `NavigationKeys.kt` | Docs | Route constants |
| `MainScreen.kt` | Docs | Main scaffold |
| `ModernMainActivity.kt` | Docs | Entry point |
| `UiModels.kt` | Docs + cleanup | UI state models, remove unused states |

### Dependency Changes

Remove from `build.gradle.kts`:
- `libs.reactivex.rxjava3`
- `libs.reactivex.rxandroid`
- `libs.reactivex.rxkotlin` (if present)
- Retrofit RxJava adapter (`com.squareup.retrofit2:adapter-rxjava3`)

## Summary

- ~60 files need changes, ~14 files get documentation only or no changes
- Core change: complete RxJava→Coroutines migration
- Major file splits: `BaseExtension.kt`, `Bean.kt`, `MovieRepository.kt`, `MovieDetailScreen.kt`
- File rename: `Gobal.kt` → `Global.kt`
- Result: fully documented, consistently threaded, no dead code
