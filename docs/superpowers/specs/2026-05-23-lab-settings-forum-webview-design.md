# Lab Settings & Forum WebView Mode Design

Date: 2026-05-23

## Overview

Add a hidden "Lab Settings" screen accessible by searching "Setting"/"设置"/"Settings" in the search bar. The first lab feature is a forum toggle: hide the Forum Tab by default, and when enabled, let users choose between the existing native-rendered forum or a new WebView-based forum with injected mobile adaptations.

## 1. Lab Settings Entry

**Trigger**: User types "Setting", "设置", or "Settings" (case-insensitive) in the search bar.

**Behavior**:
- `SearchViewModel` matches the query prefix without triggering a real search
- `SearchScreen` inserts a lab entry card at the top of the search suggestions/history area
- Card has an icon, title ("实验室"), and brief description
- Clicking the card navigates to `RouteLabSettings` via Navigation

**New route**:
```kotlin
@Serializable
data object RouteLabSettings : NavKey
```

## 2. LabSettingsScreen UI

Grouped card style (Material3 dark theme). Each lab feature gets its own card with description, toggle, and sub-options.

**Forum card**:
- Toggle switch: enable/disable forum (default off)
- When enabled, show a mode selector: "原生渲染" | "WebView"
- Visual indicator of current selection

**Extensibility**: New lab features are added as new cards below. A placeholder hint at the bottom says "更多实验功能即将推出…"

## 3. Settings State Management

```kotlin
enum ForumMode { NATIVE, WEBVIEW }

class LabSettingsStore @Inject constructor(
    private val prefs: SharedPreferences  // "lab_settings"
) {
    val forumEnabled: StateFlow<Boolean>     // default false
    val forumMode: StateFlow<ForumMode>      // default NATIVE
    fun setForumEnabled(enabled: Boolean)
    fun setForumMode(mode: ForumMode)
}
```

Persistence via `SharedPreferences("lab_settings")`. Read on init, write on change.

Hilt-provided singleton, injected into `MainScreen`, `LabSettingsScreen`, and `SearchViewModel`.

## 4. MainScreen Tab Behavior

| State | Bottom Nav Tabs | Forum Tab Content |
|-------|----------------|-------------------|
| `forumEnabled = false` | 影片 / 演員 / 收藏 (3 tabs) | Hidden |
| `forumEnabled = true` + `native` | 影片 / 演員 / 论坛 / 收藏 (4 tabs) | `ForumBoardsScreen` (existing) |
| `forumEnabled = true` + `webview` | 影片 / 演員 / 论坛 / 收藏 (4 tabs) | `ForumWebViewScreen` (new) |

Tab changes are immediate — `MainScreen` collects `StateFlow` and recomposes. No app restart needed.

When `forumEnabled` changes and the currently selected tab is Forum, auto-switch to the Movie tab to avoid blank state.

## 5. Forum WebView Implementation

### Architecture

```
ForumWebViewScreen (Composable)
  ├── WebView component (loads forum URL)
  ├── Injected resources:
  │   ├── forum_mobile.css  — mobile style overrides (dark theme, single column, cards)
  │   └── forum_mobile.js   — DOM manipulation + touch optimization
  ├── Cookie sync (SessionCookieStore ↔ WebView CookieManager)
  └── Custom top bar (back, title, refresh)
```

### URL Handling

`WebViewClient.shouldOverrideUrlLoading`:
- Forum-internal URLs → load within WebView
- Movie detail URLs → navigate to `RouteMovieDetail`
- Image URLs → navigate to `RouteImageViewer`
- External URLs → open in system browser

### Cookie Sharing

On WebView init, sync cookies from `SessionCookieStore` to `CookieManager`. This ensures age verification and login state are preserved across native and WebView modes.

### Injection

- **Timing**: Inject CSS and JS in `onPageFinished`
- **CSS via `<style>` tag**: Dark theme colors matching app theme, full-width single-column layout, card-based post display, image `width: 100%`, hide desktop nav/ads/footer
- **JS via `evaluateJavascript`**: Remove ad elements, restructure DOM for mobile, enlarge touch targets, fix viewport

### Injection Scope (per page type)

| Page | CSS Changes | JS Changes |
|------|------------|------------|
| Index | Full-width layout, hide sidebar, card-style board list | Remove ads, restructure carousel |
| SubForum | Single-column thread list, card threads | Remove icon columns, full-width titles |
| Post | Full-width images, dark code blocks, larger text | Remove ads, fix image sizes, enlarge touch targets |
| Other | Basic full-width + dark theme | Minimal cleanup |

### Lifecycle

- WebView instance preserved across tab switches via `rememberSaveable`
- Page reloads on URL change; injection runs on each `onPageFinished`

## 6. New Files

```
me.jbusdriver.modern/
  data/
    LabSettingsStore.kt           — StateFlow + SharedPreferences for lab settings
  ui/
    settings/
      LabSettingsScreen.kt        — Lab settings UI (grouped cards)
    forum/
      ForumWebViewScreen.kt       — WebView composable with injection
      ForumWebViewClient.kt       — URL interception + injection logic
      assets/
        forum_mobile.css          — Injected mobile styles
        forum_mobile.js           — Injected mobile DOM manipulation
```

## 7. Modified Files

| File | Change |
|------|--------|
| `NavigationKeys.kt` | Add `RouteLabSettings` |
| `Navigation.kt` | Add `RouteLabSettings` → `LabSettingsScreen` route |
| `MainScreen.kt` | Dynamic tab count based on `forumEnabled`, select mode based on `forumMode` |
| `SearchScreen.kt` | Detect "setting"/"设置" query, show lab entry card |
| `SearchViewModel.kt` | Add lab entry detection logic, inject `LabSettingsStore` |
| `di/DataModule.kt` | Bind `LabSettingsStore` |

## 8. Out of Scope

- Forum posting/replying via WebView (view-only for now)
- Custom WebView settings UI (user-agent, etc.)
- Migration of native forum data to/from WebView mode
- Any lab features beyond the forum toggle in this iteration
