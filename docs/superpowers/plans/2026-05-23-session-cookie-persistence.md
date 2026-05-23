# Session Cookie Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist WebView-obtained session cookies to SharedPreferences so forum visits reuse OkHttp instead of creating a new WebView every time.

**Architecture:** New `SessionCookieStore` serializes cookies from `CookieManager` to SharedPreferences. `ForumSessionManager` tries to restore persisted cookies first; only falls back to WebView creation when cookies are missing or expired. After WebView verification succeeds, cookies are saved for future use.

**Tech Stack:** Kotlin, SharedPreferences, Gson, Android CookieManager, Hilt DI

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt` | **Create** | Cookie serialization/deserialization, validity checks |
| `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt` | **Modify** | Add cookie restore/save logic to ensureSession flow |

No DI changes needed — `SessionCookieStore` is instantiated directly inside `ForumSessionManager` (same pattern as `SearchHistoryStore` using `JBus.getSharedPreferences`). No interface needed for a single implementation used by one consumer.

---

### Task 1: Create SessionCookieStore

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt`

- [ ] **Step 1: Create SessionCookieStore with full implementation**

```kotlin
package me.jbusdriver.modern.data

import android.webkit.CookieManager
import androidx.core.content.edit
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson

/**
 * Persists session cookies from CookieManager to SharedPreferences.
 *
 * Stores critical cookies (age verification, Discuz! session) with their expiry
 * timestamps so they can be restored on next app launch without creating a WebView.
 */
class SessionCookieStore {

    private val prefs by lazy {
        JBus.getSharedPreferences(PREFS_NAME, 0)
    }

    /**
     * Save all cookies for the given URL from CookieManager to SharedPreferences.
     * Only saves cookies whose names are in [TRACKED_COOKIES].
     */
    fun saveCookies(url: String) {
        val cookieString = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieString(cookieString)
        val entries = mutableMapOf<String, PersistedCookie>()

        for ((name, value) in cookies) {
            if (name in TRACKED_COOKIES) {
                // Parse max-age from raw cookie header if available;
                // for now, estimate expiry from cookie type
                val ttlSeconds = COOKIE_TTL[name] ?: 0L
                val expiresAt = if (ttlSeconds > 0) {
                    System.currentTimeMillis() / 1000 + ttlSeconds
                } else {
                    0L // session cookie
                }
                entries[name] = PersistedCookie(value, expiresAt)
            }
        }

        if (entries.isNotEmpty()) {
            val json = GSON.toJson(entries)
            prefs.edit { putString(prefsKey(url), json) }
            KLog.d("[SessionCookieStore] Saved ${entries.size} cookies for $url", TAG)
        }
    }

    /**
     * Restore persisted cookies for the given URL back into CookieManager.
     * Only restores non-expired cookies.
     */
    fun restoreCookies(url: String) {
        val json = prefs.getString(prefsKey(url), null) ?: return
        val entries = GSON.fromJson<Map<String, PersistedCookie>>(json) ?: return
        val now = System.currentTimeMillis() / 1000
        val cookieManager = CookieManager.getInstance()

        for ((name, cookie) in entries) {
            if (cookie.expiresAt == 0L || cookie.expiresAt > now) {
                cookieManager.setCookie(url, "$name=${cookie.value}; path=/")
            }
        }
        cookieManager.flush()
        KLog.d("[SessionCookieStore] Restored cookies for $url", TAG)
    }

    /**
     * Check if critical cookies exist and are not expired.
     * Returns true if [CRITICAL_COOKIES] are all present and valid.
     */
    fun isSessionValid(url: String): Boolean {
        val json = prefs.getString(prefsKey(url), null) ?: return false
        val entries = GSON.fromJson<Map<String, PersistedCookie>>(json) ?: return false
        val now = System.currentTimeMillis() / 1000

        for (name in CRITICAL_COOKIES) {
            val cookie = entries[name] ?: return false
            if (cookie.expiresAt != 0L && cookie.expiresAt <= now) {
                KLog.d("[SessionCookieStore] Cookie $name expired", TAG)
                return false
            }
        }
        return true
    }

    /** Remove persisted cookie data. */
    fun clear() {
        prefs.edit { clear() }
        KLog.d("[SessionCookieStore] Cleared all persisted cookies", TAG)
    }

    private fun prefsKey(url: String): String {
        val host = url.substringAfter("://").substringBefore("/")
        return "session_cookies_$host"
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        return cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    internal data class PersistedCookie(
        val value: String,
        /** Unix timestamp (seconds) when this cookie expires. 0 = session cookie. */
        val expiresAt: Long
    )

    companion object {
        private const val PREFS_NAME = "session_cookies"
        private const val TAG = "SessionCookie"

        /** Cookies to persist. */
        private val TRACKED_COOKIES = setOf(
            "age", "PHPSESSID",
            "4fJN_2132_saltkey", "4fJN_2132_sid",
            "4fJN_2132_lastvisit", "4fJN_2132_lastact"
        )

        /** Cookies that must be present for session to be considered valid. */
        private val CRITICAL_COOKIES = setOf("age", "4fJN_2132_saltkey")

        /** TTL in seconds for each cookie type, used when saving. */
        private val COOKIE_TTL = mapOf(
            "age" to 30 * 24 * 3600L,           // 30 days
            "4fJN_2132_saltkey" to 30 * 24 * 3600L,  // 30 days
            "4fJN_2132_sid" to 24 * 3600L,      // 1 day
            "4fJN_2132_lastvisit" to 30 * 24 * 3600L,
            "4fJN_2132_lastact" to 24 * 3600L
        )
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/SessionCookieStore.kt
git commit -m "feat: add SessionCookieStore for cookie persistence"
```

---

### Task 2: Modify ForumSessionManager to use SessionCookieStore

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`

This is the core change. The `ensureSession()` method now tries cookie restore before creating a WebView. After WebView init succeeds, cookies are saved.

- [ ] **Step 1: Add SessionCookieStore field and modify ensureSession**

In `ForumSessionManager.kt`, make these changes:

Add import at the top:
```kotlin
// (no new imports needed — SessionCookieStore is in same package)
```

Add field after `private val mutex = Mutex()`:
```kotlin
private val cookieStore = SessionCookieStore()
```

Replace the existing `ensureSession` method (lines 66-72) with:
```kotlin
    /**
     * Initialize session for forum access.
     * Tries to restore persisted cookies first; only creates WebView if needed.
     */
    suspend fun ensureSession(activity: Activity) {
        if (initialized.get()) return
        mutex.withLock {
            if (initialized.get()) return

            // Try restoring persisted cookies first
            val url = siteConfig.referer()
            if (cookieStore.isSessionValid(url)) {
                cookieStore.restoreCookies(url)
                initialized.set(true)
                KLog.d("[Forum] Session restored from persisted cookies", TAG)
                return
            }

            // Fall back to WebView initialization
            initWebView(activity)
        }
    }
```

- [ ] **Step 2: Modify initWebView to save cookies after successful init**

Replace the existing `initWebView` method (lines 74-95) with:
```kotlin
    private suspend fun initWebView(activity: Activity) {
        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                val wv = WebView(activity.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                webView = wv

                // Load main site to establish session
                val mainUrl = siteConfig.referer()
                KLog.d("[Forum] Loading main site: $mainUrl", TAG)
                loadPageUrl(wv, mainUrl)

                // Save cookies for future reuse
                cookieStore.saveCookies(mainUrl)

                initialized.set(true)
                KLog.d("[Forum] WebView session initialized", TAG)
            }
        }
    }
```

The only addition from original is `cookieStore.saveCookies(mainUrl)` after `loadPageUrl`.

- [ ] **Step 3: Modify destroy to keep cookies but re-allow restore**

Replace the existing `destroy` method (lines 116-125) with:
```kotlin
    fun destroy() {
        val wv = webView
        if (wv != null) {
            KLog.d("[Forum] Destroying WebView", TAG)
            wv.stopLoading()
            wv.destroy()
            webView = null
        }
        initialized.set(false)
        // Note: persisted cookies are NOT cleared here.
        // They will be restored on next ensureSession() call.
    }
```

The change is the comment — no functional difference, but cookies in SharedPreferences survive WebView destruction. The `initialized.set(false)` means the next `ensureSession()` will check `isSessionValid()` and potentially restore without WebView.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt
git commit -m "feat: ForumSessionManager restores persisted cookies before creating WebView"
```

---

### Task 3: Add cookie refresh after driver-verify fallback

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt`

When `HtmlClient` detects driver-verify and calls `warmUp()` → `ensureSession()`, the WebView is created but the `initWebView` path already saves cookies. However, we also need to handle the case where the WebView fetches forum pages after the initial warm-up — the Discuz! cookies (`4fJN_2132_*`) are only set when the forum URL is first loaded.

We need to save cookies again after the first successful forum page fetch.

- [ ] **Step 1: Add a public method to trigger cookie save from outside**

Add this method to `ForumSessionManager` after the `fetchDocument` method:

```kotlin
    /**
     * Save current cookies from CookieManager.
     * Called after a successful forum page fetch to capture Discuz! session cookies
     * that are only set when /forum/ is first accessed.
     */
    fun persistCookies() {
        cookieStore.saveCookies(siteConfig.referer())
    }
```

- [ ] **Step 2: Call persistCookies from ForumRepository after successful fetch**

Open `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`.

Add a new dependency to `DefaultForumRepository` constructor — inject `ForumSessionManager` directly so we can call `persistCookies()` after the first forum page loads successfully.

Replace the constructor (line 27-31):
```kotlin
@Singleton
class DefaultForumRepository @Inject constructor(
    private val sessionClient: ForumSessionClient,
    private val sessionManager: ForumSessionManager,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : ForumRepository {
```

Then modify `fetchForumDocument` to save cookies after successful fetch:
```kotlin
    private var cookiesPersistedForForum = false

    private suspend fun fetchForumDocument(url: String): org.jsoup.nodes.Document {
        val doc = sessionClient.fetchDocument(url)
        KLog.d("[Forum] fetched: title=${doc.title()}, length=${doc.html().length}", TAG)
        // Persist cookies after first successful forum page fetch
        // to capture Discuz! session cookies (4fJN_2132_*)
        if (!cookiesPersistedForForum) {
            sessionManager.persistCookies()
            cookiesPersistedForForum = true
        }
        return doc
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt
git commit -m "feat: persist cookies after first forum fetch to capture Discuz! session"
```

---

### Task 4: Build and smoke test

- [ ] **Step 1: Full debug build**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install and verify on device/emulator**

Manual verification steps:
1. Install debug APK
2. Open forum tab — first time should create WebView (same as before)
3. Leave forum tab (triggers `destroy()`)
4. Re-enter forum tab — should load via restored cookies without WebView
5. Check logcat for `[SessionCookieStore] Restored cookies` and `[Forum] Session restored from persisted cookies`
6. Kill app and relaunch — enter forum tab should still restore from persisted cookies

- [ ] **Step 3: Commit final state if any fixes were needed**

```bash
git add -A
git commit -m "fix: session cookie persistence adjustments from smoke test"
```

---

## Self-Review

**Spec coverage:**
- SessionCookieStore with save/restore/isValid/clear → Task 1 ✓
- ForumSessionManager restore-before-WebView → Task 2 ✓
- Cookie save after WebView init → Task 2 ✓
- Cookie save after forum page fetch (Discuz! cookies) → Task 3 ✓
- No background refresh → Not implemented (correct per spec) ✓
- bus_auth unchanged → No modifications to NetClient ✓
- destroy() preserves cookies → Task 2 ✓

**Placeholder scan:** No TBD/TODO/fill-in-later. All code blocks contain complete implementation.

**Type consistency:**
- `SessionCookieStore.PersistedCookie` data class defined in Task 1, used consistently in Tasks 1-3
- `cookieStore.saveCookies(siteConfig.referer())` — `siteConfig.referer()` returns `String` matching the `url: String` parameter ✓
- `ForumSessionManager.persistCookies()` is public, called from `DefaultForumRepository` ✓
- `ForumSessionManager` injected into `DefaultForumRepository` — Hilt can provide this since it's `@Singleton` with `@Inject constructor` ✓
