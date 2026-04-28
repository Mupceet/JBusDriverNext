# Phase 1: Remove Legacy UI Code and Resources

## Context
The project has fully migrated to Compose UI under `modern/`. The legacy `ui/` package (Activities, Fragments, Adapters, Holders, Widgets) and 49 layout XML files are unused. Two enum files in `ui/data/enums/` are used by modern code and need migration first. SettingsScreen has a "更多设置（旧版）" button linking to legacy SettingActivity — remove that button.

## Pre-requisite: Migrate Shared Enums
Before deleting `ui/`, move two enum files that modern code depends on:
- `ui/data/enums/DataSourceType.kt` → `modern/domain/model/DataSourceType.kt` (referenced by 10 modern files)
- `ui/data/enums/SearchType.kt` → `modern/domain/model/SearchType.kt` (referenced by 5 modern files)

After moving, update all imports across the codebase.

## Deletions

### Legacy UI package (`me.jbusdriver.ui`)
Delete entire directories:
- `ui/activity/` — 8 files (MainActivity, MovieDetailActivity, etc.)
- `ui/fragment/` — 15 files
- `ui/adapter/` — 4 files
- `ui/holder/` — 7 files
- `ui/widget/` — 6 files (Java)
- `ui/data/` — delete all except `enums/` which is migrated first

### Layout XML resources
Delete all 49 files in `app/src/main/res/layout/`.

### AndroidManifest.xml changes
- Remove all legacy activity declarations (SplashActivity, MainActivity, MovieListActivity, MovieDetailActivity, SearchResultActivity, SettingActivity, WatchLargeImageActivity, MagnetPagerListActivity)
- Set `ModernMainActivity` as LAUNCHER activity (add MAIN/LAUNCHER intent-filter)
- Remove SplashActivity theme reference if any

### SettingsScreen.kt changes
- Remove "更多设置（旧版）" button and SettingActivity import
- Keep URL selector as the only settings content

## Files NOT deleted (kept for now)
- `ui/data/enums/` — migrated, then directory deleted
- `magnet/` — kept (used by modern MovieDetailViewModel)
- `mvp/bean/` — kept (data models used by modern code)
- `mvp/presenter/`, `mvp/model/`, `mvp/Contract.kt` — will be cleaned in Phase 2

## Verification
- `./gradlew clean assembleDebug` passes
- App launches directly into ModernMainActivity
- All modern screens (home, detail, search, settings) work
