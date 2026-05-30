# Android MAD Code Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the highest-risk findings from the Android MAD code review, then tighten module boundaries, tests, storage, and security defaults.

**Architecture:** The first pass keeps behavior stable and fixes concrete defects in session/WebView lifecycle, URL generation, collection persistence, and cache correctness. The second pass moves global object access behind injectable interfaces, reduces UI-to-data coupling, and adds regression tests around previously untested seams.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, OkHttp, Jsoup, Coil, Coroutines, JUnit4, Android instrumented tests when device-only APIs are involved.

---

## File Structure Map

**Immediate repair files:**
- `app/src/main/java/me/jbusdriver/modern/core/http/BrowserSessionClient.kt`: browser session contract.
- `app/src/main/java/me/jbusdriver/modern/core/http/WebViewHelper.kt`: WebView coroutine cleanup helpers.
- `app/src/main/java/me/jbusdriver/modern/data/ForumSessionClient.kt`: session adapter.
- `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`: hidden WebView lifetime and restored-cookie path.
- `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt`: activity-level session cleanup.
- `app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt`: search URL encoding.
- `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`: collection movie date consistency.
- `app/src/main/java/me/jbusdriver/modern/data/db/entity/LinkItem.kt`: collection uniqueness.
- `app/src/main/java/me/jbusdriver/modern/data/db/CollectDatabase.kt`: schema version.
- `app/src/main/java/me/jbusdriver/modern/data/db/DB.kt`: Room migration.
- `app/src/main/java/me/jbusdriver/modern/core/FileCache.kt`: cache key hashing.

**Immediate test files:**
- `app/src/test/java/me/jbusdriver/modern/data/SearchRepositoryUrlTest.kt`
- `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`
- `app/src/test/java/me/jbusdriver/modern/data/db/LinkItemEntityTest.kt`
- `app/src/test/java/me/jbusdriver/modern/core/FileCacheTest.kt`

**Supplemental cleanup files:**
- `app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt`
- `app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt`
- `app/src/main/java/me/jbusdriver/modern/core/cache/DefaultCacheStore.kt`
- `app/src/main/java/me/jbusdriver/modern/core/cache/CacheStore.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryViewModel.kt`
- `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`

---

## Repair Plan

### Task 1: Fix restored-cookie forum session crash

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`
- Verify: `./gradlew.bat :app:compileDebugKotlin --console=plain`

- [ ] **Step 1: Add a helper that creates the hidden WebView without forcing a warm page load**

Add this private method inside `ForumSessionManager`, near `initWebView`:

```kotlin
    private suspend fun ensureWebViewCreated() {
        if (webView != null) return
        withContext(Dispatchers.Main) {
            if (webView == null) {
                webView = WebViewHelper.createWebView()
            }
        }
    }
```

- [ ] **Step 2: Use the helper when cookies are restored**

Replace the valid-cookie branch in `ensureSession(activity: Activity)` with:

```kotlin
            if (cookieStore.isSessionValid(url)) {
                cookieStore.restoreCookies(url)
                ensureWebViewCreated()
                initialized.set(true)
                KLog.d("[Forum] Session restored from persisted cookies", TAG)
                return
            }
```

- [ ] **Step 3: Compile and confirm the session path is still type-safe**

Run:

```bash
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 2: Clean up WebView coroutine cancellation and timeout paths

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/core/http/WebViewHelper.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`
- Verify: `./gradlew.bat :app:compileDebugKotlin --console=plain`

- [ ] **Step 1: Add cancellation cleanup to `WebViewHelper.loadUrlAwait`**

Inside the `suspendCancellableCoroutine` block, after assigning `webViewClient`, add:

```kotlin
                cont.invokeOnCancellation {
                    stopLoading()
                    webViewClient = WebViewClient()
                }
```

The final block should still call `loadUrl(url)` after the cancellation handler is installed.

- [ ] **Step 2: Add cancellation cleanup to `WebViewHelper.evaluateJs`**

Inside the `suspendCancellableCoroutine` block, before `evaluateJavascript(js)`, add:

```kotlin
                cont.invokeOnCancellation {
                    stopLoading()
                }
```

- [ ] **Step 3: Add cancellation cleanup to `ForumSessionManager.loadPageWithBlockedResources`**

Inside the `suspendCancellableCoroutine` block, after assigning the custom `webView.webViewClient`, add:

```kotlin
                cont.invokeOnCancellation {
                    webView.stopLoading()
                    webView.webViewClient = android.webkit.WebViewClient()
                }
```

- [ ] **Step 4: Reset the WebViewClient after a successful page load**

In `loadPageWithBlockedResources`, update `onPageFinished`:

```kotlin
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        if (cont.isActive) {
                            webView.webViewClient = android.webkit.WebViewClient()
                            cont.resume(pageUrl ?: url) { _, _, _ -> }
                        }
                    }
```

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 3: Destroy the hidden browser session from the Activity lifecycle

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/core/http/BrowserSessionClient.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionClient.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt`
- Verify: `./gradlew.bat :app:compileDebugKotlin --console=plain`

- [ ] **Step 1: Add destroy to the browser session contract**

Change `BrowserSessionClient` to:

```kotlin
interface BrowserSessionClient {
    suspend fun warmUp()
    suspend fun fetchDocument(url: String): Document
    fun destroy()
}
```

- [ ] **Step 2: Keep `ForumSessionClient` as a marker extension**

Change `ForumSessionClient` to:

```kotlin
interface ForumSessionClient : BrowserSessionClient
```

`DefaultForumSessionClient` already implements `destroy()`, so no implementation body is needed beyond the existing method.

- [ ] **Step 3: Destroy the browser session when the Activity is destroyed**

Add this override to `ModernMainActivity`:

```kotlin
    override fun onDestroy() {
        browserSessionClient.destroy()
        super.onDestroy()
    }
```

- [ ] **Step 4: Compile**

Run:

```bash
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 4: Fix search URL encoding

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/data/SearchRepositoryUrlTest.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/SearchRepository.kt`
- Verify: `./gradlew.bat testDebugUnitTest --console=plain`

- [ ] **Step 1: Write a failing URL encoding test**

Create `SearchRepositoryUrlTest.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.SearchType
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepositoryUrlTest {
    @Test
    fun searchMovies_encodesQueryBeforeBuildingUrl() = runTest {
        var capturedUrl = ""
        val repository = DefaultSearchRepository(
            htmlClient = object : HtmlClient {
                override val imageOkHttpClient: OkHttpClient = OkHttpClient()
                override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?) = ""
                override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
                    capturedUrl = url
                    return Jsoup.parse("""<html><body></body></html>""", url)
                }
            },
            cacheStore = memoryCacheStore(),
            siteConfig = object : SiteConfig {
                override var baseUrl: String = "https://example.test"
                override fun resolve(pathOrUrl: String) = pathOrUrl
            }
        )

        repository.searchMovies(SearchType.CENSORED, "演員 1", page = 1, forceRefresh = true)

        assertEquals("https://example.test/search/%E6%BC%94%E5%93%A1%201", capturedUrl)
    }

    private fun memoryCacheStore(): CacheStore {
        val memory = mutableMapOf<String, String>()
        return object : CacheStore {
            override fun readMemory(key: String) = memory[key]
            override fun writeMemory(key: String, value: String) {
                memory[key] = value
            }
            override suspend fun readDisk(key: String): String? = null
            override suspend fun writeDisk(key: String, value: String) = Unit
        }
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.data.SearchRepositoryUrlTest --console=plain
```

Expected before implementation: assertion failure showing the raw unencoded query in the captured URL.

- [ ] **Step 3: Implement query path encoding**

In `SearchRepository.kt`, add:

```kotlin
private fun encodeSearchPathSegment(query: String): String =
    URLEncoder.encode(query, "UTF-8").replace("+", "%20")
```

Then update both URL builders:

```kotlin
        val encodedQuery = encodeSearchPathSegment(query)
        val url = "${baseUrl}${type.urlPathFormater.format(encodedQuery)}${if (page > 1) "/$page" else ""}"
```

Use the same `encodedQuery` form in `searchActresses`.

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.data.SearchRepositoryUrlTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 5: Make movie collection date extraction consistent

**Files:**
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`
- Verify: `./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest --console=plain`

- [ ] **Step 1: Write a failing regression test**

Add this test to `MovieDetailViewModelTest`:

```kotlin
    @Test
    fun loadDetail_usesReleaseDateHeaderWhenCheckingCollection() = runTest(testDispatcher) {
        var capturedMovie: Movie? = null
        val detail = testDetail.copy(
            headers = listOf(
                Header("番号", "ABC-001", ""),
                Header("發行日期", "2024-01-01", "")
            )
        )
        val collectRepo = object : CollectRepository by stubCollectRepo {
            override suspend fun isMovieCollected(movie: Movie): Boolean {
                capturedMovie = movie
                return false
            }
        }
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detail
        }
        val viewModel = MovieDetailViewModel(detailRepo, collectRepo, stubMagnetRepo)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("2024-01-01", capturedMovie?.date)
    }
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest --console=plain
```

Expected before implementation: assertion failure because `loadDetail` currently reads `"日期"` while `toggleCollect` reads `"發行日期"`.

- [ ] **Step 3: Add a single conversion helper**

In `MovieDetailViewModel.kt`, add this private top-level helper below the ViewModel class:

```kotlin
private fun MovieDetailUiModel.toCollectionMovie(link: String): Movie =
    Movie(
        title = title,
        imageUrl = cover,
        code = headers.firstOrNull()?.value ?: "",
        date = headers.firstOrNull { it.name == "發行日期" || it.name == "日期" || it.name == "发行日期" }?.value ?: "",
        link = link
    )
```

Then replace the duplicated `Movie(...)` construction in `loadDetail` and `toggleCollect` with:

```kotlin
val movie = detail.toUiModel().toCollectionMovie(url)
```

For `toggleCollect`, use:

```kotlin
val movie = detail.toCollectionMovie(url)
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 6: Fix collection uniqueness to include dbType

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/data/db/LinkItemEntityTest.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/entity/LinkItem.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/CollectDatabase.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/db/DB.kt`
- Verify: `./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.data.db.LinkItemEntityTest --console=plain`
- Verify: `./gradlew.bat :app:compileDebugKotlin --console=plain`

- [ ] **Step 1: Write a failing annotation test**

Create `LinkItemEntityTest.kt`:

```kotlin
package me.jbusdriver.modern.data.db

import androidx.room.Entity
import me.jbusdriver.modern.data.db.entity.LinkItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkItemEntityTest {
    @Test
    fun linkItem_uniqueIndexUsesDbTypeAndKey() {
        val entity = LinkItem::class.java.getAnnotation(Entity::class.java)
        val uniqueIndex = entity.indices.first { it.unique }

        assertArrayEquals(arrayOf("dbType", "key"), uniqueIndex.value)
        assertTrue(uniqueIndex.unique)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.data.db.LinkItemEntityTest --console=plain
```

Expected before implementation: assertion failure because the unique index only uses `key`.

- [ ] **Step 3: Change the entity index**

In `LinkItem.kt`, change the entity annotation:

```kotlin
@Entity(
    tableName = "t_link",
    indices = [Index(value = ["dbType", "key"], unique = true)]
)
```

- [ ] **Step 4: Bump the database version**

In `CollectDatabase.kt`, change:

```kotlin
@Database(entities = [Category::class, LinkItem::class], version = 2, exportSchema = true)
```

- [ ] **Step 5: Add the Room migration**

In `DB.kt`, add imports:

```kotlin
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
```

Add this inside `object DB`:

```kotlin
    private val COLLECT_MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_t_link_key`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_t_link_dbType_key` ON `t_link` (`dbType`, `key`)")
        }
    }
```

Then add the migration to the collect database builder:

```kotlin
        ).addMigrations(COLLECT_MIGRATION_1_2).build()
```

- [ ] **Step 6: Run the focused test and compile**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.data.db.LinkItemEntityTest --console=plain
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL`.

### Task 7: Prevent FileCache key collisions

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/core/FileCacheTest.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/core/FileCache.kt`
- Verify: `./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.core.FileCacheTest --console=plain`

- [ ] **Step 1: Write a failing hash-collision test**

Create `FileCacheTest.kt`:

```kotlin
package me.jbusdriver.modern.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class FileCacheTest {
    @Test
    fun cacheKeysWithSameJavaHashCodeDoNotOverwriteEachOther() {
        val dir = Files.createTempDirectory("jbus-file-cache").toFile()
        val cache = FileCache(dir, maxSizeBytes = 1024 * 1024)

        cache.put("FB", "first")
        cache.put("Ea", "second")

        assertEquals("first", cache.get("FB"))
        assertEquals("second", cache.get("Ea"))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.core.FileCacheTest --console=plain
```

Expected before implementation: `"FB"` returns `"second"` because `"FB"` and `"Ea"` share the same Java hash code.

- [ ] **Step 3: Use SHA-256 filenames with legacy fallback**

In `FileCache.kt`, add imports:

```kotlin
import java.security.MessageDigest
```

Replace `file(key)` with:

```kotlin
    private fun file(key: String): File = File(cacheDir, key.sha256())

    private fun legacyFile(key: String): File = File(cacheDir, key.hashCode().toString())

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
```

Update `get`:

```kotlin
    fun get(key: String): String? {
        val f = file(key)
        if (f.exists()) return f.readText()
        val legacy = legacyFile(key)
        return if (legacy.exists()) legacy.readText() else null
    }
```

Update `remove`:

```kotlin
    fun remove(key: String) {
        file(key).let { if (it.exists()) it.delete() }
        legacyFile(key).let { if (it.exists()) it.delete() }
    }
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests me.jbusdriver.modern.core.FileCacheTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

---

## Supplemental Plan

### Task 8: Move SharedPreferences access behind injectable preferences

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/AppPreferences.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/di/PreferencesModule.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/SearchHistoryStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/LabSettingsStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/MainScreen.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/components/MovieList.kt`
- Verify: `./gradlew.bat testDebugUnitTest --console=plain`

- [ ] **Step 1: Create preference names**

Create `AppPreferences.kt`:

```kotlin
package me.jbusdriver.modern.data

object AppPreferences {
    const val LAB_SETTINGS = "lab_settings"
    const val SEARCH_HISTORY = "search_history"
    const val SESSION_COOKIES = "session_cookies"
    const val UI_PREFS = "ui_prefs"
    const val GIF_LOADED_URLS = "gif_loaded_urls"
}
```

- [ ] **Step 2: Provide named SharedPreferences through Hilt**

Create `PreferencesModule.kt`:

```kotlin
package me.jbusdriver.modern.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.AppPreferences
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class LabSettingsPrefs
@Qualifier annotation class SearchHistoryPrefs
@Qualifier annotation class SessionCookiePrefs
@Qualifier annotation class UiPrefs
@Qualifier annotation class GifPrefs

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides @Singleton @LabSettingsPrefs
    fun provideLabSettingsPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.LAB_SETTINGS, 0)

    @Provides @Singleton @SearchHistoryPrefs
    fun provideSearchHistoryPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.SEARCH_HISTORY, 0)

    @Provides @Singleton @SessionCookiePrefs
    fun provideSessionCookiePrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.SESSION_COOKIES, 0)

    @Provides @Singleton @UiPrefs
    fun provideUiPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.UI_PREFS, 0)

    @Provides @Singleton @GifPrefs
    fun provideGifPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppPreferences.GIF_LOADED_URLS, 0)
}
```

- [ ] **Step 3: Inject preferences into stores**

Change constructors:

```kotlin
class DefaultSearchHistoryStore @Inject constructor(
    @SearchHistoryPrefs private val prefs: SharedPreferences
) : SearchHistoryStore
```

```kotlin
class LabSettingsStore @Inject constructor(
    @LabSettingsPrefs private val prefs: SharedPreferences
)
```

```kotlin
class SessionCookieStore @Inject constructor(
    @SessionCookiePrefs private val prefs: SharedPreferences
)
```

Then remove direct `JBus.getSharedPreferences(...)` usage from those classes.

- [ ] **Step 4: Compile and run tests**

Run:

```bash
./gradlew.bat testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 9: Move collection import/export out of the Composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/movielist/CollectCategoryScreen.kt`
- Verify: `./gradlew.bat :app:compileDebugKotlin --console=plain`

- [ ] **Step 1: Create a ViewModel for import/export actions**

Create `CollectCategoryViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.CollectRepository
import javax.inject.Inject

data class CollectionImportResult(val imported: Int, val skipped: Int)

@HiltViewModel
class CollectCategoryViewModel @Inject constructor(
    private val repository: CollectRepository
) : ViewModel() {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _importResult = MutableSharedFlow<CollectionImportResult>()
    val importResult: SharedFlow<CollectionImportResult> = _importResult.asSharedFlow()

    suspend fun exportCollectionsJson(): String =
        repository.exportCollectionsJson()

    fun importCollectionsJson(json: String, onDone: () -> Unit, onError: (Throwable) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            runCatching { repository.importCollectionsFromJson(json) }
                .onSuccess { result ->
                    _importResult.emit(CollectionImportResult(result.first, result.second))
                    onDone()
                }
                .onFailure(onError)
            _isBusy.value = false
        }
    }
}
```

- [ ] **Step 2: Use the new ViewModel in `CollectCategoryScreen`**

At the top of `CollectCategoryScreen`, add:

```kotlin
    val actionVm: CollectCategoryViewModel = hiltViewModel()
```

Replace:

```kotlin
    val repo = movieVm.collectRepository
```

with calls to `actionVm.exportCollectionsJson()` and `actionVm.importCollectionsJson(...)`.

- [ ] **Step 3: Compile**

Run:

```bash
./gradlew.bat :app:compileDebugKotlin --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 10: Tighten release security defaults

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Verify: `./gradlew.bat :app:processReleaseManifest --console=plain`
- Verify: `./gradlew.bat :app:assembleRelease --console=plain`

- [ ] **Step 1: Add manifest placeholders per build type**

In `app/build.gradle.kts`, add to `debug`:

```kotlin
            manifestPlaceholders["allowBackup"] = "true"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
```

Add to `release`:

```kotlin
            manifestPlaceholders["allowBackup"] = "false"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
```

- [ ] **Step 2: Wire placeholders in the manifest**

In `AndroidManifest.xml`, replace:

```xml
        android:allowBackup="true"
        android:usesCleartextTraffic="true"
```

with:

```xml
        android:allowBackup="${allowBackup}"
        android:usesCleartextTraffic="${usesCleartextTraffic}"
```

- [ ] **Step 3: Build release**

Run:

```bash
./gradlew.bat :app:processReleaseManifest --console=plain
./gradlew.bat :app:assembleRelease --console=plain
```

Expected: both commands report `BUILD SUCCESSFUL`, and the merged release manifest has backup and cleartext disabled.

---

## Execution Order

1. Run Tasks 1-3 first because they address memory/session lifecycle risks.
2. Run Tasks 4-7 next because each has a focused regression test.
3. Run Tasks 8-10 after all repair tests pass because they change boundaries and release configuration.
4. After every task, run its focused command before moving on.
5. After Task 10, run the full verification set:

```bash
./gradlew.bat testDebugUnitTest --console=plain
./gradlew.bat :app:compileDebugKotlin --console=plain
./gradlew.bat :app:assembleRelease --console=plain
```

Expected: all commands report `BUILD SUCCESSFUL`.

## Residual Follow-Up

- Split `MovieDetailScreen.kt`, `ForumThreadDetailScreen.kt`, and `MovieList.kt` after the repair work lands, because those files are large but not currently the most defect-prone path.
- Replace `SDCardDatabaseContext` with Storage Access Framework import/export as the primary collection backup mechanism. Keep the current database location stable until export/import is proven reliable.
- Add parser fixture tests for representative JavBus and forum HTML pages. Store sanitized fixtures under `app/src/test/resources/parser/` and test parsers without network.
- Move `DataSourceType` and `SearchType` URL templates out of `domain/model` into a data-layer route builder so domain models stop knowing site URL structure.
