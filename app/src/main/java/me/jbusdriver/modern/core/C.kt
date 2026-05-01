package me.jbusdriver.modern.core

import me.jbusdriver.modern.core.ACache

/**
 * 职责：全局常量定义，按功能分组
 *
 * 使用场景：缓存 key、时间常量等，避免在业务代码中硬编码字符串
 * 线程：无，纯常量定义
 */
object C {
    /**
     * 缓存相关常量
     *
     * - DAY/WEEK: 缓存过期时间基准值
     * - BUS_URLS: 可用站点 URL 列表的缓存 key
     */
    object Cache {
        const val DAY = ACache.TIME_DAY
        const val WEEK = ACache.TIME_DAY * 7
        const val BUS_URLS = "bus_urls"
    }
}
