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
