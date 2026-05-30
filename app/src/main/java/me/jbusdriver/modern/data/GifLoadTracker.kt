package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gifDataStore by preferencesDataStore("gif_loaded_urls")

@Singleton
class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.gifDataStore

    suspend fun loadedUrls(): Set<String> {
        return dataStore.data.map { it[URLS] ?: emptySet() }.first()
    }

    suspend fun markLoaded(url: String) {
        dataStore.edit { prefs ->
            val current = prefs[URLS] ?: emptySet()
            val updated = if (current.size >= MAX_CACHE) {
                current.toList().takeLast(MAX_CACHE - 1).toSet() + url
            } else {
                current + url
            }
            prefs[URLS] = updated
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val URLS = stringSetPreferencesKey("urls")
        private const val MAX_CACHE = 500
    }
}
