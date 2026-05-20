# Forum Session WebView Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the failing OkHttp-based forum session init with a hidden WebView approach that shares cookies via Android CookieManager.

**Architecture:** A `CookieManagerCookieJar` replaces OkHttp's in-memory HashMap so WebView and OkHttp share one cookie store. A new `ForumSessionManager` uses a hidden WebView to load the main site, establishing cookies that OkHttp then uses for all forum requests.

**Tech Stack:** Android WebView, CookieManager, OkHttp 5 CookieJar, Kotlin Coroutines

---

## File Structure

| File | Responsibility |
|------|---------------|
| `core/http/CookieManagerCookieJar.kt` | **New.** CookieJar backed by Android CookieManager. Converts between OkHttp Cookie objects and CookieManager's string format. |
| `core/http/NetClient.kt` | Swap HashMap CookieJar → CookieManagerCookieJar. Remove `setCookie()` and `postForm()` (dead code after forum session rewrite). |
| `data/ForumSessionManager.kt` | **New.** Creates hidden WebView, loads main site to establish session cookies, destroys WebView when done. |
| `data/ForumRepository.kt` | Inject `ForumSessionManager`. Replace `ensureForumSession()` to delegate to manager. Add login-redirect retry logic. |

No DI module changes needed — `ForumSessionManager` is `@Singleton @Inject constructor()` which Hilt resolves automatically.

---

### Task 1: Create CookieManagerCookieJar

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/core/http/CookieManagerCookieJar.kt`

- [ ] **Step 1: Create the CookieManagerCookieJar class**

```kotlin
package me.jbusdriver.modern.core.http

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar backed by Android's system CookieManager.
 *
 * Single source of truth: WebView sets cookies via CookieManager,
 * OkHttp reads them here. No sync logic needed.
 */
class CookieManagerCookieJar : CookieJar {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val cookieValue = buildString {
                append(cookie.name).append('=').append(cookie.value)
                append("; path=").append(cookie.path)
                if (cookie.domain.isNotEmpty()) {
                    append("; domain=").append(cookie.domain)
                }
                if (cookie.secure) append("; secure")
                if (cookie.httpOnly) append("; httponly")
            }
            cookieManager.setCookie(url.toString(), cookieValue)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieString.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .mapNotNull { cookiePart ->
                val parts = cookiePart.split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .domain(url.host)
                        .path(url.encodedPath)
                        .name(parts[0].trim())
                        .value(parts[1].trim())
                        .build()
                } else null
            }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/core/http/CookieManagerCookieJar.kt
git commit -m "feat: add CookieManagerCookieJar for shared WebView/OkHttp cookie store"
```

---

### Task 2: Wire CookieManagerCookieJar into NetClient

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/core/http/NetClient.kt`

- [ ] **Step 1: Replace HashMap CookieJar with CookieManagerCookieJar**

In `NetClient.kt`, replace lines 89–97 (the inline `object : CookieJar` block) with:

```kotlin
            .cookieJar(CookieManagerCookieJar())
```

The full `okHttpClient` lazy block should become:

```kotlin
    private val okHttpClient by lazy {
        val client = OkHttpClient.Builder()
            .writeTimeout(30 * 1000L, TimeUnit.MILLISECONDS)
            .readTimeout(20 * 1000L, TimeUnit.MILLISECONDS)
            .connectTimeout(15 * 1000L, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(EXIST_MAGNET_INTERCEPTOR)
            .cookieJar(CookieManagerCookieJar())
        if (BuildConfig.DEBUG) {
            client.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        client.build()
    }
```

- [ ] **Step 2: Remove dead code — `setCookie()` and `postForm()` methods**

Delete the entire `setCookie()` method (lines 183–204) and the entire `postForm()` method (lines 156–181). Both were only used by the old OkHttp-based forum session init.

Remove unused imports: `FormBody`, `Cookie`, `HttpUrl` (if no longer referenced — `HttpUrl` is still used in `EXIST_MAGNET_INTERCEPTOR`, `Cookie` is no longer used directly in NetClient).

After removal, verify these imports remain in NetClient.kt:

```kotlin
import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.KLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/core/http/NetClient.kt app/src/main/java/me/jbusdriver/modern/core/http/CookieManagerCookieJar.kt
git commit -m "feat: replace HashMap CookieJar with CookieManagerCookieJar in NetClient"
```

---

### Task 3: Create ForumSessionManager

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`

- [ ] **Step 1: Create ForumSessionManager**

```kotlin
package me.jbusdriver.modern.data

import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumSession"

/**
 * WebView-based forum session initializer.
 *
 * Loads the main site in a hidden WebView to establish cookies
 * (including JS-triggered ones), then OkHttp reuses those cookies
 * via the shared CookieManagerCookieJar.
 */
@Singleton
class ForumSessionManager @Inject constructor() {

    private val initialized = AtomicBoolean(false)

    fun isInitialized(): Boolean = initialized.get()

    fun reset() {
        initialized.set(false)
    }

    /**
     * Ensure forum session is established via WebView.
     *
     * Flow:
     * 1. Load forum URL → if no redirect, done
     * 2. If redirected to member.php (login page), load main site homepage
     * 3. Then load forum URL again → should succeed this time
     *
     * Must be called from a coroutine scope that provides an Activity
     * via [JBusManager].
     */
    suspend fun ensureSession(activity: Activity) {
        if (initialized.get()) return

        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                val webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    visibility = android.view.View.INVISIBLE
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                rootView.addView(webView, FrameLayout.LayoutParams(1, 1))

                try {
                    // Step 1: Try loading forum directly
                    val forumUrl = "${NetClient.defaultFastUrl}/forum/"
                    val firstUrl = loadPage(webView, forumUrl)
                    KLog.d("[Forum] First load landed at: $firstUrl", TAG)

                    if (firstUrl.contains("member.php")) {
                        // Redirected to login — need main site warmup
                        KLog.d("[Forum] Login redirect detected, loading main site", TAG)
                        val mainUrl = "${NetClient.defaultFastUrl}/"
                        loadPage(webView, mainUrl)
                        KLog.d("[Forum] Main site loaded, retrying forum", TAG)

                        // Retry forum
                        val retryUrl = loadPage(webView, forumUrl)
                        KLog.d("[Forum] Retry landed at: $retryUrl", TAG)

                        if (retryUrl.contains("member.php")) {
                            throw IOException("Forum still redirects to login after main site warmup")
                        }
                    }

                    initialized.set(true)
                    KLog.d("[Forum] Session initialization complete", TAG)
                } finally {
                    webView.stopLoading()
                    rootView.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }

    /**
     * Load a URL in the WebView and wait for onPageFinished.
     * Returns the final URL after any redirects.
     */
    private suspend fun loadPage(webView: WebView, url: String): String {
        return suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (cont.isActive) {
                        KLog.d("[Forum] Page finished: $pageUrl", TAG)
                        cont.resume(pageUrl ?: url)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true && cont.isActive) {
                        KLog.e("[Forum] WebView error: ${error?.description}", TAG)
                        cont.resumeWith(
                            Result.failure(
                                IOException("WebView error loading $url: ${error?.description}")
                            )
                        )
                    }
                }
            }
            webView.loadUrl(url)
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt
git commit -m "feat: add ForumSessionManager with hidden WebView session init"
```

---

### Task 4: Update ForumRepository to use ForumSessionManager

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`

- [ ] **Step 1: Rewrite ForumRepository**

Replace the entire file content with:

```kotlin
package me.jbusdriver.modern.data

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.JBusManager
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.parser.parseForumBoards
import me.jbusdriver.modern.data.parser.parseForumThreadDetail
import me.jbusdriver.modern.data.parser.parseForumThreads
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumRepo"

interface ForumRepository {
    suspend fun loadForumBoards(forceRefresh: Boolean = false): List<ForumBoard>
    suspend fun loadThreads(fid: Int, page: Int, typeId: Int? = null, forceRefresh: Boolean = false): ForumThreadPageResult
    suspend fun loadThreadDetail(tid: Int, page: Int = 1, forceRefresh: Boolean = false): ForumThreadDetail
}

@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionManager: ForumSessionManager
) : ForumRepository {

    private suspend fun ensureForumSession() {
        if (sessionManager.isInitialized()) return
        val activity = JBusManager.manager.firstOrNull()?.get()
            ?: throw IllegalStateException("No activity available for forum session init")
        sessionManager.ensureSession(activity)
    }

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        ensureForumSession()
        val doc = NetClient.fetchDocument(url)
        KLog.d("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}", TAG)

        // If redirected to login page, reset session and retry once
        if (isLoginRedirect(doc)) {
            KLog.w("[Forum] Login redirect detected, resetting session", TAG)
            sessionManager.reset()
            ensureForumSession()
            return NetClient.fetchDocument(url)
        }
        return doc
    }

    private fun isLoginRedirect(doc: org.jsoup.nodes.Document): Boolean {
        val html = doc.html()
        return html.contains("member.php") && html.contains("mod=logging")
    }

    override suspend fun loadForumBoards(forceRefresh: Boolean): List<ForumBoard> {
        val url = "${NetClient.defaultFastUrl}/forum/forum.php"
        KLog.d("[Forum] loadForumBoards: url=$url", TAG)
        return CacheLoader.lruCached("forum_boards", forceRefresh) {
            val doc = fetchForumDocument(url)
            val result = parseForumBoards(doc)
            KLog.d("[Forum] parseForumBoards: ${result.size} boards", TAG)
            result
        }
    }

    override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult {
        val cacheKey = "forum_threads_${fid}_${page}_${typeId ?: "all"}"
        val baseUrl = "${NetClient.defaultFastUrl}/forum/forum.php?mod=forumdisplay&fid=$fid&page=$page"
        val url = if (typeId != null) "$baseUrl&filter=typeid&typeid=$typeId" else baseUrl
        KLog.d("[Forum] loadThreads: url=$url", TAG)
        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val doc = fetchForumDocument(url)
            parseForumThreads(doc)
        }
    }

    override suspend fun loadThreadDetail(tid: Int, page: Int, forceRefresh: Boolean): ForumThreadDetail {
        val cacheKey = "forum_detail_${tid}_$page"
        val url = "${NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid&page=$page"
        KLog.d("[Forum] loadThreadDetail: url=$url", TAG)
        return CacheLoader.persistentCached(cacheKey) {
            val doc = fetchForumDocument(url)
            parseForumThreadDetail(doc)
        }
    }
}
```

Key changes from previous version:
- Constructor now injects `ForumSessionManager` instead of having its own `AtomicBoolean`
- `ensureForumSession()` gets Activity from `JBusManager.manager` and delegates to `sessionManager`
- `fetchForumDocument()` adds login-redirect detection with automatic retry
- Removed all OkHttp-based warmup code (GET main site, POST verification, GET forum root)
- Removed `sessionInitialized` field (moved to ForumSessionManager)

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt
git commit -m "feat: rewrite ForumRepository to use ForumSessionManager for WebView session init"
```

---

### Task 5: Full build verification

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no regressions in existing features**

Run: `./gradlew test`
Expected: All tests pass (any pre-existing failures unrelated to this change are acceptable)

- [ ] **Step 3: Install and manually test**

Install the debug APK on device. Test:
1. Open app → movie tabs load normally (cookies still work via CookieManager)
2. Tap "论坛" tab → loading spinner appears briefly, then forum boards load
3. Tap a board → thread list loads
4. Tap a thread → detail with replies loads
5. Kill and reopen app → forum loads faster (CookieManager persisted cookies)
6. Check Logcat for `ForumSession` and `ForumRepo` tags to verify the flow

Expected Logcat sequence on first forum visit:
```
D/ForumSession: Page finished: https://www.javbus.com/forum/member.php?mod=logging&action=login
D/ForumSession: Login redirect detected, loading main site
D/ForumSession: Page finished: https://www.javbus.com/
D/ForumSession: Main site loaded, retrying forum
D/ForumSession: Page finished: https://www.javbus.com/forum/
D/ForumSession: Session initialization complete
D/ForumRepo: fetched: title=... length=...
D/ForumRepo: parseForumBoards: N boards
```

Or if session is already valid:
```
D/ForumSession: Page finished: https://www.javbus.com/forum/forum.php
D/ForumSession: Session initialization complete
D/ForumRepo: fetched: title=... length=...
```
