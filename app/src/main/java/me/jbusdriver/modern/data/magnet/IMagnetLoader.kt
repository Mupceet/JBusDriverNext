package me.jbusdriver.modern.data.magnet

import me.jbusdriver.modern.KLog
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup

/**
 * 为 Jsoup [Connection] 添加通用 HTTP 请求头，模拟浏览器行为。
 * 设置 User-Agent、Accept-Encoding、Accept-Language 等，
 * 并启用重定向跟随以处理目标站点的 302 跳转。
 */
fun Connection.initHeaders(): Connection = this.userAgent(IMagnetLoader.USER_AGENT).followRedirects(true)
    .header("Accept-Encoding", "gzip, deflate, sdch")
    .header("Accept-Language", "zh-CN,zh;q=0.8")

/**
 * 磁力链接加载器接口，定义从磁力搜索站点获取磁力链接的通用契约。
 *
 * 职责：声明磁力搜索、链接提取、分页状态的核心方法，
 * 各磁力搜索站点的具体实现类需实现此接口。
 *
 * 使用场景：[MagnetManager] 通过此接口多态调用不同磁力搜索站点的加载逻辑；
 * UI 层通过 [MagnetManager] 间接使用，不直接依赖具体实现。
 *
 * 线程：[loadMagnets] 和 [fetchMagnetLink] 涉及网络请求，必须在后台线程执行。
 */
interface IMagnetLoader {

    /** 是否有下一页数据，由最后一次 [loadMagnets] 调用后更新。 */
    var hasNexPage: Boolean

    /**
     * 根据关键词和页码加载磁力链接列表。
     * 必须在后台线程调用。
     *
     * @param key 搜索关键词或目标 URL
     * @param page 页码（从 1 开始）
     * @return 磁力链接信息的 JSONObject 列表，每个对象包含 name、size、date、link 字段
     */
    fun loadMagnets(key: String, page: Int): List<JSONObject>

    /**
     * 从给定的 URL 页面提取磁力链接地址。
     * 默认实现返回空字符串，子类可按需覆盖。
     *
     * @param url 目标页面 URL
     * @return 磁力链接地址（magnet:?xt=urn:btih:...）
     */
    fun fetchMagnetLink(url: String): String = ""

    companion object {

        /**
         * 安全的 Jsoup GET 请求工具方法，自动添加请求头并处理异常。
         * 失败时记录警告日志并返回 null。
         *
         * @param url 目标 URL
         * @return 解析后的 Document，失败时返回 null
         */
        fun safeJsoupGet(url: String) = kotlin.runCatching {
            Jsoup.connect(url).initHeaders().cookies(emptyMap()).followRedirects(true).get()
        }.onFailure {
            KLog.w("Jsoup get url $url error :$it")
        }.onSuccess {
            KLog.d("Jsoup get url $url success ")
        }.getOrNull()

        /** 模拟 Chrome 浏览器的 User-Agent 字符串。 */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.67 Safari/537.36"

        /** 磁力链接的标准前缀。 */
        const val MagnetFormatPrefix = "magnet:?xt=urn:btih:"

    }

}
