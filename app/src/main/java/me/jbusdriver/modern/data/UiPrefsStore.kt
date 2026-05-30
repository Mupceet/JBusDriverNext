package me.jbusdriver.modern.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uiPrefsDataStore by preferencesDataStore("ui_prefs")

@Singleton
class UiPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.uiPrefsDataStore

    val isGrid: Flow<Boolean> = dataStore.data.map { it[IS_GRID] ?: false }

    suspend fun setGrid(isGrid: Boolean) {
        dataStore.edit { it[IS_GRID] = isGrid }
    }

    companion object {
        private val IS_GRID = booleanPreferencesKey("is_grid")
    }
}
