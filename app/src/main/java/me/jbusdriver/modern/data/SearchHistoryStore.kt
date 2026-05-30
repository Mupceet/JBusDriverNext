package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject
import javax.inject.Singleton

interface SearchHistoryStore {
    suspend fun getHistory(): List<String>
    suspend fun addQuery(query: String)
    suspend fun removeQuery(query: String)
    suspend fun clearHistory()
}

private val Context.searchHistoryDataStore by preferencesDataStore("search_history")

@Singleton
class DefaultSearchHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SearchHistoryStore {

    private val dataStore = context.searchHistoryDataStore

    override suspend fun getHistory(): List<String> {
        val json = dataStore.data.map { it[KEY_HISTORY] }.first() ?: return emptyList()
        return GSON.fromJson<List<String>>(json) ?: emptyList()
    }

    override suspend fun addQuery(query: String) {
        if (query.isBlank()) return
        val current = getHistory().toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.removeAt(current.lastIndex)
        }
        dataStore.edit { it[KEY_HISTORY] = GSON.toJson(current) }
    }

    override suspend fun removeQuery(query: String) {
        val current = getHistory().toMutableList()
        if (current.remove(query)) {
            dataStore.edit { it[KEY_HISTORY] = GSON.toJson(current) }
        }
    }

    override suspend fun clearHistory() {
        dataStore.edit { it.remove(KEY_HISTORY) }
    }

    companion object {
        private val KEY_HISTORY = stringPreferencesKey("search_history_queries")
        private const val MAX_HISTORY = 20
    }
}
