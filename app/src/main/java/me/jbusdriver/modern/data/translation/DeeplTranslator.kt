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
