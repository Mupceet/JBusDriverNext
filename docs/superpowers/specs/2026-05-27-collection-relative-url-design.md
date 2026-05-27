# Collection Relative URL Storage

## Goal

Store collection items with relative URL paths instead of absolute URLs, so they work correctly when the base URL changes to a mirror domain.

## Problem

Collection items store absolute URLs (e.g., `https://www.javbus.com/ABC-123`). When the user switches to a mirror URL (e.g., `https://www.cdnbus.bond`), stored links and images point to the old domain and break.

## Approach

Strip absolute URLs to relative paths at database insertion time, and re-resolve them with the current base URL when reading from the database.

## Changes

### 1. `UrlParserExt.kt` — Add `stripToPath()`

```kotlin
fun String.stripToPath(): String
```

- If starts with `http`, extract path portion: `"https://www.javbus.com/ABC-123"` → `"/ABC-123"`
- If starts with `/`, return as-is
- If blank, return as-is

### 2. `LinkMappers.kt` — Strip URLs on insert

In `ILink.convertDBItem()`: before serializing to JSON, strip `link` and `imageUrl`/`avatar` fields to relative paths via `stripToPath()`.

### 3. `LinkMappers.kt` — Restore URLs on read

In `LinkItem.toILink()`: after deserializing from JSON, restore URL fields to absolute URLs using `wrapImage(currentBaseUrl)`.

### 4. `MovieDetail.kt` — Remove `checkUrl()`

No longer needed since URLs are always rebuilt from relative paths with the current base URL.

## What stays the same

- **HTML parsing**: Still produces absolute URLs (needed for network requests)
- **CacheLoader**: Caches runtime objects with absolute URLs, not affected
- **Forum**: Uses its own data path, not affected
- **Navigation and image loading**: Work as before since URLs are restored on read

## No migration

Fresh installs only. No backward compatibility with existing database data.
