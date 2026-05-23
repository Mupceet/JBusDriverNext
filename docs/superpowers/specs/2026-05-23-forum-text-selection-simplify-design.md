# Forum Thread Detail Text Interaction Simplification

## Problem

The forum thread detail page currently handles link clicks with a complex chain: `TextPart.Link` in data models, `AnnotatedString` + `ClickableText` in UI, and a `rememberLinkClickHandler` routing different link types. This adds complexity in parser, data model, serialization, and UI layers for a feature that can be replaced by simple long-press text selection.

## Solution

Remove link click handling entirely. All post text becomes plain, selectable text via `SelectionContainer`. Links lose their special styling (no blue/underline) and are treated as regular text that users can long-press to select and copy.

## Changes

### Data Layer

**`TextPart` sealed class** — Remove `Link` subclass, keep only `Plain`:
```kotlin
sealed class TextPart {
    data class Plain(val text: String) : TextPart()
}
```

**`ContentBlockAdapter` (Gson TypeAdapter)** — Remove `TextPart.Link` serialization/deserialization branch.

**`ForumThreadParser`** — `<a>` tags produce `TextPart.Plain(text)` instead of `TextPart.Link(text, href)`:
```kotlin
"a" -> parts.add(TextPart.Plain(text))
```

### UI Layer

**`ForumThreadDetailScreen`** — Replace `PostContent` / `RichTextContent` / `SelectableRichTextContent` with a simple `SelectionContainer` + `Text`:
- Remove `rememberLinkClickHandler`
- Remove `ClickableText` and `AnnotatedString` construction
- Remove `linkStyle` SpanStyle
- Remove selection mode toggle logic

`ContentBlock.Quote` rendering also simplifies — its inner content blocks follow the same plain text approach.

### Share Button

Add a share button to the thread detail top bar. Tapping it opens the system share sheet with the thread URL, allowing the user to open it in a browser or share via other apps.

- Button: share icon in the top app bar
- Action: `Intent.ACTION_SEND` with the thread URL (e.g. `https://www.javbus.com/forum/forum.php?mod=viewthread&tid=123`)
- Thread URL is already available in the screen's ViewModel state

### Unchanged

- `ContentBlock.Image` — images and GIFs unaffected
- `ContentBlock.Quote` — quote blocks keep their visual distinction, just inner text is plain
- Image click-to-view — unaffected
- `Comment` (simple text comments) — unaffected
- `ForumReply` with content blocks — structure unchanged, just no link data
