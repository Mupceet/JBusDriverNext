package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPrefsDataStore by preferencesDataStore("ui_prefs")

@Singleton
class UiPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.uiPrefsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Cached grid/list preference — StateFlow avoids re-reading from disk on each subscription. */
    val isGrid: StateFlow<Boolean> = dataStore.data
        .map { it[IS_GRID] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setGrid(isGrid: Boolean) {
        dataStore.edit { it[IS_GRID] = isGrid }
    }

    /** Persisted sort option for movie collection */
    val movieSortOption: StateFlow<String> = dataStore.data
        .map { it[MOVIE_SORT] ?: "COLLECT_DESC" }
        .stateIn(scope, SharingStarted.Eagerly, "COLLECT_DESC")

    /** Persisted sort option for actress collection */
    val actressSortOption: StateFlow<String> = dataStore.data
        .map { it[ACTRESS_SORT] ?: "COLLECT_DESC" }
        .stateIn(scope, SharingStarted.Eagerly, "COLLECT_DESC")

    suspend fun setSortOption(dbType: Int, optionName: String) {
        val key = if (dbType == 1) MOVIE_SORT else ACTRESS_SORT // 1 = MovieDBType
        dataStore.edit { it[key] = optionName }
    }

    companion object {
        private val IS_GRID = booleanPreferencesKey("is_grid")
        private val MOVIE_SORT = stringPreferencesKey("movie_sort_option")
        private val ACTRESS_SORT = stringPreferencesKey("actress_sort_option")
    }
}
