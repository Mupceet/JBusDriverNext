# Forum Session: WebView-Based Initialization Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the failing OkHttp-based forum session initialization with a hidden WebView approach, so the forum browsing feature works reliably.

**Architecture:** A hidden WebView loads the main site to establish browser cookies (including JavaScript-triggered ones), storing them in Android's system `CookieManager`. OkHttp's CookieJar is replaced with a `CookieManager`-backed implementation that reads from the same store, eliminating cookie sync issues.

**Scope:** Changes are limited to the data/network layer. UI, ViewModels, navigation, and HTML parsers remain untouched.

---

## Problem

The current `ForumRepository.ensureForumSession()` uses OkHttp to:
1. GET main site homepage
2. POST age verification form
3. GET forum root

This fails because the site's cookie setup relies on JavaScript execution that plain HTTP requests cannot replicate. The forum redirects to `member.php?mod=logging&action=login` when the necessary cookies are missing.

In a real browser, visiting `https://www.javbus.com/` first (the main site homepage) sets the required cookies via JavaScript, then visiting the forum works without redirect. This behavior needs to be replicated via WebView.

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
6. Load `https://www.javbus.com/forum/`
7. Wait for `onPageFinished`:
   - If the loaded URL does NOT contain `member.php` (no login redirect) → forum loaded successfully, session is valid. Done.
   - If the loaded URL contains `member.php?mod=logging&action=login` (login redirect) → need to warm up main site first:
     a. Load `https://www.javbus.com/` (main site homepage)
     b. Wait for `onPageFinished` — this sets necessary cookies via JavaScript
     c. Load `https://www.javbus.com/forum/` again
     d. Wait for `onPageFinished` — this time no redirect
8. Destroy the WebView (`webView.destroy()`)
9. Set `initialized = true`
10. Return to caller

No form submission or JS injection needed. The simple act of loading the main site homepage in a WebView is sufficient to establish the required cookies.

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

In `fetchForumDocument()`, after fetching, check if the response URL contains `member.php` or the document redirects to login. If so, reset the session and retry once:

```kotlin
private suspend fun fetchForumDocument(url: String): Document {
    ensureForumSession()
    val doc = NetClient.fetchDocument(url)
    if (isLoginRedirect(doc)) {
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

Android's `CookieManager` persists cookies to disk. On subsequent app launches, session cookies may still be valid. `ensureSession()` can skip the WebView if a quick check shows the forum is accessible:

```kotlin
private suspend fun hasValidSession(): Boolean {
    return try {
        val doc = NetClient.fetchDocument("${NetClient.defaultFastUrl}/forum/")
        !isLoginRedirect(doc)
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
| Site changes redirect behavior | WebView handles it naturally since it's a real browser |
| CookieManager not accepting third-party cookies | Explicitly call `setAcceptThirdPartyCookies(webView, true)` |
