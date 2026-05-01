package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.data.magnet.loaders.MagnetLoaders
import org.json.JSONArray
import org.json.JSONObject

/**
 * 磁力链接加载器管理器，提供统一的磁力搜索 API。
 *
 * 职责：作为磁力加载器的门面（Facade），将具体的加载器实现细节对上层屏蔽，
 * 通过 loader 名称查找并委托给对应的 [IMagnetLoader] 实现。
 *
 * 使用场景：详情页点击"获取磁力"后，UI 层通过此管理器获取可用加载器列表、
 * 执行磁力搜索、提取具体磁力链接地址。
 *
 * 线程：[getMagnets] 和 [fetchMagLink] 底层涉及网络请求，必须在后台线程调用。
 */
object MagnetManager {

    /**
     * 根据名称获取指定的磁力加载器。
     *
     * @param name 加载器标识键，对应 [MagnetLoaders.Loaders] 的 key
     * @return 匹配的加载器实例，未找到时返回 null
     */
    fun getLoader(name: String): IMagnetLoader? {
        return MagnetLoaders.Loaders[name]
    }

    /**
     * 获取所有已注册的磁力加载器。
     *
     * @return 加载器名称到实例的映射
     */
    fun getAllLoaders(): Map<String, IMagnetLoader> {
        return MagnetLoaders.Loaders
    }

    /**
     * 使用指定加载器搜索磁力链接并返回 JSON 数组字符串。
     *
     * @param loader 加载器标识键
     * @param key 搜索关键词或 URL
     * @param page 页码
     * @return 磁力列表的 JSON 数组字符串，加载器不存在时返回空数组
     */
    fun getMagnets(loader: String, key: String, page: Int): String {
        return JSONArray(
            MagnetLoaders.Loaders[loader]?.loadMagnets(key, page)
                ?: emptyList<JSONObject>()
        ).toString()
    }

    /**
     * 获取所有已注册加载器的名称列表。
     *
     * @return 加载器名称列表
     */
    fun getLoaderKeys(): List<String> {
        return MagnetLoaders.Loaders.keys.toList()
    }

    /**
     * 使用指定加载器从详情页 URL 提取磁力链接地址。
     *
     * @param magnetLoaderKey 加载器标识键
     * @param url 详情页 URL
     * @return 磁力链接地址（magnet:?xt=urn:btih:...），加载器不存在时返回空字符串
     */
    fun fetchMagLink(magnetLoaderKey: String, url: String): String {
        return MagnetLoaders.Loaders[magnetLoaderKey]?.fetchMagnetLink(url) ?: ""
    }

    /**
     * 检查指定加载器是否还有下一页结果。
     *
     * @param magnetLoaderKey 加载器标识键
     * @return 是否有下一页，加载器不存在时返回 false
     */
    fun hasNext(magnetLoaderKey: String): Boolean {
        return MagnetLoaders.Loaders[magnetLoaderKey]?.hasNexPage ?: false
    }
}
