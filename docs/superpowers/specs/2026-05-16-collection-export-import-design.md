# Collection Export & Import Design

## Goal

Allow users to export their collected movies and actresses to a JSON file, and import collections from both the new format and the legacy MVP project format.

## Export Format

New format (version 1):

```json
{
  "version": 1,
  "exportTime": "2026-05-16T10:30:00Z",
  "movies": [
    {"code": "SONE-912", "title": "...", "imageUrl": "/pics/thumb/bov9.jpg", "link": "https://...", "date": "2025-10-09", "tags": ["tag1"]}
  ],
  "actresses": [
    {"name": "楓カレン", "avatar": "https://...", "link": "https://..."}
  ]
}
```

Fields map directly to `Movie` and `ActressInfo` domain model properties. No category info is exported; imported items go to default categories.

## Legacy Format Compatibility

Legacy format (old MVP project):

```json
[
  {"categoryId":1, "type":1, "key":"/SONE-912", "jsonStr": "{\"code\":\"SONE-912\",\"title\":\"...\",\"imageUrl\":\"/pics/thumb/bov9.jpg\",\"detailUrl\":\"https://...\"}"},
  {"categoryId":2, "type":2, "key":"/star/u4m", "jsonStr": "{\"name\":\"楓カレン\",\"avatar\":\"https://...\",\"link\":\"https://...\"}"}
]
```

Detection: top-level `JsonArray` = legacy, top-level `JsonObject` with `version` field = new.

Legacy mapping:
- `type=1` → Movie: `jsonStr.detailUrl` → `link`, other fields map directly
- `type=2` → ActressInfo: fields map directly
- Other types (3+) are ignored during import

## Import Conflict Strategy

Skip items whose `key` (`link.urlPath`) already exists in the database. No overwrite, no data loss.

## UI Entry Point

Collection tab TopBar: MoreVert icon → dropdown menu with two options:

- **Export**: `ActivityResultContracts.CreateDocument()` with default filename `jbus_backup_<yyyyMMdd>.json`
- **Import**: `ActivityResultContracts.OpenDocument()` filtered to `application/json`

Result shown via Toast: "Exported N movies, M actresses" or "Imported N movies, M actresses (K skipped)".

## Implementation Scope

1. Add export/import logic to `CollectRepository` (new methods + Gson serialization)
2. Add `rememberLauncherForActivityResult` in CollectionListScreen for file pickers
3. Add MoreVert menu to collection TopBar
4. Legacy format detection and mapping in repository layer

## Files to Modify

- `CollectRepository.kt` — add `exportCollections()` and `importCollections(jsonString)` methods
- `CollectionListScreen.kt` — add TopBar menu and activity result launchers
- No database schema changes needed
