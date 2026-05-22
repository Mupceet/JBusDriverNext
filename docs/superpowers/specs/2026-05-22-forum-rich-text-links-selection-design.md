# Forum Rich Text: Hyperlink Rendering & Text Selection

## Problem

`parsePostContent()` strips `<a>` tag `href` attributes, producing plain `ContentBlock.Text`. Links are invisible and unclickable in the UI. Forum post text is rendered with basic `Text` composables that don't support text selection or copy.

## Design

### 1. Data Model

Replace `ContentBlock.Text` with `ContentBlock.RichText` that carries an ordered list of typed text parts:

```kotlin
sealed class TextPart {
    data class Plain(val text: String) : TextPart()
    data class Link(val text: String, val url: String) : TextPart()
}

sealed class ContentBlock {
    data class RichText(val parts: List<TextPart>) : ContentBlock()
    // Image, Quote unchanged
}
```

`ContentBlock.Text` is removed. Plain text paragraphs become `RichText(parts = listOf(Plain("...")))`.

### 2. Parser Changes

Modify `processNode` in `HtmlParser.parsePostContent()`:

- `<a>` tag → emit `TextPart.Link(text, abs:href)`
- `TextNode` → emit `TextPart.Plain(text)`
- `<br>` → flush accumulated parts as `RichText`, start new accumulator
- `<img>` → flush accumulated parts, emit `Image` block
- `<div class="quote">` → flush accumulated parts, emit `Quote` block
- Other elements → recurse into children

Parts accumulate in a mutable list between flush boundaries (br, img, quote, block-level elements). Empty lists are not emitted as blocks.

### 3. UI Rendering

**AnnotatedString construction:**

- `Plain` → plain `SpanStyle` matching current `bodyMedium`
- `Link` → `SpanStyle(color = primary, fontWeight = SemiBold, textDecoration = Underline)` + `StringAnnotation(url)`

**Interaction modes:**

- **Browse mode (default):** `ClickableText` renders the `AnnotatedString`. Tapping a link region triggers URL handling — app-internal navigation for forum URLs (threads, boards), external browser for everything else.
- **Selection mode:** Long press detected via `detectTapGestures.onLongPress` switches to `selectionMode = true`. Content re-renders inside `SelectionContainer` wrapping plain `Text` (full text concatenated, no link spans). User can select and copy text.
- **Exit selection:** Tap outside the SelectionContainer area or press back resets `selectionMode = false`.

**Visual hint:** When entering selection mode, a small banner briefly appears at the top of the content area: "選擇模式 · 點擊空白退出".

### Files to Modify

1. `ForumModels.kt` — add `TextPart`, replace `ContentBlock.Text` with `ContentBlock.RichText`, update `ContentBlockTypeAdapter`
2. `HtmlParser.kt` — rewrite `processNode` to emit `TextPart` list, update callers
3. `ForumThreadDetailScreen.kt` — new `RichTextContent` composable with `ClickableText` / `SelectionContainer` mode switching, replace `ContentBlock.Text` rendering
