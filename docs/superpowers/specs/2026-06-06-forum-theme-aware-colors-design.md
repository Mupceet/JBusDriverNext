# Forum Theme-Aware Rich Text Colors

## Goal

Preserve the semantic identity of colors extracted from Forum HTML while keeping text readable in both light and dark themes. Typical source colors such as red and blue must not disappear by falling back to the normal body color solely because their original shade has insufficient contrast.

## Scope

- Applies to source colors stored in `TextPart.color`.
- Does not change link behavior or link color. Links continue to use the app's Material `primary` color and remain non-clickable.
- Does not change HTML parsing, rich-text models, caching, or supported formatting effects.

## Color Mapping

1. Parse the source color using Android's platform color parser.
2. Force the parsed color to full opacity because Forum text colors are semantic foreground colors.
3. Measure its contrast against the active Material surface color.
4. If contrast is at least `4.5:1`, preserve the source color exactly.
5. Otherwise, preserve the source hue and saturation while searching both lighter and darker HSL lightness values.
6. Select the closest lightness adjustment that reaches `4.5:1`.
7. If neither direction can produce a qualifying color, use the direction with the highest achievable contrast.
8. Only malformed or missing source colors fall back to `onSurface`.

The search must be deterministic and bounded. It should operate as a pure Kotlin function so light- and dark-theme behavior can be unit tested without Compose instrumentation.

## Expected Behavior

- Red text on a light surface becomes a darker red when necessary, rather than normal body text.
- Blue text on a dark surface becomes a lighter blue when necessary, rather than normal body text.
- Green, purple, and arbitrary valid colors retain their hue identity under the same rule.
- A source color that already meets the contrast threshold remains unchanged.
- Invalid values still fall back to the app's normal foreground color.

## Testing

Unit tests will cover:

- low-contrast red on a light background remains recognizably red and reaches `4.5:1`;
- low-contrast blue on a dark background remains recognizably blue and reaches `4.5:1`;
- an already-readable source color remains unchanged;
- invalid source input returns the supplied fallback;
- the selected result uses the smallest qualifying lightness change when both directions are possible.

Existing Forum parser, plain-text, Compose semantics, device tests, and Debug APK build remain regression gates.
