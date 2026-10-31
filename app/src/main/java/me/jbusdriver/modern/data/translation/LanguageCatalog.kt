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
        val regionCode = "$lang-${region.uppercase()}"
        return when {
            lang == "zh" ->
                if (region.uppercase() in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            else -> all.firstOrNull { it.code.equals(regionCode, ignoreCase = true) }?.code
                ?: all.firstOrNull { it.code.startsWith(lang) }?.code
                ?: "zh-TW"
        }
    }
}
