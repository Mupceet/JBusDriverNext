package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.C
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.remote.GitHub
import me.jbusdriver.modern.data.remote.JAVBusService
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    fun getCurrentUrl(): String
    fun getAvailableUrls(): List<String>
    suspend fun updateUrl(url: String)
    suspend fun fetchAnnounce()
}

@Singleton
class DefaultSettingsRepository @Inject constructor() : SettingsRepository {

    override fun getCurrentUrl(): String = JAVBusService.defaultFastUrl

    override fun getAvailableUrls(): List<String> {
        val cachedJson = CacheLoader.lru.get(C.Cache.BUS_URLS)
        if (!cachedJson.isNullOrBlank()) {
            val map = GSON.fromJson<LinkedHashMap<String, String>>(cachedJson)
            return map?.values?.distinct()?.filter { it.isNotBlank() } ?: emptyList()
        }
        val diskJson = CacheLoader.acache.getAsString(C.Cache.BUS_URLS)
        if (!diskJson.isNullOrBlank()) {
            val map = GSON.fromJson<LinkedHashMap<String, String>>(diskJson)
            return map?.values?.distinct()?.filter { it.isNotBlank() } ?: emptyList()
        }
        return listOf(JAVBusService.defaultFastUrl)
    }

    override suspend fun updateUrl(url: String) {
        JAVBusService.defaultFastUrl = url
        JAVBusService.INSTANCE = JAVBusService.getInstance(url)
        JBus.JBusServices.clear()
    }

    override suspend fun fetchAnnounce() {
        try {
            val json = suspendCancellableCoroutine<String> { cont ->
                val disposable = GitHub.INSTANCE.announce()
                    .subscribe(
                        { result -> cont.resumeWith(Result.success(result)) },
                        { error ->
                            KLog.w("fetchAnnounce failed: $error")
                            cont.resumeWith(Result.failure(error))
                        }
                    )
                cont.invokeOnCancellation { disposable.dispose() }
            }

            val root = JSONObject(json)

            // Parse "backUp" array -> Map<key, url> and cache
            val backUpArray = root.optJSONArray("backUp")
            if (backUpArray != null && backUpArray.length() > 0) {
                val urlMap = linkedMapOf<String, String>()
                for (i in 0 until backUpArray.length()) {
                    val url = backUpArray.optString(i)
                    if (url.isNotBlank()) {
                        val host = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
                        urlMap[host] = url
                    }
                }
                val urlMapJson = GSON.toJson(urlMap)
                CacheLoader.cacheLruAndDisk(C.Cache.BUS_URLS to urlMapJson)

                // Set first URL as default if current is the hardcoded fallback
                val firstUrl = urlMap.values.first()
                if (JAVBusService.defaultFastUrl == "https://www.javbus.com" ||
                    JAVBusService.defaultFastUrl == "https://www.seedmm.life"
                ) {
                    JAVBusService.defaultFastUrl = firstUrl
                    JAVBusService.INSTANCE = JAVBusService.getInstance(firstUrl)
                }
            }

            // Parse "xyz" URL
            val xyzUrl = root.optString("xyz")
            if (!xyzUrl.isNullOrBlank()) {
                JAVBusService.defaultXyzUrl = xyzUrl
            }

            // Parse "xyzLoader" for host domains
            val xyzLoader = root.optJSONObject("xyzLoader")
            if (xyzLoader != null) {
                val loaderUrl = xyzLoader.optString("url")
                if (!loaderUrl.isNullOrBlank()) {
                    JAVBusService.defaultXyzUrl = loaderUrl
                }
                val legacyHosts = xyzLoader.optJSONArray("legacyHost")
                if (legacyHosts != null) {
                    val domains = mutableSetOf<String>()
                    for (i in 0 until legacyHosts.length()) {
                        domains.add(".${legacyHosts.optString(i)}")
                    }
                    JAVBusService.xyzHostDomains.clear()
                    JAVBusService.xyzHostDomains.addAll(domains)
                }
            }

            KLog.d("fetchAnnounce success: ${backUpArray?.length() ?: 0} urls, xyz=$xyzUrl")
        } catch (e: Exception) {
            KLog.w("fetchAnnounce error: ${e.message}")
        }
    }
}
