package me.jbusdriver.modern.data.cache

import me.jbusdriver.modern.core.site.normalizeBaseUrl

internal fun siteCacheKey(baseUrl: String, namespace: String, identity: String): String =
    "$namespace:${normalizeBaseUrl(baseUrl)}:$identity"
