# Translation Services: Multi-Provider Selection & Configuration

**Date:** 2026-11-01
**Status:** Approved
**Supersedes:** the provider/config portion of `2026-10-30-movie-detail-title-translation-design.md` (that spec's repository/ViewModel/UI sections remain in force). Reference implementation: [komi-store](https://github.com/komi-store/komi-store) (Apache-2.0) at `D:\Projects\refs\komi-store` — engine logic adapted to OkHttp + Gson.

## Problem

The built-in Google free endpoint (translate_a/single?client=gtx) rate-limits by egress IP (HTTP 429) and the user's network egress shares that quota, making the single-default-provider implementation unusable in practice. The app needs komi-store's "翻译服务" capability: a selectable set of translation providers, per-provider credential configuration, and a selectable target language.

## Confirmed requirements

| Item | Decision |
|---|---|
| Providers | komi's five: **Google** (free, no config, default) / **有道 Youdao** (appKey+appSecret) / **LibreTranslate** (instance URL, default `https://translate.disroot.org`, optional API key) / **DeepL** (auth key, free/pro host auto by `:fx` suffix) / **Microsoft** (subscription key + region) |
| Custom URL template | **Removed** (replaced by Libre's instance URL for self-hosted cases); unmerged branch ⇒ no migration |
| Target language | Full picker: 跟隨系統 (default) + komi's 33-language list; effective target resolved from device locale when unset. Follow-system: `zh` + region TW/HK/MO → `zh-TW`, else `zh-CN`; other locales matched by language; fallback `zh-TW` |
| Source language | `auto` (each engine maps/omits it) |
| Usage scope | Unchanged: movie detail title only; engine layer stays content-agnostic |
| UI conventions | Settings card follows JBusDriver's existing rows/dropdowns (not komi's Komi* components); credential forms keep our extra "測試翻譯" button (komi has save only) |

## Solution

Keep the reviewed skeleton (`TranslationRepository` persistent cache + in-flight dedup, `TitleTranslationState` ViewModel, detail-screen toggle) and replace the engine/config layer with ports of komi's five translators.

### 1. `data/translation/` — models & engines

**`TranslationModels.kt`** (modified)

```kotlin
enum class TranslationProvider(val id: String) {
    GOOGLE("google"), YOUDAO("youdao"), LIBRE("libre"), DEEPL("deepl"), MICROSOFT("microsoft");
    // fromId(id): unknown/null -> GOOGLE (old "custom" safely falls back)
}
const val TRANSLATION_SOURCE_LANG = "auto"          // was "ja"
const val TRANSLATION_DEFAULT_TARGET_LANG = "zh-TW"
data class TranslatorConfig(                          // flat snapshot, resolved by AppSettingsStore
    val provider: TranslationProvider,
    val targetLang: String,                           // effective (follow-system resolved)
    val youdaoAppKey: String = "", val youdaoAppSecret: String = "",
    val libreBaseUrl: String = "", val libreApiKey: String = "",
    val deeplAuthKey: String = "",
    val microsoftKey: String = "", val microsoftRegion: String = "",
)
class TranslationConfigException / TranslationHttpException  // unchanged
```

**Engine files** (one per provider, komi layout; internal pure helpers unit-tested; header comment "Adapted from komi-store (Apache-2.0)"):

| File | Request | Response parse | Key pure functions |
|---|---|---|---|
| `TranslatorEngine.kt` (Google, existing) | GET gtx, `sl=$source` (auto ok) | existing `parseGtxBody` | unchanged |
| `YoudaoTranslator.kt` | POST form `openapi.youdao.com/api`: q/from/to/appKey/salt/sign/signType=v3/curtime | `errorCode=="0"`, join `translation[]` | `buildSignInput` (≤20 chars; else head10+len+tail10), `sha256Hex(appKey+input+salt+curtime+secret)`, `mapLanguageCode` (`zh-TW→zh-CHT`…), salt = `UUID.randomUUID()` |
| `LibreTranslator.kt` | POST form `{baseUrl}/translate`: q/source/target/format=text(+api_key) | `translatedText`; `error` field → failure | target/source mapping (`zh-TW→zt`, `zh-CN→zh`, `auto` source) |
| `DeeplTranslator.kt` | POST form host by key suffix (`:fx`→api-free else api.deepl.com) `/v2/translate`, header `Authorization: DeepL-Auth-Key $key` | `translations[0].text`; HTTP error `message` | `deeplHost(key)`, target map (`zh-TW→ZH-HANT`, `en→EN-US`…), source `auto`→omit |
| `MicrosoftTranslator.kt` | POST `api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=…`, JSON `[{Text}]`, headers `Ocp-Apim-Subscription-Key/(-Region if not global/blank)` | `[0].translations[0].text`; object body with `error` → failure | target map (`zh-TW→zh-Hant`…), source `auto`→omit `from` |

All engines: blank required credentials ⇒ `TranslationConfigException` before any network; non-2xx ⇒ `TranslationHttpException(code)`; parse failures ⇒ `Result.failure`. Same dedicated `TranslationHttp` client (browser UA, no site cookies).

**`LanguageCatalog.kt`** (new) — komi's 33 `SupportedLanguage(code, displayName)` entries (English display names, as komi), `findByCode`, and `resolveDeviceLanguage(locale): String` for follow-system.

### 2. Settings — `AppSettingsStore.kt`

New/changed keys: `translation_provider` (stores provider id), `youdao_app_key`, `youdao_app_secret`, `libre_base_url`, `libre_api_key`, `deepl_auth_key`, `microsoft_key`, `microsoft_region`, `translation_target_lang` ("" = follow system). Removed: custom template/path keys + setters.

`TranslationSettingsReader.currentTranslatorConfig()` resolves: provider + credentials + **effective target** (blank setting → `resolveDeviceLanguage`; invalid → `zh-TW`). `translateByDefault` unchanged.

### 3. `TranslationRepository.kt`

- Cache key: `trans:{providerId}:{source}:{targetLang}:{sha256(text)}` (target now variable ⇒ must isolate).
- Per-provider credential validation before cache/engine (missing ⇒ `TranslationConfigException`, zero engine calls): Youdao key+secret, Libre baseUrl (fallback disroot default if blank ⇒ Libre needs no mandatory config), DeepL key, Microsoft key.
- Everything else unchanged: memory→disk `CacheStore`, singleton-scope in-flight dedup (publish-before-register), failures never cached, main-safe.

### 4. Settings UI — `TranslationSettingsSection.kt` (rewritten)

- Row 1: 翻譯接口 dropdown (5 providers)
- Row 2 (selected provider's form, komi-style with explicit 儲存 button, disabled until required fields non-blank; secrets use password visuals):
  - Youdao: AppKey + AppSecret
  - Libre: 實例 URL (placeholder disroot) + API 金鑰(optional)
  - DeepL: Auth Key + "獲取免費密鑰" link (deepl.com/pro-api)
  - Microsoft: 金鑰 + 區域(placeholder global) + "獲取金鑰" link (Azure portal)
  - Google: hint text (free, IP rate-limited)
  - Every form keeps 測試翻譯 (uses current field values + selected provider + effective target, re-entry guarded)
- Row 3: 自動翻譯開關 (existing)
- Row 4: 目標語言 dropdown — 跟隨系統 (default) + 33 languages; always visible (governs manual translate too; komi shows it only under auto-translate)

`SettingsViewModel`: per-form save methods + provider/target setters + `testTranslation(provider, formFields)` (one running flag).

### 5. Removals

`CustomUrlTranslator` + `buildCustomRequestUrl`/`extractCustomResponse` + their tests; custom template/path settings, strings, hint texts.

### 6. Strings (~30 new, values + values-en)

Provider names, form labels, save/test buttons, target-language row + 跟隨系統, links, per-provider not-configured hint.

## Interaction & edge cases

- **Switch provider/target** ⇒ new cache namespace; previously cached translations remain (LRU-evicted eventually).
- **Unconfigured provider selected** ⇒ detail-page translate shows 翻譯接口未配置或無效 (existing `translate_config_error`); settings forms disable save until valid.
- **Follow-system target** resolved per call in `currentTranslatorConfig()`; device language change takes effect next translate.
- **429 / HTTP errors / parse errors** — existing mapping (rate-limited toast vs generic failure) applies to all engines.
- **Libre blank URL** ⇒ repository substitutes disroot default (komi behavior).
- **Old stored values** (`translation_provider="custom"`, `translate_custom_*` keys) — `fromId` falls back to GOOGLE; stale custom keys are simply ignored.
- Detail screen, ViewModel state machine, toggle UX, share-original-title: unchanged.

## Testing

- `YoudaoTranslatorTest` — sign-input truncation, sha256 vector, response parse (ok/errorCode≠0), lang mapping
- `LibreTranslatorTest` / `DeeplTranslatorTest` / `MicrosoftTranslatorTest` — response parsing (success/error variants), host-by-`:fx` (DeepL), lang mappings, `auto` source handling
- `LanguageCatalogTest` — findByCode, device-locale resolution (zh-TW/zh-CN/en/fallback)
- `TranslationRepositoryTest` — per-provider config validation (zero engine calls), cache key includes target (different targets ⇒ different engine calls), existing dedup/cache tests adapted
- Gates: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`

## No changes to

Detail screen UI, `MovieDetailViewModel` behavior (state machine identical; only repository internals change), forum/list screens, `NetClient`.

## Out of scope (extension points)

- Long-text chunking & span protection (komi's `chunkText`/`protectFromTranslation`) — unnecessary for titles; add when synopsis/forum translation lands
- In-app proxy settings for the translation client (komi honors app proxy config; we rely on system/VPN)
- Localized display names for the 33 languages (English names as komi)
- OpenAI-style LLM translators
