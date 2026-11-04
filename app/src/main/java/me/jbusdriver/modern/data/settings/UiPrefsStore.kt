package me.jbusdriver.modern.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPrefsDataStore by preferencesDataStore("ui_prefs")

interface CollectionUiPrefs {
    val movieSortOption: StateFlow<String>
    val actressSortOption: StateFlow<String>
    val ltxSortField: StateFlow<String>
    val ltxSortDirection: StateFlow<String>
    suspend fun setSortOption(dbType: Int, optionName: String)
    suspend fun setLtxSortField(optionName: String)
    suspend fun setLtxSortDirection(optionName: String)
}

@Singleton
class UiPrefsStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CollectionUiPrefs {
    private val dataStore = context.uiPrefsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Persisted sort option for movie collection */
    override val movieSortOption: StateFlow<String> = dataStore.data
        .map { it[MOVIE_SORT] ?: "COLLECT_DESC" }
        .stateIn(scope, SharingStarted.Eagerly, "COLLECT_DESC")

    /** Persisted sort option for actress collection */
    override val actressSortOption: StateFlow<String> = dataStore.data
        .map { it[ACTRESS_SORT] ?: "COLLECT_DESC" }
        .stateIn(scope, SharingStarted.Eagerly, "COLLECT_DESC")

    /** Persisted LTX sort field（入库时间/评分/大小/发行时间/番号） */
    override val ltxSortField: StateFlow<String> = dataStore.data
        .map { it[LTX_SORT_FIELD] ?: "ADDED" }
        .stateIn(scope, SharingStarted.Eagerly, "ADDED")

    /** Persisted LTX sort direction（DESC 倒序为默认） */
    override val ltxSortDirection: StateFlow<String> = dataStore.data
        .map { it[LTX_SORT_DIRECTION] ?: "DESC" }
        .stateIn(scope, SharingStarted.Eagerly, "DESC")

    override suspend fun setSortOption(dbType: Int, optionName: String) {
        val key = if (dbType == 1) MOVIE_SORT else ACTRESS_SORT // 1 = MovieDBType
        dataStore.edit { it[key] = optionName }
    }

    override suspend fun setLtxSortField(optionName: String) {
        dataStore.edit { it[LTX_SORT_FIELD] = optionName }
    }

    override suspend fun setLtxSortDirection(optionName: String) {
        dataStore.edit { it[LTX_SORT_DIRECTION] = optionName }
    }

    companion object {
        private val MOVIE_SORT = stringPreferencesKey("movie_sort_option")
        private val ACTRESS_SORT = stringPreferencesKey("actress_sort_option")
        private val LTX_SORT_FIELD = stringPreferencesKey("ltx_sort_field")
        private val LTX_SORT_DIRECTION = stringPreferencesKey("ltx_sort_direction")
    }
}
