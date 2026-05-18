package me.jbusdriver.modern.core

import me.jbusdriver.modern.JBus

object CoverStats {

    private const val PREFS_NAME = "cover_stats"
    private const val KEY_DATA = "aspect_ratios"

    private val prefs by lazy { JBus.getSharedPreferences(PREFS_NAME, 0) }

    fun record(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val ratio = String.format("%.2f", width.toFloat() / height.toFloat())
        val map = loadMap()
        map[ratio] = (map[ratio] ?: 0) + 1
        prefs.edit().putString(KEY_DATA, GSON.toJson(map)).apply()
    }

    fun getStats(): List<AspectEntry> {
        val map = loadMap()
        val total = map.values.sum().toFloat()
        return map.entries
            .sortedByDescending { it.value }
            .map { (ratio, count) ->
                AspectEntry(
                    ratio = ratio,
                    count = count,
                    percent = if (total > 0) String.format("%.1f%%", count / total * 100) else "0%"
                )
            }
    }

    fun totalSamples(): Int = loadMap().values.sum()

    fun clear() {
        prefs.edit().remove(KEY_DATA).apply()
    }

    private fun loadMap(): MutableMap<String, Int> {
        val json = prefs.getString(KEY_DATA, null) ?: return mutableMapOf()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
        return GSON.fromJson<MutableMap<String, Int>>(json, type) ?: mutableMapOf()
    }
}

data class AspectEntry(
    val ratio: String,
    val count: Int,
    val percent: String
)
