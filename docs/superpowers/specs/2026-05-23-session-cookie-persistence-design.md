# Session Cookie Persistence Design

## Problem

The app maintains two independent auth mechanisms:

1. **Main site**: Hardcoded `bus_auth` cookie in OkHttp interceptor (`NetClient.EXIST_MAGNET_INTERCEPTOR`)
2. **Forum**: Hidden `WebView` that handles age verification + Discuz! session, created every time the user enters the forum tab

The WebView is expensive to create and the session it establishes (cookies in `CookieManager`) is lost when the user leaves the forum. This means re-creating the WebView on every forum visit.

Investigation via local proxy (port 7897) confirmed:

- `bus_auth` bypasses main site age verification but does **not** set `age=verified` for the forum
- Even with `bus_auth` + manually obtained `age=verified`, the forum (Discuz!) requires its own session cookies (`4fJN_2132_*`)
- The forum is an independent Discuz! application with its own user/session system
- WebView remains necessary because the verification flow involves JS execution and browser-level cookie management

## Solution

Persist WebView-obtained session cookies to local storage. On subsequent forum visits, restore cookies into `CookieManager` and attempt OkHttp requests first. Only create WebView when persisted cookies are missing or expired.

## Architecture

```
Optimized flow:
  Enter forum → Check persisted cookies
    ├─ Valid → Restore to CookieManager → OkHttp request
    │                                        ├─ Success → done
    │                                        └─ driver-verify → WebView fallback
    └─ Missing/expired → WebView init → Save cookies → done
```

`bus_auth` remains unchanged for main site requests. WebView is only used for forum and driver-verify scenarios.

## Components

### SessionCookieStore (new)

Responsibility: Serialize/deserialize cookies between CookieManager and SharedPreferences.

Stored cookies:
- `age=verified` — main site age verification (30-day expiry)
- `PHPSESSID` — main site session
- `4fJN_2132_saltkey` — Discuz! security key (30-day expiry)
- `4fJN_2132_sid` — Discuz! session ID (1-day expiry)
- `4fJN_2132_lastvisit` / `4fJN_2132_lastact` — Discuz! visit tracking

API:
- `saveCookies(url: String)`: Read cookies from CookieManager, serialize with expiry timestamps to SharedPreferences
- `restoreCookies(url: String)`: Deserialize and write back to CookieManager
- `isSessionValid(): Boolean`: Check critical cookies exist and not expired
- `clear()`: Remove persisted data

Storage format (SharedPreferences, key: `session_cookies_{host}`):
```json
{
  "age": {"value": "verified", "expiresAt": 1782096000},
  "PHPSESSID": {"value": "abc123", "expiresAt": 0},
  "4fJN_2132_saltkey": {"value": "xxx", "expiresAt": 1782096000},
  "4fJN_2132_sid": {"value": "yyy", "expiresAt": 1779580000}
}
```

`expiresAt = 0` means session cookie (valid until browser closes — treat as valid if present).

### ForumSessionManager (modify)

Current: `ensureSession()` always creates WebView.

Modified:
1. Try `SessionCookieStore.restoreCookies()` first
2. If `isSessionValid()` → set `initialized = true` without creating WebView
3. If no valid cookies → fall through to existing WebView init
4. After WebView successfully loads a page → `SessionCookieStore.saveCookies()`

Refresh triggers:
- No persisted cookies (first use)
- Critical cookie expired (checked via stored timestamp)
- OkHttp request hits driver-verify (detected by `HtmlClient.isDriverVerify()`)
- No periodic background refresh (avoids complexity and battery drain)

### HtmlClient (no change needed)

Already has the three-tier fallback: OkHttp → warmUp → WebView fetch. The warmUp path now benefits from restored cookies in CookieManager.

## Data Flows

### First use (no persisted cookies)
1. User opens forum tab
2. `ForumSessionClient.ensureSession()` → no cookies → `initWebView()`
3. WebView loads main site → passes age verification → cookies in CookieManager
4. `SessionCookieStore.saveCookies()` persists cookies
5. WebView fetches forum pages

### Subsequent use (valid persisted cookies)
1. User opens forum tab
2. `ForumSessionClient.ensureSession()` → `isSessionValid()` → `restoreCookies()` → set initialized
3. OkHttp + `CookieManagerCookieJar` sends restored cookies → forum returns 200
4. No WebView created

### Cookie expiry / invalidation
1. OkHttp request returns driver-verify page
2. `HtmlClient` detects it → `warmUp()` → WebView init
3. WebView re-verifies → `saveCookies()` → back to normal

## Error Handling

| Scenario | Handling |
|----------|----------|
| Persisted cookies expired | OkHttp hits driver-verify → auto-degrade to WebView → re-save |
| WebView init timeout (15s) | Throw exception, UI shows error |
| WebView destroyed then re-requested | `ensureSession()` re-creates or restores |
| Network error | Existing logic, no change |
| User clears app data / CookieManager cleared | Same as first use |

## Scope

- Forum browsing only (anonymous, no login required)
- `bus_auth` for main site unchanged
- No background refresh, no WorkManager
- No login state management
