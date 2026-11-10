# Site Manager: Preset Main Site + Scanned Mirrors + Custom URLs

**Date:** 2026-11-10
**Status:** Approved (design confirmed in conversation; four default decisions accepted)

## Problem

`AppSettingsStore.PRESET_MIRROR_URLS` hardcodes 4 URLs (main site `www.javbus.com` plus 3 mirrors `cdnbus.bond` / `cdnbus.cyou` / `seejav.cyou`). Two issues:

1. **Stale presets:** the mirror presets rot as domains get blocked; we cannot keep them accurate from release to release. The main site is the only URL we should vouch for.
2. **No custom URLs:** a user who cannot reach any preset has no way to point the app at a URL they *can* reach (e.g. a personal reverse proxy). This is the primary escape hatch for blocked-network users.

Presentation is also limiting: site selection lives in an `ExposedDropdownMenuBox` inside the Settings NetworkCard. The dropdown has no room for grouping, per-entry management (add/delete), or per-group latency display. Cached URLs that were never measured are rendered as `MirrorUrl(url, isReachable = true)` — a fabricated reachability.

## Solution

1. **Preset = main site only.** Delete `PRESET_MIRROR_URLS`; `DEFAULT_SITE_URL` (`https://www.javbus.com`) becomes the single preset source.
2. **Custom URLs.** New persisted `custom_base_urls` StringSet with add/remove APIs. Custom URLs are selectable, speed-testable, and act as scan seeds.
3. **Dedicated Site Manager page** (`RouteSiteManager`) showing three labeled groups — 官方 / 掃描發現 / 自定義 — with one **unified speed test** (測速) action covering all groups, plus the existing scan (掃描) action.
4. **Scan kept, seeds expanded** to: main site + currently selected URL + custom URLs + cached scan results — so a user whose network blocks the main site can still discover mirrors through their custom URL.

## Data Layer

### `AppSettingsContract` (AppSettingsStore.kt)

New members:

```kotlin
val customBaseUrls: StateFlow<List<String>>
suspend fun addCustomUrl(url: String): Boolean   // false = invalid or duplicate (across all groups)
suspend fun removeCustomUrl(url: String)
suspend fun removeScannedUrl(url: String)        // prune a stale scanned entry
```

Changed members:

- `cachedMirrorUrls` default becomes the **empty set** (was `PRESET_MIRROR_URLS`).
- `scanMirrorUrls(state)` — the `seedUrl` parameter is dropped; the store composes seeds itself:
  `setOf(DEFAULT_SITE_URL, selectedBaseUrl) + customBaseUrls + cachedMirrorUrls` (all normalized/trimmed).
- `verifyMirrorUrls(state)` — verifies the union of preset + custom + cached (was cached only).

Semantics:

- `addCustomUrl` normalizes with `normalizeBaseUrl()`, rejects blank/non-http(s) input, and rejects duplicates against preset + custom + cached (`false` → UI shows a toast). Reachability is **not** a save condition; the add dialog may show an advisory HEAD check result but never blocks saving.
- Scan persistence (`scanMirrorUrls` → `KEY_CACHED_MIRROR_URLS`) **filters out** `DEFAULT_SITE_URL` and custom URLs from the discovered set, so the cached set only ever holds "scanned" entries and never aliases another group.

### Migration

No migration code. Existing users' persisted `KEY_CACHED_MIRROR_URLS` (which contains old presets + discovered mirrors) is kept as-is; those URLs simply appear under 掃描發現 and can be deleted individually. `selectedBaseUrl` values pointing at removed presets keep working — the entry survives in the user's cached set.

### `MirrorScanner.kt`

- `scanAndVerify(state, seeds: List<String>)` replaces `(state, seedUrl, cachedUrls)` — the store composes and dedups seeds; scanner behavior (WebView extraction of 防屏蔽地址/永久域名, parallel HEAD verification, `ScanState` updates) is unchanged.
- `sortMirrorUrls` drops the hardcoded `www.javbus.com`-first preference (grouping now carries that distinction); sort becomes: unreachable last → latency ascending → URL. Unmeasured entries sort by URL.

### UI-layer derived model (defined in `ui/sitemanager/SiteManagerViewModel.kt`)

```kotlin
enum class SiteGroup { PRESET, SCANNED, CUSTOM }
data class SiteEntry(val url: String, val group: SiteGroup)
```

Derived from `DEFAULT_SITE_URL` / `cachedMirrorUrls` / `customBaseUrls` with dedup priority **PRESET > CUSTOM > SCANNED** (a URL never appears in two groups). Latency/reachability is merged in from the latest verify/scan result by URL; entries with no measurement show no badge (no fabricated reachability).

## UI

### Settings — `NetworkCard` (SettingsScreen.kt) slims down

- The `ExposedDropdownMenuBox`, scan/verify buttons, progress and error blocks are removed.
- The URL row becomes: label 選擇網址 / current `selectedBaseUrl` / trailing chevron; clicking navigates to the Site Manager (`onOpenSiteManager`).
- The LTX switch and URL row are untouched.

### New: `ui/sitemanager/SiteManagerScreen.kt` + `SiteManagerViewModel.kt`

Top bar: back, title 站點管理, actions: **全部測速** (verify all groups), **＋** (add custom URL).

```
← 站點管理                    [全部測速] [＋]
──────────────────────────────────────────
官方
 ● www.javbus.com                  231 ms
──────────────────────────────────────────
掃描發現                            [掃描]
 ○ cdnbus.bond                     480 ms  🗑
 ○ seejav.cyou                     不可達   🗑
──────────────────────────────────────────
自定義
 ○ my-proxy.example                156 ms  🗑
```

- **Row tap = switch site** (same as today: `store.selectUrl(url)` + `siteConfig.baseUrl = url`). Unreachable rows are **selectable** — the reachability/latency badge is advisory only (HEAD can false-negative behind anti-bot; the feature's premise is that the user knows which URL works for them).
- 🗑 (delete) on scanned and custom rows only; preset row has none.
- 掃描 action sits in the 掃描發現 group header (relabel 重新掃描 after first scan), progress UI (linear indicator + phase text + current URL) as today.
- Add dialog: single URL text field + save. Save-time validation is **format + duplicate only** (never reachability). Saving triggers an advisory HEAD check whose result (看起來可達 / 暫時不可達，仍已保存) is reported as a toast/snackbar hint after the entry is saved.
- Empty 掃描發現/自定義 groups hide their headers (官方 always has exactly one row).

### ViewModel state (UDF per AGENTS.md)

Single immutable `SiteManagerUiState` via `StateFlow`: grouped entries, `selectedBaseUrl`, scan/verify progress fields (from `ScanState`), per-URL measurement map. One-shot toasts (add-duplicate, add-invalid, deleted, switched) flow through `messages: SharedFlow<UserMessage>`. The `_scanState` `MutableStateFlow` currently owned by `SettingsViewModel` **moves** here; `SettingsViewModel` loses `startScan`/`startVerify`/`cancelScan`/`selectUrl` and the `mirrorUrls` computation.

### Navigation

- `NavigationKeys.kt`: `data object RouteSiteManager : NavKey` (same style as `RouteSettings`).
- `Navigation.kt`: `entry<RouteSiteManager>` rendering `SiteManagerScreen`; `SettingsScreen` gains an `onOpenSiteManager: () -> Unit` wired to `backStack.add(RouteSiteManager)`. iOS-style transition consistent with `RouteSettings`.

### Strings

All new visible text as resources in `res/values/strings.xml` (繁體 default) + `values-en/strings.xml`: 站點管理, 官方, 掃描發現, 自定義, 全部測速, 掃描/重新掃描, 添加自定義網址, 刪除, duplicate/invalid URL toasts, advisory reachability hints, content descriptions. Existing `select_url` / `scan_url` / `check_connectivity` / `unreachable` reused where they fit.

## Interaction & edge cases

- **Deleting the currently selected entry:** blocked with a toast (請先切換到其他站點) — never silently changes the active data source. (Preset can't be deleted; if the selected entry is the preset this can't occur.)
- **Selected URL not visible in any group** (possible when a past scan replaced the cached set and dropped the URL the user had selected): no ghost row is added; the Settings row keeps showing the true `selectedBaseUrl`. Accepted rarity.
- **Concurrent scan/verify:** buttons disabled while `isScanning`; cancel resets state (same guard as today's `launchScan`).
- **Latency data lifetime:** measurements live in `SiteManagerViewModel` state for the session; nothing persisted. Re-entering the page shows entries without badges until 測速 runs.
- **Duplicate across groups:** display dedup priority PRESET > CUSTOM > SCANNED; scan persistence already filters preset/custom out of the cached set so deletes don't resurrect entries.
- **Duplicate add against scanned entry:** rejected with toast (the scanned copy already exists — user can switch to it directly).
- **URL normalization:** all persisted URLs pass through `normalizeBaseUrl()`; comparisons use the normalized form.

## Testing

- **`AppSettingsStoreTest` (new, `data/settings/`):** custom add/remove, invalid URL rejection, cross-group duplicate rejection, scan-seed composition (uses fake `MirrorScanner` seam), scan persistence filters preset/custom, legacy cached set surfaces as SCANNED, `verifyMirrorUrls` union coverage. Data-layer tests use injected/fake seams per existing test style.
- **`SiteManagerViewModelTest` (new):** grouping + dedup derivation, row-tap switches site and updates `SiteConfig`, delete-selected blocked, delete updates groups, scan state transitions, measurement merge.
- **`MirrorScannerTest`:** update for `scanAndVerify(state, seeds)` signature; `sortMirrorUrls` new ordering.
- **Gates:** `./gradlew testDebugUnitTest assembleDebug` before commit (per AGENTS.md).
- No Gson/ProGuard changes → no release smoke required; but run `assembleRelease` once before release per standing habit.

## No changes to

- `SiteConfig` / `DefaultSiteConfig` resolution, `awaitReady()`, `selectedBaseUrl` key, or how repositories consume the base URL.
- `MirrorScanner` WebView extraction JS, `EXTRACT_MIRROR_JS`, `NetClient.checkReachable` HEAD probe, concurrency (Semaphore 6).
- LTX settings UI, translation settings, update check.
- `SiteCacheKey` or any cache keying.

## Out of scope (recorded for later)

- Persisting latency history / background scheduled speed tests.
- Auto-switching to the fastest site.
- Import/export of custom URL lists via collection backup codec.
- Custom display names for URLs.
