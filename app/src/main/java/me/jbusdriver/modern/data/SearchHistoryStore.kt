package me.jbusdriver.modern.data

import android.content.SharedPreferences
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.data.di.SearchHistoryPrefs
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

interface SearchHistoryStore {
    fun getHistory(): List<String>
    fun addQuery(query: String)
    fun removeQuery(query: String)
    fun clearHistory()
}

@Singleton
class DefaultSearchHistoryStore @Inject constructor(
    @SearchHistoryPrefs private val prefs: SharedPreferences
) : SearchHistoryStore {

    override fun getHistory(): List<String> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return GSON.fromJson<List<String>>(json) ?: emptyList()
    }

    override fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        prefs.edit { putString(KEY_HISTORY, GSON.toJson(current)) }
    }

    override fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        if (current.remove(query)) {
            prefs.edit { putString(KEY_HISTORY, GSON.toJson(current)) }
        }
    }

    override fun clearHistory() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    companion object {
        private const val KEY_HISTORY = "search_history_queries"
        private const val MAX_HISTORY = 20
    }
}
