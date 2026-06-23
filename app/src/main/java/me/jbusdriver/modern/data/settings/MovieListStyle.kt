package me.jbusdriver.modern.data.settings

/** Display layout for movie lists. */
enum class MovieListStyle(val preferenceValue: String) {
    GRID("grid"),
    LIST("list");

    val isGrid: Boolean get() = this == GRID

    companion object {
        fun fromPreferenceValue(value: String?): MovieListStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: LIST
    }
}
