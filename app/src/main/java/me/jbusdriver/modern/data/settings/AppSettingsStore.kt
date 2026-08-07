package me.jbusdriver.modern.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.jbusdriver.modern.core.site.DEFAULT_SITE_URL
import me.jbusdriver.modern.core.site.SitePreferenceSource
import me.jbusdriver.modern.data.mirror.MirrorScanner
import me.jbusdriver.modern.data.mirror.ScanState
import javax.inject.Inject
import javax.inject.Singleton

/** Narrow read interface for theme consumers (ThemeViewModel/JBusTheme). */
interface ThemeSettingsReader {
    val themeMode: StateFlow<ThemeMode>
    val dynamicColor: StateFlow<Boolean>
}

/** Narrow read interface for forum consumers (ForumThreadDetailViewModel / ForumThreadListViewModel). */
interface ForumSettingsReader {
    val autoLoadGifs: StateFlow<Boolean>
    suspend fun currentForumFloorOrder(): ForumFloorOrder
    suspend fun currentThreadSortOrder(): ForumThreadOrder
}

/** Narrow read/write interface for movie list display consumers (UiPrefsViewModel). */
interface MovieListSettings {
    val movieListStyle: StateFlow<MovieListStyle>
    val movieLoadMode: StateFlow<MovieLoadMode>
    suspend fun setMovieListStyle(style: MovieListStyle)
    suspend fun setMovieLoadMode(mode: MovieLoadMode)
}

/** Full settings read/write contract for SettingsViewModel. */
interface AppSettingsContract : ThemeSettingsReader, ForumSettingsReader, MovieListSettings, SitePreferenceSource {
    // Appearance
    val showMovieTab: StateFlow<Boolean>
    val showActressTab: StateFlow<Boolean>
    val showForumTab: StateFlow<Boolean>
    val forumFloorOrder: StateFlow<ForumFloorOrder>
    val threadSortOrder: StateFlow<ForumThreadOrder>
    // Network
    val selectedBaseUrl: StateFlow<String>
    val cachedMirrorUrls: StateFlow<List<String>>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setShowMovieTab(visible: Boolean)
    suspend fun setShowActressTab(visible: Boolean)
    suspend fun setShowForumTab(enabled: Boolean)
    suspend fun setAutoLoadGifs(enabled: Boolean)
    suspend fun setForumFloorOrder(order: ForumFloorOrder)
    suspend fun setThreadSortOrder(order: ForumThreadOrder)
    suspend fun selectUrl(url: String)

    suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>, seedUrl: String)
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>)
}

private val Context.appSettingsDataStore by preferencesDataStore("app_settings")

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mirrorScanner: MirrorScanner
) : AppSettingsContract {

    private val dataStore = context.appSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun <T> flowOf(key: Preferences.Key<T>, default: T): StateFlow<T> =
        dataStore.data.map { it[key] ?: default }.stateIn(scope, SharingStarted.Eagerly, default)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun <T> mapped(
        key: Preferences.Key<String>,
        default: T,
        decode: (String?) -> T
    ): StateFlow<T> = dataStore.data.map { decode(it[key]) }.stateIn(scope, SharingStarted.Eagerly, default)

    // region Appearance
    override val themeMode: StateFlow<ThemeMode> = mapped(KEY_THEME_MODE, ThemeMode.SYSTEM) { ThemeMode.fromPreferenceValue(it) }
    override val dynamicColor: StateFlow<Boolean> = flowOf(KEY_DYNAMIC_COLOR, true)
    override val showMovieTab: StateFlow<Boolean> = flowOf(KEY_SHOW_MOVIE_TAB, true)
    override val showActressTab: StateFlow<Boolean> = flowOf(KEY_SHOW_ACTRESS_TAB, true)
    override val showForumTab: StateFlow<Boolean> = flowOf(KEY_SHOW_FORUM_TAB, false)
    override val movieListStyle: StateFlow<MovieListStyle> = mapped(KEY_MOVIE_LIST_STYLE, MovieListStyle.LIST) { MovieListStyle.fromPreferenceValue(it) }
    override val movieLoadMode: StateFlow<MovieLoadMode> = mapped(KEY_MOVIE_LOAD_MODE, MovieLoadMode.WITH_MAGNET) { MovieLoadMode.fromPreferenceValue(it) }

    override suspend fun setThemeMode(mode: ThemeMode) = put(KEY_THEME_MODE, mode.preferenceValue)
    override suspend fun setDynamicColor(enabled: Boolean) = put(KEY_DYNAMIC_COLOR, enabled)
    override suspend fun setShowMovieTab(visible: Boolean) = put(KEY_SHOW_MOVIE_TAB, visible)
    override suspend fun setShowActressTab(visible: Boolean) = put(KEY_SHOW_ACTRESS_TAB, visible)
    override suspend fun setShowForumTab(enabled: Boolean) = put(KEY_SHOW_FORUM_TAB, enabled)
    override suspend fun setMovieListStyle(style: MovieListStyle) = put(KEY_MOVIE_LIST_STYLE, style.preferenceValue)
    override suspend fun setMovieLoadMode(mode: MovieLoadMode) = put(KEY_MOVIE_LOAD_MODE, mode.preferenceValue)
    // endregion

    // region Forum
    override val autoLoadGifs: StateFlow<Boolean> = flowOf(KEY_AUTO_LOAD_GIFS, false)
    override val forumFloorOrder: StateFlow<ForumFloorOrder> = mapped(KEY_FORUM_FLOOR_ORDER, ForumFloorOrder.REGULAR) { ForumFloorOrder.fromPreferenceValue(it) }
    override val threadSortOrder: StateFlow<ForumThreadOrder> = mapped(KEY_THREAD_SORT_ORDER, ForumThreadOrder.LASTPOST) { ForumThreadOrder.fromPreferenceValue(it) }
    override suspend fun currentForumFloorOrder(): ForumFloorOrder =
        ForumFloorOrder.fromPreferenceValue(dataStore.data.first()[KEY_FORUM_FLOOR_ORDER])
    override suspend fun currentThreadSortOrder(): ForumThreadOrder =
        ForumThreadOrder.fromPreferenceValue(dataStore.data.first()[KEY_THREAD_SORT_ORDER])
    override suspend fun setAutoLoadGifs(enabled: Boolean) = put(KEY_AUTO_LOAD_GIFS, enabled)
    override suspend fun setForumFloorOrder(order: ForumFloorOrder) = put(KEY_FORUM_FLOOR_ORDER, order.preferenceValue)
    override suspend fun setThreadSortOrder(order: ForumThreadOrder) = put(KEY_THREAD_SORT_ORDER, order.preferenceValue)
    // endregion

    // region Network
    override val selectedBaseUrl: StateFlow<String> = flowOf(KEY_SELECTED_BASE_URL, DEFAULT_SITE_URL)
    override val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS }
        .stateIn(scope, SharingStarted.Eagerly, PRESET_MIRROR_URLS)
    override suspend fun currentSelectedBaseUrl(): String =
        dataStore.data.first()[KEY_SELECTED_BASE_URL] ?: DEFAULT_SITE_URL
    override suspend fun selectUrl(url: String) = put(KEY_SELECTED_BASE_URL, url.trimEnd('/'))
    override suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>, seedUrl: String) {
        val cached = cachedMirrorUrls.first()
        val discovered = mirrorScanner.scanAndVerify(state, seedUrl, cached)
        dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = discovered }
    }
    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        mirrorScanner.verifyOnly(state, cachedMirrorUrls.first())
    }
    // endregion

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_SHOW_MOVIE_TAB = booleanPreferencesKey("show_movie_tab")
        private val KEY_SHOW_ACTRESS_TAB = booleanPreferencesKey("show_actress_tab")
        private val KEY_SHOW_FORUM_TAB = booleanPreferencesKey("show_forum_tab")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_FORUM_FLOOR_ORDER = stringPreferencesKey("forum_floor_order")
        private val KEY_THREAD_SORT_ORDER = stringPreferencesKey("forum_thread_sort_order")
        private val KEY_MOVIE_LIST_STYLE = stringPreferencesKey("movie_list_style")
        private val KEY_MOVIE_LOAD_MODE = stringPreferencesKey("movie_load_mode")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")

        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
