package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.data.magnet.loaders.MagnetLoaders
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple singleton replacing the Phantom-based MagnetService.
 * Directly instantiates and manages loaders without service discovery.
 */
object MagnetManager {

    fun getLoader(name: String): IMagnetLoader? {
        return MagnetLoaders.Loaders[name]
    }

    fun getAllLoaders(): Map<String, IMagnetLoader> {
        return MagnetLoaders.Loaders
    }

    fun getMagnets(loader: String, key: String, page: Int): String {
        return JSONArray(
            MagnetLoaders.Loaders[loader]?.loadMagnets(key, page)
                ?: emptyList<JSONObject>()
        ).toString()
    }

    fun getLoaderKeys(): List<String> {
        return MagnetLoaders.Loaders.keys.toList()
    }

    fun fetchMagLink(magnetLoaderKey: String, url: String): String {
        return MagnetLoaders.Loaders[magnetLoaderKey]?.fetchMagnetLink(url) ?: ""
    }

    fun hasNext(magnetLoaderKey: String): Boolean {
        return MagnetLoaders.Loaders[magnetLoaderKey]?.hasNexPage ?: false
    }
}
