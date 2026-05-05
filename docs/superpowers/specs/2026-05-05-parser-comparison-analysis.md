# Parser Comparison: javbus-parser.ts vs HtmlParser.kt

Comparative analysis of HTML parsing logic between the reference TypeScript project (`javbus-parser.ts`) and the JBus Android project (`HtmlParser.kt`). This document records differences for future prioritized implementation.

## 1. Movie List Parsing

**Selectors differ but achieve the same result.**

| Field | TS (`#waterfall .item`) | Kotlin (`.movie-box`) |
|-------|-------------------------|----------------------|
| Cover | `.photo-frame img[src]` | `img[src]` |
| Title | `.photo-frame img[title]` | `img[title]` |
| Code | `.photo-info date` [0] | `date` [0] |
| Date | `.photo-info date` [1] | `date` [1] |
| Tags | `.item-tag button` | `.item-tag` children / `.photo-info button` fallback |

- **TS-only**: `filter` callback parameter to exclude results
- **Functional parity**: Both extract the same data

## 2. Movie Detail Parsing — Key Differences

### 2.1 gid/uc Parameters (MISSING in Kotlin)

TS extracts `gid` and `uc` from inline JavaScript via regex (`var gid = (\d+)` / `var uc = (\d+)`). These are required parameters for the site's internal magnet AJAX endpoint (`/ajax/uncledatoolsbyajax.php`).

**Kotlin does not parse these values.** The magnet system relies entirely on external loader plugins.

### 2.2 Structured Metadata vs Generic Headers

**TS**: Parses specific fields individually — `date`, `videoLength` (number), `director`, `producer`, `publisher`, `series` (each with `{id, name}` structure), `genres`, `stars`.

**Kotlin**: Parses all info rows uniformly as `Header(name, value, link)`. More flexible but loses type distinction — `videoLength` is a string, `director` is not separated from other headers.

### 2.3 Actress Extraction

**TS**: Extracts from `.info` area's `.genre` elements with `onmouseover` attribute, returns `{id, name}`.

**Kotlin**: Extracts from `#avatar-waterfall .avatar-box`, returns `{name, avatar, link}`. Different source, but includes avatar URL (TS does not extract actress avatar in detail page).

### 2.4 Genre Extraction

**TS**: Excludes genres with `onmouseover` attribute (those are stars), takes `label a` within genre.

**Kotlin**: `.genre:has(a[href*=genre])` — different selector, same intent.

### 2.5 Image Size (MISSING in Kotlin)

TS uses `probe-image-size` library to fetch actual cover dimensions (`{width, height}`). Kotlin has no image size data — cover aspect ratio is unknown at parse time.

### 2.6 Title Source

- TS: `.container h3` text content
- Kotlin: `.bigImage img` title attribute

These may produce slightly different strings (e.g., with/without code prefix).

### 2.7 Description (Kotlin-only)

Kotlin extracts `[name=description]` content attribute. TS does not parse this field.

## 3. Magnet Link Parsing — Structural Differences

### TS: Site-Internal AJAX

- Endpoint: `/ajax/uncledatoolsbyajax.php?lang=zh&gid={gid}&uc={uc}`
- Requires `referer` header pointing to movie page
- Parses HTML table: `id` (btih hash), `link`, `isHD`, `hasSubtitle`, `title`, `size`, `numberSize` (bytes), `shareDate`
- Default sort by `numberSize` descending
- Supports re-sort by date/size ascending/descending

### Kotlin: External Plugin System

- `MagnetManager` facade delegates to `IMagnetLoader` implementations
- `Magnet` model: `name`, `size`, `date`, `link` only
- No `isHD`, `hasSubtitle`, `id` (hash) fields
- No built-in sorting

**Gap**: Kotlin magnet model lacks HD/subtitle flags and hash ID. The external loader approach is more extensible but loses per-item metadata.

## 4. Actress Info Parsing

### TS: Structured StarInfo

Maps known attribute prefixes to typed fields:
- `birthday`, `age`, `height`, `bust`, `waistline`, `hipline`, `birthplace`, `hobby`

### Kotlin: Raw Text List

`ActressAttrs.info: List<String>` stores raw strings like `"身高: 165cm"`. Displayable but not queryable/filterable by individual attributes.

## 5. Search URL Construction

### TS: `type=1` Parameter

Search URL appends `&type=1` directly: `/{type}/search/{keyword}/{page}&type=1`

### Kotlin: No `type=1`

`SearchType.urlPathFormater` generates `/search/{keyword}` paths without the `type=1` query parameter. Unknown if this affects search result quality.

### Kotlin-Only Search Types

Kotlin supports 7 search types (CENSORED, UNCENSORED, ACTRESS, DIRECTOR, MAKER, PUBLISHER, SERIES). TS only supports movie search with optional type prefix.

## 6. Summary: Gaps to Address

| # | Gap | Priority | Effort | Impact |
|---|-----|----------|--------|--------|
| 1 | **gid/uc not parsed** | High | Low | Required for site-internal magnet AJAX |
| 2 | **Magnet lacks isHD/hasSubtitle/id** | Medium | Low | Better magnet metadata display |
| 3 | **Actress attrs not structured** | Low | Low | Enables attribute-based filtering |
| 4 | **Search missing `type=1`** | Medium | Trivial | May improve search accuracy |
| 5 | **No image size** | Low | Medium | Cover aspect ratio in UI |
| 6 | **No list filter callback** | Low | Trivial | Minor quality-of-life |

Items are ordered by suggested priority. Each can be implemented independently.
