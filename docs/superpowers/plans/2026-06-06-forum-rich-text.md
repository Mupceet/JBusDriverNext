# Forum Detail Rich Text Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix restricted and pinned Forum replies and render a controlled subset of Forum HTML as native Compose rich text.

**Architecture:** Jsoup converts post HTML into theme-independent semantic blocks and styled text runs. A shared Compose renderer displays those blocks in the first post, replies, and preview dialog, while a pure Kotlin formatter supplies copy text. Existing image, GIF, quote, pagination, and refresh behavior remains in place.

**Tech Stack:** Kotlin 2.3, Jetpack Compose Material 3, Jsoup 1.22, Gson 2.14, JUnit 4, AndroidX Compose UI Test, Gradle/AGP 9.2.

---

## File Structure

- Modify `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt`: semantic rich-text/list models, pinned reply flag, and Gson compatibility.
- Create `app/src/main/java/me/jbusdriver/modern/data/parser/ForumPostParser.kt`: isolated DOM-to-semantic-content parser.
- Modify `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt`: reply floor/restriction extraction and delegation to `ForumPostParser`.
- Create `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt`: shared Compose renderer and pure plain-text conversion.
- Modify `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt`: use the shared renderer and pinned floor label.
- Modify `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt`: version the persisted Forum detail cache key.
- Create `app/src/main/res/drawable/lock_24px.xml`: local lock icon for restricted notices.
- Create `app/src/test/resources/forum/restricted-replies.html`: minimal restricted-reply fixture derived from thread 154969.
- Create `app/src/test/resources/forum/pinned-rich-replies.html`: minimal pinned/rich-text fixture derived from thread 171794.
- Create `app/src/test/java/me/jbusdriver/modern/domain/model/ContentBlockTypeAdapterTest.kt`: model round-trip and legacy JSON tests.
- Create `app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt`: restricted, pinned, ordering, style, list, and whitespace tests.
- Create `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumPlainTextTest.kt`: copy-text tests.
- Create `app/src/androidTest/java/me/jbusdriver/modern/ui/forum/ForumPostContentTest.kt`: renderer semantics and non-clickable link tests.
- Modify `gradle/libs.versions.toml` and `app/build.gradle.kts`: Compose UI test dependencies.

## Task 1: Introduce Semantic Rich-Text Models and Compatible Gson Serialization

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt:112-240`
- Create: `app/src/test/java/me/jbusdriver/modern/domain/model/ContentBlockTypeAdapterTest.kt`

- [ ] **Step 1: Write failing model serialization tests**

Create `ContentBlockTypeAdapterTest.kt`:

```kotlin
package me.jbusdriver.modern.domain.model

import me.jbusdriver.modern.core.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContentBlockTypeAdapterTest {
    @Test
    fun `round trips styled paragraphs lists and restricted notices`() {
        val blocks = listOf<ContentBlock>(
            ContentBlock.RichText(
                paragraphs = listOf(
                    RichParagraph(
                        parts = listOf(
                            TextPart("normal "),
                            TextPart(
                                text = "important",
                                bold = true,
                                color = "#ff0000",
                                size = ForumTextSize.EMPHASIS,
                                isLink = true
                            )
                        )
                    )
                )
            ),
            ContentBlock.ListBlock(
                RichList(
                    ordered = true,
                    items = listOf(
                        RichListItem(
                            paragraphs = listOf(RichParagraph(listOf(TextPart("first")))),
                            children = listOf(
                                RichList(
                                    ordered = false,
                                    items = listOf(
                                        RichListItem(listOf(RichParagraph(listOf(TextPart("nested")))))
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            ContentBlock.RestrictedNotice("此帖僅作者可見")
        )

        val json = GSON.toJson(blocks)
        val decoded = GSON.fromJson(json, Array<ContentBlock>::class.java).toList()

        assertEquals(blocks, decoded)
    }

    @Test
    fun `reads legacy plain parts as one paragraph`() {
        val json = """{"type":"richtext","parts":[{"type":"plain","text":"legacy"}]}"""

        val block = GSON.fromJson(json, ContentBlock::class.java)

        assertEquals(
            ContentBlock.RichText(listOf(RichParagraph(listOf(TextPart("legacy"))))),
            block
        )
    }

    @Test
    fun `forum reply defaults to unpinned for legacy json`() {
        val json = """{
            "floor":5,"author":"a","authorUid":1,"authorAvatar":"",
            "authorGroup":"","contentBlocks":[],"postTime":"now"
        }""".trimIndent()

        val reply = GSON.fromJson(json, ForumReply::class.java)

        assertFalse(reply.isPinned)
    }
}
```

- [ ] **Step 2: Run the tests and confirm the new types are missing**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.domain.model.ContentBlockTypeAdapterTest"
```

Expected: compilation fails because `RichParagraph`, `ForumTextSize`, `RichList`, `ListBlock`, `RestrictedNotice`, styled `TextPart`, and `ForumReply.isPinned` do not exist.

- [ ] **Step 3: Replace the plain-only model with semantic types**

Use these definitions in `ForumModels.kt`:

```kotlin
@Immutable
data class ForumReply(
    val floor: Int,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val authorGroup: String,
    val contentBlocks: List<ContentBlock>,
    val postTime: String,
    val isPinned: Boolean = false
)

enum class ForumTextSize { BODY, EMPHASIS, HEADING }

@Immutable
data class TextPart(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: String? = null,
    val size: ForumTextSize = ForumTextSize.BODY,
    val isLink: Boolean = false
)

@Immutable
data class RichParagraph(val parts: List<TextPart>)

@Immutable
data class RichListItem(
    val paragraphs: List<RichParagraph>,
    val children: List<RichList> = emptyList()
)

@Immutable
data class RichList(
    val ordered: Boolean,
    val start: Int = 1,
    val items: List<RichListItem>
)

@Immutable
sealed class ContentBlock {
    @Immutable
    data class RichText(val paragraphs: List<RichParagraph>) : ContentBlock()

    @Immutable
    data class ListBlock(val list: RichList) : ContentBlock()

    @Immutable
    data class Image(
        val url: String,
        val width: Int = 0,
        val height: Int = 0,
        val isFullSize: Boolean = false,
        val isGif: Boolean = false
    ) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()

    @Immutable
    data class RestrictedNotice(val message: String) : ContentBlock()
}
```

- [ ] **Step 4: Update `ContentBlockTypeAdapter` with explicit new and legacy formats**

Change the adapter factory to pass `Gson` into the adapter. Serialize with these discriminator values and payload fields:

```kotlin
private val paragraphListType = object : TypeToken<List<RichParagraph>>() {}.type
private val richListType = object : TypeToken<RichList>() {}.type

class ContentBlockTypeAdapter(private val gson: Gson) : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) {
            out.nullValue()
            return
        }
        val json = com.google.gson.JsonObject()
        when (value) {
            is ContentBlock.RichText -> {
                json.addProperty("type", "richtext")
                json.add("paragraphs", gson.toJsonTree(value.paragraphs, paragraphListType))
            }
            is ContentBlock.ListBlock -> {
                json.addProperty("type", "list")
                json.add("list", gson.toJsonTree(value.list, richListType))
            }
            is ContentBlock.Image -> {
                json.addProperty("type", "image")
                json.addProperty("url", value.url)
                json.addProperty("width", value.width)
                json.addProperty("height", value.height)
                json.addProperty("fullSize", value.isFullSize)
                json.addProperty("isGif", value.isGif)
            }
            is ContentBlock.Quote -> {
                json.addProperty("type", "quote")
                json.addProperty("author", value.author)
                json.addProperty("content", value.content)
            }
            is ContentBlock.RestrictedNotice -> {
                json.addProperty("type", "restricted")
                json.addProperty("message", value.message)
            }
        }
        gson.toJson(json, out)
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
            `in`.nextNull()
            return null
        }
        val json = com.google.gson.JsonParser.parseReader(`in`).asJsonObject
        return when (json["type"]?.asString.orEmpty()) {
            "richtext" -> {
                val paragraphs = json["paragraphs"]?.let {
                    gson.fromJson<List<RichParagraph>>(it, paragraphListType)
                } ?: json["parts"]?.asJsonArray?.mapNotNull { part ->
                    val text = part.asJsonObject["text"]?.asString.orEmpty()
                    text.takeIf(String::isNotEmpty)?.let { TextPart(it) }
                }?.let { listOf(RichParagraph(it)) }.orEmpty()
                ContentBlock.RichText(paragraphs)
            }
            "text" -> ContentBlock.RichText(
                listOf(RichParagraph(listOf(TextPart(json["content"]?.asString.orEmpty()))))
            )
            "list" -> ContentBlock.ListBlock(gson.fromJson(json["list"], richListType))
            "image" -> ContentBlock.Image(
                url = json["url"]?.asString.orEmpty(),
                width = json["width"]?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                height = json["height"]?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                isFullSize = json["fullSize"]?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
                isGif = json["isGif"]?.takeUnless { it.isJsonNull }?.asBoolean ?: false
            )
            "quote" -> ContentBlock.Quote(
                json["author"]?.asString.orEmpty(),
                json["content"]?.asString.orEmpty()
            )
            "restricted" -> ContentBlock.RestrictedNotice(json["message"]?.asString.orEmpty())
            else -> null
        }
    }
}

object ContentBlockAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!ContentBlock::class.java.isAssignableFrom(type.rawType)) return null
        @Suppress("UNCHECKED_CAST")
        return ContentBlockTypeAdapter(gson) as TypeAdapter<T>
    }
}
```

Keep the adapter fields at file level so each read/write does not rebuild `TypeToken` instances. Apply the same `takeUnless { it.isJsonNull }` guard to every optional primitive JSON member.

- [ ] **Step 5: Run the model tests**

Run the Task 1 test command again.

Expected: all three tests pass.

- [ ] **Step 6: Commit the model change**

```powershell
git add app/src/main/java/me/jbusdriver/modern/domain/model/ForumModels.kt app/src/test/java/me/jbusdriver/modern/domain/model/ContentBlockTypeAdapterTest.kt
git commit -m "refactor: add semantic forum content models"
```

## Task 2: Parse Restricted and Pinned Replies

**Files:**
- Create: `app/src/test/resources/forum/restricted-replies.html`
- Create: `app/src/test/resources/forum/pinned-rich-replies.html`
- Create: `app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt:137-170`

- [ ] **Step 1: Add minimal UTF-8 fixtures**

The restricted fixture must contain a first post and one reply with no `td.t_f`:

```html
<html><body>
<span id="thread_subject">申訴區</span>
<div class="nthread_postbox nthread_firstpostbox">
  <a class="au" href="home.php?mod=space&uid=1">管理員</a>
  <table><tbody><tr><td class="t_f" id="postmessage_1">首帖</td></tr></tbody></table>
</div>
<div id="post_4158039" class="nthread_postbox">
  <a id="postnum4158039"><em>2</em><sup>#</sup></a>
  <a class="xw1" href="home.php?mod=space&uid=620784">Mmmmm.</a>
  <div class="locked">此帖僅作者可見</div>
</div>
</body></html>
```

The pinned fixture must contain floor anchors with the saved-page structure:

```html
<html><body>
<span id="thread_subject">置頂回復</span>
<div class="nthread_postbox nthread_firstpostbox">
  <a class="au" href="home.php?mod=space&uid=1">author</a>
  <table><tbody><tr><td class="t_f" id="postmessage_1">first</td></tr></tbody></table>
</div>
<div class="nthread_postbox">
  <a id="postnum4765049"><img src="settop.png" title="置頂回復"> 來自 2#</a>
  <a class="xw1" href="home.php?mod=space&uid=2">two</a>
  <table><tbody><tr><td class="t_f">reply two</td></tr></tbody></table>
</div>
<div class="nthread_postbox">
  <a id="postnum4766058"><img src="settop.png" title="置頂回復"> 來自 3#</a>
  <a class="xw1" href="home.php?mod=space&uid=3">three</a>
  <table><tbody><tr><td class="t_f">reply three</td></tr></tbody></table>
</div>
<div class="nthread_postbox">
  <a id="postnum4766944"><img src="settop.png" title="置頂回復"> 來自 4#</a>
  <a class="xw1" href="home.php?mod=space&uid=4">four</a>
  <table><tbody><tr><td class="t_f">reply four</td></tr></tbody></table>
</div>
<div class="nthread_postbox">
  <a id="postnum4764777"><em>5</em><sup>#</sup></a>
  <a class="xw1" href="home.php?mod=space&uid=5">five</a>
  <table><tbody><tr><td class="t_f">reply five</td></tr></tbody></table>
</div>
</body></html>
```

- [ ] **Step 2: Add failing reply parsing tests**

Append these helpers and tests to `ForumThreadParserTest.kt`:

```kotlin
package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumThreadParserTest {
    private fun fixture(name: String, location: String) =
        Jsoup.parse(
            checkNotNull(javaClass.getResourceAsStream("/forum/$name"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() },
            location
        )

    @Test
    fun `restricted reply remains visible without normal body cell`() {
        val detail = parseForumThreadDetail(
            fixture("restricted-replies.html", "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=154969"),
            "https://www.javbus.com"
        )

        assertEquals(1, detail.replies.size)
        assertEquals(2, detail.replies.single().floor)
        assertEquals(
            listOf(ContentBlock.RestrictedNotice("此帖僅作者可見")),
            detail.replies.single().contentBlocks
        )
    }

    @Test
    fun `pinned reply floors are parsed and retain document order`() {
        val replies = parseForumThreadDetail(
            fixture("pinned-rich-replies.html", "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"),
            "https://www.javbus.com"
        ).replies

        assertEquals(listOf(2, 3, 4, 5), replies.map { it.floor })
        assertTrue(replies.take(3).all { it.isPinned })
        assertFalse(replies.last().isPinned)
    }
}
```

- [ ] **Step 3: Run the parser tests and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest"
```

Expected: restricted content is empty and pinned replies 2-4 are absent.

- [ ] **Step 4: Add floor and restriction helpers**

Add to `ForumThreadParser.kt`:

```kotlin
private data class ReplyFloor(val number: Int, val isPinned: Boolean)

private fun parseReplyFloor(postBox: Element): ReplyFloor? {
    val anchor = postBox.selectFirst("a[id^=postnum]") ?: return null
    val number = anchor.selectFirst("em")?.text()?.trim()?.toIntOrNull()
        ?: Regex("(\\d+)\\s*#").find(anchor.text())?.groupValues?.get(1)?.toIntOrNull()
        ?: return null
    val isPinned = anchor.select("img[title*=置頂], img[src*=settop]").isNotEmpty() ||
        anchor.text().contains("來自")
    return ReplyFloor(number, isPinned)
}

private fun parseReplyContent(postBox: Element, baseUrl: String): List<ContentBlock> {
    val restricted = postBox.selectFirst(".locked")?.text()?.trim().orEmpty()
    if (restricted.isNotEmpty()) return listOf(ContentBlock.RestrictedNotice(restricted))
    return parseForumPostContent(postBox.selectFirst("td.t_f"), baseUrl)
}
```

Change the reply loop to use `val floor = parseReplyFloor(postBox) ?: return@mapNotNull null`, assign `floor.number`, `floor.isPinned`, and `parseReplyContent(postBox, baseUrl)`.

Temporarily rename the current `parsePostContent` to `parseForumPostContent` and make it `internal`; Task 3 moves it into its own file.

- [ ] **Step 5: Run the reply parser tests**

Run the Task 2 test command again.

Expected: both tests pass.

- [ ] **Step 6: Commit the reply fixes**

```powershell
git add app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt app/src/test/resources/forum
git commit -m "fix: parse restricted and pinned forum replies"
```

## Task 3: Parse Controlled Rich Text, Lists, Colors, and Whitespace

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/parser/ForumPostParser.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/parser/ForumThreadParser.kt:137-138`
- Modify: `app/src/test/resources/forum/pinned-rich-replies.html`
- Modify: `app/src/test/java/me/jbusdriver/modern/data/parser/ForumThreadParserTest.kt`

- [ ] **Step 1: Expand the fixture with supported and malformed markup**

Replace the content inside floor 2's `td.t_f` with:

```html
<font size="5"><font color="#ff0000"><strong>观前提醒：</strong></font></font><br>
normal <a href="https://example.com">link text</a>
<ol start="2">
  <li>first <em>italic</em></li>
  <li>second<ul><li><u>nested</u></li></ul></li>
</ol>
<span style="color:not-a-color"><del>removed</del></span>
<custom-tag>kept text</custom-tag>
```

- [ ] **Step 2: Add failing semantic parsing tests**

Add tests that assert exact model values:

```kotlin
@Test
fun `parses controlled inline styles and preserves spaces`() {
    val blocks = parsedPinnedReply(2).contentBlocks
    val richText = blocks.filterIsInstance<ContentBlock.RichText>().first()
    val parts = richText.paragraphs.flatMap { it.parts }

    assertEquals("观前提醒：", parts.first().text)
    assertTrue(parts.first().bold)
    assertEquals("#ff0000", parts.first().color)
    assertEquals(ForumTextSize.HEADING, parts.first().size)
    assertEquals("normal link text", parts.drop(1).take(3).joinToString("") { it.text }.trim())
    assertTrue(parts.single { it.text == "link text" }.isLink)
    assertFalse(parts.single { it.text == "link text" }.underline)
}

@Test
fun `parses ordered and nested unordered lists`() {
    val list = parsedPinnedReply(2).contentBlocks
        .filterIsInstance<ContentBlock.ListBlock>().single().list

    assertTrue(list.ordered)
    assertEquals(2, list.start)
    assertEquals(listOf("first italic", "second"), list.items.map { item ->
        item.paragraphs.flatMap { it.parts }.joinToString("") { it.text }.trim()
    })
    assertFalse(list.items[1].children.single().ordered)
    assertEquals("nested", list.items[1].children.single().items.single()
        .paragraphs.single().parts.single().text)
}

@Test
fun `unknown tags retain text and invalid colors are discarded`() {
    val parts = parsedPinnedReply(2).contentBlocks
        .filterIsInstance<ContentBlock.RichText>()
        .flatMap { it.paragraphs }
        .flatMap { it.parts }

    assertTrue(parts.any { it.text.contains("kept text") })
    assertEquals(null, parts.single { it.text == "removed" }.color)
    assertTrue(parts.single { it.text == "removed" }.strikethrough)
}

private fun parsedPinnedReply(floor: Int) = parseForumThreadDetail(
    fixture("pinned-rich-replies.html", "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"),
    "https://www.javbus.com"
).replies.single { it.floor == floor }
```

Import `ForumTextSize` and `assertNull` or use the equality shown above.

- [ ] **Step 3: Run tests and verify the plain parser fails**

Run the Task 2 parser test command.

Expected: compilation or assertions fail because paragraphs, styles, and lists are not produced.

- [ ] **Step 4: Implement the isolated post parser**

Create `ForumPostParser.kt` with these internal parser states and entry point:

```kotlin
package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val MAX_LIST_DEPTH = 3

private data class InlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: String? = null,
    val size: ForumTextSize = ForumTextSize.BODY,
    val isLink: Boolean = false
)

internal fun parseForumPostContent(root: Element?, baseUrl: String): List<ContentBlock> {
    if (root == null) return emptyList()
    return PostContentParser(baseUrl).parse(root)
}
```

Implement `PostContentParser` with these concrete rules:

1. Maintain `blocks`, `paragraphs`, and `parts` buffers.
2. `TextNode` uses `text.wholeText.replace(Regex("\\s+"), " ")`; append a single separating space only when both the previous and next visible characters require one.
3. `<br>` flushes the current parts into one paragraph. Block boundaries flush paragraphs into `ContentBlock.RichText`.
4. `<strong>/<b>`, `<em>/<i>`, `<u>`, `<s>/<strike>/<del>`, `<a>`, `<font>`, and `<span>` recurse with `InlineStyle.copy(...)`.
5. Ignore `i.pstatus`, `.modact`, `.locked`, `.cm`, `script`, `style`, `form`, `button`, and `.sign`.
6. `<img>` flushes text, applies the existing URL/filter/size/GIF logic, and appends `ContentBlock.Image`.
7. `div.quote` flushes text and appends the existing `ContentBlock.Quote` extraction.
8. `<ol>/<ul>` flush text and call `parseList(element, depth = 1)`.
9. Unknown elements recurse into children.

Use these helpers exactly:

```kotlin
private fun Element.inlineColor(): String? {
    val raw = attr("color").ifBlank {
        Regex("(?:^|;)\\s*color\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
            .find(attr("style"))?.groupValues?.get(1)?.trim().orEmpty()
    }
    return raw.lowercase().takeIf {
        it.matches(Regex("#[0-9a-f]{3}([0-9a-f]{3})?")) ||
            it in setOf("red", "blue", "green", "black", "white", "gray", "grey", "orange", "purple")
    }
}

private fun Element.inlineSize(current: ForumTextSize): ForumTextSize {
    val size = attr("size").toIntOrNull() ?: return current
    return when {
        size >= 5 -> ForumTextSize.HEADING
        size >= 4 -> ForumTextSize.EMPHASIS
        else -> ForumTextSize.BODY
    }
}

private fun InlineStyle.toPart(text: String) = TextPart(
    text = text,
    bold = bold,
    italic = italic,
    underline = underline,
    strikethrough = strikethrough,
    color = color,
    size = size,
    isLink = isLink
)
```

`parseList` reads direct `:scope > li` children, parses direct non-list children into item paragraphs, recursively parses direct child `ol/ul`, and passes `minOf(depth + 1, MAX_LIST_DEPTH)`. For deeper source nesting, retain the children under depth 3 rather than discarding them. Use `start` only for ordered lists and default it to 1.

Move all old image and quote extraction from `ForumThreadParser.kt` into this class, then delete the old recursive `parsePostContent` implementation. Update first-post and reply calls to `parseForumPostContent`.

- [ ] **Step 5: Run parser and model tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest" --tests "me.jbusdriver.modern.domain.model.ContentBlockTypeAdapterTest"
```

Expected: all tests pass, including exact spaces and list nesting.

- [ ] **Step 6: Commit the rich-text parser**

```powershell
git add app/src/main/java/me/jbusdriver/modern/data/parser app/src/test/java/me/jbusdriver/modern/data/parser app/src/test/resources/forum/pinned-rich-replies.html
git commit -m "feat: parse forum rich text semantics"
```

## Task 4: Add Pure Plain-Text Formatting for Copy Actions

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt`
- Create: `app/src/test/java/me/jbusdriver/modern/ui/forum/ForumPlainTextTest.kt`

- [ ] **Step 1: Write failing copy-text tests**

```kotlin
package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumPlainTextTest {
    @Test
    fun `formats paragraphs lists quotes and restrictions`() {
        val blocks = listOf<ContentBlock>(
            ContentBlock.RichText(listOf(RichParagraph(listOf(TextPart("intro"))))),
            ContentBlock.ListBlock(
                RichList(
                    ordered = true,
                    start = 2,
                    items = listOf(
                        RichListItem(listOf(RichParagraph(listOf(TextPart("second"))))),
                        RichListItem(
                            paragraphs = listOf(RichParagraph(listOf(TextPart("third")))),
                            children = listOf(
                                RichList(false, items = listOf(
                                    RichListItem(listOf(RichParagraph(listOf(TextPart("nested")))))
                                ))
                            )
                        )
                    )
                )
            ),
            ContentBlock.Quote("Alice", "quoted"),
            ContentBlock.RestrictedNotice("此帖僅作者可見")
        )

        assertEquals(
            "intro\n2. second\n3. third\n  • nested\nAlice：quoted\n此帖僅作者可見",
            buildForumPlainText(blocks)
        )
    }
}
```

- [ ] **Step 2: Run the test and confirm the formatter is missing**

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumPlainTextTest"
```

Expected: compilation fails because `buildForumPlainText` does not exist.

- [ ] **Step 3: Add the pure formatter before Compose code**

Start `ForumPostContent.kt` with:

```kotlin
package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichParagraph

internal fun buildForumPlainText(blocks: List<ContentBlock>): String =
    blocks.flatMap { block -> block.toPlainLines() }
        .filter(String::isNotBlank)
        .joinToString("\n")

private fun ContentBlock.toPlainLines(): List<String> = when (this) {
    is ContentBlock.RichText -> paragraphs.map(RichParagraph::plainText)
    is ContentBlock.ListBlock -> list.toPlainLines(depth = 0)
    is ContentBlock.Image -> emptyList()
    is ContentBlock.Quote -> listOf(if (author.isEmpty()) content else "$author：$content")
    is ContentBlock.RestrictedNotice -> listOf(message)
}

private fun RichParagraph.plainText(): String = parts.joinToString("") { it.text }.trim()

private fun RichList.toPlainLines(depth: Int): List<String> = items.flatMapIndexed { index, item ->
    val indent = "  ".repeat(depth)
    val marker = if (ordered) "${start + index}. " else "• "
    val ownLines = item.paragraphs.mapIndexed { paragraphIndex, paragraph ->
        val prefix = if (paragraphIndex == 0) marker else "  "
        "$indent$prefix${paragraph.plainText()}"
    }
    ownLines + item.children.flatMap { it.toPlainLines(depth + 1) }
}
```

- [ ] **Step 4: Run the formatter test**

Run the Task 4 test command again.

Expected: test passes.

- [ ] **Step 5: Commit the formatter**

```powershell
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumPlainTextTest.kt
git commit -m "feat: format forum rich text for copying"
```

## Task 5: Build the Shared Compose Renderer with UI Tests

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt`
- Create: `app/src/main/res/drawable/lock_24px.xml`
- Create: `app/src/androidTest/java/me/jbusdriver/modern/ui/forum/ForumPostContentTest.kt`

- [ ] **Step 1: Add Compose UI test aliases and dependencies**

Add under the Compose catalog entries:

```toml
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
```

Add to `app/build.gradle.kts`:

```kotlin
androidTestImplementation(composeBom)
androidTestImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
```

- [ ] **Step 2: Add failing Compose tests**

Create `ForumPostContentTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.forum

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.junit.Rule
import org.junit.Test

class ForumPostContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersRestrictedNoticeAndListMarkers() {
        composeRule.setContent {
            MaterialTheme {
                ForumPostContent(
                    blocks = listOf(
                        ContentBlock.ListBlock(
                            RichList(true, items = listOf(
                                RichListItem(listOf(RichParagraph(listOf(TextPart("item")))))
                            ))
                        ),
                        ContentBlock.RestrictedNotice("此帖僅作者可見")
                    ),
                    onImageClick = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onNodeWithText("此帖僅作者可見").assertIsDisplayed()
        composeRule.onNodeWithTag("restricted_notice").assertIsDisplayed()
    }

    @Test
    fun linkStyledTextHasNoClickAction() {
        composeRule.setContent {
            MaterialTheme {
                ForumPostContent(
                    blocks = listOf(
                        ContentBlock.RichText(
                            listOf(RichParagraph(listOf(TextPart("link text", isLink = true))))
                        )
                    ),
                    onImageClick = { _, _ -> }
                )
            }
        }

        composeRule.onNode(hasText("link text")).assertHasNoClickAction()
    }
}
```

- [ ] **Step 3: Compile the instrumentation test and verify renderer symbols are missing**

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: compilation fails because `ForumPostContent` and `restricted_notice` semantics are not implemented.

- [ ] **Step 4: Add the lock vector**

Create `lock_24px.xml` as a 24dp vector using a single themed path:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FF000000"
        android:pathData="M12,17a2,2 0,1 0,0 -4,2 2,0 0,0 0,4zM18,8h-1V6a5,5 0,0 0,-10 0v2H6a2,2 0,0 0,-2 2v10a2,2 0,0 0,2 2h12a2,2 0,0 0,2 -2V10a2,2 0,0 0,-2 -2zM9,6a3,3 0,0 1,6 0v2H9V6z" />
</vector>
```

- [ ] **Step 5: Implement `ForumPostContent`, styled paragraphs, lists, notices, images, and quotes**

Expose this composable API in `ForumPostContent.kt`:

```kotlin
@Composable
internal fun ForumPostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onLongClick: () -> Unit = {},
    showImages: Boolean = true
)
```

Move the current image/GIF and quote branches from `ForumThreadDetailScreen.kt` into this renderer unchanged. Add these renderers:

```kotlin
@Composable
private fun StyledParagraph(paragraph: RichParagraph) {
    val scheme = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        paragraph.parts.forEach { part ->
            val sourceColor = part.color?.let(::parseSourceColor)
            val safeColor = sourceColor?.takeIf { contrastRatio(it, scheme.surface) >= 4.5f }
                ?: scheme.onSurface
            withStyle(
                SpanStyle(
                    color = if (part.isLink) scheme.primary else safeColor,
                    fontWeight = if (part.bold) FontWeight.Bold else null,
                    fontStyle = if (part.italic) FontStyle.Italic else null,
                    textDecoration = textDecorationFor(part),
                    fontSize = when (part.size) {
                        ForumTextSize.BODY -> 14.sp
                        ForumTextSize.EMPHASIS -> 16.sp
                        ForumTextSize.HEADING -> 20.sp
                    }
                )
            ) { append(part.text) }
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun textDecorationFor(part: TextPart): TextDecoration? {
    val decorations = buildList {
        if (part.isLink || part.underline) add(TextDecoration.Underline)
        if (part.strikethrough) add(TextDecoration.LineThrough)
    }
    return when (decorations.size) {
        0 -> null
        1 -> decorations.single()
        else -> TextDecoration.combine(decorations)
    }
}

@Composable
private fun RichListContent(list: RichList, depth: Int = 0) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(start = (depth * 12).dp)) {
                Text(if (list.ordered) "${list.start + index}." else "•")
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    item.paragraphs.forEach { StyledParagraph(it) }
                    item.children.forEach { RichListContent(it, minOf(depth + 1, 2)) }
                }
            }
        }
    }
}

@Composable
private fun RestrictedNotice(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restricted_notice")
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.lock_24px), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Import `androidx.compose.ui.graphics.luminance`. `parseSourceColor` should use `android.graphics.Color.parseColor` in `runCatching`, returning Compose `Color?`. Do not add `clickable`, string annotations, or URI handlers to styled text.

The top-level block switch must handle every `ContentBlock` subtype. Calculate image viewer URLs once with `remember`, filter images out when `showImages` is false, and increment the image index only for images that are actually viewable, preserving current GIF behavior. Use `Modifier.pointerInput(onLongClick) { detectTapGestures(onLongPress = { onLongClick() }) }` on the outer column instead of `combinedClickable`; this preserves long press without exposing an empty click action to accessibility or making link-looking text clickable.

- [ ] **Step 6: Compile and run available UI tests**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
```

Expected: compilation passes. On an attached emulator/device, both Compose tests pass. If no device is attached, record that `connectedDebugAndroidTest` could not run and retain successful compilation as evidence.

- [ ] **Step 7: Commit the renderer and test setup**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt app/src/main/res/drawable/lock_24px.xml app/src/androidTest/java/me/jbusdriver/modern/ui/forum/ForumPostContentTest.kt
git commit -m "feat: render forum rich text with compose"
```

## Task 6: Integrate the Renderer, Pinned Labels, Preview, and Cache Version

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt:72-83, 187-203, 304-444, 566-598, 603-720`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt:74-82`
- Modify: `app/src/androidTest/java/me/jbusdriver/modern/ui/forum/ForumPostContentTest.kt`

- [ ] **Step 1: Add a failing pinned-label test around a testable formatter**

Add to `ForumPostContentTest.kt` or a unit test if the formatter remains pure:

```kotlin
@Test
fun pinnedFloorLabelIncludesPinnedPrefix() {
    assertEquals("置頂 · 2#", forumFloorLabel(floor = 2, isPinned = true))
    assertEquals("5#", forumFloorLabel(floor = 5, isPinned = false))
}
```

For a JVM unit test, place this method in `ForumPlainTextTest.kt`; do not depend on Compose for a pure string function.

- [ ] **Step 2: Run the focused test and confirm `forumFloorLabel` is missing**

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumPlainTextTest"
```

Expected: compilation fails because `forumFloorLabel` is missing.

- [ ] **Step 3: Add the floor formatter and wire the screen**

Add to `ForumPostContent.kt`:

```kotlin
internal fun forumFloorLabel(floor: Int, isPinned: Boolean): String =
    if (isPinned) "置頂 · $floor#" else "$floor#"
```

In `ForumThreadDetailScreen.kt`:

- Replace both `PostContent(...)` calls with `ForumPostContent(...)` using the same arguments.
- Replace the reply header string with `"${forumFloorLabel(reply.floor, reply.isPinned)} · ${reply.postTime}"`.
- Replace `buildPlainText(blocks)` with `buildForumPlainText(blocks)`.
- Replace the duplicated preview block loop with `ForumPostContent(blocks, onImageClick = { _, _ -> }, showImages = false)` inside `SelectionContainer`.
- Delete the old private `PostContent` and `buildPlainText` functions and remove now-unused imports.

The renderer must skip `ContentBlock.Image` when `showImages` is false, preserving the current preview behavior.

- [ ] **Step 4: Version the detail cache key**

In `DefaultForumRepository.loadThreadDetail`, change:

```kotlin
val cacheKey = "forum_detail_v2_${tid}_$page"
```

This deterministically bypasses persisted `TextPart.Plain`-only details while the adapter still supports legacy data used elsewhere or during rollback.

- [ ] **Step 5: Run focused unit tests and compile the app**

```powershell
.\gradlew.bat testDebugUnitTest --tests "me.jbusdriver.modern.ui.forum.ForumPlainTextTest" --tests "me.jbusdriver.modern.data.parser.ForumThreadParserTest" --tests "me.jbusdriver.modern.domain.model.ContentBlockTypeAdapterTest"
.\gradlew.bat assembleDebug
```

Expected: all focused tests pass and the debug APK builds.

- [ ] **Step 6: Commit integration**

```powershell
git add app/src/main/java/me/jbusdriver/modern/ui/forum/ForumThreadDetailScreen.kt app/src/main/java/me/jbusdriver/modern/ui/forum/ForumPostContent.kt app/src/main/java/me/jbusdriver/modern/data/ForumRepository.kt app/src/test/java/me/jbusdriver/modern/ui/forum/ForumPlainTextTest.kt
git commit -m "feat: integrate forum rich text rendering"
```

## Task 7: Full Regression Verification

**Files:**
- Verify all files changed in Tasks 1-6.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat test
```

Expected: all unit tests pass.

- [ ] **Step 2: Build the debug variant**

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Run instrumentation tests when a device is available**

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all instrumentation tests pass. If no device is available, report this verification gap explicitly.

- [ ] **Step 4: Manually verify the two target threads**

With the app's existing authenticated Forum session:

1. Open thread `154969`; confirm every restricted reply shows a subdued lock notice containing `此帖僅作者可見` and no blank reply card remains.
2. Open thread `171794`; confirm replies 2, 3, and 4 appear before reply 5 and labels read `置頂 · 2#`, `置頂 · 3#`, and `置頂 · 4#`.
3. Confirm the first post preserves red emphasis, bold text, mapped heading/body sizes, paragraph spacing, and link appearance without tap behavior.
4. Long-press a post; confirm preview and copied output retain paragraphs, list markers, quotes, and restriction messages.
5. Confirm images open at the correct index, GIF placeholders still require the configured manual action, pagination still loads more replies, and pull-to-refresh still works.

- [ ] **Step 5: Inspect the final diff for accidental scope expansion**

```powershell
git diff --check HEAD~6..HEAD
git status --short
```

Expected: no whitespace errors, no saved web pages or `.superpowers` artifacts staged, and only the planned Forum/test/build files changed.
