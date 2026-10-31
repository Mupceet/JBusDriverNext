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
