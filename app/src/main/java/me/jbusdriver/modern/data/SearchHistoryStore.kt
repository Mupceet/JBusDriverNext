package me.jbusdriver.modern.data

import android.content.SharedPreferences
import me.jbusdriver.modern.JBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryStore @Inject constructor() {
    private val prefs: SharedPreferences =
        JBus.getSharedPreferences("search_history", 0)

    fun getHistory(): List<String> {
        return prefs.getStringSet(KEY_HISTORY, emptySet())?.toList() ?: emptyList()
    }

    fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = prefs.getStringSet(KEY_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(query)
        if (current.size > MAX_HISTORY) {
            val toRemove = current.toList().drop(MAX_HISTORY)
            toRemove.forEach { current.remove(it) }
        }
        prefs.edit().putStringSet(KEY_HISTORY, current).apply()
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_HISTORY = "search_history_queries"
        private const val MAX_HISTORY = 20
    }
}
