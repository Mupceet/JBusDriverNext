# Forum Thread Text Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove link click handling from forum thread detail, replace with plain selectable text, and add a share button to open threads in a browser.

**Architecture:** Simplify data model (remove `TextPart.Link`), parser (treat `<a>` as plain text), and UI (replace `ClickableText` with `SelectionContainer + Text`). Add share button to top bar using `Intent.ACTION_SEND`.

**Tech Stack:** Kotlin, Jetpack Compose, Gson, Android Intent

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt` | **Modify** | Remove `TextPart.Link`, simplify `ContentBlockTypeAdapter` |
| `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt` | **Modify** | Treat `<a>` tags as plain text |
| `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt` | **Modify** | Replace clickable text with SelectionContainer, remove link handler, add share button |

---

### Task 1: Remove TextPart.Link from data model and serializer

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt`

- [ ] **Step 1: Remove `TextPart.Link` subclass**

In `ForumModels.kt`, replace the `TextPart` sealed class (lines 123-130):

```kotlin
@Immutable
sealed class TextPart {
    @Immutable
    data class Plain(val text: String) : TextPart()
}
```

- [ ] **Step 2: Simplify `ContentBlockTypeAdapter.write` — remove Link branch**

In the `write` method, replace the `parts.forEach` block inside the `richtext` case (lines 152-159):

```kotlin
                out.name("parts").beginArray()
                value.parts.forEach { part ->
                    out.beginObject()
                    when (part) {
                        is TextPart.Plain -> { out.name("type").value("plain").name("text").value(part.text) }
                    }
                    out.endObject()
                }
                out.endArray()
```

- [ ] **Step 3: Simplify `ContentBlockTypeAdapter.read` — handle legacy link data gracefully**

In the `read` method, replace the inner `when (partType)` block (lines 209-211):

```kotlin
                            when (partType) {
                                "plain" -> list.add(TextPart.Plain(partText))
                                "link" -> list.add(TextPart.Plain(partText))
                            }
```

This ensures that old cached data containing `"type":"link"` entries deserializes correctly as `Plain` text (discarding the URL).

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt
git commit -m "refactor: remove TextPart.Link, simplify ContentBlockTypeAdapter"
```

---

### Task 2: Simplify ForumThreadParser — treat <a> as plain text

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt`

- [ ] **Step 1: Replace the `"a"` tag handling**

In `ForumThreadParser.kt`, find the `"a"` case in the `when (node.tagName())` block (around line 213-220). Replace:

```kotlin
                    "a" -> {
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            parts.add(TextPart.Plain(text))
                        }
                    }
```

The `href` extraction (`node.attr("abs:href")`) is removed entirely.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt
git commit -m "refactor: treat <a> tags as plain text in forum parser"
```

---

### Task 3: Simplify ForumThreadDetailScreen UI and add share button

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`

This is the largest task. Three changes:
1. Remove `RichTextContent`, `SelectableRichTextContent`, `buildAnnotatedString`, `rememberLinkClickHandler`
2. Simplify `PostContent` to use `SelectionContainer + Text`
3. Add share button to top bar

- [ ] **Step 1: Remove unused imports and composables**

Remove these imports that are no longer needed:
```kotlin
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.input.pointer.pointerInput
import me.jbusdriver.modern.ui.RouteForumThreadDetail
```

Delete these entire composable functions (they are all `private`):
- `RichTextContent`
- `SelectableRichTextContent`
- `buildAnnotatedString`
- `rememberLinkClickHandler`

- [ ] **Step 2: Simplify PostContent — remove link click, selection mode toggle**

Replace the entire `PostContent` composable with:

```kotlin
@Composable
private fun PostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    loadedGifUrls: Set<String> = emptySet(),
    onLoadAllGifs: (Collection<String>) -> Unit = {}
) {
    val allGifUrls = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Image>().filter { it.isGif }.map { it.url }
    }
    val viewableImageUrls = remember(blocks, loadedGifUrls) {
        blocks.filterIsInstance<ContentBlock.Image>()
            .filter { !it.isGif || it.url in loadedGifUrls }
            .map { it.url }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var viewableIndex = 0
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.RichText -> {
                    val text = block.parts.joinToString("") { (it as TextPart.Plain).text }
                    SelectionContainer {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
                is ContentBlock.Image -> {
                    if (block.isGif && block.url !in loadedGifUrls) {
                        GifPlaceholder(
                            onClick = { onLoadAllGifs(allGifUrls) },
                            modifier = if (block.isFullSize) {
                                Modifier.fillMaxWidth().height(180.dp)
                            } else {
                                Modifier.size(48.dp)
                            }
                        )
                    } else {
                        val currentIdx = viewableIndex++
                        if (block.isFullSize) {
                            AsyncImage(
                                model = block.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(viewableImageUrls, currentIdx) },
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            AsyncImage(
                                model = block.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onImageClick(viewableImageUrls, currentIdx) },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                is ContentBlock.Quote -> {
                    val accentColor = MaterialTheme.colorScheme.primary
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .drawBehind {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                        ) {
                            Column {
                                if (block.author.isNotEmpty()) {
                                    Text(
                                        "${block.author}：",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                SelectionContainer {
                                    Text(
                                        block.content,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

Key changes from original:
- Removed `onLinkClick` parameter
- Removed `selectionMode` state and toggle logic
- Removed `contentModifier` with `detectTapGestures`
- `RichText` now joins all parts into one string, wrapped in `SelectionContainer`
- `Quote.content` also wrapped in `SelectionContainer`
- Image handling unchanged

- [ ] **Step 3: Simplify ReplyItem — remove onLinkClick**

Replace the `ReplyItem` composable signature and body to remove `onLinkClick`:

```kotlin
@Composable
private fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    loadedGifUrls: Set<String> = emptySet(),
    onLoadAllGifs: (Collection<String>) -> Unit = {}
) {
```

And in its body, the `PostContent` call inside (around line 635) becomes:

```kotlin
                PostContent(
                    blocks = reply.contentBlocks,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(top = 4.dp),
                    loadedGifUrls = loadedGifUrls,
                    onLoadAllGifs = onLoadAllGifs
                )
```

- [ ] **Step 4: Remove handleLinkClick and onLinkClick from callers**

In `ForumThreadDetailScreen`, remove the `val handleLinkClick = rememberLinkClickHandler()` line.

Update the `PostContent` call in the main content (around line 165-172):

```kotlin
                                PostContent(
                                    blocks = detail.contentBlocks,
                                    onImageClick = onImageClick,
                                    modifier = Modifier.padding(12.dp),
                                    loadedGifUrls = loadedGifUrls,
                                    onLoadAllGifs = { viewModel.onLoadAllGifs(it) }
                                )
```

Update the `ReplyItem` call in the replies section (around line 192-198):

```kotlin
                            items(count = detail.replies.size, key = { "reply_$it" }) { index ->
                                ReplyItem(
                                    reply = detail.replies[index],
                                    onImageClick = onImageClick,
                                    loadedGifUrls = loadedGifUrls,
                                    onLoadAllGifs = { viewModel.onLoadAllGifs(it) }
                                )
                            }
```

- [ ] **Step 5: Add share button to TopAppBar**

Add import:
```kotlin
import android.content.Intent
```

Replace the `TopAppBar` in `ForumThreadDetailScreen` with:

```kotlin
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = "${me.jbusdriver.modern.core.http.NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享帖子"))
                    }) {
                        Icon(painterResource(R.drawable.share_24px), contentDescription = "分享")
                    }
                }
            )
```

This requires a `val context = LocalContext.current` in the composable scope. Add it near the top of `ForumThreadDetailScreen` (after the `val scope` line):

```kotlin
    val context = androidx.compose.ui.platform.LocalContext.current
```

Also add the missing import:
```kotlin
import androidx.compose.ui.platform.LocalContext
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt
git commit -m "refactor: simplify forum text to SelectionContainer, add share button"
```

---

### Task 4: Build and verify

- [ ] **Step 1: Full debug build**

Run: `./gradlew assembleDebug 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install and verify**

Manual verification:
1. Open a forum thread — text should be plain, no blue/underline links
2. Long-press text — selection handles should appear
3. Tap share icon in top bar — system share sheet should appear with thread URL
4. Share to browser — should open the thread page
5. Images and GIFs should still work as before
6. Quote blocks should render correctly with selectable text

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix: adjustments from smoke test"
```

---

## Self-Review

**Spec coverage:**
- Remove `TextPart.Link` from sealed class → Task 1 ✓
- Simplify `ContentBlockTypeAdapter` → Task 1 ✓
- Parser: `<a>` → `TextPart.Plain` → Task 2 ✓
- UI: replace ClickableText with SelectionContainer → Task 3 ✓
- Remove `rememberLinkClickHandler` → Task 3 ✓
- Remove selection mode toggle → Task 3 ✓
- Share button in top bar → Task 3 ✓
- Quote blocks with selectable text → Task 3 ✓

**Placeholder scan:** No TBD/TODO. All code blocks contain complete implementation.

**Type consistency:** `TextPart.Plain` used consistently across all tasks. `PostContent` signature matches between definition and all call sites (in main content and `ReplyItem`).
