package me.jbusdriver.modern.data

import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.common.JBus
import me.jbusdriver.http.JAVBusService
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    fun getCurrentUrl(): String
    fun getAvailableUrls(): List<String>
    suspend fun updateUrl(url: String)
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
}
