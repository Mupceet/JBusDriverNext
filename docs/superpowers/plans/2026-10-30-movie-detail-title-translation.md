# Movie Detail Title Translation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Translate the movie detail page title from Japanese to Traditional Chinese, with a free default API, a user-configurable GET-template API, memory+disk caching, and a translate/show-original toggle.

**Architecture:** ViewModel-owned `TitleTranslationState` (UDF) fed by a new data-layer `TranslationRepository` (cache lookup → in-flight dedup → engine request). Engines: built-in Google `translate_a/single` and a custom URL-template engine. Settings live in `AppSettingsStore` behind a new `TranslationSettingsReader` interface.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, OkHttp 5, Gson `JsonElement` parsing (no new model classes), DataStore Preferences, existing `CacheStore` (LruCache + FileCache).

**Spec:** `docs/superpowers/specs/2026-10-30-movie-detail-title-translation-design.md`

## Global Constraints

- Package root `me.jbusdriver.modern`; no new dependencies.
- UDF: screen state in ViewModel `StateFlow`; no callbacks to UI. ViewModels must NOT hardcode `Dispatchers.IO` — the translation engines/repo own IO switching (same precedent as `DefaultCacheStore`).
- Data-layer suspend functions are main-safe.
- All visible UI strings via `R.string` resources. Default `values/strings.xml` is **Traditional Chinese**; add English to `values-en/strings.xml`. Escape `&` as `&amp;` inside XML string values.
- No new Gson model classes → no new ProGuard/R8 keep rules.
- The worktree has unrelated dirty files (`.idea/`, `gradle/`, `.codex/`). After every `git add`, run `git status --short` and confirm ONLY intended files are staged before `git commit`.
- Shell is Git Bash on Windows; gradle via `./gradlew`.
- Commit style follows repo history: `feat(translation): …`, `test(translation): …`, `chore(translation): …`.

## File Structure

| File | Responsibility |
|---|---|
| `data/translation/TranslationModels.kt` (new) | Provider enum, config data class, lang constants, config exception, test sample |
| `data/translation/TranslatorEngine.kt` (new) | Engine interface + Google/Custom engines + dedicated OkHttp client + pure parse helpers |
| `data/translation/TranslationRepository.kt` (new) | Repository interface + impl (cache/dedup) + engine factory interface |
| `data/settings/AppSettingsStore.kt` (mod) | `TranslationSettingsReader` interface, new keys/flows/setters |
| `data/di/DataModule.kt` (mod) | Hilt bindings |
| `ui/detail/MovieDetailViewModel.kt` (mod) | `TitleTranslationState`, toggle, default-on policy |
| `ui/detail/MovieDetailScreen.kt` (mod) | Top-bar toggle action + title swap |
| `ui/settings/SettingsViewModel.kt` (mod) | Translation setters + test-translation |
| `ui/settings/TranslationSettingsSection.kt` (new) | Settings "翻译" card |
| `ui/settings/SettingsScreen.kt` (mod) | Wire card; `SwitchRow` private→internal |
| `res/drawable/translate_24px.xml` (new) | Translate icon |
| `res/values/strings.xml` + `res/values-en/strings.xml` (mod) | ~20 new strings |

---

### Task 1: Translation engines with pure parse helpers

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationModels.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/translation/TranslatorEngineTest.kt`

**Interfaces:**
- Produces: `interface TranslatorEngine { suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> }`; `enum class TranslationProvider(val id: String) { GOOGLE("google"), CUSTOM("custom") }`; `data class TranslatorConfig(provider: TranslationProvider, customUrlTemplate: String, customResponsePath: String)`; `class TranslationConfigException : Exception`; `const val TRANSLATION_SOURCE_LANG = "ja"`, `TRANSLATION_TARGET_LANG = "zh-TW"`, `TRANSLATION_TEST_SAMPLE = "テスト"`; internal pure helpers `parseGtxBody(body: String): String?`, `buildCustomRequestUrl(template: String, text: String, sourceLang: String, targetLang: String): String?`, `extractCustomResponse(body: String, path: String): String?`. Used by Tasks 2, 3, 4, 6.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/jbusdriver/modern/data/translation/TranslatorEngineTest.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslatorEngineTest {

    // ---- parseGtxBody ----

    @Test
    fun parseGtxBody_joinsSegments() {
        val body = """[[["譯文A","原文A",null,null,1],["譯文B","原文B",null,null,1]],null,"ja"]"""
        assertEquals("譯文A譯文B", parseGtxBody(body))
    }

    @Test
    fun parseGtxBody_blankResultReturnsNull() {
        assertNull(parseGtxBody("""[[["","原文"]]]"""))
    }

    @Test
    fun parseGtxBody_malformedReturnsNull() {
        assertNull(parseGtxBody("not json"))
    }

    // ---- buildCustomRequestUrl ----

    @Test
    fun buildCustomRequestUrl_encodesTextAndReplacesPlaceholders() {
        assertEquals(
            "https://x/api/ja/zh-TW/%E3%83%86%E3%82%B9%E3%83%88",
            buildCustomRequestUrl("https://x/api/{source}/{target}/{text}", "テスト", "ja", "zh-TW")
        )
    }

    @Test
    fun buildCustomRequestUrl_missingTextPlaceholderReturnsNull() {
        assertNull(buildCustomRequestUrl("https://x/api?q=1", "テスト", "ja", "zh-TW"))
    }

    @Test
    fun buildCustomRequestUrl_blankTemplateReturnsNull() {
        assertNull(buildCustomRequestUrl("   ", "テスト", "ja", "zh-TW"))
    }

    // ---- extractCustomResponse ----

    @Test
    fun extractCustomResponse_blankPathReturnsTrimmedBody() {
        assertEquals("譯文", extractCustomResponse("  譯文  ", ""))
    }

    @Test
    fun extractCustomResponse_blankPathBlankBodyReturnsNull() {
        assertNull(extractCustomResponse("   ", ""))
    }

    @Test
    fun extractCustomResponse_objectPath() {
        assertEquals("譯文", extractCustomResponse("""{"translation":"譯文"}""", "translation"))
    }

    @Test
    fun extractCustomResponse_nestedObjectPath() {
        assertEquals("譯文", extractCustomResponse("""{"responseData":{"translatedText":"譯文"}}""", "responseData.translatedText"))
    }

    @Test
    fun extractCustomResponse_arrayIndexPath() {
        assertEquals("譯文", extractCustomResponse("""{"data":[["譯文","原文"]]}""", "data.0.0"))
    }

    @Test
    fun extractCustomResponse_missingPathReturnsNull() {
        assertNull(extractCustomResponse("""{"other":1}""", "translation"))
    }

    @Test
    fun extractCustomResponse_blankLeafReturnsNull() {
        assertNull(extractCustomResponse("""{"translation":"   "}""", "translation"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.TranslatorEngineTest"`
Expected: COMPILATION ERROR (`parseGtxBody`/`buildCustomRequestUrl`/`extractCustomResponse` unresolved).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationModels.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

/** 翻译引擎选择;DataStore 以 [id] 持久化。 */
enum class TranslationProvider(val id: String) {
    GOOGLE("google"),
    CUSTOM("custom");

    companion object {
        fun fromId(id: String?): TranslationProvider = when (id) {
            CUSTOM.id -> CUSTOM
            else -> GOOGLE
        }
    }
}

/** 当前生效的翻译配置,由 AppSettingsStore 从 DataStore 解析生成。 */
data class TranslatorConfig(
    val provider: TranslationProvider,
    val customUrlTemplate: String,
    val customResponsePath: String,
)

const val TRANSLATION_SOURCE_LANG = "ja"
const val TRANSLATION_TARGET_LANG = "zh-TW"

/** 设置页"测试翻译"按钮使用的固定样例。 */
const val TRANSLATION_TEST_SAMPLE = "テスト"

/** 自定义接口配置无效(模板缺失或不含 {text})。 */
class TranslationConfigException(message: String) : Exception(message)
```

Create `app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

interface TranslatorEngine {
    /** 主线程安全;成功返回非空译文,失败返回 [Result.failure]。 */
    suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String>
}

/**
 * 翻译专用轻量客户端:不带目标站 CookieJar/existmag 拦截器,
 * 避免把站点 Cookie 发给第三方翻译接口。
 */
private object TranslationHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
}

/** Google 免费接口(translate_a/single?client=gtx),无需密钥。 */
class GoogleFreeTranslator : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://translate.googleapis.com/translate_a/single" +
                    "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=${urlEncode(text)}"
                val body = execute(url)
                parseGtxBody(body) ?: error("gtx unparsable body")
            }.onFailure { KLog.t(TAG).w("google translate failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "GoogleFreeTranslator"
    }
}

/** 用户自定义 GET 模板接口;{text} URL 编码后替换,{source}/{target} 原样替换。 */
class CustomUrlTranslator(private val config: TranslatorConfig) : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = buildCustomRequestUrl(config.customUrlTemplate, text, sourceLang, targetLang)
                    ?: throw TranslationConfigException("custom url template missing {text}")
                val body = execute(url)
                extractCustomResponse(body, config.customResponsePath) ?: error("custom empty translation")
            }
        }
}

private fun execute(url: String): String {
    TranslationHttp.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "http ${response.code}" }
        return response.body?.string() ?: error("empty body")
    }
}

internal fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

/** gtx 响应形如 [[["譯文","原文",null,null,1],…],null,"ja",…];拼接每个分段首元素,空白视为解析失败。 */
internal fun parseGtxBody(body: String): String? = runCatching {
    val segments = JsonParser.parseString(body).asJsonArray.first().asJsonArray
    segments.mapNotNull { it.asJsonArray.firstOrNull()?.asString }.joinToString("").ifBlank { null }
}.getOrNull()

/** 模板空白或不含 {text} 返回 null;{source}/{target} 缺失时占位符原样保留。 */
internal fun buildCustomRequestUrl(template: String, text: String, sourceLang: String, targetLang: String): String? {
    if (template.isBlank() || !template.contains("{text}")) return null
    return template
        .replace("{text}", urlEncode(text))
        .replace("{source}", sourceLang)
        .replace("{target}", targetLang)
}

/** path 为空 → 响应体(去空白)即译文;否则按点分路径取值,数字段表数组下标;非原始类型叶子视为失败。 */
internal fun extractCustomResponse(body: String, path: String): String? {
    if (path.isBlank()) return body.trim().ifBlank { null }
    return try {
        var node: JsonElement? = JsonParser.parseString(body)
        for (segment in path.trim().split('.')) {
            val current = node ?: return null
            node = when (current) {
                is JsonObject -> current.get(segment)
                is JsonArray -> segment.toIntOrNull()?.let { current.get(it) }
                else -> null
            } ?: return null
        }
        (node as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim()?.ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
```

Note: `check(...)` inside `execute` and the `use` block — `use` returns the lambda result, so `return response.body?.string()...` returns from `execute` with the non-null-checked body; keep exactly as written (the `?: error(...)` handles null).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.TranslatorEngineTest"`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/translation/TranslationModels.kt \
        app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt \
        app/src/test/java/me/jbusdriver/modern/data/translation/TranslatorEngineTest.kt
git status --short   # only the 3 files staged
git commit -m "feat(translation): add translator engines with google free endpoint and custom url template"
```

---

### Task 2: Settings store extension

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt`

**Interfaces:**
- Consumes: `TranslationProvider`, `TranslatorConfig` from Task 1.
- Produces: `interface TranslationSettingsReader { val translateByDefault: StateFlow<Boolean>; suspend fun currentTranslatorConfig(): TranslatorConfig }` (in `data/settings/AppSettingsStore.kt`); on `AppSettingsContract`: `translationProvider: StateFlow<TranslationProvider>`, `customTranslateUrlTemplate: StateFlow<String>`, `customTranslateResponsePath: StateFlow<String>`, `setTranslateByDefault(Boolean)`, `setTranslationProvider(TranslationProvider)`, `setCustomTranslateUrlTemplate(String)`, `setCustomTranslateResponsePath(String)`. Used by Tasks 3, 4, 6.

No unit test (DataStore-backed store needs Android context; no existing `AppSettingsStore` test). Gate = compile + existing tests still pass.

- [ ] **Step 1: Add the reader interface**

In `AppSettingsStore.kt`, after `MovieListSettings` (around line 46) add:

```kotlin
/** Narrow read interface for translation consumers (MovieDetailViewModel / DefaultTranslationRepository). */
interface TranslationSettingsReader {
    val translateByDefault: StateFlow<Boolean>
    suspend fun currentTranslatorConfig(): TranslatorConfig
}
```

Add imports:

```kotlin
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslatorConfig
```

- [ ] **Step 2: Extend `AppSettingsContract`**

Change the declaration (line 49) to add `TranslationSettingsReader`:

```kotlin
interface AppSettingsContract : ThemeSettingsReader, ForumSettingsReader, MovieListSettings,
    SitePreferenceSource, TranslationSettingsReader {
```

Inside the interface body (after the Network section, before the setters), add:

```kotlin
    // Translation
    val translationProvider: StateFlow<TranslationProvider>
    val customTranslateUrlTemplate: StateFlow<String>
    val customTranslateResponsePath: StateFlow<String>

    suspend fun setTranslateByDefault(enabled: Boolean)
    suspend fun setTranslationProvider(provider: TranslationProvider)
    suspend fun setCustomTranslateUrlTemplate(template: String)
    suspend fun setCustomTranslateResponsePath(path: String)
```

- [ ] **Step 3: Implement in `AppSettingsStore`**

Add a new region after the `// region Network` block's `// endregion` (around line 145):

```kotlin
    // region Translation
    override val translateByDefault: StateFlow<Boolean> = flowOf(KEY_TRANSLATE_BY_DEFAULT, false)
    override val translationProvider: StateFlow<TranslationProvider> =
        mapped(KEY_TRANSLATE_PROVIDER, TranslationProvider.GOOGLE) { TranslationProvider.fromId(it) }
    override val customTranslateUrlTemplate: StateFlow<String> = flowOf(KEY_TRANSLATE_CUSTOM_URL_TEMPLATE, "")
    override val customTranslateResponsePath: StateFlow<String> = flowOf(KEY_TRANSLATE_CUSTOM_RESPONSE_PATH, "")
    override suspend fun currentTranslatorConfig(): TranslatorConfig {
        val prefs = dataStore.data.first()
        return TranslatorConfig(
            provider = TranslationProvider.fromId(prefs[KEY_TRANSLATE_PROVIDER]),
            customUrlTemplate = prefs[KEY_TRANSLATE_CUSTOM_URL_TEMPLATE].orEmpty(),
            customResponsePath = prefs[KEY_TRANSLATE_CUSTOM_RESPONSE_PATH].orEmpty(),
        )
    }
    override suspend fun setTranslateByDefault(enabled: Boolean) = put(KEY_TRANSLATE_BY_DEFAULT, enabled)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = put(KEY_TRANSLATE_PROVIDER, provider.id)
    override suspend fun setCustomTranslateUrlTemplate(template: String) = put(KEY_TRANSLATE_CUSTOM_URL_TEMPLATE, template.trim())
    override suspend fun setCustomTranslateResponsePath(path: String) = put(KEY_TRANSLATE_CUSTOM_RESPONSE_PATH, path.trim())
    // endregion
```

Add keys in the `companion object`:

```kotlin
        private val KEY_TRANSLATE_BY_DEFAULT = booleanPreferencesKey("translate_by_default")
        private val KEY_TRANSLATE_PROVIDER = stringPreferencesKey("translate_provider")
        private val KEY_TRANSLATE_CUSTOM_URL_TEMPLATE = stringPreferencesKey("translate_custom_url_template")
        private val KEY_TRANSLATE_CUSTOM_RESPONSE_PATH = stringPreferencesKey("translate_custom_response_path")
```

- [ ] **Step 4: Verify compile + existing tests**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (this only adds members; no consumers yet).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt
git status --short   # only this file staged
git commit -m "feat(translation): add translation settings keys and reader interface to AppSettingsStore"
```

---

### Task 3: Translation repository + DI bindings

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationRepository.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/translation/TranslationRepositoryTest.kt`

**Interfaces:**
- Consumes: `TranslatorEngine`/`TranslatorConfig`/`TranslationProvider`/`TranslationConfigException` (Task 1), `TranslationSettingsReader` (Task 2), `CacheStore` (`readMemory`/`writeMemory`/`readDisk`/`writeDisk`).
- Produces: `interface TranslationRepository { suspend fun translate(text: String, sourceLang: String = TRANSLATION_SOURCE_LANG, targetLang: String = TRANSLATION_TARGET_LANG): Result<String> }`; `interface TranslatorEngineFactory { fun create(config: TranslatorConfig): TranslatorEngine }`. Used by Tasks 4, 6.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/jbusdriver/modern/data/translation/TranslationRepositoryTest.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryTest {

    private class FakeEngine(var result: Result<String>) : TranslatorEngine {
        var calls = 0
        override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
            calls++
            return result
        }
    }

    private class FakeSettings(
        var config: TranslatorConfig = TranslatorConfig(TranslationProvider.GOOGLE, "", ""),
    ) : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(false)
        override suspend fun currentTranslatorConfig(): TranslatorConfig = config
    }

    private class FakeCacheStore : CacheStore {
        val memory = mutableMapOf<String, String>()
        val disk = mutableMapOf<String, String>()
        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) { memory[key] = value }
        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) { disk[key] = value }
    }

    private fun repo(
        cache: FakeCacheStore = FakeCacheStore(),
        engine: FakeEngine = FakeEngine(Result.success("譯文")),
        settings: FakeSettings = FakeSettings(),
    ) = DefaultTranslationRepository(
        cacheStore = cache,
        settings = settings,
        engineFactory = { _ -> engine },
    )

    @Test
    fun translate_successWritesMemoryAndDiskCache() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("譯文"))
        val result = repo(cache, engine).translate("テスト")
        assertEquals("譯文", result.getOrThrow())
        assertEquals(1, engine.calls)
        assertEquals(1, cache.memory.size)
        assertEquals(1, cache.disk.size)
    }

    @Test
    fun translate_memoryCacheHitSkipsEngine() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("譯文"))
        val repository = repo(cache, engine)
        repository.translate("テスト")
        val second = repository.translate("テスト")
        assertEquals("譯文", second.getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_diskCacheHitBackfillsMemory() = runTest {
        val cache = FakeCacheStore()
        cache.disk["trans:google:zh-TW:${sha256("テスト")}"] = "磁盤譯文"
        val engine = FakeEngine(Result.success("不應請求"))
        val result = repo(cache, engine).translate("テスト")
        assertEquals("磁盤譯文", result.getOrThrow())
        assertEquals(0, engine.calls)
        assertEquals("磁盤譯文", cache.memory.values.single())
    }

    @Test
    fun translate_failureIsNotCached() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.failure(RuntimeException("boom")))
        val repository = repo(cache, engine)
        assertTrue(repository.translate("テスト").isFailure)
        assertTrue(repository.translate("テスト").isFailure)
        assertEquals(2, engine.calls)
        assertTrue(cache.memory.isEmpty())
        assertTrue(cache.disk.isEmpty())
    }

    @Test
    fun translate_customProviderWithoutTemplateFailsFastWithoutEngineCall() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine(Result.success("x"))
        val result = repo(cache, engine, FakeSettings(TranslatorConfig(TranslationProvider.CUSTOM, "", "")))
            .translate("テスト")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_concurrentSameKeyCallsEngineOnce() = runTest {
        val engine = FakeEngine(Result.success("譯文"))
        val repository = repo(FakeCacheStore(), engine)
        val results = (1..3).map { async { repository.translate("テスト") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, engine.calls)
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```

Note the `engineFactory = { _ -> engine }` SAM conversion — `TranslatorEngineFactory` is declared `fun interface` in Step 3.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.TranslationRepositoryTest"`
Expected: COMPILATION ERROR (`DefaultTranslationRepository` unresolved).

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationRepository.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface TranslationRepository {
    /**
     * 翻译文本;主线程安全。命中内存/磁盘缓存不发请求,同键并发请求只打一次接口。
     * 失败不写缓存,返回 [Result.failure](配置无效时为 [TranslationConfigException])。
     */
    suspend fun translate(
        text: String,
        sourceLang: String = TRANSLATION_SOURCE_LANG,
        targetLang: String = TRANSLATION_TARGET_LANG,
    ): Result<String>
}

/** 依据配置创建引擎;独立成接口便于测试注入假引擎。 */
fun interface TranslatorEngineFactory {
    fun create(config: TranslatorConfig): TranslatorEngine
}

@Singleton
class DefaultTranslatorEngineFactory @Inject constructor() : TranslatorEngineFactory {
    private val googleFreeTranslator = GoogleFreeTranslator()
    override fun create(config: TranslatorConfig): TranslatorEngine = when (config.provider) {
        TranslationProvider.GOOGLE -> googleFreeTranslator
        TranslationProvider.CUSTOM -> CustomUrlTranslator(config)
    }
}

@Singleton
class DefaultTranslationRepository @Inject constructor(
    private val cacheStore: CacheStore,
    private val settings: TranslationSettingsReader,
    private val engineFactory: TranslatorEngineFactory,
) : TranslationRepository {

    /** 请求生命周期与调用方解耦:任一调用方取消都不中断共享请求(单例作用域)。 */
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<String>>>()
    private val inFlightMutex = Mutex()

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): Result<String> {
        val config = settings.currentTranslatorConfig()
        if (config.provider == TranslationProvider.CUSTOM && !config.customUrlTemplate.contains("{text}")) {
            return Result.failure(TranslationConfigException("custom url template missing {text}"))
        }
        val key = cacheKey(config.provider.id, targetLang, text)
        cacheStore.readMemory(key)?.let { return Result.success(it) }
        cacheStore.readDisk(key)?.let { cached ->
            cacheStore.writeMemory(key, cached)
            return Result.success(cached)
        }
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: requestScope.async {
                engineFactory.create(config).translate(text, sourceLang, targetLang)
                    .onSuccess {
                        cacheStore.writeMemory(key, it)
                        cacheStore.writeDisk(key, it)
                    }
                    .onFailure { KLog.t(TAG).w("translate failed: ${it.message}") }
            }.also { newDeferred ->
                newDeferred.invokeOnCompletion { inFlight.remove(key, newDeferred) }
                inFlight[key] = newDeferred
            }
        }
        return deferred.await()
    }

    private fun cacheKey(providerId: String, targetLang: String, text: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "trans:$providerId:$targetLang:$hash"
    }

    private companion object {
        const val TAG = "TranslationRepository"
    }
}
```

Simplify the test's factory lambda in Step 1 to `{ _ -> engine }` (both providers use the same fake):

```kotlin
        engineFactory = { _ -> engine },
```

- [ ] **Step 4: Bind in Hilt**

In `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`, add imports:

```kotlin
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import me.jbusdriver.modern.data.translation.DefaultTranslationRepository
import me.jbusdriver.modern.data.translation.DefaultTranslatorEngineFactory
import me.jbusdriver.modern.data.translation.TranslationRepository
import me.jbusdriver.modern.data.translation.TranslatorEngineFactory
```

Add bindings at the end of the module (before the closing brace):

```kotlin
    @Binds
    @Singleton
    abstract fun bindTranslationSettingsReader(impl: AppSettingsStore): TranslationSettingsReader

    @Binds
    @Singleton
    abstract fun bindTranslatorEngineFactory(impl: DefaultTranslatorEngineFactory): TranslatorEngineFactory

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(impl: DefaultTranslationRepository): TranslationRepository
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.TranslationRepositoryTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/translation/TranslationRepository.kt \
        app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt \
        app/src/test/java/me/jbusdriver/modern/data/translation/TranslationRepositoryTest.kt
git status --short   # only the 3 files staged
git commit -m "feat(translation): add cached translation repository with in-flight dedup"
```

---

### Task 4: MovieDetailViewModel translation state + toggle

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `TranslationRepository.translate(text, sourceLang, targetLang): Result<String>` (Task 3), `TranslationSettingsReader.translateByDefault: StateFlow<Boolean>` (Task 2), `TranslationConfigException` (Task 1).
- Produces: `sealed interface TitleTranslationState { data object Hidden; data object Loading; data class Translated(val text: String) }`; `MovieDetailUiState.titleTranslation: TitleTranslationState`; `MovieDetailViewModel.toggleTitleTranslation()`. Used by Task 5.

- [ ] **Step 1: Add strings (tests reference `R.string.translate_failed`)**

In `app/src/main/res/values/strings.xml` (Traditional Chinese, default), append before `</resources>`:

```xml
    <!-- Translation -->
    <string name="translate_failed">翻譯失敗</string>
    <string name="translate_config_error">翻譯接口未配置或無效</string>
```

In `app/src/main/res/values-en/strings.xml` append before `</resources>`:

```xml
    <!-- Translation -->
    <string name="translate_failed">Translation failed</string>
    <string name="translate_config_error">Translation API not configured or invalid</string>
```

- [ ] **Step 2: Update existing tests (constructor gains 2 params) and add new tests**

In `MovieDetailViewModelTest.kt` add imports:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslationRepository
import me.jbusdriver.modern.data.translation.TranslatorConfig
import me.jbusdriver.modern.ui.UserMessage
```

Add stubs next to the other stubs:

```kotlin
    private val stubTranslationRepo = object : TranslationRepository {
        var calls = 0
        override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
            calls++
            return Result.success("譯文:$text")
        }
    }

    private val stubTranslationSettings = object : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(false)
        override suspend fun currentTranslatorConfig() =
            TranslatorConfig(TranslationProvider.GOOGLE, "", "")
    }

    private fun defaultOnSettings() = object : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(true)
        override suspend fun currentTranslatorConfig() =
            TranslatorConfig(TranslationProvider.GOOGLE, "", "")
    }
```

Replace ALL existing `MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo)` constructor calls (8 occurrences across tests — use replace-all in the editor) with:

```kotlin
MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings)
```

Append these new tests:

```kotlin
    @Test
    fun loadDetail_defaultOff_keepsTitleTranslationHidden() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun loadDetail_defaultOn_autoTranslatesTitle() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, defaultOnSettings())

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Translated("譯文:Test Movie"), viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun toggleTitleTranslation_switchesBetweenOriginalAndTranslated() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)

        viewModel.toggleTitleTranslation()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Test Movie"), viewModel.uiState.value.titleTranslation)

        viewModel.toggleTitleTranslation()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun toggleTitleTranslation_failureResetsAndEmitsMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val failingRepo = object : TranslationRepository {
            override suspend fun translate(text: String, sourceLang: String, targetLang: String) =
                Result.failure(RuntimeException("boom"))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, failingRepo, stubTranslationSettings)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
        assertEquals(R.string.translate_failed, messages.single().resId)
    }

    @Test
    fun toggleTitleTranslation_configErrorEmitsConfigMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val configErrorRepo = object : TranslationRepository {
            override suspend fun translate(text: String, sourceLang: String, targetLang: String) =
                Result.failure(me.jbusdriver.modern.data.translation.TranslationConfigException("bad"))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, configErrorRepo, stubTranslationSettings)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(R.string.translate_config_error, messages.single().resId)
    }

    @Test
    fun refresh_reappliesDefaultTranslationPolicyForNewTitle() = runTest(testDispatcher) {
        var title = "Title One"
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                testDetail.copy(title = title)
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, defaultOnSettings())

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Title One"), viewModel.uiState.value.titleTranslation)

        title = "Title Two"
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Title Two"), viewModel.uiState.value.titleTranslation)
    }
```

Also add import `kotlinx.coroutines.launch` if not present.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: COMPILATION ERROR (`TitleTranslationState` unresolved, constructor arity mismatch).

- [ ] **Step 4: Implement in `MovieDetailViewModel.kt`**

Add imports:

```kotlin
import kotlinx.coroutines.flow.first
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import me.jbusdriver.modern.data.translation.TranslationConfigException
import me.jbusdriver.modern.data.translation.TranslationRepository
```

Add the sealed interface at file level (after the imports, before `MovieDetailUiState`):

```kotlin
/** 详情页标题翻译显示状态:原文 / 请求中 / 已译(持有译文)。 */
sealed interface TitleTranslationState {
    data object Hidden : TitleTranslationState
    data object Loading : TitleTranslationState
    data class Translated(val text: String) : TitleTranslationState
}
```

Extend `MovieDetailUiState` (add after `movieDetail`):

```kotlin
    val titleTranslation: TitleTranslationState = TitleTranslationState.Hidden,
```

Extend the constructor:

```kotlin
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository,
    private val magnetRepository: MagnetRepository,
    private val localVideoRepository: LocalVideoRepository,
    private val translationRepository: TranslationRepository,
    private val translationSettings: TranslationSettingsReader,
) : ViewModel() {
```

In `loadDetail`, immediately after the `_uiState.update { it.copy(movieDetail = ..., isLoading = false, gid = ..., uc = ...) }` block and BEFORE `loadMagnets()`, add:

```kotlin
                applyDefaultTranslationPolicy()
```

In `refresh`, after `_uiState.update { it.copy(movieDetail = detail.toUiModel(), isRefreshing = false) }` add:

```kotlin
            applyDefaultTranslationPolicy()
```

Add the translation methods at the end of the class (after `emitDeleteResult`):

```kotlin
    /** 详情页顶栏翻译入口:原文→译文 / 译文→原文;请求中忽略重复点击。 */
    fun toggleTitleTranslation() {
        when (_uiState.value.titleTranslation) {
            TitleTranslationState.Loading -> Unit
            is TitleTranslationState.Translated ->
                _uiState.update { it.copy(titleTranslation = TitleTranslationState.Hidden) }
            TitleTranslationState.Hidden -> startTitleTranslation()
        }
    }

    /** 详情(重新)加载后:重置翻译状态;按"默认翻译"设置决定是否自动翻译新标题。 */
    private fun applyDefaultTranslationPolicy() {
        _uiState.update { it.copy(titleTranslation = TitleTranslationState.Hidden) }
        viewModelScope.launch {
            if (translationSettings.translateByDefault.first()) startTitleTranslation()
        }
    }

    private fun startTitleTranslation() {
        val title = _uiState.value.movieDetail?.title?.takeIf { it.isNotBlank() } ?: return
        _uiState.update { it.copy(titleTranslation = TitleTranslationState.Loading) }
        viewModelScope.launch {
            translationRepository.translate(title).fold(
                onSuccess = { text ->
                    // 仅当仍处于 Loading(未被 refresh 重置)时落地,避免覆盖刷新后的原文
                    _uiState.update { state ->
                        if (state.titleTranslation == TitleTranslationState.Loading)
                            state.copy(titleTranslation = TitleTranslationState.Translated(text)) else state
                    }
                },
                onFailure = { cause ->
                    if (cause is CancellationException) return@launch
                    _uiState.update { state ->
                        if (state.titleTranslation == TitleTranslationState.Loading)
                            state.copy(titleTranslation = TitleTranslationState.Hidden) else state
                    }
                    val resId = if (cause is TranslationConfigException) {
                        R.string.translate_config_error
                    } else {
                        R.string.translate_failed
                    }
                    _messages.emit(UserMessage(resId))
                }
            )
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest"`
Expected: PASS (14 tests: 8 existing + 6 new).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModel.kt \
        app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git status --short   # only these 4 files staged
git commit -m "feat(translation): add title translation state and toggle to MovieDetailViewModel"
```

---

### Task 5: Detail screen UI (icon + toggle action + title swap)

**Files:**
- Create: `app/src/main/res/drawable/translate_24px.xml`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: `TitleTranslationState` / `uiState.titleTranslation` / `viewModel.toggleTitleTranslation()` (Task 4).
- Produces: visible toggle action + swapped title. No downstream consumers.

- [ ] **Step 1: Add the translate icon**

Create `app/src/main/res/drawable/translate_24px.xml` (classic Material "translate" glyph, 24 viewport — visually verify it renders as a "文/A" translate symbol after the build):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="?attr/colorControlNormal"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12.87 15.07l-2.54-2.51 0.03-0.03c1.09-1.24 1.94-2.62 2.53-4.11L17 8.42 17 6L11 6 11 3L9 3 9 6 1 6 1 8 11.17 8c-0.67 1.92-1.73 3.75-3.17 5.35-0.93-1.03-1.7-2.16-2.31-3.35L3.69 10c0.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 21l5-5 3.11 3.11 0.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z" />
</vector>
```

- [ ] **Step 2: Add action strings**

`values/strings.xml`:

```xml
    <string name="translate_action">翻譯</string>
    <string name="translate_show_original">顯示原文</string>
```

`values-en/strings.xml`:

```xml
    <string name="translate_action">Translate</string>
    <string name="translate_show_original">Show original</string>
```

(Append inside the existing `<!-- Translation -->` block from Task 4.)

- [ ] **Step 3: Wire the screen**

In `MovieDetailScreen.kt`:

3a. Top-bar title (~line 150) — show translated text when available:

```kotlin
                title = {
                    val translatedTitle = (uiState.titleTranslation as? TitleTranslationState.Translated)?.text
                    Text(
                        translatedTitle ?: detail?.title ?: stringResource(R.string.loading),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
```

3b. Actions — insert this block INSIDE `actions = { if (detail != null) { ... } }`, as the FIRST action (before `ShareButton(text = shareText)`). The whole block is hidden when the title is blank (spec: blank title ⇒ no translation entry):

```kotlin
                        if (detail.title.isNotBlank()) {
                            when (uiState.titleTranslation) {
                                TitleTranslationState.Loading -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                is TitleTranslationState.Translated -> IconButton(
                                    onClick = viewModel::toggleTitleTranslation
                                ) {
                                    Icon(
                                        painterResource(R.drawable.translate_24px),
                                        contentDescription = stringResource(R.string.translate_show_original),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TitleTranslationState.Hidden -> IconButton(
                                    onClick = viewModel::toggleTitleTranslation
                                ) {
                                    Icon(
                                        painterResource(R.drawable.translate_24px),
                                        contentDescription = stringResource(R.string.translate_action)
                                    )
                                }
                            }
                        }
```

3c. In-page title item (`item(key = "title")`, ~line 411) — swap text and copy what is displayed:

```kotlin
        // Title
        item(key = "title") {
            val translatedTitle = (uiState.titleTranslation as? TitleTranslationState.Translated)?.text
            val displayTitle = translatedTitle ?: detail.title
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClick = {
                            context.copy(displayTitle)
                            Toast.makeText(context, copiedTitleMessage, Toast.LENGTH_SHORT).show()
                        },
                        onClick = {}
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
```

`TitleTranslationState` is in the same package (`me.jbusdriver.modern.ui.detail`, declared in `MovieDetailViewModel.kt`) — no import needed. All composables used (`CircularProgressIndicator`, `IconButton`, `MaterialTheme`, `Modifier.size`) are already imported in this file.

Share text keeps using `detail.title` (original) — unchanged.

- [ ] **Step 4: Verify build + lint**

Run: `./gradlew assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL, no new lint errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/translate_24px.xml \
        app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git status --short   # only these 4 files staged
git commit -m "feat(translation): add detail screen title swap and top bar translate toggle"
```

---

### Task 6: Settings UI (translation card)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/TranslationSettingsSection.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- Modify: `docs/superpowers/specs/2026-10-30-movie-detail-title-translation-design.md` (validation-behavior refinement)

**Interfaces:**
- Consumes: `AppSettingsContract` new members (Task 2), `TranslatorEngineFactory` (Task 3), `TRANSLATION_TEST_SAMPLE`/`TRANSLATION_SOURCE_LANG`/`TRANSLATION_TARGET_LANG`/`TranslatorConfig`/`TranslationProvider` (Task 1).
- Produces: `SettingsViewModel.translationTestRunning: StateFlow<Boolean>`, setter methods, `TranslationSettingsCard` composable.

No SettingsViewModel unit test exists today (no `AppSettingsContract` fake precedent); translation logic itself is covered by Tasks 1/3. Gate = compile + lint + manual verify.

- [ ] **Step 1: Add strings**

`values/strings.xml`:

```xml
    <string name="settings_translation_section">翻譯</string>
    <string name="settings_translation_default">進入詳情頁預設翻譯標題</string>
    <string name="settings_translation_provider">翻譯接口</string>
    <string name="settings_translation_provider_builtin">內建免費接口</string>
    <string name="settings_translation_provider_custom">自訂接口</string>
    <string name="settings_translation_url_template">URL 模板</string>
    <string name="settings_translation_url_template_hint">https://lt.example/translate?q={text}&amp;source={source}&amp;target={target}</string>
    <string name="settings_translation_response_path">響應路徑</string>
    <string name="settings_translation_response_path_hint">留空=純文本;如 translatedText 或 data.0.0</string>
    <string name="settings_translation_test">測試翻譯</string>
    <string name="translate_template_invalid">URL 模板必須包含 {text}</string>
    <string name="translate_test_success">翻譯成功:%1$s</string>
    <string name="translate_test_failed">測試失敗,請檢查接口配置</string>
```

`values-en/strings.xml`:

```xml
    <string name="settings_translation_section">Translation</string>
    <string name="settings_translation_default">Auto-translate title on detail screen</string>
    <string name="settings_translation_provider">Translation API</string>
    <string name="settings_translation_provider_builtin">Built-in free API</string>
    <string name="settings_translation_provider_custom">Custom API</string>
    <string name="settings_translation_url_template">URL template</string>
    <string name="settings_translation_url_template_hint">https://lt.example/translate?q={text}&amp;source={source}&amp;target={target}</string>
    <string name="settings_translation_response_path">Response path</string>
    <string name="settings_translation_response_path_hint">Blank = plain text; e.g. translatedText or data.0.0</string>
    <string name="settings_translation_test">Test translation</string>
    <string name="translate_template_invalid">URL template must contain {text}</string>
    <string name="translate_test_success">Translation OK: %1$s</string>
    <string name="translate_test_failed">Test failed — check the API configuration</string>
```

- [ ] **Step 2: Extend `SettingsViewModel`**

Add imports:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.jbusdriver.modern.data.translation.TRANSLATION_SOURCE_LANG
import me.jbusdriver.modern.data.translation.TRANSLATION_TARGET_LANG
import me.jbusdriver.modern.data.translation.TRANSLATION_TEST_SAMPLE
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslatorConfig
import me.jbusdriver.modern.data.translation.TranslatorEngineFactory
```

Add constructor parameter (after `appUpdateDownloader`, before `ioDispatcher`):

```kotlin
    private val translatorEngineFactory: TranslatorEngineFactory,
```

Add a region after the Network-scan region:

```kotlin
    // region Translation settings
    private val _translationTestRunning = MutableStateFlow(false)
    val translationTestRunning: StateFlow<Boolean> = _translationTestRunning.asStateFlow()

    fun setTranslateByDefault(enabled: Boolean) {
        viewModelScope.launch { store.setTranslateByDefault(enabled) }
    }

    fun setTranslationProvider(provider: TranslationProvider) {
        viewModelScope.launch { store.setTranslationProvider(provider) }
    }

    /** 保存自定义模板;缺少 {text} 时提示且不落盘。 */
    fun setCustomTranslateUrlTemplate(template: String) {
        if (!template.contains("{text}")) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.translate_template_invalid)) }
            return
        }
        viewModelScope.launch { store.setCustomTranslateUrlTemplate(template) }
    }

    fun setCustomTranslateResponsePath(path: String) {
        viewModelScope.launch { store.setCustomTranslateResponsePath(path) }
    }

    /** 用当前输入值(未保存)试译固定样例,结果经 messages Toast。 */
    fun testTranslation(template: String, responsePath: String) {
        if (_translationTestRunning.value) return
        if (!template.contains("{text}")) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.translate_template_invalid)) }
            return
        }
        _translationTestRunning.value = true
        viewModelScope.launch {
            try {
                val config = TranslatorConfig(TranslationProvider.CUSTOM, template.trim(), responsePath.trim())
                translatorEngineFactory.create(config)
                    .translate(TRANSLATION_TEST_SAMPLE, TRANSLATION_SOURCE_LANG, TRANSLATION_TARGET_LANG)
                    .fold(
                        onSuccess = { _messages.emit(UserMessage(R.string.translate_test_success, listOf(it))) },
                        onFailure = { _messages.emit(UserMessage(R.string.translate_test_failed)) }
                    )
            } finally {
                _translationTestRunning.value = false
            }
        }
    }
    // endregion
```

- [ ] **Step 3: Create `TranslationSettingsSection.kt`**

Create `app/src/main/java/me/jbusdriver/modern/ui/settings/TranslationSettingsSection.kt`:

```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.ui.components.SelectableDropdownItem

/**
 * 设置页"翻譯"分组:默认翻译开关、接口选择、自定义模板/响应路径与试译。
 * 文本框在失焦时提交;无效模板(缺 {text})被 ViewModel 拒绝并 Toast 提示。
 */
@Composable
internal fun TranslationSettingsCard(
    translateByDefault: Boolean,
    provider: TranslationProvider,
    customUrlTemplate: String,
    customResponsePath: String,
    testRunning: Boolean,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onCustomUrlTemplateChange: (String) -> Unit,
    onCustomResponsePathChange: (String) -> Unit,
    onTest: (template: String, responsePath: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                stringResource(R.string.settings_translation_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp)
            )
            SwitchRow(
                label = stringResource(R.string.settings_translation_default),
                checked = translateByDefault,
                onCheckedChange = onTranslateByDefaultChange
            )
            TranslationProviderRow(provider = provider, onProviderChange = onProviderChange)
            if (provider == TranslationProvider.CUSTOM) {
                CustomEndpointFields(
                    initialTemplate = customUrlTemplate,
                    initialResponsePath = customResponsePath,
                    testRunning = testRunning,
                    onTemplateChange = onCustomUrlTemplateChange,
                    onResponsePathChange = onCustomResponsePathChange,
                    onTest = onTest
                )
            }
        }
    }
}

@Composable
private fun TranslationProviderRow(
    provider: TranslationProvider,
    onProviderChange: (TranslationProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_translation_provider), style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                providerLabel(provider),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TranslationProvider.entries.forEach { candidate ->
                    SelectableDropdownItem(
                        label = providerLabel(candidate),
                        selected = candidate == provider,
                        onClick = {
                            onProviderChange(candidate)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun providerLabel(provider: TranslationProvider): String = when (provider) {
    TranslationProvider.GOOGLE -> stringResource(R.string.settings_translation_provider_builtin)
    TranslationProvider.CUSTOM -> stringResource(R.string.settings_translation_provider_custom)
}

@Composable
private fun CustomEndpointFields(
    initialTemplate: String,
    initialResponsePath: String,
    testRunning: Boolean,
    onTemplateChange: (String) -> Unit,
    onResponsePathChange: (String) -> Unit,
    onTest: (template: String, responsePath: String) -> Unit,
) {
    var template by remember(initialTemplate) { mutableStateOf(initialTemplate) }
    var responsePath by remember(initialResponsePath) { mutableStateOf(initialResponsePath) }
    Column(
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = template,
            onValueChange = { template = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (!state.isFocused && template != initialTemplate) onTemplateChange(template.trim())
                },
            label = { Text(stringResource(R.string.settings_translation_url_template)) },
            placeholder = { Text(stringResource(R.string.settings_translation_url_template_hint)) },
            singleLine = true
        )
        OutlinedTextField(
            value = responsePath,
            onValueChange = { responsePath = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (!state.isFocused && responsePath != initialResponsePath) onResponsePathChange(responsePath.trim())
                },
            label = { Text(stringResource(R.string.settings_translation_response_path)) },
            placeholder = { Text(stringResource(R.string.settings_translation_response_path_hint)) },
            singleLine = true
        )
        Button(
            onClick = { onTest(template.trim(), responsePath.trim()) },
            enabled = !testRunning
        ) {
            Text(stringResource(R.string.settings_translation_test))
        }
    }
}
```

- [ ] **Step 4: Wire into `SettingsScreen.kt`**

4a. Make `SwitchRow` reusable: change `private fun SwitchRow(` (line ~359) to `internal fun SwitchRow(`.

4b. Collect state after the Network state block (~line 105):

```kotlin
    // Translation state
    val translateByDefault by store.translateByDefault.collectAsStateWithLifecycle()
    val translationProvider by store.translationProvider.collectAsStateWithLifecycle()
    val customTranslateUrlTemplate by store.customTranslateUrlTemplate.collectAsStateWithLifecycle()
    val customTranslateResponsePath by store.customTranslateResponsePath.collectAsStateWithLifecycle()
    val translationTestRunning by viewModel.translationTestRunning.collectAsStateWithLifecycle()
```

4c. Insert the card between `NetworkCard(...)` and the Local Video comment (~line 215):

```kotlin
            // === Translation Card ===
            TranslationSettingsCard(
                translateByDefault = translateByDefault,
                provider = translationProvider,
                customUrlTemplate = customTranslateUrlTemplate,
                customResponsePath = customTranslateResponsePath,
                testRunning = translationTestRunning,
                onTranslateByDefaultChange = { viewModel.setTranslateByDefault(it) },
                onProviderChange = { viewModel.setTranslationProvider(it) },
                onCustomUrlTemplateChange = { viewModel.setCustomTranslateUrlTemplate(it) },
                onCustomResponsePathChange = { viewModel.setCustomTranslateResponsePath(it) },
                onTest = { template, path -> viewModel.testTranslation(template, path) }
            )
```

- [ ] **Step 5: Amend the spec (validation refinement)**

In `docs/superpowers/specs/2026-10-30-movie-detail-title-translation-design.md`, replace the validation sentence:

OLD: `- Validation: switching to CUSTOM requires a template containing \`{text}\`; template edits missing \`{text}\` are rejected with a message and not persisted. Text fields persist on IME action / focus loss. Response path is free-form (validated implicitly by the test button).`

NEW: `- Validation: the provider selection persists freely (selecting CUSTOM reveals the fields for configuration); template saves missing \`{text}\` are rejected with a message and not persisted. Text fields persist on focus loss. Response path is free-form (validated implicitly by the test button). The repository additionally fails fast with a config error when CUSTOM is active without a usable template.`

(Rationale: blocking the switch to CUSTOM with no visible field would dead-end the user.)

- [ ] **Step 6: Verify build + lint**

Run: `./gradlew assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL, no new lint errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/TranslationSettingsSection.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        docs/superpowers/specs/2026-10-30-movie-detail-title-translation-design.md
git status --short   # only these 6 files staged
git commit -m "feat(translation): add settings card with provider config and endpoint test"
```

---

### Task 7: Full quality gates + manual smoke checklist

**Files:** none created (verification only).

- [ ] **Step 1: Run all quality gates**

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Expected: all BUILD SUCCESSFUL. If release fails on R8, the cause would be a missing keep rule for a new Gson model class — by design there are none (JsonElement parsing only); investigate before proceeding.

- [ ] **Step 2: Manual smoke (emulator/device, debug build)**

1. Default state (fresh install / default settings): open any movie detail → top bar shows translate icon (contentDescription 翻譯); tap → title (top bar + in-page) becomes Traditional Chinese; icon becomes highlighted with contentDescription 顯示原文; tap again → original.
2. Tap translate again after toggling back → returns instantly (memory cache; no spinner flash on fast network is fine).
3. Kill process, reopen same movie, tap translate → translation appears without a fresh network request (disk cache; verify via offline/airplane mode).
4. Settings → 翻譯 → enable 進入詳情頁預設翻譯標題 → open a detail page → auto-translated, top bar shows 顯示原文 action.
5. Settings → 翻譯接口 → 自訂接口 → template without `{text}` → blur the field → Toast "URL 模板必須包含 {text}", value not saved (reopen settings to confirm).
6. Custom endpoint with a real Lingva-style URL (`https://lingva.example/api/v1/{source}/{target}/{text}`, path `translation`) → 測試翻譯 → Toast shows the translated sample; then open a detail page and translate — custom endpoint result is used and cached separately from the built-in one.
7. Airplane mode + un-cached movie → tap 翻譯 → Toast 翻譯失敗, title stays original; next tap retries.
8. Share button text still contains the ORIGINAL Japanese title.

- [ ] **Step 3: Final wrap-up commit (if anything was fixed)**

If gates forced small fixes, commit them with `chore(translation): …`; otherwise nothing to commit. Run `git status --short` and `git log --oneline -8` to confirm a clean feature history: 6 commits, one per task.
