# Forum Bandwidth Optimization

## Problem

ForumSessionManager uses a WebView to fetch all forum pages. WebView downloads the full page (HTML + CSS + JS + images + fonts + ads), but we only extract the HTML text. This wastes 80-90% of the downloaded bytes. Additionally, forum list data uses memory-only caching (`lruCached`), so app restarts trigger full re-fetches.

## Design

### 1. WebView Resource Interception

Override `shouldInterceptRequest` in ForumSessionManager's WebViewClient to block non-HTML resources. Return an empty `WebResourceResponse` for requests whose URL ends with known static resource extensions:

**Blocked extensions:**
- Styles: `.css`
- Scripts: `.js`
- Fonts: `.woff`, `.woff2`, `.ttf`, `.eot`, `.otf`
- Images: `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.svg`, `.ico`

**Allowed:** HTML pages and API requests (no matching extension) load normally.

This applies to both `loadPageUrl` (session init and page loads) and `loadPageHtml` (HTML extraction). The WebViewClient is set per-load, so the interception logic goes into the shared client configuration.

**Scope:** Only `ForumSessionManager.kt`. No changes to `fetchDocument`'s callers or return types.

### 2. Disk Cache for Forum Lists

Change forum list data from memory-only to persistent caching:

- `ForumRepository.loadForumBoards`: `lruCached` → `persistentCached`
- `ForumRepository.loadThreads`: `lruCached` → `persistentCached`

This matches the existing pattern used for movie details and genre data. Pull-to-refresh (`forceRefresh = true`) still bypasses cache to fetch fresh data.

**Scope:** Only `ForumRepository.kt`, two cache method calls.

## Files to Modify

1. `app/src/main/java/me/jbusdriver/modern/data/ForumSessionManager.kt` — add `shouldInterceptRequest` to WebViewClient
2. `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt` — change caching strategy for boards and threads

## Expected Impact

- WebView bandwidth reduced by 80-90% (CSS/JS/images/fonts blocked)
- Page load latency reduced (fewer resources to download)
- App restart no longer re-fetches forum boards and thread lists
