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
