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
    val error: String? = null
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

class LabSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mirrorScanner: MirrorScanner
) : ForumSettingsReader {

    private val dataStore = context.labSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val forumEnabled: StateFlow<Boolean> = dataStore.data.map { it[KEY_FORUM_ENABLED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setForumEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FORUM_ENABLED] = enabled }
    }

    override val autoLoadGifs: StateFlow<Boolean> = dataStore.data.map { it[KEY_AUTO_LOAD_GIFS] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setAutoLoadGifs(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LOAD_GIFS] = enabled }
    }

    val forumFloorOrder: StateFlow<ForumFloorOrder> = dataStore.data.map {
        ForumFloorOrder.fromPreferenceValue(it[KEY_FORUM_FLOOR_ORDER])
    }.stateIn(scope, SharingStarted.Eagerly, ForumFloorOrder.REGULAR)

    override suspend fun currentForumFloorOrder(): ForumFloorOrder =
        ForumFloorOrder.fromPreferenceValue(dataStore.data.first()[KEY_FORUM_FLOOR_ORDER])

    suspend fun setForumFloorOrder(order: ForumFloorOrder) {
        dataStore.edit { it[KEY_FORUM_FLOOR_ORDER] = order.preferenceValue }
    }

    val selectedBaseUrl: StateFlow<String> = dataStore.data.map {
        it[KEY_SELECTED_BASE_URL] ?: DEFAULT_BASE_URL
    }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_BASE_URL)

    val cachedMirrorUrls: StateFlow<List<String>> = dataStore.data.map {
        it[KEY_CACHED_MIRROR_URLS]?.toList() ?: PRESET_MIRROR_URLS
    }.stateIn(scope, SharingStarted.Eagerly, PRESET_MIRROR_URLS)

    suspend fun selectUrl(url: String) {
        val trimmed = url.trimEnd('/')
        dataStore.edit { it[KEY_SELECTED_BASE_URL] = trimmed }
        NetClient.defaultFastUrl = trimmed
    }

    suspend fun scanMirrorUrls(
        state: MutableStateFlow<ScanState>,
        seedUrl: String
    ) {
        val currentCached = cachedMirrorUrls.first()
        val discoveredUrls = mirrorScanner.scanAndVerify(state, seedUrl, currentCached)
        dataStore.edit { it[KEY_CACHED_MIRROR_URLS] = discoveredUrls }
    }

    suspend fun verifyMirrorUrls(state: MutableStateFlow<ScanState>) {
        val urls = cachedMirrorUrls.first()
        mirrorScanner.verifyOnly(state, urls)
    }

    companion object {
        private val KEY_FORUM_ENABLED = booleanPreferencesKey("forum_enabled")
        private val KEY_AUTO_LOAD_GIFS = booleanPreferencesKey("auto_load_gifs")
        private val KEY_FORUM_FLOOR_ORDER = stringPreferencesKey("forum_floor_order")
        private val KEY_SELECTED_BASE_URL = stringPreferencesKey("selected_base_url")
        private val KEY_CACHED_MIRROR_URLS = stringSetPreferencesKey("cached_mirror_urls")
        const val DEFAULT_BASE_URL = "https://www.javbus.com"
        private val PRESET_MIRROR_URLS = listOf(
            "https://www.javbus.com",
            "https://www.cdnbus.bond",
            "https://www.cdnbus.cyou",
            "https://www.seejav.cyou"
        )
    }
}
