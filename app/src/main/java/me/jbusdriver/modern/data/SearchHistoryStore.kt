package me.jbusdriver.modern.data

import android.content.SharedPreferences
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class SearchHistoryStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences("search_history", 0)

    fun getHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return GSON.fromJson<List<String>>(json) ?: emptyList()
    }

    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        prefs.edit { putString(KEY_HISTORY, GSON.toJson(current)) }
    }

    fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        if (current.remove(query)) {
            prefs.edit { putString(KEY_HISTORY, GSON.toJson(current)) }
        }
    }

    fun clearHistory() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        private const val KEY_HISTORY = "search_history_queries"
        private const val MAX_HISTORY = 20
    }
}
