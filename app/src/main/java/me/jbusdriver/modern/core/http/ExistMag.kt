package me.jbusdriver.modern.core.http

/**
 * JavBus 磁力筛选 cookie（existmag）的共享契约。
 *
 * existmag 控制 JavBus 列表页返回的影片范围，是 per-request cookie：OkHttp 拦截器
 * （[NetClient]）和 WebView 会话（[DefaultHtmlClient]）两条获取路径都必须按当前加载模式
 * （[me.jbusdriver.modern.data.settings.MovieLoadMode]）写入对应值。
 *
 * 把"是否显示全部 → cookie 值"的映射抽成纯函数集中在此，避免两条路径各自硬编码
 * "mag"/"all" 字面量而产生不一致——历史上 WebView 路径只写了 "all"、漏掉 "mag"，
 * 就导致切回"仅磁力"时不生效（详见 [DefaultHtmlClient.fetchPage]）。
 */
internal const val EXIST_MAG_COOKIE = "existmag"

/**
 * 将"是否显示全部影片"映射为 existmag cookie 值：
 * `true` → `"all"`（全部），`false` → `"mag"`（仅磁力）。
 */
internal fun existMagCookieValue(showAll: Boolean): String = if (showAll) "all" else "mag"
