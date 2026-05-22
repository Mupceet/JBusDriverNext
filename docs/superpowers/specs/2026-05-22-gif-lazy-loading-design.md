# GIF Lazy Loading Design

## Problem

Forum posts contain large GIF animations that consume significant bandwidth. All images (including GIFs) load immediately via Coil `AsyncImage` with no lazy loading or bandwidth optimization.

## Solution

Show a unified placeholder for GIF images instead of loading them automatically. Users must click to load individual GIFs. The ImageView screen inherits the loading state from the post.

## Design

### 1. Data Model

Add `isGif: Boolean` to `ContentBlock.Image`:

```kotlin
data class Image(
    val url: String,
    val width: Int,
    val height: Int,
    val isFullSize: Boolean,
    val isGif: Boolean  // new field
)
```

**Parsing** (`HtmlParser.kt`): In `wrapForumImage()`, detect `.gif` URL suffix (ignoring query parameters) and set `isGif = true`.

**Gson TypeAdapter**: Update the custom `ContentBlock` serializer/deserializer to handle `isGif`. Default `false` for backward compatibility with cached data.

### 2. ViewModel State

`ForumThreadDetailViewModel` tracks loaded GIF URLs:

```kotlin
private val _loadedGifUrls = mutableStateSetOf<String>()
val loadedGifUrls: Set<String> get() = _loadedGifUrls

fun onGifClick(url: String) {
    _loadedGifUrls.add(url)
}
```

### 3. Post UI Rendering

In `ForumThreadDetailScreen.kt`, when rendering `ContentBlock.Image`:

- **Unloaded GIF** (`isGif && url !in loadedGifUrls`): Show `GifPlaceholder` composable (gray background + play icon + "点击加载" text). Click calls `viewModel.onGifClick(url)`.
- **Loaded GIF or static image**: Normal `AsyncImage` rendering. Click enters ImageView.

**GifPlaceholder composable**: A new reusable component with:
- Gray/dark background matching post theme
- Centered play icon (▶)
- "点击加载" text below the icon

### 4. ImageView Inheritance

When navigating to ImageView, pass the set of loaded GIF URLs as a navigation parameter. In ImageView:
- If GIF URL is in the loaded set: display animation directly
- If GIF URL is not loaded: show placeholder + "点击加载"

### 5. Error Handling

- If GIF load fails after clicking, show an error state with retry option
- Static images remain unaffected by this change
