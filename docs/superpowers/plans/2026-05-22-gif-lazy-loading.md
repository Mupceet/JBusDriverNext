# GIF Lazy Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace automatic GIF loading in forum posts with click-to-load placeholders to reduce bandwidth consumption.

**Architecture:** Add `isGif` field to `ContentBlock.Image` during HTML parsing, track loaded GIF state in `ForumThreadDetailViewModel`, and render a placeholder composable for unloaded GIFs. ImageView inherits loading state via navigation parameters.

**Tech Stack:** Kotlin, Jetpack Compose, Coil, Material3

---

### Task 1: Add `isGif` field to `ContentBlock.Image` data model

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt:138` (data class)
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt:162-167` (TypeAdapter write)
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt:183-184,228` (TypeAdapter read)

- [ ] **Step 1: Add `isGif` parameter to `ContentBlock.Image`**

In `ForumModels.kt` line 138, change the data class:

```kotlin
@Immutable
data class Image(val url: String, val width: Int = 0, val height: Int = 0, val isFullSize: Boolean = false, val isGif: Boolean = false) : ContentBlock()
```

- [ ] **Step 2: Update TypeAdapter `write` to serialize `isGif`**

In `ForumModels.kt` inside `ContentBlockTypeAdapter.write()`, after the `isFullSize` line (~line 166), add:

```kotlin
if (value.isGif) out.name("isGif").value(true)
```

The full Image write block becomes:

```kotlin
is ContentBlock.Image -> {
    out.name("type").value("image").name("url").value(value.url)
    if (value.width > 0) out.name("width").value(value.width)
    if (value.height > 0) out.name("height").value(value.height)
    if (value.isFullSize) out.name("fullSize").value(true)
    if (value.isGif) out.name("isGif").value(true)
}
```

- [ ] **Step 3: Update TypeAdapter `read` to deserialize `isGif` with default `false`**

In `ForumModels.kt` inside `ContentBlockTypeAdapter.read()`, add `isGif` variable and parsing:

After `var fullSize = false` (~line 184), add:

```kotlin
var isGif = false
```

In the `when` block (~line 218), after `"fullSize" -> fullSize =` `in.nextBoolean()`, add:

```kotlin
"isGif" -> isGif = `in`.nextBoolean()
```

Change the Image construction (~line 228) to:

```kotlin
"image" -> ContentBlock.Image(url, width, height, fullSize, isGif)
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt
git commit -m "feat: add isGif field to ContentBlock.Image model"
```

---

### Task 2: Detect GIF URLs during HTML parsing

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt:664-672` (img tag parsing)

- [ ] **Step 1: Add GIF detection helper function**

In `HtmlParser.kt`, in the `// region URL 工具` section (after ~line 708), add:

```kotlin
private fun String.isGifUrl(): Boolean {
    val path = this.substringBefore("?").substringBefore("#").lowercase()
    return path.endsWith(".gif")
}
```

- [ ] **Step 2: Use `isGif` in ContentBlock.Image construction**

In `HtmlParser.kt` line 671, change:

```kotlin
blocks.add(ContentBlock.Image(src, w, h, isFullSize))
```

to:

```kotlin
blocks.add(ContentBlock.Image(src, w, h, isFullSize, src.isGifUrl()))
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt
git commit -m "feat: detect GIF URLs during forum HTML parsing"
```

---

### Task 3: Add loaded GIF state to ViewModel

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt:213-289` (ForumThreadDetailViewModel)

- [ ] **Step 1: Add loaded GIF tracking state and action**

In `ForumViewModels.kt`, inside `ForumThreadDetailViewModel`, after the `_uiState` declaration, add:

```kotlin
private val _loadedGifUrls = mutableStateSetOf<String>()
val loadedGifUrls: Set<String> get() = _loadedGifUrls

fun onGifPlaceholderClick(url: String) {
    _loadedGifUrls.add(url)
}
```

This requires adding the import:

```kotlin
import androidx.compose.runtime.mutableStateSetOf
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumViewModels.kt
git commit -m "feat: add loaded GIF URL tracking to ForumThreadDetailViewModel"
```

---

### Task 4: Create `GifPlaceholder` composable

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/components/GifPlaceholder.kt`
- Create: `app/src/main/res/drawable/play_arrow_24px.xml`

- [ ] **Step 1: Add play_arrow vector drawable**

Create `app/src/main/res/drawable/play_arrow_24px.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960"
    android:tint="?attr/colorOnSurfaceVariant">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M320,760L320,200l440,280Z" />
</vector>
```

- [ ] **Step 2: Create GifPlaceholder composable**

Create `app/src/main/java/me/jbusdriver/modern/ui/components/GifPlaceholder.kt`:

```kotlin
package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.jbusdriver.R

@Composable
fun GifPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.play_arrow_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "點擊加載",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/components/GifPlaceholder.kt app/src/main/res/drawable/play_arrow_24px.xml
git commit -m "feat: add GifPlaceholder composable with play icon"
```

---

### Task 5: Render GifPlaceholder for unloaded GIFs in PostContent

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt:346-468` (PostContent)
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt:78-92` (ForumThreadDetailScreen)

This is the core UI change. `PostContent` needs access to the loaded GIF set and a callback for GIF placeholder clicks.

- [ ] **Step 1: Add `loadedGifUrls` and `onGifPlaceholderClick` parameters to `PostContent`**

Change `PostContent` signature at line ~346:

```kotlin
@Composable
private fun PostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    loadedGifUrls: Set<String> = emptySet(),
    onGifPlaceholderClick: (String) -> Unit = {}
)
```

- [ ] **Step 2: Update GIF rendering logic inside PostContent**

Replace the `is ContentBlock.Image ->` block (lines 400-424) with:

```kotlin
is ContentBlock.Image -> {
    val currentIdx = imageIndex++
    if (block.isGif && block.url !in loadedGifUrls) {
        GifPlaceholder(
            onClick = { onGifPlaceholderClick(block.url) },
            modifier = if (block.isFullSize) {
                Modifier.fillMaxWidth().height(180.dp)
            } else {
                Modifier.size(48.dp)
            }
        )
    } else if (block.isFullSize) {
        AsyncImage(
            model = block.url,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onImageClick(imageUrls, currentIdx) },
            contentScale = ContentScale.FillWidth
        )
    } else {
        AsyncImage(
            model = block.url,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onImageClick(imageUrls, currentIdx) },
            contentScale = ContentScale.Fit
        )
    }
}
```

Add the import:

```kotlin
import me.jbusdriver.modern.ui.components.GifPlaceholder
```

- [ ] **Step 3: Pass ViewModel state to PostContent from ForumThreadDetailScreen**

In `ForumThreadDetailScreen` (~line 88), add after `val state by ...`:

```kotlin
val loadedGifUrls by remember { derivedStateOf { viewModel.loadedGifUrls } }
```

Update `PostContent` calls at ~line 163 and ~line 602:

```kotlin
PostContent(
    blocks = detail.contentBlocks,
    onImageClick = onImageClick,
    onLinkClick = handleLinkClick,
    modifier = Modifier.padding(12.dp),
    loadedGifUrls = loadedGifUrls,
    onGifPlaceholderClick = { viewModel.onGifPlaceholderClick(it) }
)
```

And in `ReplyItem` (~line 559), update signature and call similarly:

```kotlin
@Composable
private fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    onLinkClick: (String) -> Unit = {},
    loadedGifUrls: Set<String> = emptySet(),
    onGifPlaceholderClick: (String) -> Unit = {}
)
```

Pass them to the `PostContent` inside `ReplyItem`.

Update the `ReplyItem` call site (~line 188) to pass the new params:

```kotlin
ReplyItem(
    reply = detail.replies[index],
    onImageClick = onImageClick,
    onLinkClick = handleLinkClick,
    loadedGifUrls = loadedGifUrls,
    onGifPlaceholderClick = { viewModel.onGifPlaceholderClick(it) }
)
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt
git commit -m "feat: render GifPlaceholder for unloaded GIFs in forum posts"
```

---

### Task 6: Pass loaded GIF state to ImageView

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt:19-23` (RouteImageViewer)
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt:260-268` (forum detail → ImageView navigation)
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/image/ImageViewScreen.kt:74-126` (main content)

- [ ] **Step 1: Add `loadedGifUrls` to `RouteImageViewer`**

In `NavigationKeys.kt`, change:

```kotlin
@Serializable
data class RouteImageViewer(
    val images: List<String>,
    val startIndex: Int = 0,
    val loadedGifUrls: Set<String> = emptySet()
) : NavKey
```

Note: `kotlinx.serialization` supports `Set<String>` natively.

- [ ] **Step 2: Change `ForumThreadDetailScreen.onImageClick` signature to include loaded GIF info**

In `ForumThreadDetailScreen.kt`, change the screen signature (line 82):

```kotlin
onImageClick: (List<String>, Int) -> Unit,
```

remains the same — the caller (Navigation.kt) will supply loaded GIF info when constructing the route.

In `ForumThreadDetailScreen.kt`, we need a new callback variant. Add a helper that wraps the click with GIF awareness:

In `ForumThreadDetailScreen`, before the Scaffold, add:

```kotlin
val handleImageClick: (List<String>, Int) -> Unit = { images, startIndex ->
    onImageClick(images, startIndex)
}

val handleGifAwareImageClick: (List<String>, Int, String?) -> Unit = { images, startIndex, _ ->
    onImageClick(images, startIndex)
}
```

Actually, simpler approach: update `PostContent` to pass `onImageClick` directly with the existing signature, and handle GIF state at the navigation level.

Revised approach: Keep `onImageClick` signature as `(List<String>, Int) -> Unit`. The navigation layer needs the loaded GIF set. Add `onImageClickWithGifs` as a new lambda:

In `ForumThreadDetailScreen`, change the screen-level click to carry GIF info. Simpler: just update the `onImageClick` in the screen to also receive loaded GIF URLs.

**Simplest approach:** The `ForumThreadDetailScreen` composable receives `onImageClick`. In `Navigation.kt`, the lambda already has access to `viewModel` if we restructure. Instead, let's pass `loadedGifUrls` as a separate parameter to the screen and build the navigation call inside the screen.

In `ForumThreadDetailScreen.kt`, change the signature:

```kotlin
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int, Set<String>) -> Unit,
    onBack: () -> Unit
)
```

Update all `onImageClick` call sites inside to pass `loadedGifUrls`:

```kotlin
onImageClick(imageUrls, currentIdx, loadedGifUrls)
```

Wait — this changes the interface used in Navigation.kt for other screens too. Better approach:

Keep `onImageClick: (List<String>, Int) -> Unit` unchanged. Add a separate callback:

```kotlin
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int, Set<String>) -> Unit,
    onBack: () -> Unit
)
```

In Navigation.kt (line 263):

```kotlin
onImageClick = { images, startIndex, loadedGifs ->
    backStack.add(RouteImageViewer(images, startIndex, loadedGifs))
},
```

In `ForumThreadDetailScreen`, the `PostContent` call passes `loadedGifUrls`:

```kotlin
PostContent(
    blocks = detail.contentBlocks,
    onImageClick = { images, idx -> onImageClick(images, idx, viewModel.loadedGifUrls) },
    ...
)
```

This keeps `PostContent.onImageClick` as `(List<String>, Int) -> Unit` and only the screen-level signature changes.

- [ ] **Step 2 (revised): Update `ForumThreadDetailScreen` signature**

Change `ForumThreadDetailScreen` signature (line 80-83):

```kotlin
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int, Set<String>) -> Unit,
    onBack: () -> Unit
)
```

Wrap the `PostContent` `onImageClick` to capture `loadedGifUrls`:

```kotlin
val imageClickHandler: (List<String>, Int) -> Unit = { images, idx ->
    onImageClick(images, idx, viewModel.loadedGifUrls)
}
```

Use `imageClickHandler` in all `PostContent` and `ReplyItem` calls.

- [ ] **Step 3: Update Navigation.kt forum detail entry**

In `Navigation.kt` (line 260-268):

```kotlin
entry<RouteForumThreadDetail> { key ->
    ForumThreadDetailScreen(
        tid = key.tid,
        onImageClick = { images, startIndex, loadedGifs ->
            backStack.add(RouteImageViewer(images, startIndex, loadedGifs))
        },
        onBack = { backStack.removeLastOrNull() }
    )
}
```

- [ ] **Step 4: Update ImageViewScreen to handle GIF placeholders**

In `ImageViewScreen.kt`, change the function signature:

```kotlin
@Composable
fun ImageViewScreen(
    images: List<String>,
    startIndex: Int = 0,
    loadedGifUrls: Set<String> = emptySet(),
    onBack: () -> Unit = {}
)
```

Update the `HorizontalPager` content (~lines 107-126):

```kotlin
) { page ->
    val imageUrl = images[page]
    val isGif = imageUrl.substringBefore("?").substringBefore("#").lowercase().endsWith(".gif")
    val isLoaded = !isGif || imageUrl in loadedGifUrls

    if (!isLoaded) {
        GifPlaceholder(
            onClick = {
                // Load this GIF — need mutable state
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 3f))),
                onState = { imageState = it }
            )
            if (imageState is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
```

For the GIF click-to-load in ImageView, use a local mutable state set:

```kotlin
var localLoadedGifs by remember(loadedGifUrls) { mutableStateOf(loadedGifUrls.toMutableStateSet()) }
```

Then the placeholder `onClick`:

```kotlin
onClick = { localLoadedGifs.add(imageUrl) }
```

And check `isLoaded` against `localLoadedGifs` instead of `loadedGifUrls`.

Add import:

```kotlin
import androidx.compose.runtime.mutableStateSetOf
import me.jbusdriver.modern.ui.components.GifPlaceholder
```

- [ ] **Step 5: Update Navigation.kt ImageView entry**

In `Navigation.kt` (line 230-235):

```kotlin
entry<RouteImageViewer> { key ->
    ImageViewScreen(
        images = key.images,
        startIndex = key.startIndex,
        loadedGifUrls = key.loadedGifUrls,
        onBack = { backStack.removeLastOrNull() }
    )
}
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt app/src/main/java/me/jbusdriver/modern/ui/image/ImageViewScreen.kt
git commit -m "feat: pass loaded GIF state to ImageView for lazy loading"
```

---

### Task 7: Final verification build

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual test checklist**

1. Open a forum thread containing GIF images
2. Verify GIFs show placeholder with play icon + "點擊加載"
3. Tap a GIF placeholder → it should load and display the animated GIF
4. Tap the loaded GIF → should enter ImageView
5. In ImageView, the already-loaded GIF should display without placeholder
6. Navigate to a GIF that wasn't loaded in the post → should show placeholder in ImageView
7. Tap placeholder in ImageView → should load and display
8. Verify static images are unaffected (load immediately as before)
9. Verify the thumbnail strip at bottom of ImageView still works
