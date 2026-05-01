package me.jbusdriver.modern.domain.model

/**
 * 搜索类型枚举，定义网站支持的所有搜索维度及其对应的 URL 路径模板。
 *
 * 职责：将搜索类型与 URL 路径格式绑定，使搜索功能只需选择枚举值即可生成正确的请求路径。
 *
 * 使用场景：搜索界面供用户选择搜索维度（影片、女优、导演等），
 *           选定后通过 [urlPathFormater] 结合关键词生成完整搜索 URL。
 *
 * 线程：枚举为不可变单例，线程安全。
 *
 * @property title 显示给用户的中文标签
 * @property urlPathFormater URL 路径模板，`%s` 为搜索关键词占位符
 */
enum class SearchType(val title: String, val urlPathFormater: String) {
    /** 有码影片搜索 */
    CENSORED("有碼影片", "/search/%s"),
    /** 无码影片搜索 */
    UNCENSORED("無碼影片", "/uncensored/search/%s"),
    /** 女优搜索，路径不同于普通搜索 */
    ACTRESS("女優", "/searchstar/%s"),
    /** 导演搜索，URL 附加 `DBtype=2` 参数 */
    DIRECTOR("導演", "/search/%s&DBtype=2"),
    /** 制作商搜索，URL 附加 `DBtype=3` 参数 */
    MAKER("製作商", "/search/%s&DBtype=3"),
    /** 发行商搜索，URL 附加 `DBtype=4` 参数 */
    PUBLISHER("發行商", "/search/%s&DBtype=4"),
    /** 系列搜索，URL 附加 `DBtype=5` 参数 */
    SERIES("系列", "/search/%s&DBtype=5")
}
