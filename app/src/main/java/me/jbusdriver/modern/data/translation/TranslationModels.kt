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

/** 翻译接口返回非 2xx;429 表示按 IP 限流(免费接口共享出口常见)。 */
class TranslationHttpException(val code: Int) : Exception("http $code")
