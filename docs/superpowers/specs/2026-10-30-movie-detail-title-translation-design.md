# Movie Detail: Title Translation (JA → Traditional Chinese)

**Date:** 2026-10-30
**Status:** Approved

## Problem

The movie detail page renders Japanese site content as-is. The title (shown twice: top app bar and in-page headline) is Japanese text that many users cannot read. We need opt-in translation of the title into Traditional Chinese (`ja` → `zh-TW`), backed by a free no-key API by default, with support for a user-configurable endpoint, on-disk caching to avoid repeat requests, and a toggle that offers "translate" when the original is shown and "show original" when the translation is shown.

## Confirmed requirements

| Item | Decision |
|---|---|
| Scope (v1) | Movie detail screen only |
| Fields | Only the currently rendered title (app-bar title + in-page headline). Synopsis (`MovieDetail.content`) stays unrendered; headers/genres/actresses untouched |
| Languages | Source `ja`, target `zh-TW`, fixed constants (no UI) |
| Display | Replacement toggle: translated text replaces the original in place; toggle switches back |
| API | Default free no-key endpoint (Google `translate_a/single?client=gtx`); user-configurable GET URL template with `{text}`/`{source}`/`{target}` placeholders + response path |
| Cache | Required; memory + disk; no TTL (translations are stable) |
| Toggle entry | Default off → "translate" action; default on (or once translated) → "show original" action. Global default lives in Settings |

## Solution

ViewModel-owned translation state fed by a new data-layer translation service (approach A). Alternatives considered and rejected:

- **Composable-local translation** (`remember` + coroutine inside a `TranslatableText`): least wiring, but puts business state in composition, violating the project's UDF rules, and breaks the SharedFlow-based error messaging pattern.
- **Translate at parse/repository time** (inside `MovieDetailRepository` before caching): pollutes the shared SWR detail cache with translated text and wastes requests when translation is off.

### New package `data/translation/`

**`TranslationModels.kt`**

```kotlin
enum class TranslationProvider(val id: String) { GOOGLE("google"), CUSTOM("custom") }

data class TranslatorConfig(
    val provider: TranslationProvider,
    val customUrlTemplate: String,   // required when CUSTOM, must contain "{text}"
    val customResponsePath: String,  // "" = response body is plain text
)

const val TRANSLATION_SOURCE_LANG = "ja"
const val TRANSLATION_TARGET_LANG = "zh-TW"
```

**`TranslatorEngine.kt`**

```kotlin
interface TranslatorEngine {
    suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String>
}
```

- `GoogleFreeTranslator` (`@Inject` class, no state): GET
  `https://translate.googleapis.com/translate_a/single?client=gtx&sl=ja&tl=zh-TW&dt=t&q=<encoded>`
  Response is a nested JSON array `[[["譯文","原文",null,null,10],[…]],null,"ja",…]`; join every first element of the inner array (skip nulls) via Gson `JsonElement` parsing.
- `CustomUrlTranslator(config)` (plain class, constructed per call by the repository): substitute `{text}` (URL-encoded), `{source}`, `{target}` into the template, GET it. Response handling: empty `responsePath` → body trimmed as result; otherwise dotted path where numeric segments index arrays (e.g. `translation`, `responseData.translatedText`, `data.0.0`). Non-string leaf or blank result → failure.
- Both engines use a **dedicated lightweight OkHttpClient** built in this package (no `CookieJar`, no `EXIST_MAGNET_INTERCEPTOR`, no site cookies leaked to third parties; 10s timeouts, generic UA).

**`TranslationRepository.kt`**

```kotlin
interface TranslationRepository {
    suspend fun translate(text: String, sourceLang: String = TRANSLATION_SOURCE_LANG,
                          targetLang: String = TRANSLATION_TARGET_LANG): Result<String>
}
```

`DefaultTranslationRepository` (`CacheStore` + `TranslationSettingsReader` injected, bound in `DataModule`):

1. Resolve current `TranslatorConfig` from settings (`currentTranslatorConfig()`), validate (CUSTOM without usable template → `Result.failure(TranslationConfigException)`).
2. Cache key `trans:{providerId}:{targetLang}:{sha256Hex(text)}`; value is the Gson-serialized translated string. Lookup memory → disk via `CacheStore` (hit ⇒ no engine call).
3. Miss: in-flight dedup via a `ConcurrentHashMap<String, Deferred<Result<String>>>` — concurrent same-key callers share one request; entry removed on completion. Engine call wrapped in `withContext(Dispatchers.IO)` (main-safe suspend; ViewModels call it directly from `viewModelScope`).
4. Success ⇒ write memory + disk cache and return; failure ⇒ nothing cached (next attempt retries).

### `AppSettingsStore.kt` — settings extension

New narrow reader interface (existing Reader pattern), extended into `AppSettingsContract`:

```kotlin
interface TranslationSettingsReader {
    val translateByDefault: StateFlow<Boolean>
    suspend fun currentTranslatorConfig(): TranslatorConfig
}
```

New DataStore keys and setters on `AppSettingsContract`:

| Key | Type | Default |
|---|---|---|
| `translate_by_default` | Boolean | `false` |
| `translate_provider` | String (`"google"`/`"custom"`) | `"google"` |
| `translate_custom_url_template` | String | `""` |
| `translate_custom_response_path` | String | `""` |

`currentTranslatorConfig()` maps the three provider keys into a `TranslatorConfig`. Setters: `setTranslateByDefault`, `setTranslationProvider`, `setCustomTranslateUrlTemplate`, `setCustomTranslateResponsePath`.

### `MovieDetailViewModel.kt` — state + toggle

```kotlin
sealed interface TitleTranslationState {
    data object Hidden : TitleTranslationState    // original shown
    data object Loading : TitleTranslationState   // request in flight
    data class Translated(val text: String) : TitleTranslationState
}
```

- `MovieDetailUiState` gains `titleTranslation: TitleTranslationState = Hidden`.
- Inject `TranslationRepository` + `TranslationSettingsReader`.
- After `loadDetail` success and after `refresh` applies a new detail: reset `titleTranslation = Hidden`, then if `translateByDefault.first()` → start translation for the new title (default-on policy re-applied; cached old titles are free).
- `toggleTitleTranslation()`: `Loading` → ignore; `Hidden` → start; `Translated` → back to `Hidden` (cache retained — switching again does not re-request).
- Start = `Loading` → `repository.translate(currentTitle)` → success: `Translated(text)`; failure: `Hidden` + `_messages.emit(UserMessage(R.string.translate_failed))` (Snackbar; retry available on next tap).
- Blank title: translation action is a no-op (button hidden in UI).

### `MovieDetailScreen.kt` — UI

- Top bar gains one action icon (existing share/collect actions untouched), placed next to them:
  - original shown → translate icon, contentDescription `翻译`, tap ⇒ `viewModel.toggleTitleTranslation()`
  - `Loading` → small `CircularProgressIndicator` (~20dp) in place of the icon (no tap target)
  - `Translated` → highlighted/filled icon, contentDescription `显示原文`, tap toggles back
- Both title render sites (app-bar title ~line 150, in-page headline ~lines 411–427) display `displayTitle = (titleTranslation as? Translated)?.text ?: detail.title`.
- Share text keeps using the **original** title (no change).

### Settings — `SettingsScreen.kt` / `SettingsViewModel.kt` + new `ui/settings/TranslationSettingsSection.kt`

`SettingsScreen` is already large; the new section is extracted into its own composable:

- Switch: 进入详情页默认翻译标题 (`translateByDefault`)
- Provider single-select: 内置免费接口 / 自定义接口
- Custom selected ⇒ expands: URL 模板 text field (hint example `https://lingva.example/api/v1/{source}/{target}/{text}`), 响应路径 text field (blank = plain-text response), and a **测试** button that translates a fixed sample (`テスト`, ja→zh-TW) using the values currently entered in the fields and shows the result (or error) in a Snackbar.
- Validation: switching to CUSTOM requires a template containing `{text}`; template edits missing `{text}` are rejected with a message and not persisted. Response path is free-form (validated implicitly by the test button).
- `SettingsViewModel` exposes section state via StateFlow and calls the new setters; no callbacks.

### Strings & R8

- ~12 new `strings.xml` entries: action labels (翻译/显示原文), failure/config messages, section title, switch label, provider labels, field labels/hints, test button + test result messages. All visible text via resources per repo i18n rule.
- No new Gson model classes (engines parse `JsonElement`; cache value is a plain String) ⇒ **no new ProGuard/R8 keep rules**.

## Interaction & edge cases

- **Repeat visits / process death:** disk cache answers without network. Cache survives until the 300MB `FileCache` evicts; eviction simply causes a re-fetch.
- **Provider switch:** key includes provider id ⇒ separate cache namespace, no stale cross-provider results.
- **Toggle spam:** `Loading` ignores taps; concurrent identical requests deduped in repository.
- **Network/parse failure:** Snackbar message, title stays original, nothing cached — next tap retries.
- **Misconfigured custom endpoint:** blocked at save time in Settings; defensively, repository returns config failure with its own message if DataStore was edited externally.
- **Refresh with changed title:** translation state resets and default policy re-applies.
- **Same-movie reopen:** `loadDetail` early-return keeps existing translation state (ViewModel survives in back stack).
- **Titles already containing Chinese/English:** translated anyway (`sl=ja` fixed); Google handles mixed content.

## Testing

Unit tests (`app/src/test/`):

- `GoogleFreeTranslatorTest` — parse gtx fixture (multi-segment join), null-segment skipping, non-200/error mapping.
- `CustomUrlTranslatorTest` — placeholder substitution + URL encoding, dotted path (object/array/plain text), missing `{text}` config error, blank result error.
- `TranslationRepositoryTest` (fake engine + fake CacheStore) — cache hit skips engine, cache write-through, concurrent same-key dedup (one engine call), failure not cached, config validation failure.
- `MovieDetailViewModelTest` — default-on auto-translates after load, default-off stays `Hidden`, toggle cycle Hidden→Loading→Translated→Hidden without second request, failure emits message + `Hidden`, refresh resets.

Quality gates: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease` (no new Gson models ⇒ low release risk, run per convention).

## No changes to

- List screens (movie/forum), forum screens, synopsis field, headers/genres/actresses rendering
- `NetClient` (translation uses its own client), `MovieDetailRepository` and the SWR detail cache
- Share text, collect/local-video flows

## Out of scope (extension points recorded)

- Forum post translation (`TextPart.text` granularity), synopsis rendering + translation, list-page titles — the `TranslatorEngine`/`TranslationRepository` boundary is content-agnostic and reusable.
- POST / API-key providers (DeepL, OpenAI-style) — would extend `TranslatorConfig` + add an engine.
- Configurable source/target languages.
