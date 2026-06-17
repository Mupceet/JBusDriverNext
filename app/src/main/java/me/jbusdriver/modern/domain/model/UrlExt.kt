package me.jbusdriver.modern.domain.model

import android.net.Uri
import androidx.collection.LruCache
import androidx.core.net.toUri
import java.net.URI

private val urlCache by lazy { LruCache<String, Uri>(512) }

private fun String.cachedAndroidUri(): Uri? {
    return urlCache[this] ?: runCatching { toUri() }.getOrNull()?.also {
        urlCache.put(this, it)
    }
}

private fun String.javaUri(): URI? = runCatching { URI(this) }.getOrNull()

val String.urlHost: String
    get() {
        val androidUri = cachedAndroidUri()
        val javaUri = javaUri()
        val scheme = androidUri?.scheme ?: javaUri?.scheme
        val host = androidUri?.host ?: javaUri?.host
        require(scheme != null && host != null) { "Invalid URL host: $this" }
        return "$scheme://$host"
    }

val String.urlPath: String
    get() = cachedAndroidUri()?.path ?: javaUri()?.path ?: ""
