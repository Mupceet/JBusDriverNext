package me.jbusdriver.modern.core.http

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar backed by Android's system CookieManager.
 *
 * Single source of truth: WebView sets cookies via CookieManager,
 * OkHttp reads them here. No sync logic needed.
 */
class CookieManagerCookieJar : CookieJar {

    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val cookieValue = buildString {
                append(cookie.name).append('=').append(cookie.value)
                append("; path=").append(cookie.path)
                if (cookie.domain.isNotEmpty()) {
                    append("; domain=").append(cookie.domain)
                }
                if (cookie.secure) append("; secure")
                if (cookie.httpOnly) append("; httponly")
            }
            cookieManager.setCookie(url.toString(), cookieValue)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieString.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .mapNotNull { cookiePart ->
                val parts = cookiePart.split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .domain(url.host)
                        .path(url.encodedPath)
                        .name(parts[0].trim())
                        .value(parts[1].trim())
                        .build()
                } else null
            }
    }
}
