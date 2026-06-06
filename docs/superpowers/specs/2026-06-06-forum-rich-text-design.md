# Forum Detail Rich Text Design

## Summary

Improve the Forum thread detail screen in three areas:

1. Show restricted reply notices such as `此帖僅作者可見` instead of an empty body.
2. Parse and display pinned replies whose floor labels use text such as `來自 2#`.
3. Render a controlled subset of the source HTML as native Compose rich text.

The implementation will parse HTML into a semantic domain model and render that model with Compose. It will not embed a WebView or retain executable HTML.

## Goals

- Preserve the readable structure and emphasis of Forum posts on a mobile screen.
- Keep rendering compatible with the app theme and dark mode.
- Make parsing and rendering independently testable.
- Reuse one renderer across the first post, replies, and the floor preview dialog.
- Preserve existing image, GIF, quote, selection, and copy behavior.

## Non-Goals

- Pixel-perfect reproduction of the website.
- Clickable links in the first version.
- Arbitrary CSS, custom web fonts, text alignment, or HTML tables.
- JavaScript execution or embedded web content.

## Confirmed Product Decisions

- Use controlled semantic styling rather than directly reproducing web CSS.
- Links retain link color and underline but do not respond to taps.
- Restricted content uses a subdued notice card with a lock icon.
- Pinned floors display as `置頂 · 2#`.
- Lists support at most three visual nesting levels.

## Domain Model

Extend the current `ContentBlock` model so block structure and inline style are explicit.

### Blocks

- `RichText`: one or more styled paragraphs.
- `OrderedList`: ordered list items containing rich text and optional nested lists.
- `UnorderedList`: unordered list items containing rich text and optional nested lists.
- `Image`: retain the existing image metadata and GIF behavior.
- `Quote`: retain the existing dedicated quote presentation.
- `RestrictedNotice`: a visible server-provided restriction message.

`ForumReply` gains an `isPinned` flag. Its numeric `floor` remains the canonical floor number.

### Inline Content

Each paragraph contains text runs with a value and a controlled style descriptor:

- bold
- italic
- underline
- strikethrough
- text size category: body, emphasis, or heading
- optional validated source text color
- link appearance flag

The model stores semantic values, not raw HTML or CSS.

## HTML Parsing

`ForumThreadParser` will recursively traverse the post DOM and build semantic blocks.

### Reply Floors

Floor extraction follows this order:

1. Read the numeric value from the existing `a[id^=postnum] em` element.
2. If absent, read the full floor anchor text and extract a number from patterns such as `來自 2#`.
3. Mark a reply as pinned when the anchor contains the pinned reply icon or pinned wording.
4. Skip a reply only when no reliable numeric floor can be extracted.

This preserves the document order, including pinned replies 2, 3, and 4 before normal reply 5.

### Restricted Replies

For each reply container, inspect `.locked` independently of `td.t_f`. If its visible text is non-empty, create `RestrictedNotice` content. This handles replies for which the website omits the normal post body cell.

The parser must preserve the server wording instead of hard-coding only one known phrase.

### Supported HTML Semantics

- Paragraph boundaries and `<br>` line breaks.
- `<ol>`, `<ul>`, and `<li>`, with visual nesting capped at three levels.
- `<strong>` and `<b>` as bold.
- `<em>` and `<i>` as italic, excluding website metadata such as edit-status elements when appropriate.
- `<u>` as underline.
- `<s>`, `<strike>`, and `<del>` as strikethrough.
- `<font color>`, CSS `color`, and known color attributes after sanitization.
- Web font sizes mapped to body, emphasis, or heading categories.
- `<a>` as styled link text without click behavior.
- Existing image and quote handling.

Unknown elements recurse into their children so visible text is not lost. Scripts, styles, controls, moderation actions, signatures, and other non-post UI are ignored.

### Whitespace

- Preserve meaningful spaces between adjacent inline nodes.
- Collapse website formatting whitespace that does not affect visible content.
- Convert line breaks into paragraph or explicit line-break semantics without generating repeated empty blocks.
- Do not call `trim()` independently on every text node because that joins words that were separated by HTML whitespace.

### Style Sanitization

- Map all source sizes into three app typography categories.
- The parser accepts valid CSS or HTML colors and rejects invalid or transparent values without consulting UI theme state.
- The Compose renderer checks a valid source color against the active surface and falls back to the themed body color when contrast is insufficient.
- Ignore arbitrary CSS properties and custom fonts.
- Flatten nesting deeper than three levels into the third visual indentation level while retaining item content.

## Compose Rendering

Create a shared native renderer used by the first post, replies, and floor content dialog.

### Components

- `PostContentRenderer`: dispatches semantic blocks and owns spacing between blocks.
- `StyledParagraph`: builds an `AnnotatedString` with Compose `SpanStyle` values.
- `RichList`: renders ordered markers or bullets with native `Column`/`Row` layout.
- `RestrictedNotice`: renders a lock icon, subdued container, and restriction text.
- Existing image/GIF and quote components remain dedicated block renderers.

Links receive the app link color and underline but no annotation click handler.

Pinned reply metadata is rendered in the reply header as `置頂 · {floor}#`; normal replies remain `{floor}#`.

The preview dialog uses the same text and list renderer as the thread body. It may continue to omit images, but it must no longer flatten rich text into plain text.

## Plain Text and Copy Behavior

The plain-text conversion used by copy actions will:

- Preserve paragraph boundaries.
- Prefix ordered items with their numeric marker.
- Prefix unordered items with a bullet.
- Include restricted notice text.
- Preserve quote author and content.
- Exclude images as today.

## Cache Compatibility

Forum thread details are persisted through Gson. The type adapter must support all new block and inline variants.

Old cached values that cannot be decoded into the new model are treated as cache misses and fetched again. The screen must not fail because an older cached detail uses the previous `TextPart.Plain`-only representation.

A cache format/version suffix may be added to the Forum detail cache key to make invalidation deterministic.

## Error Handling

- Invalid style attributes fall back to theme defaults.
- Unsupported tags retain visible descendant text where safe.
- A malformed list item is rendered as a paragraph rather than discarded.
- A reply with metadata but no normal body remains visible when a restriction notice exists.
- Parser failures for one child node do not discard the rest of the post.

## Testing

### Parser Unit Tests

Create minimal UTF-8 fixtures derived from the two saved pages, containing only the relevant post structures.

- Restricted replies from thread `154969` produce `RestrictedNotice("此帖僅作者可見")`.
- Pinned replies from thread `171794` produce floors 2, 3, and 4, all marked pinned and in document order.
- Normal reply 5 remains unpinned and follows pinned replies.
- Bold, italic, underline, strikethrough, controlled colors, size categories, and link appearance are parsed.
- Ordered and unordered lists retain item order and supported nesting.
- Unknown tags retain visible text.
- Invalid attributes and excessive nesting degrade safely.
- Inline whitespace between styled nodes is preserved.

### Compose Tests

- Restricted notice text and lock semantics are visible.
- A pinned reply exposes `置頂 · 2#`.
- Ordered and unordered markers are displayed.
- Link-looking text has no click action.
- First post, reply, and preview dialog use consistent rich-text output.

### Regression Tests

- Images and image viewer indices remain correct.
- GIF placeholders and manual loading behavior remain correct.
- Quotes retain their dedicated presentation.
- Copy-all output includes lists and restriction messages.
- Existing plain posts continue to render without extra whitespace.

## Acceptance Criteria

- Thread `154969` displays `此帖僅作者可見` for restricted replies rather than blank reply cards.
- Thread `171794` displays pinned replies 2, 3, and 4, each labeled `置頂 · N#`.
- Supported rich-text semantics are visibly preserved using app-controlled styling.
- Link text is colored and underlined but not clickable.
- Unsupported or malformed HTML does not hide otherwise visible text or crash the screen.
- Existing Forum detail image, GIF, quote, copy, refresh, and pagination behavior continues to work.
