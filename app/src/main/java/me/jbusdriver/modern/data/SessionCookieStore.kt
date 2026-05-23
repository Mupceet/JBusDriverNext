package me.jbusdriver.modern.data

import android.webkit.CookieManager
import androidx.core.content.edit
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson

/**
 * Persists session cookies from CookieManager to SharedPreferences.
 *
 * Stores critical cookies (age verification, Discuz! session) with their expiry
 * timestamps so they can be restored on next app launch without creating a WebView.
 */
class SessionCookieStore {

    private val prefs by lazy {
        JBus.getSharedPreferences(PREFS_NAME, 0)
    }

    /**
     * Save all cookies for the given URL from CookieManager to SharedPreferences.
     * Only saves cookies whose names are in [TRACKED_COOKIES].
     */
    fun saveCookies(url: String) {
        val cookieString = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookieString(cookieString)
        val entries = mutableMapOf<String, PersistedCookie>()

        for ((name, value) in cookies) {
            if (name in TRACKED_COOKIES) {
                val ttlSeconds = COOKIE_TTL[name] ?: 0L
                val expiresAt = if (ttlSeconds > 0) {
                    System.currentTimeMillis() / 1000 + ttlSeconds
                } else {
                    0L // session cookie
                }
                entries[name] = PersistedCookie(value, expiresAt)
            }
        }

        if (entries.isNotEmpty()) {
            val json = GSON.toJson(entries)
            prefs.edit { putString(prefsKey(url), json) }
            KLog.d("[SessionCookieStore] Saved ${entries.size} cookies for $url", TAG)
        }
    }

    /**
     * Restore persisted cookies for the given URL back into CookieManager.
     * Only restores non-expired cookies.
     */
    fun restoreCookies(url: String) {
        val json = prefs.getString(prefsKey(url), null) ?: return
        val entries = GSON.fromJson<Map<String, PersistedCookie>>(json) ?: return
        val now = System.currentTimeMillis() / 1000
        val cookieManager = CookieManager.getInstance()

        for ((name, cookie) in entries) {
            if (cookie.expiresAt == 0L || cookie.expiresAt > now) {
                cookieManager.setCookie(url, "$name=${cookie.value}; path=/")
            }
        }
        cookieManager.flush()
        KLog.d("[SessionCookieStore] Restored cookies for $url", TAG)
    }

    /**
     * Check if critical cookies exist and are not expired.
     * Returns true if [CRITICAL_COOKIES] are all present and valid.
     */
    fun isSessionValid(url: String): Boolean {
        val json = prefs.getString(prefsKey(url), null) ?: return false
        val entries = GSON.fromJson<Map<String, PersistedCookie>>(json) ?: return false
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

    /** Remove persisted cookie data. */
    fun clear() {
        prefs.edit { clear() }
        KLog.d("[SessionCookieStore] Cleared all persisted cookies", TAG)
    }

    private fun prefsKey(url: String): String {
        val host = url.substringAfter("://").substringBefore("/")
        return "session_cookies_$host"
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        return cookieString.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
    }

    internal data class PersistedCookie(
        val value: String,
        /** Unix timestamp (seconds) when this cookie expires. 0 = session cookie. */
        val expiresAt: Long
    )

    companion object {
        private const val PREFS_NAME = "session_cookies"
        private const val TAG = "SessionCookie"

        /** Cookies to persist. */
        private val TRACKED_COOKIES = setOf(
            "age", "PHPSESSID",
            "4fJN_2132_saltkey", "4fJN_2132_sid",
            "4fJN_2132_lastvisit", "4fJN_2132_lastact"
        )

        /** Cookies that must be present for session to be considered valid. */
        private val CRITICAL_COOKIES = setOf("age", "4fJN_2132_saltkey")

        /** TTL in seconds for each cookie type, used when saving. */
        private val COOKIE_TTL = mapOf(
            "age" to 30 * 24 * 3600L,           // 30 days
            "4fJN_2132_saltkey" to 30 * 24 * 3600L,  // 30 days
            "4fJN_2132_sid" to 24 * 3600L,      // 1 day
            "4fJN_2132_lastvisit" to 30 * 24 * 3600L,
            "4fJN_2132_lastact" to 24 * 3600L
        )
    }
}
