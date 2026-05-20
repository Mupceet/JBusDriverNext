# Forum Session: WebView-Based Initialization Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the failing OkHttp-based forum session initialization with a hidden WebView approach, so the forum browsing feature works reliably.

**Architecture:** A hidden WebView loads the main site and handles age verification (including JavaScript execution), storing cookies in Android's system `CookieManager`. OkHttp's CookieJar is replaced with a `CookieManager`-backed implementation that reads from the same store, eliminating cookie sync issues.

**Scope:** Changes are limited to the data/network layer. UI, ViewModels, navigation, and HTML parsers remain untouched.

---

## Problem

The current `ForumRepository.ensureForumSession()` uses OkHttp to:
1. GET main site homepage
2. POST age verification form
3. GET forum root

This fails because the site's age verification relies on JavaScript execution and browser-specific behavior that plain HTTP requests cannot replicate. Multiple iterations confirmed that even when the `age=verified` cookie is set and sent, the forum still redirects to the verification page.

## Solution: Shared CookieManager

Android's `CookieManager` is a singleton that both WebView and OkHttp can use. By making OkHttp read cookies from `CookieManager` instead of a private HashMap, we get a single source of truth.

### Component: CookieManagerCookieJar

Replace the current `HashMap<String, List<Cookie>>` in `NetClient.okHttpClient` with a custom `CookieJar` that delegates to `CookieManager`:

- `saveFromResponse(url, cookies)` — converts OkHttp `Cookie` objects to HTTP `Set-Cookie` header format and calls `CookieManager.setCookie(url, headerValue)`
- `loadForRequest(url)` — calls `CookieManager.getCookie(url.toString())`, parses the cookie string into OkHttp `Cookie` objects

This is transparent to all existing code. No other changes needed in NetClient consumers.

### Component: ForumSessionManager

A new singleton class that manages the hidden WebView session initialization.

**Location:** `data/ForumSessionManager.kt`

**Interface:**
```kotlin
class ForumSessionManager @Inject constructor() {
    private val initialized = AtomicBoolean(false)

    suspend fun ensureSession(activity: Activity)
    fun isInitialized(): Boolean
    fun reset()
}
```

**Initialization flow:**

1. `ensureSession(activity)` is called from `ForumRepository` on first forum access
2. Switch to `Dispatchers.Main` (WebView requirement)
3. Create a `WebView(activity)` with zero-size layout params, no visibility
4. Enable cookies: `CookieManager.getInstance().setAcceptCookie(true)`, `setAcceptThirdPartyCookies(webView, true)`
5. Set a `WebViewClient` with `onPageFinished` and `onReceivedError` callbacks
6. Load `https://www.javbus.com/`
7. Wait for `onPageFinished`:
   - If page URL contains `driver-verify` (age verification redirect), inject JS to submit the verification form: `document.forms[0].submit()` (the form has a single Submit=確認 button)
   - Wait for another `onPageFinished` after verification completes
8. Load `https://www.javbus.com/forum/` to trigger Discuz! session cookie initialization (saltkey, sid)
9. At this point, CookieManager has: PHPSESSID, age=verified, 4fJN_2132_saltkey, 4fJN_2132_sid
10. Destroy the WebView (`webView.destroy()`)
11. Set `initialized = true`
12. Return to caller

**Threading:** The `suspend` function uses `suspendCancellableCoroutine` with `withContext(Dispatchers.Main)` to bridge the async WebView callbacks to coroutine world.

### Integration Point: ForumRepository

Replace `ensureForumSession()` in `DefaultForumRepository`:

```kotlin
@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionManager: ForumSessionManager
) : ForumRepository {

    private suspend fun ensureForumSession() {
        if (sessionManager.isInitialized()) return
        val activity = JBusManager.currentActivity ?: throw IllegalStateException("No activity")
        sessionManager.ensureSession(activity)
    }

    // fetchForumDocument stays the same — calls ensureForumSession() then fetchDocument()
}
```

### Session Expiry Detection

In `fetchForumDocument()`, after fetching, check if the response title contains "age verification" or the URL was redirected to `driver-verify`. If so, reset the session and retry once:

```kotlin
private suspend fun fetchForumDocument(url: String): Document {
    ensureForumSession()
    val doc = NetClient.fetchDocument(url)
    if (isVerificationRedirect(doc)) {
        sessionManager.reset()
        ensureForumSession()
        return NetClient.fetchDocument(url)
    }
    return doc
}
```

### Error Handling

- **Timeout:** 15-second timeout via `withTimeout(15_000)`. If exceeded, cancel WebView loading and throw.
- **Network error:** `onReceivedError` callback propagates as exception.
- **No activity:** `JBusManager.currentActivity` returns null → throw `IllegalStateException` → ViewModel catches and shows error UI with retry button.

### Cookie Persistence

Android's `CookieManager` persists cookies to disk. On subsequent app launches, session cookies may still be valid. `ensureSession()` can skip the WebView if a quick check shows cookies are present and the forum is accessible:

```kotlin
private suspend fun hasValidSession(): Boolean {
    return try {
        val doc = NetClient.fetchDocument("${NetClient.defaultFastUrl}/forum/")
        !isVerificationRedirect(doc)
    } catch (_: Exception) {
        false
    }
}
```

If valid, skip WebView entirely. This makes the second and subsequent app launches fast.

## Files Changed

| File | Change |
|------|--------|
| `core/http/NetClient.kt` | Replace HashMap CookieJar with CookieManagerCookieJar. Remove `setCookie()` method. |
| `data/ForumSessionManager.kt` | **New.** Hidden WebView session initializer. |
| `data/ForumRepository.kt` | Inject `ForumSessionManager`, replace `ensureForumSession()` to call it. Remove `sessionInitialized` AtomicBoolean (moved to manager). |
| `data/di/DataModule.kt` | Bind `ForumSessionManager`. |

Unchanged: All ViewModels, all screens, navigation, parsers, domain models.

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| WebView requires Activity context | Use `JBusManager.currentActivity`, fail gracefully if null |
| WebView on main thread blocks UI | Load happens only once, shows loading spinner in forum tab |
| Site changes verification flow | WebView handles it naturally since it's a real browser |
| CookieManager not accepting third-party cookies | Explicitly call `setAcceptThirdPartyCookies(webView, true)` |
