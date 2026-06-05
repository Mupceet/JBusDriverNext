# GIF Load Mode Design

## Goal

Replace the boolean "auto-load GIFs" toggle with a three-option enum, giving users finer control over how GIF images load in forum thread details.

## Current State

- `LabSettingsStore.autoLoadGifs: StateFlow<Boolean>` — stored in DataStore as `auto_load_gifs`
- `LabSettingsScreen` shows a Switch: "自動載入動圖"
- `ForumThreadDetailViewModel.onLoadGif(url)` loads one GIF at a time into `_loadedGifUrls`
- `PostContent` checks `autoLoadGifs` and `loadedGifUrls` to show `GifPlaceholder` or actual image

## Design

### 1. Enum: `GifLoadMode`

New enum in `me.jbusdriver.modern.data`:

```kotlin
enum class GifLoadMode {
    ON_CLICK,            // Click one → load one (current false behavior)
    ON_CLICK_LOAD_ALL,   // Click one → load all GIFs on current page
    AUTO                 // Auto-load all (current true behavior)
}
```

### 2. Data Layer: `LabSettingsStore`

- Replace `autoLoadGifs: StateFlow<Boolean>` with `gifLoadMode: StateFlow<GifLoadMode>`
- Replace `setAutoLoadGifs(Boolean)` with `setGifLoadMode(GifLoadMode)`
- Storage key: reuse `auto_load_gifs`, store as string enum name
- Migration: `false` → `ON_CLICK`, `true` → `AUTO`, missing → `ON_CLICK`

### 3. ViewModel: `ForumThreadDetailViewModel`

- Add `gifLoadMode: StateFlow<GifLoadMode>` (delegated from `labSettingsStore`)
- Add `onLoadAllGifs()` — collects all GIF URLs from current detail's content blocks + all replies' content blocks, adds to `_loadedGifUrls`, persists via `GifLoadTracker`
- `onLoadGif(url)` behavior changes based on mode:
  - `ON_CLICK`: load single URL (existing behavior)
  - `ON_CLICK_LOAD_ALL`: delegate to `onLoadAllGifs()`
  - `AUTO`: no-op (placeholder not shown)

### 4. Settings UI: `LabSettingsScreen`

- Remove Switch row for auto-load GIFs
- Add `SingleChoiceSegmentedButtonRow` with three segments:
  - "點擊載入" → `ON_CLICK`
  - "載入全部" → `ON_CLICK_LOAD_ALL`
  - "自動播放" → `AUTO`
- Keep description text: "論壇帖中的 GIF 圖片載入方式"

### 5. Screen: `ForumThreadDetailScreen`

- Replace `autoLoadGifs: Boolean` usage with `gifLoadMode: GifLoadMode`
- `PostContent` and `ReplyItem` parameters: `autoLoadGifs: Boolean` → `gifLoadMode: GifLoadMode`
- Placeholder visibility: `gifLoadMode != GifLoadMode.AUTO`

## Files to Modify

| File | Change |
|------|--------|
| `data/LabSettingsStore.kt` | `autoLoadGifs` → `gifLoadMode`, migration logic |
| `data/GifLoadMode.kt` | New file: enum definition |
| `ui/forum/ForumViewModels.kt` | `gifLoadMode` flow, `onLoadAllGifs()`, `onLoadGif` dispatch |
| `ui/forum/ForumThreadDetailScreen.kt` | Replace `autoLoadGifs` with `gifLoadMode` in all composables |
| `ui/settings/LabSettingsScreen.kt` | Replace Switch with SegmentedButtonRow |

## Backward Compatibility

- Old DataStore value `true` → `AUTO`, `false` → `ON_CLICK`
- No DataStore migration needed; reading code handles both old boolean and new string formats
