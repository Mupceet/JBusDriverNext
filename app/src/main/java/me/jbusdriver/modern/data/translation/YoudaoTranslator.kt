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
