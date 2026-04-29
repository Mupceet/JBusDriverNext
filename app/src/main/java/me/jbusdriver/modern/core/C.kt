package me.jbusdriver.modern.core

import me.jbusdriver.modern.core.ACache

object C {
    object Cache {
        const val DAY = ACache.TIME_DAY
        const val WEEK = ACache.TIME_DAY * 7
        const val ANNOUNCE_URL = "announceUrl"
        const val ANNOUNCE_VALUE = "announce_value"
        const val BUS_URLS = "bus_urls"
        const val IMG_HOSTS = "img_hosts"
    }

    object Components {
        const val Magnet = "C_Magnet"
        const val PluginManager = "C_PluginManager"
    }

    object PluginComponents {
        const val PluginMagnet = "C_Magnet_Plugin"
        fun AllPlugins() = arrayOf(PluginMagnet)
    }

    object SavedInstanceState {
        const val RECREATION_SAVED_STATE = "RECREATION_SAVED_STATE"
        const val LOADER_ID_SAVED_STATE = "LOADER_ID_SAVED_STATE"
        const val LOADER_SAVED_STATES = "LOADER_SAVED_STATES:"
    }

    object BundleKey {
        const val Key_1 = "Key_1"
        const val Key_2 = "Key_2"
    }
}
