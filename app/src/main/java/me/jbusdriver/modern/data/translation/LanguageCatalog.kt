package me.jbusdriver.modern.data.translation

/** 可选翻译目标语言;显示名由设置页字符串资源提供。 */
data class SupportedLanguage(val code: String)

/** 目标语言目录(3 项);未设置或非法值由调用方回退默认繁体。 */
object LanguageCatalog {
    val all: List<SupportedLanguage> = listOf(
        SupportedLanguage("zh-CN"),
        SupportedLanguage("zh-TW"),
        SupportedLanguage("en"),
    )

    fun findByCode(code: String?): SupportedLanguage? =
        all.firstOrNull { it.code.equals(code, ignoreCase = true) }
}
