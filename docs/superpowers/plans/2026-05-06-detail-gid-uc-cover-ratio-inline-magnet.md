# Detail Page: gid/uc Parsing, Cover Aspect Ratio & Inline Magnet Fetch

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse `gid`/`uc` and cover dimensions from movie detail HTML, use parsed width/height as the initial cover placeholder aspect ratio, and simplify magnet fetching to a single AJAX call using the already-parsed `gid`/`uc`.

**Architecture:** The `parseMovieDetails` function in `HtmlParser.kt` already parses the full detail page HTML. We extend it to also extract `gid`/`uc` (via regex on the raw HTML, same as the TS reference) and cover image dimensions from the HTML attributes. These values flow into `MovieDetail` → `MovieDetailUiModel` → `MovieDetailScreen`. The magnet loader is refactored to accept `gid`/`uc` directly instead of re-fetching the detail page, making it a single HTTP request.

**Tech Stack:** Kotlin, Jsoup, OkHttp, Jetpack Compose, Hilt

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `domain/model/MovieDetail.kt` | Add `gid`, `uc`, `coverWidth`, `coverHeight` fields |
| Modify | `data/parser/HtmlParser.kt` | Parse gid/uc/cover dimensions in `parseMovieDetails` |
| Modify | `ui/UiModels.kt` | Add `coverWidth`/`coverHeight` to `MovieDetailUiModel` |
| Modify | `ui/detail/MovieDetailScreen.kt` | Use parsed aspect ratio as initial cover placeholder |
| Modify | `data/magnet/loaders/DefaultLoaderImpl.kt` | Accept gid/uc directly, skip re-fetch |
| Modify | `data/magnet/MagnetManager.kt` | Add overload that takes gid/uc |
| Modify | `ui/detail/MovieDetailViewModel.kt` | Pass gid/uc from detail to magnet fetch |
| Modify | `test/.../MovieDetailViewModelTest.kt` | Update test data for new fields |

---

### Task 1: Add gid/uc/coverDimensions to MovieDetail domain model

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/MovieDetail.kt`

- [ ] **Step 1: Add new fields to MovieDetail data class**

In `MovieDetail.kt`, add four parameters to the data class constructor, after `relatedMovies` and before the closing parenthesis:

```kotlin
@Immutable
data class MovieDetail(
    val title: String,
    val content: String,
    val cover: String,
    val headers: List<Header>,
    val genres: List<Genre>,
    val actress: List<ActressInfo>,
    val imageSamples: List<ImageSample>,
    val relatedMovies: List<Movie>,
    val gid: String? = null,
    val uc: String? = null,
    val coverWidth: Int = 0,
    val coverHeight: Int = 0
)
```

All new fields have defaults so existing call sites compile without changes.

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/MovieDetail.kt
git commit -m "feat: add gid/uc/coverDimensions fields to MovieDetail"
```

---

### Task 2: Parse gid/uc and cover image dimensions in HtmlParser

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt`

- [ ] **Step 1: Update `parseMovieDetails` to extract gid, uc, and cover dimensions**

Replace the current `parseMovieDetails` function (lines 105–154) with the following. The changes are:
- Extract `gid` and `uc` from inline `<script>` via regex on `doc.html()`
- Extract cover image `width`/`height` from the `<a class="bigImage">` → `<img>` element attributes (the site serves `<img>` with explicit `width`/`height` or we can parse from `style`)
- Pass these to the `MovieDetail` constructor

```kotlin
fun parseMovieDetails(doc: Document): MovieDetail {
    val roeMovie = doc.select("[class=row movie]")
    val bigImage = roeMovie.select(".bigImage")
    val title = bigImage.select("img").attr("title")
    val cover = bigImage.attr("href").wrapImage()

    val coverImg = bigImage.select("img")
    val coverWidth = coverImg.attr("width").toIntOrNull() ?: 0
    val coverHeight = coverImg.attr("height").toIntOrNull() ?: 0

    val html = doc.html()
    val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)
    val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)

    val headers = mutableListOf<Header>()
    val headersContainer = roeMovie.select(".info")

    headersContainer.select("span.header").filterNot { it.parent()?.hasClass("star-show") == true }.forEach { span ->
        val p = span.parent() ?: return@forEach
        val name = span.text().trimEnd(':').trim()
        val linkEl = p.select("a").firstOrNull()
        val value = linkEl?.text() ?: p.text().removePrefix(span.text()).trim()
        val link = linkEl?.attr("href") ?: ""
        headers.add(Header(name, value, link))
    }

    val content = doc.select("[name=description]").attr("content")?.trim() ?: ""

    val geneses = headersContainer.select(".genre:has(a[href*=genre])").map {
        Genre(it.text(), it.select("a").attr("href"))
    }

    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(), it.attr("href"))
    }

    val samples = doc.select("#sample-waterfall .sample-box").map {
        val thumb = it.select("img").attr("src").wrapImage()
        val image = it.attr("href")
        ImageSample(
            it.select("img").attr("title"),
            thumb,
            if (TextUtils.isEmpty(image)) thumb else image
        )
    }

    val relatedMovies = doc.select("#related-waterfall .movie-box").map {
        val url = it.attr("href")
        Movie(
            it.attr("title"),
            it.select("img").attr("src").wrapImage(),
            url.split("/").last(), "", url
        )
    }

    return MovieDetail(
        title, content, cover, headers, geneses, actresses, samples, relatedMovies,
        gid = gid,
        uc = uc,
        coverWidth = coverWidth,
        coverHeight = coverHeight
    )
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt
git commit -m "feat: parse gid/uc and cover dimensions in parseMovieDetails"
```

---

### Task 3: Propagate cover dimensions through UI model

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt`

- [ ] **Step 1: Add coverWidth/coverHeight to MovieDetailUiModel and update toUiModel()**

In `UiModels.kt`, add `coverWidth` and `coverHeight` to `MovieDetailUiModel`:

```kotlin
@Immutable
data class MovieDetailUiModel(
    val title: String,
    val content: String,
    val cover: String,
    val coverWidth: Int = 0,
    val coverHeight: Int = 0,
    val headers: List<HeaderUiModel>,
    val genres: List<GenreUiModel>,
    val actresses: List<ActressUiModel>,
    val imageSamples: List<ImageSampleUiModel>,
    val relatedMovies: List<MovieUiModel>
)
```

Update the `MovieDetail.toUiModel()` function to pass the new fields:

```kotlin
fun MovieDetail.toUiModel(): MovieDetailUiModel {
    val code = headers.firstOrNull { it.name == "識別碼" }?.value.orEmpty()
    return MovieDetailUiModel(
        title = title,
        content = content,
        cover = cover,
        coverWidth = coverWidth,
        coverHeight = coverHeight,
        headers = headers
            .filter { it.name != "類別" }
            .map {
                if (it.name == "描述") HeaderUiModel("描述", title.removePrefix(code).trim())
                else HeaderUiModel(it.name, it.value, it.link)
            },
        genres = genres.map { GenreUiModel(it.name, it.link) },
        actresses = actress.map { ActressUiModel(it.name, it.avatar, it.link) },
        imageSamples = imageSamples.map { ImageSampleUiModel(it.title, it.thumb, it.image) },
        relatedMovies = relatedMovies.map { it.toUiModel() }
    )
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/UiModels.kt
git commit -m "feat: propagate cover dimensions through MovieDetailUiModel"
```

---

### Task 4: Use parsed aspect ratio as initial cover placeholder in MovieDetailScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

- [ ] **Step 1: Update coverAspectRatio initialization to use parsed dimensions**

In `DetailContent`, replace the line:

```kotlin
var coverAspectRatio by remember { mutableFloatStateOf(3f / 2f) }
```

with:

```kotlin
var coverAspectRatio by remember {
    mutableFloatStateOf(
        if (detail.coverWidth > 0 && detail.coverHeight > 0)
            detail.coverWidth.toFloat() / detail.coverHeight.toFloat()
        else 3f / 2f
    )
}
```

This uses the parsed cover dimensions as the initial placeholder ratio. The `onSuccess` callback still overrides with the actual decoded dimensions once Coil loads the image, so the layout will be correct even if HTML attributes are missing.

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat: use parsed cover dimensions as initial aspect ratio placeholder"
```

---

### Task 5: Refactor DefaultLoaderImpl to accept gid/uc directly

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/magnet/loaders/DefaultLoaderImpl.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/magnet/IMagnetLoader.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/magnet/MagnetManager.kt`

This is the core change: `DefaultLoaderImpl` currently makes **two** HTTP requests (fetch detail page → extract gid/uc → call AJAX). Since `parseMovieDetails` now extracts gid/uc, we can skip the first request entirely.

- [ ] **Step 1: Add a new method to IMagnetLoader for direct gid/uc magnet loading**

In `IMagnetLoader.kt`, add a new interface method after `loadMagnets`:

```kotlin
/**
 * Load magnets using pre-extracted gid/uc parameters, skipping the detail page fetch.
 * Implementations that support this should override; the default falls back to [loadMagnets].
 */
suspend fun loadMagnetsWithParams(gid: String, uc: String, movieUrl: String): List<JSONObject> =
    loadMagnets(movieUrl, 1)
```

- [ ] **Step 2: Override loadMagnetsWithParams in DefaultLoaderImpl**

Replace the entire content of `DefaultLoaderImpl.kt` with:

```kotlin
package me.jbusdriver.modern.data.magnet.loaders

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.magnet.IMagnetLoader
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 磁力链接加载器，直接使用预提取的 gid/uc 调用 AJAX 接口。
 *
 * 流程：调用 AJAX 接口 /ajax/uncledatoolsbyajax.php 获取磁力表格，Jsoup 解析提取磁力链接。
 * 相比旧实现省去了首次获取详情页的 HTTP 请求。
 */
class DefaultLoaderImpl : IMagnetLoader {

    override var hasNexPage: Boolean = false

    override suspend fun loadMagnets(key: String, page: Int): List<JSONObject> {
        // Fallback: fetch detail page to extract gid/uc, then call AJAX
        val html = NetClient.fetchHtml(key, showAll = true)
        val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: "0"
        return fetchMagnetsAjax(gid, uc, key)
    }

    override suspend fun loadMagnetsWithParams(gid: String, uc: String, movieUrl: String): List<JSONObject> {
        return fetchMagnetsAjax(gid, uc, movieUrl)
    }

    private suspend fun fetchMagnetsAjax(gid: String, uc: String, movieUrl: String): List<JSONObject> {
        val baseUrl = NetClient.defaultFastUrl
        val floor = (Math.random() * 1000 + 1).toInt()
        val ajaxUrl = "$baseUrl/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&uc=$uc&floor=$floor"

        KLog.d("Magnet: gid=$gid, uc=$uc, floor=$floor")

        val ajaxHtml = NetClient.fetchHtml(ajaxUrl, showAll = true, referer = "$baseUrl/")
        KLog.d("Magnet: ajax response length=${ajaxHtml.length}")

        val doc = Jsoup.parse("<table>${ajaxHtml}</table>")
        val rows = doc.select("table tr")
        KLog.d("Magnet: table tr count=${rows.size}")

        hasNexPage = false

        return rows.asSequence()
            .drop(1).map {
                val contents = it.select("td")
                val link = it.select("a").attr("href").orEmpty()
                JSONObject().apply {
                    put("name", contents.getOrNull(0)?.text().orEmpty())
                    put("size", contents.getOrNull(1)?.text().orEmpty())
                    put("date", contents.getOrNull(2)?.text().orEmpty())
                    put("link", link)
                }
            }.toList()
    }
}
```

- [ ] **Step 3: Add convenience method to MagnetManager**

In `MagnetManager.kt`, add a new method after `getMagnets`:

```kotlin
/**
 * 使用预提取的 gid/uc 参数直接获取磁力链接，跳过详情页请求。
 *
 * @param loader 加载器标识键
 * @param gid 从详情页 HTML 提取的 gid
 * @param uc 从详情页 HTML 提取的 uc
 * @param movieUrl 影片详情页 URL，用作 referer
 * @return 磁力列表的 JSON 数组字符串，加载器不存在时返回空数组
 */
suspend fun getMagnetsWithParams(loader: String, gid: String, uc: String, movieUrl: String): String {
    return JSONArray(
        MagnetLoaders.Loaders[loader]?.loadMagnetsWithParams(gid, uc, movieUrl)
            ?: emptyList<JSONObject>()
    ).toString()
}
```

Add the missing import at the top of `MagnetManager.kt` if not already present:

```kotlin
import me.jbusdriver.modern.data.magnet.loaders.MagnetLoaders
```

Note: `MagnetLoaders` and `JSONArray`/`JSONObject` imports already exist in the file.

- [ ] **Step 4: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/magnet/IMagnetLoader.kt
git add app/src/main/java/me/jbusdriver/modern/data/magnet/loaders/DefaultLoaderImpl.kt
git add app/src/main/java/me/jbusdriver/modern/data/magnet/MagnetManager.kt
git commit -m "refactor: DefaultLoaderImpl accepts pre-parsed gid/uc for single-request magnet fetch"
```

---

### Task 6: Update MovieDetailViewModel to pass gid/uc to magnet fetch

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`

- [ ] **Step 1: Store gid/uc from detail and use them in loadMagnets**

Replace the `loadMagnets` method in `MovieDetailViewModel` (lines 137–158) with:

```kotlin
fun loadMagnets() {
    if (_uiState.value.isLoadingMagnets) return
    val url = currentUrl.ifBlank { return }
    val gid = _uiState.value.movieDetail?.gid?.ifBlank { null } ?: run {
        // No gid available, fall back to old two-request path
        magnetPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, 1)
                }
                _uiState.update {
                    it.copy(
                        magnets = magnets,
                        isLoadingMagnets = false,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
        return
    }
    val uc = _uiState.value.movieDetail?.uc ?: "0"
    viewModelScope.launch {
        _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
        try {
            val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                fetchMagnetsWithParams(gid, uc, url)
            }
            _uiState.update {
                it.copy(
                    magnets = magnets,
                    isLoadingMagnets = false,
                    hasMoreMagnets = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
        }
    }
}
```

Add the new `fetchMagnetsWithParams` method after the existing `fetchMagnets` method:

```kotlin
private suspend fun fetchMagnetsWithParams(gid: String, uc: String, movieUrl: String): List<MagnetUiModel> {
    val json = MagnetManager.getMagnetsWithParams("default", gid, uc, movieUrl)
    val arr = JSONArray(json)
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        Magnet(
            name = obj.optString("name", ""),
            size = obj.optString("size", ""),
            date = obj.optString("date", ""),
            link = obj.optString("link", "")
        ).toUiModel()
    }
}
```

We also need to add `gid` and `uc` fields to `MovieDetailUiModel` so the ViewModel can read them. However, since they're only used internally by the ViewModel and not displayed in UI, we can instead store them directly in the ViewModel state.

**Better approach:** Add `gid`/`uc` to `MovieDetailUiState` instead of the UI model.

Add these fields to `MovieDetailUiState`:

```kotlin
data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: String? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: String? = null,
    val hasMoreMagnets: Boolean = true,
    val isCollected: Boolean = false,
    val gid: String? = null,
    val uc: String? = null
)
```

Now update `loadDetail` to also store gid/uc. Change the success path in `loadDetail` from:

```kotlin
val detail = repository.getMovieDetail(url)
_uiState.update { it.copy(movieDetail = detail.toUiModel(), isLoading = false) }
```

to:

```kotlin
val detail = repository.getMovieDetail(url)
_uiState.update {
    it.copy(
        movieDetail = detail.toUiModel(),
        isLoading = false,
        gid = detail.gid,
        uc = detail.uc
    )
}
```

Now update `loadMagnets` to read from state instead of UI model. Replace the `loadMagnets` method entirely:

```kotlin
fun loadMagnets() {
    if (_uiState.value.isLoadingMagnets) return
    val url = currentUrl.ifBlank { return }
    val gid = _uiState.value.gid
    val uc = _uiState.value.uc

    if (gid != null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnetsWithParams(gid, uc ?: "0", url)
                }
                _uiState.update {
                    it.copy(
                        magnets = magnets,
                        isLoadingMagnets = false,
                        hasMoreMagnets = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
    } else {
        // Fallback: no gid/uc, use two-request path
        magnetPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, 1)
                }
                _uiState.update {
                    it.copy(
                        magnets = magnets,
                        isLoadingMagnets = false,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt
git commit -m "feat: pass pre-parsed gid/uc from detail to magnet fetch, single-request path"
```

---

### Task 7: Update MovieDetailViewModelTest for new fields

**Files:**
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

- [ ] **Step 1: Update test data to include gid/uc**

The `testDetail` in the test file uses the default values for `gid`/`uc`/`coverWidth`/`coverHeight` (null/0), so existing tests compile. Add one new test to verify gid/uc are propagated to state:

```kotlin
@Test
fun loadDetail_storesGidAndUc() = runTest(testDispatcher) {
    val detailWithGid = testDetail.copy(gid = "12345", uc = "67890")
    val detailRepo = object : MovieDetailRepository {
        override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detailWithGid
    }
    val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo)

    viewModel.loadDetail("http://example.com/ABC-001")
    advanceUntilIdle()

    assertEquals("12345", viewModel.uiState.value.gid)
    assertEquals("67890", viewModel.uiState.value.uc)
}
```

- [ ] **Step 2: Run tests to verify**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt
git commit -m "test: verify gid/uc propagation in MovieDetailViewModelTest"
```

---

### Task 8: Build and smoke test

**Files:** None (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 3: Verify no warnings**

Run: `./gradlew compileDebugUnitTestKotlin 2>&1 | grep -i "warning:" || echo "No warnings"`
Expected: No new warnings related to the changes

---

## Self-Review Checklist

1. **Spec coverage:**
   - gid/uc parsing → Task 2 (HtmlParser) + Task 6 (ViewModel propagation)
   - Cover dimensions → Task 2 (parsing) + Task 3 (UI model) + Task 4 (screen usage)
   - Initial cover placeholder → Task 4 (aspectRatio from parsed dimensions)
   - Single-request magnet → Task 5 (loader refactor) + Task 6 (ViewModel uses gid/uc)

2. **Placeholder scan:** No TBD, TODO, or vague steps found. All steps contain exact code.

3. **Type consistency:**
   - `MovieDetail(gid: String?, uc: String?, coverWidth: Int, coverHeight: Int)` — matches across all tasks
   - `MovieDetailUiModel(coverWidth: Int, coverHeight: Int)` — Task 3 defines, Task 4 uses
   - `MovieDetailUiState(gid: String?, uc: String?)` — Task 6 defines and uses
   - `IMagnetLoader.loadMagnetsWithParams(gid: String, uc: String, movieUrl: String)` — Task 5 defines, Task 6 calls
   - `MagnetManager.getMagnetsWithParams(loader, gid, uc, movieUrl)` — Task 5 defines, Task 6 calls
   - `fetchMagnetsWithParams(gid, uc, movieUrl)` — defined and used within Task 6
