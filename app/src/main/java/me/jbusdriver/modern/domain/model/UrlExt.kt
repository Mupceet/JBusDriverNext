package me.jbusdriver.modern.domain.model

import android.net.Uri
import androidx.collection.LruCache
import androidx.core.net.toUri

/** URL 解析结果缓存 */
private val urlCache by lazy { LruCache<String, Uri>(512) }

/** 从 URL 字符串提取 host 部分（scheme://host） */
val String.urlHost: String
    get() = (urlCache.get(this) ?: let {
        val uri = Uri.parse(this)
        urlCache.put(this, uri)
        uri
    }).let {
        checkNotNull(it)
        "${it.scheme}://${it.host}"
    }

/** 从 URL 字符串提取路径部分（不含 scheme 和 host） */
val String.urlPath: String
    get() = (urlCache[this] ?: let {
        val uri = this.toUri()
        urlCache.put(this, uri)
        uri
    }).path ?: ""
