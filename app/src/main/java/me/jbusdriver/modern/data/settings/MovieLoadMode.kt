package me.jbusdriver.modern.data.settings

/** Default loading mode for movie list pages. */
enum class MovieLoadMode(val preferenceValue: String) {
    WITH_MAGNET("with_magnet"),
    ALL("all");

    val showAll: Boolean get() = this == ALL

    companion object {
        fun fromPreferenceValue(value: String?): MovieLoadMode =
            entries.firstOrNull { it.preferenceValue == value } ?: WITH_MAGNET
    }
}
