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
import me.jbusdriver.modern.core.site.normalizeBaseUrl
import me.jbusdriver.modern.data.ltx.DEFAULT_LTX_BASE_URL
import me.jbusdriver.modern.data.mirror.MirrorScanner
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.translation.LanguageCatalog
import me.jbusdriver.modern.data.translation.LIBRE_TRANSLATE_DEFAULT_URL
import me.jbusdriver.modern.data.translation.TRANSLATION_DEFAULT_TARGET_LANG
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslatorConfig
import java.net.URI
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

/** Narrow read interface for translation consumers (MovieDetailViewModel / DefaultTranslationRepository). */
interface TranslationSettingsReader {
    val translateByDefault: StateFlow<Boolean>
    suspend fun currentTranslatorConfig(): TranslatorConfig
}

/** Narrow read interface for LTX library consumers (DefaultLtxRepository / collect tab). */
interface LtxSettingsSource {
    val ltxEnabled: StateFlow<Boolean>
    suspend fun currentLtxBaseUrl(): String
}

/** Narrow read/write interface for site selection consumers (SiteManagerViewModel). */
interface SiteSettingsSource : SitePreferenceSource {
    val selectedBaseUrl: StateFlow<String>
    val cachedMirrorUrls: StateFlow<List<String>>
    val customBaseUrls: StateFlow<List<String>>

    suspend fun selectUrl(url: String)
    /** 归一并校验后保存自定义网址；非法或跨组重复返回 false。 */
    suspend fun addCustomUrl(url: String): Boolean
    suspend fun removeCustomUrl(url: String)
    suspend fun removeScannedUrl(url: String)

    /** 种子 = 主站 + 当前选中 + 自定义 + 缓存，由实现方统一组装。 */
    suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>)
    /** 验证 preset + custom + cached 的并集。 */
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>)
}

/** 校验并归一化用户输入的自定义网址；非法（空白/非 http(s)/无主机）返回 null。 */
internal fun normalizeCustomUrlInput(rawUrl: String): String? {
    val normalized = normalizeBaseUrl(rawUrl)
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return null
    val host = runCatching { URI(normalized).host }.getOrNull() ?: return null
    if (host.isBlank() || !host.contains('.')) return null
    return normalized
}

/** 扫描种子 = 主站 + 当前选中 + 自定义 + 缓存（归一去重，保持首次出现顺序）。 */
internal fun composeScanSeeds(
    presetUrl: String,
    selectedUrl: String,
    customUrls: List<String>,
    cachedUrls: List<String>
): List<String> = (listOf(presetUrl, selectedUrl) + customUrls + cachedUrls)
    .map { normalizeBaseUrl(it) }
    .distinct()

/** 扫描结果持久化前剔除官方/自定义 URL，缓存只存"扫描发现"。 */
internal fun filterScannedForPersistence(
    discovered: Set<String>,
    presetUrl: String,
    customUrls: List<String>
): Set<String> {
    val excluded = buildSet {
        add(normalizeBaseUrl(presetUrl))
        customUrls.forEach { add(normalizeBaseUrl(it)) }
    }
    return discovered.map { normalizeBaseUrl(it) }.filterNot { it in excluded }.toSet()
}

/** Full settings read/write contract for SettingsViewModel. */
interface AppSettingsContract : ThemeSettingsReader, ForumSettingsReader, MovieListSettings,
    SiteSettingsSource, TranslationSettingsReader, LtxSettingsSource {
    // Appearance
    val showMovieTab: StateFlow<Boolean>
    val showActressTab: StateFlow<Boolean>
    val showForumTab: StateFlow<Boolean>
    val forumFloorOrder: StateFlow<ForumFloorOrder>
    val threadSortOrder: StateFlow<ForumThreadOrder>
    // Translation
    val translationProvider: StateFlow<TranslationProvider>
    val translationTargetLang: StateFlow<String>       // 原始设置值;"" = 跟随系统
    val effectiveTranslationTarget: StateFlow<String>   // 已解析(跟随系统时按设备 locale)
    val youdaoAppKey: StateFlow<String>
    val youdaoAppSecret: StateFlow<String>
    val libreBaseUrl: StateFlow<String>
    val libreApiKey: StateFlow<String>
    val deeplAuthKey: StateFlow<String>
    val microsoftKey: StateFlow<String>
    val microsoftRegion: StateFlow<String>
    // LTX
    val ltxBaseUrl: StateFlow<String>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setShowMovieTab(visible: Boolean)
    suspend fun setShowActressTab(visible: Boolean)
    suspend fun setShowForumTab(enabled: Boolean)
    suspend fun setAutoLoadGifs(enabled: Boolean)
    suspend fun setForumFloorOrder(order: ForumFloorOrder)
    suspend fun setThreadSortOrder(order: ForumThreadOrder)
    suspend fun setTranslateByDefault(enabled: Boolean)
    suspend fun setTranslationProvider(provider: TranslationProvider)
    suspend fun setTranslationTargetLang(code: String)
    suspend fun setYoudaoCredentials(appKey: String, appSecret: String)
    suspend fun setLibreCredentials(baseUrl: String, apiKey: String)
    suspend fun setDeeplAuthKey(key: String)
    suspend fun setMicrosoftCredentials(key: String, region: String)
    suspend fun setLtxEnabled(enabled: Boolean)
    suspend fun setLtxBaseUrl(baseUrl: String)
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

    // region Network (SiteSettingsSource)
    override val selectedBaseUrl: StateFlow<String> = flowOf(KEY_SELECTED_BASE_URL, DEFAULT_SITE_URL)
    override val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CACHED_MIRROR_URLS]?.toList() ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val customBaseUrls: StateFlow<List<String>> = dataStore.data
        .map { it[KEY_CUSTOM_BASE_URLS]?.toList()?.sorted() ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override suspend fun currentSelectedBaseUrl(): String =
        dataStore.data.first()[KEY_SELECTED_BASE_URL] ?: DEFAULT_SITE_URL
    override suspend fun selectUrl(url: String) = put(KEY_SELECTED_BASE_URL, url.trimEnd('/'))
    override suspend fun addCustomUrl(url: String): Boolean {
        val normalized = normalizeCustomUrlInput(url) ?: return false
        val existing = buildList {
            add(DEFAULT_SITE_URL)
            addAll(cachedMirrorUrls.first())
            addAll(customBaseUrls.first())
        }
        if (existing.any { normalizeBaseUrl(it) == normalized }) return false
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BASE_URLS] = (prefs[KEY_CUSTOM_BASE_URLS] ?: emptySet()) + normalized
        }
        return true
    }
    override suspend fun removeCustomUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_BASE_URLS] = (prefs[KEY_CUSTOM_BASE_URLS] ?: emptySet()) - url
        }
    }
    override suspend fun removeScannedUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_MIRROR_URLS] = (prefs[KEY_CACHED_MIRROR_URLS] ?: emptySet()) - url
        }
    }
    override suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>) {
        val seeds = composeScanSeeds(
            presetUrl = DEFAULT_SITE_URL,
            selectedUrl = selectedBaseUrl.first(),
            customUrls = customBaseUrls.first(),
            cachedUrls = cachedMirrorUrls.first()
        )
        val discovered = mirrorScanner.scanAndVerify(state, seeds)
        dataStore.edit {
            it[KEY_CACHED_MIRROR_URLS] =
                filterScannedForPersistence(discovered, DEFAULT_SITE_URL, customBaseUrls.first())
        }
    }
    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = (listOf(DEFAULT_SITE_URL) + customBaseUrls.first() + cachedMirrorUrls.first())
            .map { normalizeBaseUrl(it) }
            .distinct()
        mirrorScanner.verifyOnly(state, urls)
    }
    // endregion

    // region Translation
    override val translateByDefault: StateFlow<Boolean> = flowOf(KEY_TRANSLATE_BY_DEFAULT, false)
    override val translationProvider: StateFlow<TranslationProvider> =
        mapped(KEY_TRANSLATE_PROVIDER, TranslationProvider.GOOGLE) { TranslationProvider.fromId(it) }
    override val translationTargetLang: StateFlow<String> = flowOf(KEY_TRANSLATION_TARGET_LANG, "")
    override val youdaoAppKey: StateFlow<String> = flowOf(KEY_YOUDAO_APP_KEY, "")
    override val youdaoAppSecret: StateFlow<String> = flowOf(KEY_YOUDAO_APP_SECRET, "")
    override val libreBaseUrl: StateFlow<String> = flowOf(KEY_LIBRE_BASE_URL, "")
    override val libreApiKey: StateFlow<String> = flowOf(KEY_LIBRE_API_KEY, "")
    override val deeplAuthKey: StateFlow<String> = flowOf(KEY_DEEPL_AUTH_KEY, "")
    override val microsoftKey: StateFlow<String> = flowOf(KEY_MICROSOFT_KEY, "")
    override val microsoftRegion: StateFlow<String> = flowOf(KEY_MICROSOFT_REGION, "")
    override val effectiveTranslationTarget: StateFlow<String> = dataStore.data
        .map { prefs -> resolveTargetLang(prefs[KEY_TRANSLATION_TARGET_LANG]) }
        .stateIn(scope, SharingStarted.Eagerly, TRANSLATION_DEFAULT_TARGET_LANG)
    override suspend fun currentTranslatorConfig(): TranslatorConfig {
        val prefs = dataStore.data.first()
        return TranslatorConfig(
            provider = TranslationProvider.fromId(prefs[KEY_TRANSLATE_PROVIDER]),
            targetLang = resolveTargetLang(prefs[KEY_TRANSLATION_TARGET_LANG]),
            youdaoAppKey = prefs[KEY_YOUDAO_APP_KEY].orEmpty(),
            youdaoAppSecret = prefs[KEY_YOUDAO_APP_SECRET].orEmpty(),
            libreBaseUrl = prefs[KEY_LIBRE_BASE_URL].orEmpty().ifBlank { LIBRE_TRANSLATE_DEFAULT_URL },
            libreApiKey = prefs[KEY_LIBRE_API_KEY].orEmpty(),
            deeplAuthKey = prefs[KEY_DEEPL_AUTH_KEY].orEmpty(),
            microsoftKey = prefs[KEY_MICROSOFT_KEY].orEmpty(),
            microsoftRegion = prefs[KEY_MICROSOFT_REGION].orEmpty(),
        )
    }

    /** 未设置(默认)或非法值统一回退繁体中文。 */
    private fun resolveTargetLang(setting: String?): String =
        LanguageCatalog.findByCode(setting)?.code ?: TRANSLATION_DEFAULT_TARGET_LANG

    override suspend fun setTranslateByDefault(enabled: Boolean) = put(KEY_TRANSLATE_BY_DEFAULT, enabled)
    override suspend fun setTranslationProvider(provider: TranslationProvider) = put(KEY_TRANSLATE_PROVIDER, provider.id)
    override suspend fun setTranslationTargetLang(code: String) = put(KEY_TRANSLATION_TARGET_LANG, code)
    override suspend fun setYoudaoCredentials(appKey: String, appSecret: String) {
        dataStore.edit {
            it[KEY_YOUDAO_APP_KEY] = appKey.trim()
            it[KEY_YOUDAO_APP_SECRET] = appSecret.trim()
        }
    }
    override suspend fun setLibreCredentials(baseUrl: String, apiKey: String) {
        dataStore.edit {
            it[KEY_LIBRE_BASE_URL] = baseUrl.trim()
            it[KEY_LIBRE_API_KEY] = apiKey.trim()
        }
    }
    override suspend fun setDeeplAuthKey(key: String) = put(KEY_DEEPL_AUTH_KEY, key.trim())
    override suspend fun setMicrosoftCredentials(key: String, region: String) {
        dataStore.edit {
            it[KEY_MICROSOFT_KEY] = key.trim()
            it[KEY_MICROSOFT_REGION] = region.trim()
        }
    }
    // endregion

    // region LTX
    override val ltxEnabled: StateFlow<Boolean> = flowOf(KEY_LTX_ENABLED, true)
    override val ltxBaseUrl: StateFlow<String> = flowOf(KEY_LTX_BASE_URL, DEFAULT_LTX_BASE_URL)
    override suspend fun currentLtxBaseUrl(): String =
        dataStore.data.first()[KEY_LTX_BASE_URL]?.takeIf { it.isNotBlank() } ?: DEFAULT_LTX_BASE_URL
    override suspend fun setLtxEnabled(enabled: Boolean) = put(KEY_LTX_ENABLED, enabled)
    override suspend fun setLtxBaseUrl(baseUrl: String) = put(KEY_LTX_BASE_URL, baseUrl.trim().trimEnd('/'))
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
        private val KEY_CUSTOM_BASE_URLS = stringSetPreferencesKey("custom_base_urls")
        private val KEY_TRANSLATE_BY_DEFAULT = booleanPreferencesKey("translate_by_default")
        private val KEY_TRANSLATE_PROVIDER = stringPreferencesKey("translate_provider")
        private val KEY_TRANSLATION_TARGET_LANG = stringPreferencesKey("translation_target_lang")
        private val KEY_YOUDAO_APP_KEY = stringPreferencesKey("youdao_app_key")
        private val KEY_YOUDAO_APP_SECRET = stringPreferencesKey("youdao_app_secret")
        private val KEY_LIBRE_BASE_URL = stringPreferencesKey("libre_base_url")
        private val KEY_LIBRE_API_KEY = stringPreferencesKey("libre_api_key")
        private val KEY_DEEPL_AUTH_KEY = stringPreferencesKey("deepl_auth_key")
        private val KEY_MICROSOFT_KEY = stringPreferencesKey("microsoft_key")
        private val KEY_MICROSOFT_REGION = stringPreferencesKey("microsoft_region")
        private val KEY_LTX_ENABLED = booleanPreferencesKey("ltx_enabled")
        private val KEY_LTX_BASE_URL = stringPreferencesKey("ltx_base_url")
    }
}
