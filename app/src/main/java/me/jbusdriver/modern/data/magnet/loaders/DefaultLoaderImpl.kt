package me.jbusdriver.modern.data.magnet.loaders

import android.annotation.SuppressLint
import android.os.Looper
import android.webkit.URLUtil
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.magnet.IMagnetLoader
import org.json.JSONObject
import org.jsoup.Jsoup


private const val TAG = "DefaultLoaderImpl"

/**
 * 默认磁力链接加载器实现，通过 WebView 加载目标页面并解析磁力表格。
 *
 * 职责：接收目标 URL，使用 [WebViewHtmlContentLoader] 获取完整 HTML 内容，
 * 然后通过 Jsoup 解析 `#magnet-table` 表格提取磁力链接信息。
 *
 * 使用场景：作为 [MagnetLoaders] 中 "default" 键对应的加载器，
 * 由 [MagnetManager] 在用户点击获取磁力时调用。
 *
 * 线程：[loadMagnets] 必须在后台线程调用（内部通过 require 断言线程检查）。
 * WebView 操作在主线程执行（由 [WebViewHtmlContentLoader] 内部处理），
 * 通过 CountDownLatch 阻塞调用线程等待结果。
 */
@SuppressLint("JavascriptInterface")
class DefaultLoaderImpl : IMagnetLoader {

    /** 是否有下一页数据，当前实现默认为 false。 */
    override var hasNexPage: Boolean = false

    /**
     * 从目标 URL 加载磁力链接列表。
     *
     * 工作流程：
     * 1. 校验 URL 合法性及调用线程非主线程
     * 2. 通过 WebView 加载页面获取动态渲染后的 HTML
     * 3. 使用 Jsoup 解析 `#magnet-table` 表格
     * 4. 提取每行的名称、大小、日期、链接字段构造 JSONObject
     *
     * @param key 目标磁力搜索页面的 URL
     * @param page 页码（当前实现未使用分页）
     * @return 磁力链接信息的 JSONObject 列表
     * @throws IllegalArgumentException 如果 key 不是 HTTP/HTTPS URL 或在主线程调用
     */
    override fun loadMagnets(key: String, page: Int): List<JSONObject> {
        require(URLUtil.isHttpUrl(key) || URLUtil.isHttpsUrl(key)) { "需要为网络连接!" }
        require(Looper.getMainLooper() != Looper.myLooper()) { "需要在子线程执行!" }
        val content = WebViewHtmlContentLoader().startLoad(key)
        KLog.w("$TAG loadMagnets: $content")
        return Jsoup.parse(content).select("#magnet-table tr").asSequence()
            .drop(1).map {
                val contents = it.select("td")
                val link = it.select("a").attr("href").orEmpty()
                JSONObject().apply {
                    put("name", contents.getOrNull(0)?.text().orEmpty())
                    put("size", contents.getOrNull(1)?.text().orEmpty())
                    put("date", contents.getOrNull(2)?.text().orEmpty())
                    put("link", link)
                }
            }.toList()

    }


}
