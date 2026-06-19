package me.jbusdriver.modern.data.session

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject

private val Context.sessionCookieDataStore by preferencesDataStore("session_cookies")

class SessionCookieStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.sessionCookieDataStore

    suspend fun saveCookies(url: String) {
        val cookieString = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieString(cookieString)
        val entries = mutableMapOf<String, PersistedCookie>()
        for ((name, value) in cookies) {
            if (name in TRACKED_COOKIES) {
                val ttlSeconds = COOKIE_TTL[name] ?: 0L
                val expiresAt =
                    if (ttlSeconds > 0) System.currentTimeMillis() / 1000 + ttlSeconds else 0L
                entries[name] = PersistedCookie(value, expiresAt)
            }
        }
        if (entries.isNotEmpty()) {
            val json = GSON.toJson(entries)
            dataStore.edit { it[prefsKey(url)] = json }
            KLog.d("[SessionCookieStore] Saved ${entries.size} cookies for $url", TAG)
        } else {
            KLog.d("[SessionCookieStore] No tracked cookies found for $url", TAG)
        }
    }

    suspend fun restoreCookies(url: String) {
        val json = dataStore.data.map { it[prefsKey(url)] }.first() ?: return
        val entries = tryParse(json) ?: return
        val now = System.currentTimeMillis() / 1000
        val cookieManager = CookieManager.getInstance()
        var restored = 0
        for ((name, cookie) in entries) {
            if (cookie.expiresAt == 0L || cookie.expiresAt > now) {
                cookieManager.setCookie(url, "$name=${cookie.value}; path=/")
                restored++
            }
        }
        if (restored > 0) cookieManager.flush()
        KLog.d("[SessionCookieStore] Restored $restored/${entries.size} cookies for $url", TAG)
    }

    suspend fun isSessionValid(url: String): Boolean {
        val json = dataStore.data.map { it[prefsKey(url)] }.first() ?: return false
        val entries = tryParse(json) ?: return false
        val now = System.currentTimeMillis() / 1000
        for (name in CRITICAL_COOKIES) {
            val cookie = entries[name] ?: return false
            if (cookie.expiresAt != 0L && cookie.expiresAt <= now) {
                KLog.d("[SessionCookieStore] Cookie $name expired", TAG)
                return false
            }
        }
        return true
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
        KLog.d("[SessionCookieStore] Cleared all persisted cookies", TAG)
    }

    private fun prefsKey(url: String): androidx.datastore.preferences.core.Preferences.Key<String> {
        val host = url.substringAfter("://").substringBefore("/")
        return stringPreferencesKey("session_cookies_$host")
    }

    private fun tryParse(json: String): Map<String, PersistedCookie>? {
        return try {
            GSON.fromJson<Map<String, PersistedCookie>>(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        return cookieString.split(";").map { it.trim() }.filter { it.contains("=") }.associate {
            val parts = it.split("=", limit = 2)
            parts[0].trim() to parts[1].trim()
        }
    }

    internal data class PersistedCookie(val value: String, val expiresAt: Long)

    companion object {
        private const val TAG = "SessionCookie"
        private val TRACKED_COOKIES = setOf(
            "age",
            "PHPSESSID",
            "4fJN_2132_saltkey",
            "4fJN_2132_sid",
            "4fJN_2132_lastvisit",
            "4fJN_2132_lastact"
        )
        private val CRITICAL_COOKIES = setOf("age", "4fJN_2132_saltkey")
        private val COOKIE_TTL = mapOf(
            "age" to 30 * 24 * 3600L,
            "4fJN_2132_saltkey" to 30 * 24 * 3600L,
            "4fJN_2132_sid" to 24 * 3600L,
            "4fJN_2132_lastvisit" to 30 * 24 * 3600L,
            "4fJN_2132_lastact" to 24 * 3600L
        )
    }
}
