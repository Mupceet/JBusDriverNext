# Forum Rich Text: Hyperlink Rendering & Text Selection

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render hyperlinks in forum posts as clickable styled text, and support long-press to enter text selection/copy mode.

**Architecture:** Replace `ContentBlock.Text` with `ContentBlock.RichText(parts: List<TextPart>)` where parts are `Plain` or `Link`. Parser emits these from HTML `<a>` tags. UI builds `AnnotatedString` with link spans, uses `ClickableText` for browsing and `SelectionContainer` for copy mode.

**Tech Stack:** Jetpack Compose (`ClickableText`, `SelectionContainer`, `AnnotatedString`), Jsoup HTML parsing, Hilt DI.

---

### Task 1: Update data models in ForumModels.kt

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt`

- [ ] **Step 1: Add `TextPart` sealed class and replace `ContentBlock.Text` with `ContentBlock.RichText`**

Replace the `ContentBlock` sealed class (lines 124–133) with:

```kotlin
sealed class TextPart {
    @Immutable
    data class Plain(val text: String) : TextPart()

    @Immutable
    data class Link(val text: String, val url: String) : TextPart()
}

sealed class ContentBlock {
    @Immutable
    data class RichText(val parts: List<TextPart>) : ContentBlock()

    @Immutable
    data class Image(val url: String, val width: Int = 0, val height: Int = 0, val isFullSize: Boolean = false) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()
}
```

- [ ] **Step 2: Update `ContentBlockTypeAdapter` to handle RichText + TextPart serialization**

Replace the existing `ContentBlockTypeAdapter` class (lines 135–187) with:

```kotlin
class ContentBlockTypeAdapter : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) { out.nullValue(); return }
        out.beginObject()
        when (value) {
            is ContentBlock.RichText -> {
                out.name("type").value("richtext")
                out.name("parts").beginArray()
                value.parts.forEach { part ->
                    out.beginObject()
                    when (part) {
                        is TextPart.Plain -> { out.name("type").value("plain").name("text").value(part.text) }
                        is TextPart.Link -> { out.name("type").value("link").name("text").value(part.text).name("url").value(part.url) }
                    }
                    out.endObject()
                }
                out.endArray()
            }
            is ContentBlock.Image -> {
                out.name("type").value("image").name("url").value(value.url)
                if (value.width > 0) out.name("width").value(value.width)
                if (value.height > 0) out.name("height").value(value.height)
                if (value.isFullSize) out.name("fullSize").value(true)
            }
            is ContentBlock.Quote -> { out.name("type").value("quote").name("author").value(value.author).name("content").value(value.content) }
        }
        out.endObject()
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
            `in`.nextNull()
            return null
        }
        `in`.beginObject()
        var type = ""
        var parts = listOf<TextPart>()
        var url = ""
        var width = 0
        var height = 0
        var fullSize = false
        var author = ""
        var content = ""
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "type" -> type = `in`.nextString()
                "parts" -> {
                    val list = mutableListOf<TextPart>()
                    `in`.beginArray()
                    while (`in`.hasNext()) {
                        `in`.beginObject()
                        var partType = ""
                        var partText = ""
                        var partUrl = ""
                        while (`in`.hasNext()) {
                            when (`in`.nextName()) {
                                "type" -> partType = `in`.nextString()
                                "text" -> partText = `in`.nextString()
                                "url" -> partUrl = `in`.nextString()
                                else -> `in`.skipValue()
                            }
                        }
                        `in`.endObject()
                        when (partType) {
                            "plain" -> list.add(TextPart.Plain(partText))
                            "link" -> list.add(TextPart.Link(partText, partUrl))
                        }
                    }
                    `in`.endArray()
                    parts = list
                }
                "url" -> url = `in`.nextString()
                "width" -> width = `in`.nextInt()
                "height" -> height = `in`.nextInt()
                "fullSize" -> fullSize = `in`.nextBoolean()
                "author" -> author = `in`.nextString()
                "content" -> content = `in`.nextString()
                else -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return when (type) {
            "richtext" -> ContentBlock.RichText(parts)
            "text" -> ContentBlock.RichText(listOf(TextPart.Plain(content))) // backward compat
            "image" -> ContentBlock.Image(url, width, height, fullSize)
            "quote" -> ContentBlock.Quote(author, content)
            else -> null
        }
    }
}
```

Note: the `"text"` case in `read()` provides backward compatibility for cached data that still uses the old `ContentBlock.Text` format.

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt
git commit -m "feat: add TextPart and RichText to ContentBlock model"
```

---

### Task 2: Rewrite parsePostContent() to emit RichText with links

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt` (lines 628–694)

- [ ] **Step 1: Replace parsePostContent with TextPart-based implementation**

Replace the entire `parsePostContent` function (lines 628–694) with:

```kotlin
private fun parsePostContent(td: org.jsoup.nodes.Element?): List<ContentBlock> {
    if (td == null) return emptyList()

    val blocks = mutableListOf<ContentBlock>()
    val parts = mutableListOf<TextPart>()

    fun flushParts() {
        val nonEmpty = parts.toList()
        parts.clear()
        if (nonEmpty.isNotEmpty()) {
            blocks.add(ContentBlock.RichText(nonEmpty))
        }
    }

    fun processNode(node: org.jsoup.nodes.Node) {
        when (node) {
            is org.jsoup.nodes.TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) {
                    parts.add(TextPart.Plain(text))
                }
            }
            is org.jsoup.nodes.Element -> {
                when (node.tagName()) {
                    "br" -> {
                        flushParts()
                    }
                    "a" -> {
                        val href = node.attr("abs:href")
                        val text = node.text().trim()
                        if (href.isNotEmpty() && text.isNotEmpty()) {
                            parts.add(TextPart.Link(text, href))
                        } else if (text.isNotEmpty()) {
                            parts.add(TextPart.Plain(text))
                        }
                    }
                    "img" -> {
                        val src = node.attr("src").wrapForumImage()
                        if (src.isNotEmpty() && !src.contains("arw_r") && !src.contains("userinfo.gif") && !src.contains("fav.gif") && !src.contains("rec_add")) {
                            flushParts()
                            val w = node.attr("width").toIntOrNull() ?: 0
                            val h = node.attr("height").toIntOrNull() ?: 0
                            val isFullSize = node.hasClass("zoom")
                            blocks.add(ContentBlock.Image(src, w, h, isFullSize))
                        }
                    }
                    "div" -> {
                        if (node.hasClass("quote")) {
                            flushParts()
                            val blockquote = node.select("blockquote").firstOrNull()
                            val authorLink = blockquote?.select("a[href]")?.firstOrNull()
                            val authorName = authorLink?.text()?.trim() ?: ""
                            val quoteText = blockquote?.let { bq ->
                                val clone = bq.clone()
                                clone.select("font > a").remove()
                                clone.text().trim()
                            } ?: ""
                            if (quoteText.isNotEmpty()) {
                                blocks.add(ContentBlock.Quote(authorName, quoteText))
                            }
                        } else if (!node.hasClass("modact") && !node.hasClass("locked") && !node.className().contains("cm")) {
                            node.childNodes().forEach { processNode(it) }
                        }
                    }
                    "font" -> node.childNodes().forEach { processNode(it) }
                    "table" -> node.childNodes().forEach { processNode(it) }
                    else -> node.childNodes().forEach { processNode(it) }
                }
            }
        }
    }

    td.childNodes().forEach { processNode(it) }
    flushParts()

    return blocks
}
```

- [ ] **Step 2: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/parser/HtmlParser.kt
git commit -m "feat: parse <a> tags into TextPart.Link in forum post content"
```

---

### Task 3: Add RichTextContent composable and update PostContent

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`

- [ ] **Step 1: Add required imports**

Add these imports at the top of `ForumThreadDetailScreen.kt` (after existing imports):

```kotlin
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import me.jbusdriver.modern.domain.model.TextPart
```

- [ ] **Step 2: Add RichTextContent composable**

Add this composable function before the `PostContent` function (around line 224):

```kotlin
@Composable
private fun RichTextContent(
    parts: List<TextPart>,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    val linkStyle = androidx.compose.ui.text.SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
    )
    val tag = "URL"

    val annotatedString = buildAnnotatedString(parts, baseStyle, linkStyle, tag)

    ClickableText(
        text = annotatedString,
        style = baseStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

@Composable
private fun SelectableRichTextContent(
    parts: List<TextPart>,
    modifier: Modifier = Modifier
) {
    val fullText = parts.joinToString("") { part ->
        when (part) {
            is TextPart.Plain -> part.text
            is TextPart.Link -> part.text
        }
    }
    SelectionContainer {
        Text(
            fullText,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier
        )
    }
}

private fun buildAnnotatedString(
    parts: List<TextPart>,
    baseStyle: androidx.compose.ui.text.TextStyle,
    linkStyle: androidx.compose.ui.text.SpanStyle,
    tag: String
): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.buildAnnotatedString {
        for (part in parts) {
            when (part) {
                is TextPart.Plain -> append(part.text)
                is TextPart.Link -> {
                    pushStringAnnotation(tag, part.url)
                    pushStyle(linkStyle)
                    append(part.text)
                    pop()
                    pop()
                }
            }
        }
    }
    return builder
}
```

- [ ] **Step 3: Add link click handler composable**

Add this helper that resolves link URLs (forum internal vs external browser). Place it after `RichTextContent`:

```kotlin
@Composable
private fun rememberLinkClickHandler(
    onForumThreadClick: (Int) -> Unit = {},
    onForumBoardClick: (Int) -> Unit = {}
): (String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseUrl = me.jbusdriver.modern.core.http.NetClient.defaultFastUrl

    return remember(context, baseUrl) {
        { url: String ->
            val threadMatch = Regex("""tid=(\d+)""").find(url)
            val fidMatch = Regex("""fid=(\d+)""").find(url)

            when {
                threadMatch != null -> {
                    val tid = threadMatch.groupValues[1].toIntOrNull()
                    if (tid != null) onForumThreadClick(tid)
                }
                fidMatch != null -> {
                    val fid = fidMatch.groupValues[1].toIntOrNull()
                    if (fid != null) onForumBoardClick(fid)
                }
                else -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, "選擇瀏覽器"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "無法打開鏈接", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rewrite PostContent to support browse/select modes**

Replace the existing `PostContent` function (lines 224–312) with:

```kotlin
@Composable
private fun PostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val imageUrls = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Image>().map { it.url }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var imageIndex = 0
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.RichText -> {
                    RichTextContent(
                        parts = block.parts,
                        onLinkClick = onLinkClick
                    )
                }
                is ContentBlock.Image -> {
                    if (block.isFullSize) {
                        val currentIdx = imageIndex++
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
                        val currentIdx = imageIndex++
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
```

- [ ] **Step 5: Wire up link click handler at call sites**

The `PostContent` call in the detail card (around line 151) needs `onLinkClick` passed through. Update the `PostContent` call in the content card:

```kotlin
val handleLinkClick = rememberLinkClickHandler()

// In the LazyColumn item(key = "content"):
PostContent(
    blocks = detail.contentBlocks,
    onImageClick = onImageClick,
    onLinkClick = handleLinkClick,
    modifier = Modifier.padding(12.dp)
)
```

The `ReplyItem` composable also calls `PostContent`. Update `ReplyItem` to accept and pass `onLinkClick`:

```kotlin
@Composable
private fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    onLinkClick: (String) -> Unit = {}
) {
```

And its `PostContent` call:

```kotlin
PostContent(
    blocks = reply.contentBlocks,
    onImageClick = onImageClick,
    onLinkClick = onLinkClick,
    modifier = Modifier.padding(top = 4.dp)
)
```

Update the `ReplyItem` call in the LazyColumn to pass the link handler:

```kotlin
items(count = detail.replies.size, key = { "reply_$it" }) { index ->
    ReplyItem(
        reply = detail.replies[index],
        onImageClick = onImageClick,
        onLinkClick = handleLinkClick
    )
}
```

- [ ] **Step 6: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt
git commit -m "feat: render hyperlinks in forum posts with click support"
```

---

### Task 4: Add long-press text selection mode

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`

- [ ] **Step 1: Add selection mode state to PostContent**

Add `selectionMode` state and gesture detection to `PostContent`. Replace the `PostContent` function with this version that wraps all RichText blocks in a single container with long-press detection:

```kotlin
@Composable
private fun PostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val imageUrls = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Image>().map { it.url }
    }

    var selectionMode by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectionMode) {
            Text(
                "選擇模式 · 點擊空白退出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectionMode = false }
                    .padding(bottom = 4.dp)
            )
        }

        val contentModifier = if (selectionMode) {
            Modifier.pointerInput(Unit) {
                detectTapGestures { selectionMode = false }
            }
        } else {
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { selectionMode = true }
                )
            }
        }

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var imageIndex = 0
            blocks.forEach { block ->
                when (block) {
                    is ContentBlock.RichText -> {
                        if (selectionMode) {
                            SelectableRichTextContent(parts = block.parts)
                        } else {
                            RichTextContent(
                                parts = block.parts,
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                    is ContentBlock.Image -> {
                        if (block.isFullSize) {
                            val currentIdx = imageIndex++
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
                            val currentIdx = imageIndex++
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

- [ ] **Step 2: Add missing imports for selection mode**

Ensure these imports are present (some may already exist):

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

- [ ] **Step 3: Build and verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt
git commit -m "feat: add long-press text selection mode for forum posts"
```

---

## Self-Review

**Spec coverage:**
- Data model (TextPart, RichText): Task 1 ✓
- Parser changes (<a> → TextPart.Link): Task 2 ✓
- AnnotatedString rendering with link styles: Task 3 ✓
- Link click handling (forum internal + browser): Task 3 ✓
- Long-press selection mode with visual hint: Task 4 ✓

**Placeholder scan:** No TBDs, TODOs, or "implement later". All steps have complete code.

**Type consistency:** `TextPart.Plain`/`TextPart.Link` used consistently across model, parser, and UI. `ContentBlock.RichText(parts)` matches everywhere. `onLinkClick: (String) -> Unit` signature consistent across all call sites.
