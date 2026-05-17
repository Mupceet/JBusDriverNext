# Movie Detail Share Feature

## Goal

Add a share button to the movie detail page TopBar that shares the movie's identification code, title, URL, and cover image via the system share sheet.

## Share Content

Format of the shared text:

```
{番号} {标题}
{详情页URL}
```

Along with the cover image as `EXTRA_STREAM`.

## UI Placement

- Share icon (Material `share_24px`) in the TopBar, to the left of the existing favorite (heart) button
- Only shown when movie detail has loaded successfully

## Implementation

### Data Sources

- **番号**: Extract from `MovieDetailUiModel.headers` (the header whose name is "識別碼")
- **标题**: `MovieDetailUiModel.title`
- **链接**: `MovieDetailViewModel.movieUrl` (the URL used to load the detail)
- **封面**: `MovieDetailUiModel.cover`

### Share Flow

1. User taps share icon in TopBar
2. Download cover image via Coil `ImageLoader` (reuse app's singleton)
3. Save bitmap to `cache/shared_images/` with timestamp filename
4. Get content URI via existing `FileProvider` (`${packageName}.fileprovider`)
5. Build `Intent.ACTION_SEND`:
   - Type: `image/*`
   - `EXTRA_TEXT`: `"{番号} {标题}\n{链接}"`
   - `EXTRA_STREAM`: cover image content URI
   - `FLAG_GRANT_READ_URI_PERMISSION`
6. Launch with `Intent.createChooser` titled "分享"

### Reuse Existing Infrastructure

- **FileProvider**: Already configured in `res/xml/file_provider_paths.xml` with `<cache-path name="shared_images" path="shared_images/" />`
- **Share pattern**: Follow `ImageViewScreen.shareImage()` implementation
- **Authority**: `${context.packageName}.fileprovider`

### Error Handling

- If cover image download fails, fall back to text-only share (番号 + 标题 + 链接 without image)
- Show Toast on unrecoverable errors

## Files to Modify

- `MovieDetailScreen.kt` — Add share icon button in TopBar, share logic
- `MovieDetailViewModel.kt` — Expose `movieUrl` and helper to get identification code if not easily accessible from UI state
