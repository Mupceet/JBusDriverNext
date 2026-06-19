package me.jbusdriver.modern.data

import android.content.Context
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
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.site.DEFAULT_SITE_URL
import me.jbusdriver.modern.core.site.SitePreferenceSource
import javax.inject.Inject
import javax.inject.Singleton

data class MirrorUrl(
    val url: String,
    val isReachable: Boolean = false,
    val latencyMs: Long = -1L
)

data class ScanState(
    val isScanning: Boolean = false,
    val phase: ScanPhase = ScanPhase.IDLE,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentUrl: String = "",
    val discoveredUrls: List<MirrorUrl> = emptyList(),
    val error: Int? = null
)

enum class ScanPhase {
    IDLE,
    DISCOVERING,
    VERIFYING,
    DONE
}

private val Context.labSettingsDataStore by preferencesDataStore("lab_settings")

@Singleton
interface ForumSettingsReader {
    val autoLoadGifs: StateFlow<Boolean>
    suspend fun currentForumFloorOrder(): ForumFloorOrder
}

interface LabSettingsStoreContract {
    val forumEnabled: StateFlow<Boolean>
    val autoLoadGifs: StateFlow<Boolean>
    val forumFloorOrder: StateFlow<ForumFloorOrder>
    val selectedBaseUrl: StateFlow<String>
    val cachedMirrorUrls: StateFlow<List<String>>

    suspend fun setForumEnabled(enabled: Boolean)
    suspend fun setAutoLoadGifs(enabled: Boolean)
    suspend fun setForumFloorOrder(order: ForumFloorOrder)
    suspend fun selectUrl(url: String)
    suspend fun scanMirrorUrls(state: MutableStateFlow<ScanState>, seedUrl: String)
    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>)
}

class LabSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mirrorScanner: MirrorScanner
) : ForumSettingsReader, SitePreferenceSource, LabSettingsStoreContract {

    private val dataStore = context.labSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val forumEnabled: StateFlow<Boolean> = dataStore.data.map { it[KEY_FORUM_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun setForumEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FORUM_ENABLED] = enabled }
    }

    override val autoLoadGifs: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_AUTO_LOAD_GIFS] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun setAutoLoadGifs(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LOAD_GIFS] = enabled }
    }

    override val forumFloorOrder: StateFlow<ForumFloorOrder> = dataStore.data.map {
        ForumFloorOrder.fromPreferenceValue(it[KEY_FORUM_FLOOR_ORDER])
    }.stateIn(scope, SharingStarted.Eagerly, ForumFloorOrder.REGULAR)

    override suspend fun currentForumFloorOrder(): ForumFloorOrder =
        ForumFloorOrder.fromPreferenceValue(dataStore.data.first()[KEY_FORUM_FLOOR_ORDER])

    override suspend fun setForumFloorOrder(order: ForumFloorOrder) {
        dataStore.edit { it[KEY_FORUM_FLOOR_ORDER] = order.preferenceValue }
    }

    override val selectedBaseUrl: StateFlow<String> = dataStore.data.map {
        it[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL
    }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_BASE_URL)

    override suspend fun currentSelectedBaseUrl(): String =
        dataStore.data.first()[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL

    override val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data.map {
        it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS
    }.stateIn(scope, SharingStarted.Eagerly, PRESET_MIRROR_URLS)

    override suspend fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        dataStore.edit { it[KEY_SELECTED_BASE_URL] = trimmed }
        NetClient.defaultFastUrl = trimmed
    }

    override suspend fun scanMirrorUrls(
        state: MutableStateFlow<ScanState>,
        seedUrl: String
    ) {
        val currentCached = cachedMirrorUrls.first()
        val discoveredUrls = mirrorScanner.scanAndVerify(state, seedUrl, currentCached)
        dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = discoveredUrls }
    }

    override suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = cachedMirrorUrls.first()
        mirrorScanner.verifyOnly(state, urls)
    }

    companion object {
        private val KEY_FORUM_ENABLED = booleanPreferencesKey("forum_enabled")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_FORUM_FLOOR_ORDER = stringPreferencesKey("forum_floor_order")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")
        const val DEFAULT_BASE_URL = DEFAULT_SITE_URL
        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
