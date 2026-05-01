package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.core.*

/**
 * 职责：磁力链接源配置管理，读写用户选择的磁力加载器列表
 *
 * 使用场景：MovieDetailViewModel 加载磁力链接时读取配置，设置页保存用户选择
 * 线程：SharedPreferences 操作，通过 saveSp 的 IO 调度器保证非阻塞
 */
object Configuration {
    private const val MagnetSourceS: String = "MagnetSourceS"

    /**
     * 获取用户配置的磁力加载器 key 列表
     *
     * 首次使用时取默认加载器前 3 个并持久化
     *
     * @return 加载器 key 列表
     */
    fun getConfigKeys() =
        GSON.fromJson<MutableList<String>>(getSp(MagnetSourceS) ?: "")?.takeIf { it.isNotEmpty() } ?: let {
            val default = MagnetManager.getLoaderKeys().take(3)
            // 同步写入：getConfigKeys 可能在非协程上下文中调用
            JBusManager.context.applicationContext.getSharedPreferences(
                "config", android.content.Context.MODE_PRIVATE
            ).edit().putString(MagnetSourceS, default.toJsonString()).apply()
            default.toMutableList()
        }

    /**
     * 保存用户选择的磁力加载器列表
     *
     * 注意：此方法改为同步写入，因为调用方可能不在协程上下文中
     *
     * @param keys 加载器 key 列表
     */
    fun saveMagnetKeys(keys: List<String>) {
        JBusManager.context.applicationContext.getSharedPreferences(
            "config", android.content.Context.MODE_PRIVATE
        ).edit().putString(MagnetSourceS, keys.toJsonString()).apply()
    }
}
