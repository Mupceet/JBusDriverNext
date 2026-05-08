# Image Viewer Enhancement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify cover+sample images into one viewer session, add bottom thumbnail strip, and add save/share buttons.

**Architecture:** `MovieDetailScreen.DetailContent` pre-builds a unified image list and passes it to all image click handlers. `ImageViewScreen` adds a bottom thumbnail `LazyRow` and top-right action icons. Save uses `MediaStore` API for gallery persistence; share uses `FileProvider` + `Intent.ACTION_SEND`.

**Tech Stack:** Jetpack Compose, Coil, OkHttp, MediaStore, FileProvider

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `ui/detail/MovieDetailScreen.kt` | Unify cover+samples into single image list |
| Modify | `ui/image/ImageViewScreen.kt` | Add thumbnail strip, save, share |
| Create | `res/xml/file_provider_paths.xml` | FileProvider share paths config |
| Modify | `AndroidManifest.xml` | Register FileProvider |

---

### Task 1: Unify cover and sample images in DetailContent

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

This task changes how `DetailContent` calls `onImageClick` so that both cover and sample clicks pass a unified `[cover, sample1, sample2, ...]` list.

- [ ] **Step 1: Add unified image list to DetailContent**

In the `DetailContent` function, add a `remember`ed unified image list right after the `coverAspectRatio` block (after line `detail.headers.firstOrNull()?.value ?: ""`):

```kotlin
    val allImages = remember(detail.cover, detail.imageSamples) {
        listOf(detail.cover) + detail.imageSamples.map { it.image }
    }
```

- [ ] **Step 2: Update cover click to use allImages**

Replace the cover's clickable modifier (line 267):

```kotlin
.clickable { onImageClick(listOf(detail.cover), 0) }
```

with:

```kotlin
.clickable { onImageClick(allImages, 0) }
```

- [ ] **Step 3: Update ImageSampleSection to use allImages**

Change the `ImageSampleSection` call (around line 328-331) to pass `allImages`:

```kotlin
                ImageSampleSection(
                    samples = detail.imageSamples,
                    allImages = allImages,
                    onImageClick = onImageClick
                )
```

- [ ] **Step 4: Update ImageSampleSection signature and clickable**

Update the `ImageSampleSection` function signature to accept `allImages`:

```kotlin
@Composable
private fun ImageSampleSection(
    samples: List<ImageSampleUiModel>,
    allImages: List<String>,
    onImageClick: (List<String>, Int) -> Unit
) {
```

Update the sample click handler inside `ImageSampleSection`. Replace:

```kotlin
                        .clickable {
                            val images = samples.map { it.image }
                            onImageClick(images, index)
                        },
```

with:

```kotlin
                        .clickable {
                            onImageClick(allImages, index + 1)
                        },
```

The `+ 1` offset accounts for the cover being at index 0.

- [ ] **Step 5: Build to verify**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt
git commit -m "feat: unify cover and sample images into single image viewer session"
```

---

### Task 2: Add FileProvider for image sharing

**Files:**
- Create: `app/src/main/res/xml/file_provider_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create file_provider_paths.xml**

Create `app/src/main/res/xml/file_provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="shared_images" path="shared_images/" />
</paths>
```

- [ ] **Step 2: Register FileProvider in AndroidManifest.xml**

Add the `<provider>` element inside the `<application>` tag, after the `</activity>` closing tag:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_provider_paths" />
        </provider>
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (manifest merging should pass)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_provider_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: add FileProvider configuration for image sharing"
```

---

### Task 3: Add thumbnail strip, save, and share to ImageViewScreen

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/image/ImageViewScreen.kt`

This is the main task. Replace the entire file with the enhanced version.

- [ ] **Step 1: Rewrite ImageViewScreen.kt**

Replace the full file content with:

```kotlin
package me.jbusdriver.modern.ui.image

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.R
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewScreen(
    images: List<String>,
    startIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    DisposableEffect(isDarkTheme) {
        val window = (view.context as Activity).window
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
        pageCount = { images.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = images[page],
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

        // Top bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            saveToGallery(context, images[pagerState.currentPage])
                        }
                    }) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "保存",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            shareImage(context, images[pagerState.currentPage])
                        }
                    }) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "分享",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White
                )
            )
        }

        // Bottom thumbnail strip
        if (images.size > 1) {
            ThumbnailStrip(
                images = images,
                currentPage = pagerState.currentPage,
                onPageClick = { page ->
                    scope.launch { pagerState.scrollToPage(page) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(
    images: List<String>,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(images) { index, imageUrl ->
            val isSelected = index == currentPage
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 48.dp, height = 36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .clickable { onPageClick(index) }
            )
        }
    }
}

private suspend fun saveToGallery(context: Context, imageUrl: String) {
    try {
        val bytes = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().body?.bytes()
        } ?: throw IllegalStateException("Empty response")

        withContext(Dispatchers.IO) {
            val filename = "JBus_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JBus")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ) ?: throw IllegalStateException("Failed to create media store entry")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "已保存到相冊", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun shareImage(context: Context, imageUrl: String) {
    try {
        val file = withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            val bytes = response.body?.bytes() ?: throw IllegalStateException("Empty response")

            val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(shareDir, "share_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享圖片"))
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "分享失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

Key design decisions in this code:
- **Icons**: `Favorite` for save (heart = "save to collection"), `Send` for share (paper plane = "send out")
- **Thumbnail strip**: Only shows when there are 2+ images. Uses `LaunchedEffect(currentPage)` to auto-scroll. Current page gets 2dp white border.
- **Save**: Uses `MediaStore` with `IS_PENDING` on API 29+, saves to `Pictures/JBus/` directory
- **Share**: Downloads to `cacheDir/shared_images/`, uses `FileProvider` to get a content URI
- **Both operations**: Run on `Dispatchers.IO`, show Toast on success/failure on Main

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/image/ImageViewScreen.kt
git commit -m "feat: add thumbnail strip, save-to-gallery, and share to image viewer"
```

---

### Task 4: Build and verify

- [ ] **Step 1: Full build + unit tests**

Run: `./gradlew assembleDebug test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Manual smoke test checklist**

On device/emulator, verify:
1. Detail page → click cover → viewer shows cover + all samples (cover first)
2. Detail page → click sample → viewer opens at correct sample (index offset +1)
3. Viewer → bottom thumbnail strip visible when 2+ images
4. Swipe in viewer → thumbnail highlight follows
5. Click thumbnail → viewer jumps to that page
6. Save button (heart) → image saved to Pictures/JBus/
7. Share button (send) → system share sheet appears
8. Single image (no samples) → no thumbnail strip shown

---

## Self-Review

1. **Spec coverage:**
   - Feature 1 (unified list) → Task 1
   - Feature 2 (thumbnail strip) → Task 3
   - Feature 3 (save/share) → Task 2 (FileProvider) + Task 3 (UI + logic)

2. **Placeholder scan:** No TBD/TODO/vague steps. All code is complete.

3. **Type consistency:**
   - `allImages: List<String>` — built in Task 1, consumed by both cover and sample clicks
   - `ImageSampleSection(samples, allImages, onImageClick)` — Task 1 updates signature
   - `ThumbnailStrip(images, currentPage, onPageClick)` — Task 3 defines and uses
   - `saveToGallery(context, imageUrl)` / `shareImage(context, imageUrl)` — private suspend functions in Task 3
   - FileProvider authority: `${applicationId}.fileprovider` — matches `context.packageName + ".fileprovider"` in code
   - `cache-path name="shared_images" path="shared_images/"` — matches `File(context.cacheDir, "shared_images")` in code
