# Translation Services (Multi-Provider) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two-provider translation layer with komi-store's five selectable providers (Google/Youdao/LibreTranslate/DeepL/Microsoft), per-provider credential configuration, and a selectable target language (follow-system + 33 languages).

**Architecture:** Keep the reviewed skeleton (persistent cache + in-flight dedup repository, `TitleTranslationState` ViewModel, detail-screen toggle) and port komi's engine implementations to OkHttp + Gson. New engines take individual credential constructor params (no config type dependency) so they land before the atomic models/settings/repository switch.

**Tech Stack:** Kotlin, OkHttp 5 (FormBody/JSON POST), Gson `JsonElement` parsing, DataStore Preferences, Compose Material3. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-01-translation-services-design.md`
**Reference:** komi-store clone at `D:\Projects\refs\komi-store` (Apache-2.0; engine logic adapted).

## Global Constraints

> **Errata (post-execution):** two embedded snippets drifted from the shipped code during review loops — (1) Task 3 Step 3's repository snippet shows `invokeOnCompletion` BEFORE the `inFlight[key]` publish; the shipped code publishes FIRST (preserves the 1d8cba5b race fix). (2) Task 1's `resolveDeviceLanguage` snippet lacks the exact `lang-REGION` match; the shipped code adds it (pt/BR → pt-BR). If re-executing this plan, trust the committed code, not these snippets.

- Branch `feat/title-translation`; package `me.jbusdriver.modern`; no new dependencies.
- BEFORE every gradle command: `export JAVA_HOME='/c/Users/laiguizhong/AppData/Local/Programs/Android Studio/jbr'`
- Focused tests: `./gradlew testDebugUnitTest --tests "<class>"`. Full gates: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`.
- Every ported engine file starts with comment `// Adapted from komi-store (Apache-2.0)`.
- All UI strings via `R.string`, both `values/` (Traditional Chinese) and `values-en/`; `&` escaped as `&amp;`.
- Blank required credentials ⇒ `TranslationConfigException` before any network; non-2xx ⇒ `TranslationHttpException(code)` (429 keeps dedicated toast); parse failure ⇒ `Result.failure` with body snippet logged.
- `TranslationHttp` client stays dedicated (browser UA `TRANSLATION_USER_AGENT`, no site cookies).
- Worktree has unrelated dirty files (`.idea/`, `gradle/`, `.codex/`, `docs/superpowers/{plans,specs}/2026-06-28-*`) — never stage them; verify `git status --short` before each commit.
- Data-layer suspend functions main-safe (engines own `withContext(Dispatchers.IO)`); ViewModels never hardcode dispatchers.
- Commit style: `feat(translation): …`, `test(translation): …`, `chore(translation): …`.

---

### Task 1: LanguageCatalog

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/LanguageCatalog.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/translation/LanguageCatalogTest.kt`

**Interfaces:**
- Produces: `data class SupportedLanguage(val code: String, val displayName: String)`; `object LanguageCatalog { val all: List<SupportedLanguage>; fun findByCode(code: String?): SupportedLanguage?; fun resolveDeviceLanguage(language: String, region: String): String }`. Used by Tasks 3 (settings target resolution) and 4 (target dropdown options).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/jbusdriver/modern/data/translation/LanguageCatalogTest.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun all_containsKomiStoreSet() {
        assertEquals(33, LanguageCatalog.all.size)
        assertEquals("zh-TW", LanguageCatalog.all.first { it.displayName == "Chinese (Traditional)" }.code)
    }

    @Test
    fun findByCode_matchesCaseInsensitive() {
        assertEquals("zh-TW", LanguageCatalog.findByCode("ZH-tw")?.code)
    }

    @Test
    fun findByCode_unknownReturnsNull() {
        assertNull(LanguageCatalog.findByCode("xx"))
        assertNull(LanguageCatalog.findByCode(null))
    }

    @Test
    fun resolveDeviceLanguage_chineseSimplifiedByRegion() {
        assertEquals("zh-CN", LanguageCatalog.resolveDeviceLanguage("zh", "CN"))
        assertEquals("zh-CN", LanguageCatalog.resolveDeviceLanguage("zh", ""))
    }

    @Test
    fun resolveDeviceLanguage_chineseTraditionalByRegion() {
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "TW"))
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "HK"))
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("zh", "MO"))
    }

    @Test
    fun resolveDeviceLanguage_matchesByLanguage() {
        assertEquals("ja", LanguageCatalog.resolveDeviceLanguage("ja", "JP"))
        assertEquals("en", LanguageCatalog.resolveDeviceLanguage("en", "US"))
        assertEquals("pt-BR", LanguageCatalog.resolveDeviceLanguage("pt", "BR"))
    }

    @Test
    fun resolveDeviceLanguage_unknownFallsBackToTraditionalChinese() {
        assertEquals("zh-TW", LanguageCatalog.resolveDeviceLanguage("xx", "XX"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.LanguageCatalogTest"`
Expected: COMPILATION ERROR (`LanguageCatalog` unresolved).

- [ ] **Step 3: Implement**

Create `app/src/main/java/me/jbusdriver/modern/data/translation/LanguageCatalog.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

/** 可选翻译目标语言;显示名沿用 komi-store 的英文命名。 */
data class SupportedLanguage(val code: String, val displayName: String)

/** 目标语言目录 + 跟随系统的设备语言推断。 */
object LanguageCatalog {
    val all: List<SupportedLanguage> = listOf(
        SupportedLanguage("ar", "Arabic"),
        SupportedLanguage("bn", "Bengali"),
        SupportedLanguage("zh-CN", "Chinese (Simplified)"),
        SupportedLanguage("zh-TW", "Chinese (Traditional)"),
        SupportedLanguage("cs", "Czech"),
        SupportedLanguage("da", "Danish"),
        SupportedLanguage("nl", "Dutch"),
        SupportedLanguage("en", "English"),
        SupportedLanguage("fi", "Finnish"),
        SupportedLanguage("fr", "French"),
        SupportedLanguage("de", "German"),
        SupportedLanguage("el", "Greek"),
        SupportedLanguage("he", "Hebrew"),
        SupportedLanguage("hi", "Hindi"),
        SupportedLanguage("hu", "Hungarian"),
        SupportedLanguage("id", "Indonesian"),
        SupportedLanguage("it", "Italian"),
        SupportedLanguage("ja", "Japanese"),
        SupportedLanguage("ko", "Korean"),
        SupportedLanguage("ms", "Malay"),
        SupportedLanguage("no", "Norwegian"),
        SupportedLanguage("pl", "Polish"),
        SupportedLanguage("pt", "Portuguese"),
        SupportedLanguage("pt-BR", "Portuguese (Brazil)"),
        SupportedLanguage("ro", "Romanian"),
        SupportedLanguage("ru", "Russian"),
        SupportedLanguage("es", "Spanish"),
        SupportedLanguage("sv", "Swedish"),
        SupportedLanguage("th", "Thai"),
        SupportedLanguage("tr", "Turkish"),
        SupportedLanguage("uk", "Ukrainian"),
        SupportedLanguage("uz", "Uzbek"),
        SupportedLanguage("vi", "Vietnamese"),
    )

    fun findByCode(code: String?): SupportedLanguage? =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    /**
     * 跟随系统:zh 按地区分简繁(TW/HK/MO → 繁体,其余简体),
     * 其他语言按语言前缀匹配目录(如 pt-BR 地区优先命中);无法匹配回退 zh-TW。
     */
    fun resolveDeviceLanguage(language: String, region: String): String {
        val lang = language.lowercase().substringBefore('-')
        return when {
            lang == "zh" ->
                if (region.uppercase() in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            else -> all.firstOrNull { it.code.startsWith(lang) }?.code ?: "zh-TW"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.LanguageCatalogTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/translation/LanguageCatalog.kt \
        app/src/test/java/me/jbusdriver/modern/data/translation/LanguageCatalogTest.kt
git status --short   # only these 2 files
git commit -m "feat(translation): add supported language catalog with device-language resolution"
```

---

### Task 2: Youdao / LibreTranslate / DeepL / Microsoft engines + POST helpers

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt` (add `postForm`/`postJson` helpers after the existing `execute`)
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/YoudaoTranslator.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/LibreTranslator.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/DeeplTranslator.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/data/translation/MicrosoftTranslator.kt`
- Test: `app/src/test/java/me/jbusdriver/modern/data/translation/NewTranslatorsTest.kt`

**Interfaces:**
- Consumes: existing `TranslatorEngine` interface (`suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String>`), `TranslationConfigException`, `TranslationHttpException`, `TranslationHttp` client.
- Produces: `class YoudaoTranslator(appKey: String, appSecret: String) : TranslatorEngine`; `class LibreTranslator(baseUrl: String, apiKey: String) : TranslatorEngine`; `class DeeplTranslator(authKey: String) : TranslatorEngine`; `class MicrosoftTranslator(key: String, region: String) : TranslatorEngine`; internal helpers `postForm(url, headers = emptyMap(), form: FormBody.Builder.() -> Unit): String`, `postJson(url: String, payload: String, headers: Map<String, String> = emptyMap()): String`; pure functions `buildSignInput`, `sha256Hex`, `parseYoudaoBody`, `youdaoLangCode`, `libreSourceCode`, `libreTargetCode`, `parseLibreBody`, `deeplHost`, `deeplTargetCode`, `deeplSourceCode`, `parseDeeplBody`, `msLangCode`, `msSourceCode`, `parseMicrosoftBody`. Used by Task 3's factory.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/me/jbusdriver/modern/data/translation/NewTranslatorsTest.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewTranslatorsTest {

    // ---- Youdao ----

    @Test
    fun buildSignInput_shortTextUnchanged() {
        assertEquals("テスト", buildSignInput("テスト"))
    }

    @Test
    fun buildSignInput_longTextHeadLenTail() {
        val text = (1..40).joinToString("") { "a" } // 40 chars
        assertEquals(text.take(10) + "40" + text.takeLast(10), buildSignInput(text))
    }

    @Test
    fun sha256Hex_knownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex("")
        )
    }

    @Test
    fun youdaoLangCode_mapsChineseVariants() {
        assertEquals("auto", youdaoLangCode("auto"))
        assertEquals("zh-CHS", youdaoLangCode("zh-CN"))
        assertEquals("zh-CHT", youdaoLangCode("zh-TW"))
        assertEquals("ja", youdaoLangCode("ja"))
    }

    @Test
    fun parseYoudaoBody_joinsTranslationArray() {
        assertEquals("第一行\n第二行", parseYoudaoBody("""{"errorCode":"0","translation":["第一行","第二行"]}"""))
    }

    @Test
    fun parseYoudaoBody_errorCodeReturnsNull() {
        assertNull(parseYoudaoBody("""{"errorCode":"108","msg":"param error"}"""))
    }

    // ---- LibreTranslate ----

    @Test
    fun libreCodes_mapVariantsAndAuto() {
        assertEquals("auto", libreSourceCode("auto"))
        assertEquals("zh", libreSourceCode("zh-CN"))
        assertEquals("zt", libreTargetCode("zh-TW"))
        assertNull(libreTargetCode("auto"))
        assertEquals("ja", libreTargetCode("ja-JP"))
    }

    @Test
    fun parseLibreBody_extractsTranslatedText() {
        assertEquals("譯文", parseLibreBody("""{"translatedText":"譯文"}"""))
    }

    @Test
    fun parseLibreBody_errorFieldReturnsNull() {
        assertNull(parseLibreBody("""{"error":"quota exceeded"}"""))
    }

    // ---- DeepL ----

    @Test
    fun deeplHost_switchesByFxSuffix() {
        assertEquals("https://api-free.deepl.com", deeplHost("abc:fx"))
        assertEquals("https://api.deepl.com", deeplHost("abc"))
    }

    @Test
    fun deeplCodes_mapVariants() {
        assertEquals("ZH-HANT", deeplTargetCode("zh-TW"))
        assertEquals("ZH-HANS", deeplTargetCode("zh-CN"))
        assertEquals("EN-US", deeplTargetCode("en"))
        assertNull(deeplSourceCode("auto"))
        assertEquals("ZH", deeplSourceCode("zh-TW"))
        assertEquals("JA", deeplSourceCode("ja"))
    }

    @Test
    fun parseDeeplBody_extractsFirstTranslation() {
        assertEquals(
            "譯文",
            parseDeeplBody("""{"translations":[{"detected_source_language":"JA","text":"譯文"}]}""")
        )
    }

    @Test
    fun parseDeeplBody_errorObjectReturnsNull() {
        assertNull(parseDeeplBody("""{"message":"Wrong auth key"}"""))
    }

    // ---- Microsoft ----

    @Test
    fun msLangCode_mapsVariants() {
        assertEquals("zh-Hant", msLangCode("zh-TW"))
        assertEquals("zh-Hans", msLangCode("zh-CN"))
        assertEquals("pt-br", msLangCode("pt"))
        assertEquals("nb", msLangCode("no"))
        assertEquals("ja", msLangCode("ja"))
    }

    @Test
    fun msSourceCode_autoReturnsNull() {
        assertNull(msSourceCode("auto"))
        assertEquals("ja", msSourceCode("ja"))
    }

    @Test
    fun parseMicrosoftBody_extractsFirstTranslation() {
        assertEquals(
            "譯文",
            parseMicrosoftBody("""[{"detectedLanguage":{"language":"ja"},"translations":[{"text":"譯文","to":"zh-Hant"}]}]""")
        )
    }

    @Test
    fun parseMicrosoftBody_errorObjectReturnsNull() {
        assertNull(parseMicrosoftBody("""{"error":{"code":401000,"message":"invalid key"}}"""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.NewTranslatorsTest"`
Expected: COMPILATION ERROR (all helpers unresolved).

- [ ] **Step 3: Add POST helpers to `TranslatorEngine.kt`**

Immediately after the existing `private fun execute(url: String): String` add:

```kotlin
/** 表单 POST(有道/Libre/DeepL);非 2xx 抛 [TranslationHttpException]。 */
internal fun postForm(
    url: String,
    headers: Map<String, String> = emptyMap(),
    form: FormBody.Builder.() -> Unit,
): String {
    val body = FormBody.Builder().apply(form).build()
    val builder = Request.Builder().url(url).header("User-Agent", TRANSLATION_USER_AGENT).post(body)
    headers.forEach { (name, value) -> builder.header(name, value) }
    TranslationHttp.client.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) throw TranslationHttpException(response.code)
        return response.body?.string() ?: error("empty body")
    }
}

/** JSON POST(微软);非 2xx 抛 [TranslationHttpException]。 */
internal fun postJson(url: String, payload: String, headers: Map<String, String> = emptyMap()): String {
    val builder = Request.Builder().url(url)
        .header("User-Agent", TRANSLATION_USER_AGENT)
        .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
    headers.forEach { (name, value) -> builder.header(name, value) }
    TranslationHttp.client.newCall(builder.build()).execute().use { response ->
        if (!response.isSuccessful) throw TranslationHttpException(response.code)
        return response.body?.string() ?: error("empty body")
    }
}
```

Add imports to `TranslatorEngine.kt`:

```kotlin
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
```

- [ ] **Step 4: Implement the four engines**

Create `app/src/main/java/me/jbusdriver/modern/data/translation/YoudaoTranslator.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

// Adapted from komi-store (Apache-2.0): YoudaoTranslator.kt (Ktor -> OkHttp port)
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import java.security.MessageDigest
import java.util.UUID

/** 有道智云文本翻译(API v3 签名);需在设置配置 AppKey/AppSecret。 */
class YoudaoTranslator(
    private val appKey: String,
    private val appSecret: String,
) : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (appKey.isBlank() || appSecret.isBlank()) {
                    throw TranslationConfigException("youdao appKey/appSecret not configured")
                }
                val salt = UUID.randomUUID().toString()
                val curtime = (System.currentTimeMillis() / 1000L).toString()
                val sign = sha256Hex(appKey + buildSignInput(text) + salt + curtime + appSecret)
                val body = postForm("https://openapi.youdao.com/api") {
                    add("q", text)
                    add("from", youdaoLangCode(sourceLang))
                    add("to", youdaoLangCode(targetLang))
                    add("appKey", appKey)
                    add("salt", salt)
                    add("sign", sign)
                    add("signType", "v3")
                    add("curtime", curtime)
                }
                parseYoudaoBody(body) ?: error("youdao unparsable: ${body.take(80)}")
            }.onFailure { KLog.t(TAG).w("youdao translate failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "YoudaoTranslator"
    }
}

/** v3 签名输入:≤20 字符原样;否则 头10+长度+尾10。 */
internal fun buildSignInput(q: String): String =
    if (q.length <= 20) q else q.substring(0, 10) + q.length.toString() + q.substring(q.length - 10)

internal fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

/** errorCode 非 0 视为失败;translation 数组按行拼接。 */
internal fun parseYoudaoBody(body: String): String? = runCatching {
    val root = JsonParser.parseString(body).asJsonObject
    val errorCode = root.get("errorCode")?.takeIf { it.isJsonPrimitive }?.asString
    if (errorCode != null && errorCode != "0") return null
    root.getAsJsonArray("translation")
        ?.mapNotNull { it.asString }
        ?.joinToString("\n")
        ?.ifBlank { null }
}.getOrNull()

internal fun youdaoLangCode(code: String): String = when (code.lowercase()) {
    "auto", "" -> "auto"
    "zh", "zh-cn", "zh-hans" -> "zh-CHS"
    "zh-tw", "zh-hant" -> "zh-CHT"
    else -> code
}
```

Create `app/src/main/java/me/jbusdriver/modern/data/translation/LibreTranslator.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

// Adapted from komi-store (Apache-2.0): LibreTranslator.kt (Ktor -> OkHttp port)
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog

/** LibreTranslate 实例(可自建);实例 URL 未配置时由仓库层回退默认实例。 */
class LibreTranslator(
    private val baseUrl: String,
    private val apiKey: String,
) : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (baseUrl.isBlank()) throw TranslationConfigException("libretranslate baseUrl not configured")
                val target = libreTargetCode(targetLang) ?: error("libre target language required")
                val body = postForm(baseUrl.trimEnd('/') + "/translate") {
                    add("q", text)
                    add("source", libreSourceCode(sourceLang))
                    add("target", target)
                    add("format", "text")
                    if (apiKey.isNotBlank()) add("api_key", apiKey)
                }
                parseLibreBody(body) ?: error("libre unparsable: ${body.take(80)}")
            }.onFailure { KLog.t(TAG).w("libre translate failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "LibreTranslator"
    }
}

internal fun libreSourceCode(code: String): String = when (code.lowercase()) {
    "auto", "" -> "auto"
    "zh", "zh-cn", "zh-hans" -> "zh"
    "zh-tw", "zh-hant" -> "zt"
    else -> code.lowercase().substringBefore('-')
}

internal fun libreTargetCode(code: String): String? = when (code.lowercase()) {
    "auto", "" -> null
    "zh", "zh-cn", "zh-hans" -> "zh"
    "zh-tw", "zh-hant" -> "zt"
    else -> code.lowercase().substringBefore('-')
}

internal fun parseLibreBody(body: String): String? = runCatching {
    val root = JsonParser.parseString(body).asJsonObject
    if (root.get("error") != null) return null
    root.get("translatedText")?.asString?.trim()?.ifBlank { null }
}.getOrNull()
```

Create `app/src/main/java/me/jbusdriver/modern/data/translation/DeeplTranslator.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

// Adapted from komi-store (Apache-2.0): DeeplTranslator.kt (Ktor -> OkHttp port)
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog

/** DeepL 官方 API;authKey 以 :fx 结尾走免费版域名。 */
class DeeplTranslator(private val authKey: String) : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (authKey.isBlank()) throw TranslationConfigException("deepl auth key not configured")
                val body = postForm(
                    deeplHost(authKey) + "/v2/translate",
                    headers = mapOf("Authorization" to "DeepL-Auth-Key $authKey"),
                ) {
                    add("text", text)
                    add("target_lang", deeplTargetCode(targetLang))
                    deeplSourceCode(sourceLang)?.let { add("source_lang", it) }
                    add("preserve_formatting", "1")
                }
                parseDeeplBody(body) ?: error("deepl unparsable: ${body.take(80)}")
            }.onFailure { KLog.t(TAG).w("deepl translate failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "DeeplTranslator"
    }
}

internal fun deeplHost(authKey: String): String =
    if (authKey.endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"

internal fun deeplTargetCode(code: String): String = when (code.lowercase()) {
    "en" -> "EN-US"
    "pt" -> "PT-PT"
    "pt-br" -> "PT-BR"
    "zh", "zh-cn", "zh-hans" -> "ZH-HANS"
    "zh-tw", "zh-hant" -> "ZH-HANT"
    else -> code.uppercase()
}

internal fun deeplSourceCode(code: String): String? = when (code.lowercase()) {
    "", "auto" -> null
    "zh", "zh-cn", "zh-hans", "zh-tw", "zh-hant" -> "ZH"
    "pt", "pt-br" -> "PT"
    else -> code.uppercase().substringBefore('-')
}

internal fun parseDeeplBody(body: String): String? = runCatching {
    JsonParser.parseString(body).asJsonObject
        .getAsJsonArray("translations")
        ?.firstOrNull()?.asJsonObject
        ?.get("text")?.asString
        ?.ifBlank { null }
}.getOrNull()
```

Create `app/src/main/java/me/jbusdriver/modern/data/translation/MicrosoftTranslator.kt`:

```kotlin
package me.jbusdriver.modern.data.translation

// Adapted from komi-store (Apache-2.0): MicrosoftTranslator.kt (Ktor -> OkHttp port)
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON

/** Azure 文本翻译;需订阅密钥,区域非 global 时附 Region 头。 */
class MicrosoftTranslator(
    private val key: String,
    private val region: String,
) : TranslatorEngine {
    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (key.isBlank()) throw TranslationConfigException("microsoft key not configured")
                val from = msSourceCode(sourceLang)?.let { "&from=$it" }.orEmpty()
                val url = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=${msLangCode(targetLang)}$from"
                val payload = GSON.toJson(listOf(mapOf("Text" to text)))
                val body = postJson(
                    url,
                    payload,
                    headers = buildMap {
                        put("Ocp-Apim-Subscription-Key", key)
                        if (region.isNotBlank() && !region.equals("global", ignoreCase = true)) {
                            put("Ocp-Apim-Subscription-Region", region.lowercase())
                        }
                    },
                )
                parseMicrosoftBody(body) ?: error("microsoft unparsable: ${body.take(80)}")
            }.onFailure { KLog.t(TAG).w("microsoft translate failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "MicrosoftTranslator"
    }
}

internal fun msLangCode(code: String): String = when (code.lowercase()) {
    "zh", "zh-cn", "zh-hans" -> "zh-Hans"
    "zh-tw", "zh-hant" -> "zh-Hant"
    "pt", "pt-br" -> "pt-br"
    "pt-pt" -> "pt-pt"
    "no" -> "nb"
    "sr-cyrl" -> "sr-Cyrl"
    "sr-latn" -> "sr-Latn"
    else -> code.lowercase()
}

internal fun msSourceCode(code: String): String? =
    if (code.lowercase() in setOf("", "auto")) null else msLangCode(code)

internal fun parseMicrosoftBody(body: String): String? = runCatching {
    val root = JsonParser.parseString(body)
    if (root.isJsonObject) return null // 错误响应是对象而非数组
    root.asJsonArray.firstOrNull()?.asJsonObject
        ?.getAsJsonArray("translations")
        ?.firstOrNull()?.asJsonObject
        ?.get("text")?.asString
        ?.ifBlank { null }
}.getOrNull()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.NewTranslatorsTest"`
Expected: PASS (17 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt \
        app/src/main/java/me/jbusdriver/modern/data/translation/YoudaoTranslator.kt \
        app/src/main/java/me/jbusdriver/modern/data/translation/LibreTranslator.kt \
        app/src/main/java/me/jbusdriver/modern/data/translation/DeeplTranslator.kt \
        app/src/main/java/me/jbusdriver/modern/data/translation/MicrosoftTranslator.kt \
        app/src/test/java/me/jbusdriver/modern/data/translation/NewTranslatorsTest.kt
git status --short   # only these 6 files
git commit -m "feat(translation): port youdao/libre/deepl/microsoft engines from komi-store"
```

---

### Task 3: Atomic switch — models, settings, repository, factory

This is the single breaking change: the 5-provider `TranslationProvider`, flat `TranslatorConfig`, single-param `translate(text)`, credential keys, target-language setting, new cache key, per-provider validation, and removal of the custom-template machinery — all in one compiling unit.

**Files:**
- Rewrite: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationModels.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslatorEngine.kt` (delete `CustomUrlTranslator` + `buildCustomRequestUrl` + `extractCustomResponse`)
- Rewrite: `app/src/main/java/me/jbusdriver/modern/data/translation/TranslationRepository.kt`
- Modify: `app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt` (translation region)
- Rewrite: `app/src/test/java/me/jbusdriver/modern/data/translation/TranslationRepositoryTest.kt`
- Modify: `app/src/test/java/me/jbusdriver/modern/data/translation/TranslatorEngineTest.kt` (remove custom-helper tests)
- Modify: `app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt` (stub signature)

**Interfaces:**
- Consumes: engines from Task 2, `LanguageCatalog` from Task 1.
- Produces: `enum class TranslationProvider(val id: String) { GOOGLE("google"), YOUDAO("youdao"), LIBRE("libre"), DEEPL("deepl"), MICROSOFT("microsoft") }` with `fromId(id: String?): TranslationProvider` (unknown → GOOGLE); `data class TranslatorConfig(provider, targetLang, youdaoAppKey, youdaoAppSecret, libreBaseUrl, libreApiKey, deeplAuthKey, microsoftKey, microsoftRegion)` (all Strings; credential fields default `""`); `const val TRANSLATION_SOURCE_LANG = "auto"`, `TRANSLATION_DEFAULT_TARGET_LANG = "zh-TW"`; `TranslationRepository { suspend fun translate(text: String): Result<String> }`; factory unchanged signature `fun interface TranslatorEngineFactory { fun create(config: TranslatorConfig): TranslatorEngine }`. `AppSettingsContract` translation members (Task 4 consumes): `translateByDefault`, `translationProvider`, `translationTargetLang` (raw, "" = follow system), `effectiveTranslationTarget: StateFlow<String>`, `setTranslateByDefault`, `setTranslationProvider`, `setTranslationTargetLang`, `setYoudaoCredentials(appKey, appSecret)`, `setLibreCredentials(baseUrl, apiKey)`, `setDeeplAuthKey(key)`, `setMicrosoftCredentials(key, region)`. `TranslationSettingsReader` keeps `translateByDefault` + `currentTranslatorConfig(): TranslatorConfig`.

- [ ] **Step 1: Rewrite `TranslationModels.kt`**

Replace the whole file with:

```kotlin
package me.jbusdriver.modern.data.translation

/** 翻译服务供应商;DataStore 以 [id] 持久化。Adapted from komi-store (Apache-2.0). */
enum class TranslationProvider(val id: String) {
    GOOGLE("google"),
    YOUDAO("youdao"),
    LIBRE("libre"),
    DEEPL("deepl"),
    MICROSOFT("microsoft");

    companion object {
        fun fromId(id: String?): TranslationProvider =
            entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/** 当前生效的翻译配置快照(供应商 + 凭据 + 已解析目标语言),由 AppSettingsStore 生成。 */
data class TranslatorConfig(
    val provider: TranslationProvider,
    val targetLang: String,
    val youdaoAppKey: String = "",
    val youdaoAppSecret: String = "",
    val libreBaseUrl: String = "",
    val libreApiKey: String = "",
    val deeplAuthKey: String = "",
    val microsoftKey: String = "",
    val microsoftRegion: String = "",
)

const val TRANSLATION_SOURCE_LANG = "auto"
const val TRANSLATION_DEFAULT_TARGET_LANG = "zh-TW"

/** LibreTranslate 实例 URL 未配置时的默认实例。 */
const val LIBRE_TRANSLATE_DEFAULT_URL = "https://translate.disroot.org"

/** 设置页"测试翻译"按钮使用的固定样例。 */
const val TRANSLATION_TEST_SAMPLE = "テスト"

/** 翻译服务未配置(凭据缺失)。 */
class TranslationConfigException(message: String) : Exception(message)

/** 翻译接口返回非 2xx;429 表示按 IP 限流。 */
class TranslationHttpException(val code: Int) : Exception("http $code")
```

- [ ] **Step 2: Delete custom-template machinery from `TranslatorEngine.kt`**

Remove: the whole `CustomUrlTranslator` class, `buildCustomRequestUrl`, and `extractCustomResponse` (keep `GoogleFreeTranslator`, `TranslationHttp`, `TRANSLATION_USER_AGENT`, `execute`, `urlEncode`, `parseGtxBody`, and the Task-2 `postForm`/`postJson`). Remove now-unused import `com.google.gson.JsonObject` and `com.google.gson.JsonArray` only if no longer referenced (keep `JsonParser` — used by `parseGtxBody`).

- [ ] **Step 3: Rewrite `TranslationRepository.kt`**

Replace the whole file with:

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
     * 翻译文本;主线程安全。源/目标语言与供应商凭据由设置解析(目标语言 = 设置值或跟随系统)。
     * 命中内存/磁盘缓存不发请求,同键并发请求只打一次接口;失败不写缓存。
     */
    suspend fun translate(text: String): Result<String>
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
        TranslationProvider.YOUDAO -> YoudaoTranslator(config.youdaoAppKey, config.youdaoAppSecret)
        TranslationProvider.LIBRE -> LibreTranslator(config.libreBaseUrl, config.libreApiKey)
        TranslationProvider.DEEPL -> DeeplTranslator(config.deeplAuthKey)
        TranslationProvider.MICROSOFT -> MicrosoftTranslator(config.microsoftKey, config.microsoftRegion)
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

    override suspend fun translate(text: String): Result<String> {
        val config = settings.currentTranslatorConfig()
        configValidationError(config)?.let { return Result.failure(it) }
        val key = cacheKey(config.provider.id, TRANSLATION_SOURCE_LANG, config.targetLang, text)
        cacheStore.readMemory(key)?.let { return Result.success(it) }
        cacheStore.readDisk(key)?.let { cached ->
            cacheStore.writeMemory(key, cached)
            return Result.success(cached)
        }
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: requestScope.async {
                engineFactory.create(config)
                    .translate(text, TRANSLATION_SOURCE_LANG, config.targetLang)
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

    /** 按供应商校验必填凭据;Google 无需配置,Libre 的实例 URL 由设置层回退默认值。 */
    private fun configValidationError(config: TranslatorConfig): TranslationConfigException? = when (config.provider) {
        TranslationProvider.GOOGLE -> null
        TranslationProvider.LIBRE -> null
        TranslationProvider.YOUDAO ->
            if (config.youdaoAppKey.isBlank() || config.youdaoAppSecret.isBlank()) {
                TranslationConfigException("youdao appKey/appSecret not configured")
            } else null
        TranslationProvider.DEEPL ->
            if (config.deeplAuthKey.isBlank()) TranslationConfigException("deepl auth key not configured") else null
        TranslationProvider.MICROSOFT ->
            if (config.microsoftKey.isBlank()) TranslationConfigException("microsoft key not configured") else null
    }

    private fun cacheKey(providerId: String, sourceLang: String, targetLang: String, text: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "trans:$providerId:$sourceLang:$targetLang:$hash"
    }

    private companion object {
        const val TAG = "TranslationRepository"
    }
}
```

- [ ] **Step 4: Rewrite the translation region of `AppSettingsStore.kt`**

Replace the whole `// region Translation` block, the four contract members, and the four keys with (contract additions: `translationTargetLang`, `effectiveTranslationTarget`, `setTranslationTargetLang`, `setYoudaoCredentials`, `setLibreCredentials`, `setDeeplAuthKey`, `setMicrosoftCredentials`; removals: `customTranslateUrlTemplate`, `customTranslateResponsePath`, `setCustomTranslateUrlTemplate`, `setCustomTranslateResponsePath`):

Contract block (inside `AppSettingsContract`):

```kotlin
    // Translation
    val translationProvider: StateFlow<TranslationProvider>
    val translationTargetLang: StateFlow<String>       // 原始设置值;"" = 跟随系统
    val effectiveTranslationTarget: StateFlow<String>   // 已解析(跟随系统时按设备 locale)
    val youdaoAppKey: StateFlow<String>
    val youdaoAppSecret: StateFlow<String>
    val libreBaseUrl: StateFlow<String>
    val libreApiKey: StateFlow<String>
    val deeplAuthKey: StateFlow<String>
    val microsoftKey: StateFlow<String>
    val microsoftRegion: StateFlow<String>

    suspend fun setTranslateByDefault(enabled: Boolean)
    suspend fun setTranslationProvider(provider: TranslationProvider)
    suspend fun setTranslationTargetLang(code: String)
    suspend fun setYoudaoCredentials(appKey: String, appSecret: String)
    suspend fun setLibreCredentials(baseUrl: String, apiKey: String)
    suspend fun setDeeplAuthKey(key: String)
    suspend fun setMicrosoftCredentials(key: String, region: String)
```

Implementation region (credential flows are one-liners after `translationTargetLang`):

```kotlin
    // region Translation
    override val translateByDefault: StateFlow<Boolean> = flowOf(KEY_TRANSLATE_BY_DEFAULT, false)
    override val translationProvider: StateFlow<TranslationProvider> =
        mapped(KEY_TRANSLATE_PROVIDER, TranslationProvider.GOOGLE) { TranslationProvider.fromId(it) }
    override val translationTargetLang: StateFlow<String> = flowOf(KEY_TRANSLATION_TARGET_LANG, "")
    override val youdaoAppKey: StateFlow<String> = flowOf(KEY_YOUDAO_APP_KEY, "")
    override val youdaoAppSecret: StateFlow<String> = flowOf(KEY_YOUDAO_APP_SECRET, "")
    override val libreBaseUrl: StateFlow<String> = flowOf(KEY_LIBRE_BASE_URL, "")
    override val libreApiKey: StateFlow<String> = flowOf(KEY_LIBRE_API_KEY, "")
    override val deeplAuthKey: StateFlow<String> = flowOf(KEY_DEEPL_AUTH_KEY, "")
    override val microsoftKey: StateFlow<String> = flowOf(KEY_MICROSOFT_KEY, "")
    override val microsoftRegion: StateFlow<String> = flowOf(KEY_MICROSOFT_REGION, "")
    override val effectiveTranslationTarget: StateFlow<String> = dataStore.data
        .map { prefs -> resolveTargetLang(prefs[KEY_TRANSLATION_TARGET_LANG]) }
        .stateIn(scope, SharingStarted.Eagerly, TRANSLATION_DEFAULT_TARGET_LANG)
    override suspend fun currentTranslatorConfig(): TranslatorConfig {
        val prefs = dataStore.data.first()
        return TranslatorConfig(
            provider = TranslationProvider.fromId(prefs[KEY_TRANSLATE_PROVIDER]),
            targetLang = resolveTargetLang(prefs[KEY_TRANSLATION_TARGET_LANG]),
            youdaoAppKey = prefs[KEY_YOUDAO_APP_KEY].orEmpty(),
            youdaoAppSecret = prefs[KEY_YOUDAO_APP_SECRET].orEmpty(),
            libreBaseUrl = prefs[KEY_LIBRE_BASE_URL].orEmpty().ifBlank { LIBRE_TRANSLATE_DEFAULT_URL },
            libreApiKey = prefs[KEY_LIBRE_API_KEY].orEmpty(),
            deeplAuthKey = prefs[KEY_DEEPL_AUTH_KEY].orEmpty(),
            microsoftKey = prefs[KEY_MICROSOFT_KEY].orEmpty(),
            microsoftRegion = prefs[KEY_MICROSOFT_REGION].orEmpty(),
        )
    }

    /** "" → 跟随系统(设备 locale 推断);非法值回退默认繁体。 */
    private fun resolveTargetLang(setting: String?): String {
        if (setting.isNullOrBlank()) {
            val locale = java.util.Locale.getDefault()
            return LanguageCatalog.resolveDeviceLanguage(locale.language, locale.country)
        }
        return LanguageCatalog.findByCode(setting)?.code ?: TRANSLATION_DEFAULT_TARGET_LANG
    }

    override suspend fun setTranslateByDefault(enabled: Boolean) = put(KEY_TRANSLATE_BY_DEFAULT, enabled)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = put(KEY_TRANSLATE_PROVIDER, provider.id)
    override suspend fun setTranslationTargetLang(code: String) = put(KEY_TRANSLATION_TARGET_LANG, code)
    override suspend fun setYoudaoCredentials(appKey: String, appSecret: String) {
        dataStore.edit {
            it[KEY_YOUDAO_APP_KEY] = appKey.trim()
            it[KEY_YOUDAO_APP_SECRET] = appSecret.trim()
        }
    }
    override suspend fun setLibreCredentials(baseUrl: String, apiKey: String) {
        dataStore.edit {
            it[KEY_LIBRE_BASE_URL] = baseUrl.trim()
            it[KEY_LIBRE_API_KEY] = apiKey.trim()
        }
    }
    override suspend fun setDeeplAuthKey(key: String) = put(KEY_DEEPL_AUTH_KEY, key.trim())
    override suspend fun setMicrosoftCredentials(key: String, region: String) {
        dataStore.edit {
            it[KEY_MICROSOFT_KEY] = key.trim()
            it[KEY_MICROSOFT_REGION] = region.trim()
        }
    }
    // endregion
```

Companion keys (replace the old four):

```kotlin
        private val KEY_TRANSLATE_BY_DEFAULT = booleanPreferencesKey("translate_by_default")
        private val KEY_TRANSLATE_PROVIDER = stringPreferencesKey("translate_provider")
        private val KEY_TRANSLATION_TARGET_LANG = stringPreferencesKey("translation_target_lang")
        private val KEY_YOUDAO_APP_KEY = stringPreferencesKey("youdao_app_key")
        private val KEY_YOUDAO_APP_SECRET = stringPreferencesKey("youdao_app_secret")
        private val KEY_LIBRE_BASE_URL = stringPreferencesKey("libre_base_url")
        private val KEY_LIBRE_API_KEY = stringPreferencesKey("libre_api_key")
        private val KEY_DEEPL_AUTH_KEY = stringPreferencesKey("deepl_auth_key")
        private val KEY_MICROSOFT_KEY = stringPreferencesKey("microsoft_key")
        private val KEY_MICROSOFT_REGION = stringPreferencesKey("microsoft_region")
```

Add imports: `me.jbusdriver.modern.data.translation.LIBRE_TRANSLATE_DEFAULT_URL`, `me.jbusdriver.modern.data.translation.LanguageCatalog`, `me.jbusdriver.modern.data.translation.TRANSLATION_DEFAULT_TARGET_LANG` (`TranslationProvider`/`TranslatorConfig` already imported).

- [ ] **Step 5: Rewrite `TranslationRepositoryTest.kt`**

Replace the whole file:

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

    private class FakeEngine(var result: Result<String> = Result.success("譯文")) : TranslatorEngine {
        var calls = 0
        var lastTarget: String? = null
        override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> {
            calls++
            lastTarget = targetLang
            return result
        }
    }

    private class FakeSettings(var config: TranslatorConfig) : TranslationSettingsReader {
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
        engine: FakeEngine = FakeEngine(),
        settings: FakeSettings = FakeSettings(TranslatorConfig(TranslationProvider.GOOGLE, "zh-TW")),
    ) = DefaultTranslationRepository(cache, settings, TranslatorEngineFactory { _ -> engine }) to settings

    @Test
    fun translate_successWritesMemoryAndDiskCache() = runTest {
        val cache = FakeCacheStore()
        val engine = FakeEngine()
        val (repository, _) = repo(cache, engine)
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
        assertEquals(1, cache.memory.size)
        assertEquals(1, cache.disk.size)
    }

    @Test
    fun translate_memoryCacheHitSkipsEngine() = runTest {
        val engine = FakeEngine()
        val (repository, _) = repo(engine = engine)
        repository.translate("テスト")
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_diskCacheHitBackfillsMemory() = runTest {
        val cache = FakeCacheStore()
        cache.disk[key("google", "auto", "zh-TW", "テスト")] = "磁盤譯文"
        val engine = FakeEngine()
        val (repository, _) = repo(cache, engine)
        assertEquals("磁盤譯文", repository.translate("テスト").getOrThrow())
        assertEquals(0, engine.calls)
        assertEquals("磁盤譯文", cache.memory.values.single())
    }

    @Test
    fun translate_failureIsNotCached() = runTest {
        val engine = FakeEngine(Result.failure(RuntimeException("boom")))
        val (repository, _) = repo(engine = engine)
        assertTrue(repository.translate("テスト").isFailure)
        assertTrue(repository.translate("テスト").isFailure)
        assertEquals(2, engine.calls)
    }

    @Test
    fun translate_unconfiguredYoudaoFailsFastWithoutEngineCall() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.YOUDAO, "zh-TW")
        val result = repository.translate("テスト")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_unconfiguredDeeplAndMicrosoftFailFast() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.DEEPL, "zh-TW")
        assertTrue(repository.translate("テスト").exceptionOrNull() is TranslationConfigException)
        settings.config = TranslatorConfig(TranslationProvider.MICROSOFT, "zh-TW")
        assertTrue(repository.translate("テスト").exceptionOrNull() is TranslationConfigException)
        assertEquals(0, engine.calls)
    }

    @Test
    fun translate_libreWithoutUrlUsesConfiguredDefaultAndCallsEngine() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.LIBRE, "zh-TW") // baseUrl 留空 → 设置层回退默认
        assertEquals("譯文", repository.translate("テスト").getOrThrow())
        assertEquals(1, engine.calls)
    }

    @Test
    fun translate_differentTargetLangsUseSeparateCacheEntries() = runTest {
        val engine = FakeEngine()
        val (repository, settings) = repo(engine = engine)
        settings.config = TranslatorConfig(TranslationProvider.GOOGLE, "zh-TW")
        repository.translate("テスト")
        settings.config = TranslatorConfig(TranslationProvider.GOOGLE, "en")
        repository.translate("テスト")
        assertEquals(2, engine.calls)
        assertEquals("en", engine.lastTarget)
    }

    @Test
    fun translate_concurrentSameKeyCallsEngineOnce() = runTest {
        val engine = FakeEngine()
        val (repository, _) = repo(engine = engine)
        val results = (1..3).map { async { repository.translate("テスト") } }.awaitAll()
        assertTrue(results.all { it.isSuccess })
        assertEquals(1, engine.calls)
    }

    private fun key(providerId: String, source: String, target: String, text: String): String =
        "trans:$providerId:$source:$target:${sha256(text)}"

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 6: Prune custom tests from `TranslatorEngineTest.kt`**

Delete the test methods `buildCustomRequestUrl_encodesTextAndReplacesPlaceholders`, `buildCustomRequestUrl_missingTextPlaceholderReturnsNull`, `buildCustomRequestUrl_blankTemplateReturnsNull`, `extractCustomResponse_blankPathReturnsTrimmedBody`, `extractCustomResponse_blankPathBlankBodyReturnsNull`, `extractCustomResponse_objectPath`, `extractCustomResponse_nestedObjectPath`, `extractCustomResponse_arrayIndexPath`, `extractCustomResponse_missingPathReturnsNull`, `extractCustomResponse_blankLeafReturnsNull` (keep the three `parseGtxBody` tests). Update the file's doc comment separators accordingly.

- [ ] **Step 7: Update stub signature in `MovieDetailViewModelTest.kt`**

Replace every occurrence of `translate(text: String, sourceLang: String, targetLang: String)` with `translate(text: String)` (replace-all; ~7 stubs: `stubTranslationRepo`, `failingRepo`, `configErrorRepo`, `rateLimitedRepo`, `httpErrorRepo`, `gatedRepo`). No call-site changes needed — `translate(title)` was already single-arg.

- [ ] **Step 8: Run all affected tests**

```bash
./gradlew testDebugUnitTest --tests "me.jbusdriver.modern.data.translation.*" --tests "me.jbusdriver.modern.ui.detail.MovieDetailViewModelTest" --tests "me.jbusdriver.modern.ui.settings.*"
```

Expected: ALL PASS (3 gtx + 7 catalog + 17 new-engine + 9 repository + 17 ViewModel; settings tests unaffected).
Note: `SettingsViewModel` will NOT compile until Task 4 if it still references removed setters — if compilation fails in `ui.settings`, verify the error points only at `setCustomTranslateUrlTemplate`/`testTranslation`/`TRANSLATION_TARGET_LANG` references; if so those must be fixed now minimally: delete the two custom-template methods and fix `testTranslation` per Task 4 Step 2's final form (apply that step's `testTranslation` + running-flag code now, leaving the form-save methods for Task 4). Re-run until green.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/translation/ \
        app/src/main/java/me/jbusdriver/modern/data/settings/AppSettingsStore.kt \
        app/src/test/java/me/jbusdriver/modern/data/translation/ \
        app/src/test/java/me/jbusdriver/modern/ui/detail/MovieDetailViewModelTest.kt \
        app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt
git status --short   # only intended files
git commit -m "feat(translation): switch to five-provider config, credential settings, target-language-aware cache"
```

---

### Task 4: Settings UI — provider forms + target language

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt` (translation region final form)
- Rewrite: `app/src/main/java/me/jbusdriver/modern/ui/settings/TranslationSettingsSection.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: `AppSettingsContract` translation members (Task 3), `TranslatorEngineFactory`, `TranslatorConfig`, `LanguageCatalog`, `TRANSLATION_TEST_SAMPLE`, `TRANSLATION_SOURCE_LANG`, `effectiveTranslationTarget`.
- Produces: `SettingsViewModel` methods `setTranslateByDefault(Boolean)`, `setTranslationProvider(TranslationProvider)`, `setTranslationTargetLang(String)`, `setYoudaoCredentials(appKey, appSecret)`, `setLibreCredentials(baseUrl, apiKey)`, `setDeeplAuthKey(String)`, `setMicrosoftCredentials(key, region)`, `testTranslation(config: TranslatorConfig)`, `translationTestRunning: StateFlow<Boolean>`; `TranslationSettingsCard(...)` same slot signature as current (called from `SettingsScreen.kt` — only the argument list changes).

- [ ] **Step 1: Add strings, remove dead ones**

`values/strings.xml` — inside `<!-- Translation -->` REMOVE `settings_translation_url_template`, `settings_translation_url_template_hint`, `settings_translation_response_path`, `settings_translation_response_path_hint`, `translate_template_invalid`; ADD:

```xml
    <string name="settings_translation_provider_youdao">有道</string>
    <string name="settings_translation_provider_libre">LibreTranslate</string>
    <string name="settings_translation_provider_deepl">DeepL</string>
    <string name="settings_translation_provider_microsoft">微軟翻譯</string>
    <string name="settings_translation_save">儲存</string>
    <string name="settings_translation_youdao_app_key">App Key</string>
    <string name="settings_translation_youdao_app_secret">App Secret</string>
    <string name="settings_translation_libre_base_url">實例 URL</string>
    <string name="settings_translation_libre_api_key">API 金鑰(選填)</string>
    <string name="settings_translation_deepl_auth_key">Auth Key</string>
    <string name="settings_translation_deepl_get_key">獲取免費密鑰</string>
    <string name="settings_translation_microsoft_key">訂閱密鑰</string>
    <string name="settings_translation_microsoft_region">區域</string>
    <string name="settings_translation_microsoft_get_key">獲取密鑰</string>
    <string name="settings_translation_target">目標語言</string>
    <string name="settings_translation_target_follow_system">跟隨系統</string>
```

`values-en/strings.xml` — mirror REMOVALS, ADD:

```xml
    <string name="settings_translation_provider_youdao">Youdao</string>
    <string name="settings_translation_provider_libre">LibreTranslate</string>
    <string name="settings_translation_provider_deepl">DeepL</string>
    <string name="settings_translation_provider_microsoft">Microsoft Translator</string>
    <string name="settings_translation_save">Save</string>
    <string name="settings_translation_youdao_app_key">App Key</string>
    <string name="settings_translation_youdao_app_secret">App Secret</string>
    <string name="settings_translation_libre_base_url">Instance URL</string>
    <string name="settings_translation_libre_api_key">API key (optional)</string>
    <string name="settings_translation_deepl_auth_key">Auth Key</string>
    <string name="settings_translation_deepl_get_key">Get a free key</string>
    <string name="settings_translation_microsoft_key">Subscription key</string>
    <string name="settings_translation_microsoft_region">Region</string>
    <string name="settings_translation_microsoft_get_key">Get a key</string>
    <string name="settings_translation_target">Target language</string>
    <string name="settings_translation_target_follow_system">Follow system</string>
```

- [ ] **Step 2: Finalize `SettingsViewModel` translation region**

Replace the whole translation region with (if Task 3 Step 8 already applied part of this, apply the remainder):

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

    fun setTranslationTargetLang(code: String) {
        viewModelScope.launch { store.setTranslationTargetLang(code) }
    }

    fun setYoudaoCredentials(appKey: String, appSecret: String) {
        viewModelScope.launch { store.setYoudaoCredentials(appKey, appSecret) }
    }

    fun setLibreCredentials(baseUrl: String, apiKey: String) {
        viewModelScope.launch { store.setLibreCredentials(baseUrl, apiKey) }
    }

    fun setDeeplAuthKey(key: String) {
        viewModelScope.launch { store.setDeeplAuthKey(key) }
    }

    fun setMicrosoftCredentials(key: String, region: String) {
        viewModelScope.launch { store.setMicrosoftCredentials(key, region) }
    }

    /** 用表单当前值(未保存)试译固定样例;目标语言取当前生效值。结果经 messages Toast。 */
    fun testTranslation(config: TranslatorConfig) {
        if (_translationTestRunning.value) return
        val invalid = when (config.provider) {
            TranslationProvider.YOUDAO -> config.youdaoAppKey.isBlank() || config.youdaoAppSecret.isBlank()
            TranslationProvider.DEEPL -> config.deeplAuthKey.isBlank()
            TranslationProvider.MICROSOFT -> config.microsoftKey.isBlank()
            TranslationProvider.GOOGLE, TranslationProvider.LIBRE -> false
        }
        if (invalid) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.translate_config_error)) }
            return
        }
        _translationTestRunning.value = true
        viewModelScope.launch {
            try {
                translatorEngineFactory.create(config)
                    .translate(TRANSLATION_TEST_SAMPLE, TRANSLATION_SOURCE_LANG, config.targetLang)
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

Imports: `TRANSLATION_SOURCE_LANG`, `TRANSLATION_TEST_SAMPLE`, `TranslationProvider`, `TranslatorConfig` (others may already exist).

- [ ] **Step 3: Rewrite `TranslationSettingsSection.kt`**

Replace the whole file with:

```kotlin
package me.jbusdriver.modern.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.data.translation.LanguageCatalog
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.ui.components.SelectableDropdownItem

/**
 * 设置页"翻譯"分组:供应商选择 + 各家凭据表单(儲存/測試翻譯)、
 * 自动翻译开关、目标语言(跟隨系統 + 目录)。Adapted from komi-store (Apache-2.0).
 */
@Composable
internal fun TranslationSettingsCard(
    translateByDefault: Boolean,
    provider: TranslationProvider,
    targetLang: String,
    effectiveTargetLang: String,
    youdaoAppKey: String,
    youdaoAppSecret: String,
    libreBaseUrl: String,
    libreApiKey: String,
    deeplAuthKey: String,
    microsoftKey: String,
    microsoftRegion: String,
    testRunning: Boolean,
    onTranslateByDefaultChange: (Boolean) -> Unit,
    onProviderChange: (TranslationProvider) -> Unit,
    onTargetLangChange: (String) -> Unit,
    onSaveYoudao: (appKey: String, appSecret: String) -> Unit,
    onSaveLibre: (baseUrl: String, apiKey: String) -> Unit,
    onSaveDeepl: (key: String) -> Unit,
    onSaveMicrosoft: (key: String, region: String) -> Unit,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
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
            ProviderRow(provider = provider, onProviderChange = onProviderChange)
            when (provider) {
                TranslationProvider.GOOGLE -> GoogleForm(testRunning, effectiveTargetLang, onTest, provider)
                TranslationProvider.YOUDAO -> YoudaoForm(youdaoAppKey, youdaoAppSecret, testRunning, effectiveTargetLang, onSaveYoudao, onTest, provider)
                TranslationProvider.LIBRE -> LibreForm(libreBaseUrl, libreApiKey, testRunning, effectiveTargetLang, onSaveLibre, onTest, provider)
                TranslationProvider.DEEPL -> DeeplForm(deeplAuthKey, testRunning, effectiveTargetLang, onSaveDeepl, onTest, provider)
                TranslationProvider.MICROSOFT -> MicrosoftForm(microsoftKey, microsoftRegion, testRunning, effectiveTargetLang, onSaveMicrosoft, onTest, provider)
            }
            TargetLangRow(targetLang = targetLang, effectiveTargetLang = effectiveTargetLang, onTargetLangChange = onTargetLangChange)
        }
    }
}

@Composable
private fun ProviderRow(provider: TranslationProvider, onProviderChange: (TranslationProvider) -> Unit) {
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
            Text(providerLabel(provider), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TranslationProvider.entries.forEach { candidate ->
                    SelectableDropdownItem(
                        label = providerLabel(candidate),
                        selected = candidate == provider,
                        onClick = { onProviderChange(candidate); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun providerLabel(provider: TranslationProvider): String = when (provider) {
    TranslationProvider.GOOGLE -> stringResource(R.string.settings_translation_provider_builtin)
    TranslationProvider.YOUDAO -> stringResource(R.string.settings_translation_provider_youdao)
    TranslationProvider.LIBRE -> stringResource(R.string.settings_translation_provider_libre)
    TranslationProvider.DEEPL -> stringResource(R.string.settings_translation_provider_deepl)
    TranslationProvider.MICROSOFT -> stringResource(R.string.settings_translation_provider_microsoft)
}

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun TestButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled) {
        Text(stringResource(R.string.settings_translation_test))
    }
}

@Composable
private fun GoogleForm(
    testRunning: Boolean,
    effectiveTarget: String,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    FormColumn {
        Text(
            stringResource(R.string.settings_translation_builtin_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TestButton(enabled = !testRunning) {
            onTest(me.jbusdriver.modern.data.translation.TranslatorConfig(provider, effectiveTarget))
        }
    }
}

@Composable
private fun YoudaoForm(
    savedKey: String, savedSecret: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var secret by remember(savedSecret) { mutableStateOf(savedSecret) }
    FormColumn {
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_youdao_app_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text(stringResource(R.string.settings_translation_youdao_app_secret)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(me.jbusdriver.modern.data.translation.TranslatorConfig(provider, effectiveTarget, youdaoAppKey = key.trim(), youdaoAppSecret = secret.trim()))
        }
        Button(
            onClick = { onSave(key.trim(), secret.trim()) },
            enabled = key.isNotBlank() && secret.isNotBlank(),
        ) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun LibreForm(
    savedUrl: String, savedApiKey: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var apiKey by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    FormColumn {
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.settings_translation_libre_base_url)) }, placeholder = { Text("https://translate.disroot.org") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(stringResource(R.string.settings_translation_libre_api_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(me.jbusdriver.modern.data.translation.TranslatorConfig(provider, effectiveTarget, libreBaseUrl = url.trim().ifBlank { me.jbusdriver.modern.data.translation.LIBRE_TRANSLATE_DEFAULT_URL }, libreApiKey = apiKey.trim()))
        }
        Button(onClick = { onSave(url.trim(), apiKey.trim()) }) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun DeeplForm(
    savedKey: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String) -> Unit,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    val context = LocalContext.current
    FormColumn {
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.deepl.com/pro-api"))) } }) {
            Text(stringResource(R.string.settings_translation_deepl_get_key))
        }
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_deepl_auth_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        TestButton(enabled = !testRunning) {
            onTest(me.jbusdriver.modern.data.translation.TranslatorConfig(provider, effectiveTarget, deeplAuthKey = key.trim()))
        }
        Button(onClick = { onSave(key.trim()) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun MicrosoftForm(
    savedKey: String, savedRegion: String, testRunning: Boolean, effectiveTarget: String,
    onSave: (String, String) -> Unit,
    onTest: (me.jbusdriver.modern.data.translation.TranslatorConfig) -> Unit,
    provider: TranslationProvider,
) {
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var region by remember(savedRegion) { mutableStateOf(savedRegion) }
    val context = LocalContext.current
    FormColumn {
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://portal.azure.com/#create/Microsoft.CognitiveServicesTextTranslation"))) } }) {
            Text(stringResource(R.string.settings_translation_microsoft_get_key))
        }
        OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.settings_translation_microsoft_key)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text(stringResource(R.string.settings_translation_microsoft_region)) }, placeholder = { Text("global") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TestButton(enabled = !testRunning) {
            onTest(me.jbusdriver.modern.data.translation.TranslatorConfig(provider, effectiveTarget, microsoftKey = key.trim(), microsoftRegion = region.trim()))
        }
        Button(onClick = { onSave(key.trim(), region.trim()) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.settings_translation_save)) }
    }
}

@Composable
private fun TargetLangRow(targetLang: String, effectiveTargetLang: String, onTargetLangChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_translation_target), style = MaterialTheme.typography.bodyLarge)
        Box {
            val label = if (targetLang.isBlank()) {
                stringResource(R.string.settings_translation_target_follow_system)
            } else {
                LanguageCatalog.findByCode(targetLang)?.displayName ?: targetLang
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SelectableDropdownItem(
                    label = stringResource(R.string.settings_translation_target_follow_system),
                    selected = targetLang.isBlank(),
                    onClick = { onTargetLangChange(""); expanded = false }
                )
                LanguageCatalog.all.forEach { lang ->
                    SelectableDropdownItem(
                        label = lang.displayName,
                        selected = lang.code == targetLang,
                        onClick = { onTargetLangChange(lang.code); expanded = false }
                    )
                }
            }
        }
    }
}
```

(Use a normal import for `TranslatorConfig`/`LIBRE_TRANSLATE_DEFAULT_URL` instead of fully-qualified references if your editor reformats — behavior identical.)

- [ ] **Step 4: Update the call site in `SettingsScreen.kt`**

Replace the `// Translation state` collection block and the `TranslationSettingsCard(...)` call with:

```kotlin
    // Translation state
    val translateByDefault by store.translateByDefault.collectAsStateWithLifecycle()
    val translationProvider by store.translationProvider.collectAsStateWithLifecycle()
    val translationTargetLang by store.translationTargetLang.collectAsStateWithLifecycle()
    val effectiveTranslationTarget by store.effectiveTranslationTarget.collectAsStateWithLifecycle()
    val youdaoAppKey by store.youdaoAppKey.collectAsStateWithLifecycle()
    val youdaoAppSecret by store.youdaoAppSecret.collectAsStateWithLifecycle()
    val libreBaseUrl by store.libreBaseUrl.collectAsStateWithLifecycle()
    val libreApiKey by store.libreApiKey.collectAsStateWithLifecycle()
    val deeplAuthKey by store.deeplAuthKey.collectAsStateWithLifecycle()
    val microsoftKey by store.microsoftKey.collectAsStateWithLifecycle()
    val microsoftRegion by store.microsoftRegion.collectAsStateWithLifecycle()
    val translationTestRunning by viewModel.translationTestRunning.collectAsStateWithLifecycle()
```

```kotlin
            // === Translation Card ===
            TranslationSettingsCard(
                translateByDefault = translateByDefault,
                provider = translationProvider,
                targetLang = translationTargetLang,
                effectiveTargetLang = effectiveTranslationTarget,
                youdaoAppKey = youdaoAppKey,
                youdaoAppSecret = youdaoAppSecret,
                libreBaseUrl = libreBaseUrl,
                libreApiKey = libreApiKey,
                deeplAuthKey = deeplAuthKey,
                microsoftKey = microsoftKey,
                microsoftRegion = microsoftRegion,
                testRunning = translationTestRunning,
                onTranslateByDefaultChange = { viewModel.setTranslateByDefault(it) },
                onProviderChange = { viewModel.setTranslationProvider(it) },
                onTargetLangChange = { viewModel.setTranslationTargetLang(it) },
                onSaveYoudao = { k, s -> viewModel.setYoudaoCredentials(k, s) },
                onSaveLibre = { u, k -> viewModel.setLibreCredentials(u, k) },
                onSaveDeepl = { viewModel.setDeeplAuthKey(it) },
                onSaveMicrosoft = { k, r -> viewModel.setMicrosoftCredentials(k, r) },
                onTest = { viewModel.testTranslation(it) }
            )
```

Note: the credential flows (`youdaoAppKey` … `microsoftRegion`) were added to `AppSettingsContract` in Task 3 Step 4.

- [ ] **Step 5: Verify build + lint**

```bash
./gradlew assembleDebug lintDebug
```

Expected: BUILD SUCCESSFUL, 0 error-level lint findings (one pre-existing `LocalContextResourcesRead` warning in untouched `LocalVideoCard` remains).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/ \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml
git status --short   # only these
git commit -m "feat(translation): provider credential forms and target language picker in settings"
```

---

### Task 5: Full quality gates + smoke checklist (controller-run)

**Files:** none (verification only).

- [ ] **Step 1: Gates**

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Expected: all BUILD SUCCESSFUL. (No new Gson model classes — `JsonElement` parsing only — so no new R8 surface.)

- [ ] **Step 2: Manual smoke (device; user-run)**

1. Settings → 翻譯: default = 內建免費接口 + 目標語言跟隨系統;表單只有限流提示 + 測試翻譯。
2. 切換到 有道 → 出现 App Key/Secret 表单,儲存在空值时禁用;填入真实 key 后測試翻譯 → Toast 成功译文;儲存后杀进程重进,字段仍在。
3. 切換 DeepL/Microsoft → 各自表单 + 外链按钮可打开网页;空 key 时儲存禁用、測試翻譯 Toast 未配置。
4. 目標語言 → 切到 English;详情页翻译 → 译文为英文;切回跟隨系統(简中系统)→ 中文简体译文。
5. 未配置的有道被选中时,详情页点翻譯 → Toast 翻譯接口未配置或無效。
6. 同一标题换目标语言后切回原语言 → 立即命中缓存(无网络请求,飞行模式可验证)。
7. Google 限流仍会得到专属提示(已有行为回归)。

- [ ] **Step 3: Update the SDD ledger + spec status if needed; report.**
