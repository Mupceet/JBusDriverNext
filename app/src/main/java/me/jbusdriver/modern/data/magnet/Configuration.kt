package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.core.*


object Configuration {
    //region magnet
    private const val MagnetSourceS: String = "MagnetSourceS"


    fun getConfigKeys() =
        GSON.fromJson<MutableList<String>>(getSp(MagnetSourceS) ?: "")?.takeIf { it.isNotEmpty() } ?: let {
            val default = MagnetManager.getLoaderKeys().take(3)
            saveSp(MagnetSourceS, default.toJsonString())
            default.toMutableList()
        }

    fun saveMagnetKeys(keys: List<String>) = saveSp(MagnetSourceS, keys.toJsonString())
//endregion
}
