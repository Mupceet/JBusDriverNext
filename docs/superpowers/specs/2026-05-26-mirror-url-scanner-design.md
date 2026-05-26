# Mirror URL Scanner Design

## Goal

Add a URL selection feature to Lab Settings that scans mirror/anti-blocking addresses from the current site, displays available mirrors, and lets users switch between them.

## Context

The app currently hardcodes `https://www.javbus.com` as the base URL in `SiteConfigStore`. The site provides mirror addresses (防屏蔽地址) in its footer HTML, e.g.:

```html
<div class="col-xs-12 col-md-6 col-lg-3 text-center">
  <strong>防屏蔽地址：</strong>
  <a href="https://www.cdnbus.bond" rel="nofollow">https://www.cdnbus.bond</a>
</div>
```

These mirrors change over time and may become unreachable. The app needs to discover and validate them dynamically.

## Data Model

```kotlin
data class MirrorUrl(
    val url: String,
    val isReachable: Boolean = false,
    val lastVerified: Long = 0L
)

data class ScanState(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val discoveredUrls: List<MirrorUrl> = emptyList(),
    val error: String? = null
)
```

## Scan Flow

1. Start from the current `defaultFastUrl` (e.g. `https://www.javbus.com`)
2. Fetch page HTML, regex-match `防屏蔽地址` adjacent `<a href="...">` links
3. Add discovered URLs to a pending queue (deduplicated)
4. For each URL in the queue, repeat step 2-3 (recursive discovery)
5. Skip already-scanned URLs to avoid loops
6. When queue is empty, run HEAD requests on all discovered URLs to check reachability
7. Present reachable URLs for user selection

Regex for extraction: look for `<a href="(https://[^"]+)"` inside elements near `防屏蔽地址` text.

## Persistence

`LabSettingsStore` gains two new fields via SharedPreferences:

- `selectedBaseUrl: String` — user's chosen URL (defaults to `https://www.javbus.com`)
- `cachedMirrorUrls: Set<String>` — last scanned URLs for display without re-scanning

`SiteConfigStore.baseUrl` initializes from persisted `selectedBaseUrl` instead of the hardcoded default.

## UI

A new "网址选择" Card in LabSettingsScreen, below the existing forum toggle Card.

**Initial state:** Shows current URL + "扫描网址" button. If cached results exist, shows the list with radio selection.

**Scanning state:** Linear progress indicator + text "正在扫描 2/5...". Discovered URLs appear incrementally. Unreachable ones are grayed out with "不可达" label.

**Complete state:** Radio group of reachable URLs. Selecting one immediately updates `defaultFastUrl`. "重新扫描" button to rescan.

**Cancel:** During scanning, the button becomes "取消" to abort the coroutine.

## File Changes

| File | Change |
|------|--------|
| `data/LabSettingsStore.kt` | Add `selectedBaseUrl`, `cachedMirrorUrls`, `scanMirrorUrls()` suspend function |
| `ui/settings/LabSettingsViewModel.kt` | Add `scanState: StateFlow<ScanState>`, `startScan()`, `cancelScan()`, `selectUrl()` |
| `ui/settings/LabSettingsScreen.kt` | Add URL selection Card with scan button, progress, radio list |
| `core/site/SiteConfig.kt` | Read initial URL from persisted settings |

No changes to `NetClient.kt` or repository layer — they already use `SiteConfigStore.baseUrl` which updates reactively.
