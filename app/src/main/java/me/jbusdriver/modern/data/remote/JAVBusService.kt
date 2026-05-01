package me.jbusdriver.modern.data.remote

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.JBus
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * 职责：JAVBus 网站 API 的 Retrofit 接口定义
 *
 * 使用场景：通过 Retrofit 获取网站 HTML 页面内容
 * 线程：suspend 方法由 Retrofit 在后台线程执行
 */
interface JAVBusService {

    /**
     * 获取指定 URL 的 HTML 内容
     *
     * @param url 完整的页面 URL
     * @param existmag 磁力链接过滤参数，"all" 显示全部，空字符串仅显示有磁力的
     * @return HTML 字符串
     */
    @GET
    suspend fun get(@Url url: String, @Header("existmag") existmag: String = ""): String

    companion object {
        /** 默认站点 URL */
        var defaultFastUrl = "https://www.javbus.com"

        /** 欧美站点 URL */
        var defaultXyzUrl = "https://www.javbus.one"

        /** 欧美站点的域名后缀集合 */
        val xyzHostDomains = mutableSetOf<String>().apply {
            this.add(".one")
        }

        /** 当前活跃的 Service 实例 */
        var INSTANCE = getInstance(defaultFastUrl)

        /**
         * 获取或创建指定 URL 的 JAVBusService 实例
         *
         * 通过 JBus.JBusServices 缓存，同一 URL 复用同一 Retrofit 实例
         *
         * @param source 站点 baseUrl
         * @return JAVBusService 实例
         */
        fun getInstance(source: String): JAVBusService {
            return JBus.JBusServices.getOrPut(source) {
                createService(source)
            }.apply {
                KLog.d("instances : ${JBus.JBusServices}, defaultFastUrl : $defaultFastUrl")
            }
        }

        private fun createService(url: String) =
            NetClient.getRetrofit("${url.trimEnd('/')}/").create(JAVBusService::class.java)
    }
}
