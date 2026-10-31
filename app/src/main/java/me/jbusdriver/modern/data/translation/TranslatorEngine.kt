package me.jbusdriver.modern.data.translation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.KLog
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/** 翻译请求以桌面浏览器 UA 发出:部分接口对 okhttp 默认 UA 按机器人分类,更易被限流/拒绝。 */
private const val TRANSLATION_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private fun execute(url: String): String {
    TranslationHttp.client.newCall(
        Request.Builder().url(url).header("User-Agent", TRANSLATION_USER_AGENT).build()
    ).execute().use { response ->
        if (!response.isSuccessful) throw TranslationHttpException(response.code)
        return response.body?.string() ?: error("empty body")
    }
}

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
