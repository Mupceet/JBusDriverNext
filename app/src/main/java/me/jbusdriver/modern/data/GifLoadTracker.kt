package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gifDataStore by preferencesDataStore("gif_loaded_urls")

@Singleton
interface LoadedGifTracker {
    suspend fun loadedUrls(): Set<String>
    suspend fun markLoaded(url: String)
}

class GifLoadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) : LoadedGifTracker {
    private val dataStore = context.gifDataStore

    override suspend fun loadedUrls(): Set<String> {
        val prefs = dataStore.data.first()
        val count = prefs[URL_COUNT] ?: 0
        return (0 until count).mapNotNull { i -> prefs[urlKey(i)] }.toSet()
    }

    override suspend fun markLoaded(url: String) {
        dataStore.edit { prefs ->
            val count = prefs[URL_COUNT] ?: 0
            val existing = (0 until count).mapNotNull { i -> prefs[urlKey(i)] }.toMutableList()
            existing.remove(url)
            existing.add(url)
            if (existing.size > MAX_CACHE) {
                existing.removeAt(0)
            }
            existing.forEachIndexed { i, u -> prefs[urlKey(i)] = u }
            prefs[URL_COUNT] = existing.size
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun urlKey(index: Int) = stringPreferencesKey("url_$index")

    companion object {
        private val URL_COUNT = intPreferencesKey("url_count")
        private const val MAX_CACHE = 500
    }
}
