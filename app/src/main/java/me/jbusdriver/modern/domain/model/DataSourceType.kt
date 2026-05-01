package me.jbusdriver.modern.domain.model

/**
 * 数据源类型枚举，定义应用支持的所有内容分区及其 URL 路径前缀。
 *
 * 职责：将内容分区（有码、无码、欧美等）与 Tab 页面的 URL 路径前缀绑定，
 *       用于首页 Tab 加载对应分区的影片列表。
 *
 * 使用场景：首页 Tab 栏的每个标签对应一个 [DataSourceType]，ViewModel 根据类型
 *           拼接完整 URL 以请求影片列表数据。带 `_GENRE` / `_ACTRESSES` 后缀的
 *           类型用于加载对应的类别列表和女优列表页面。
 *
 * 线程：枚举为不可变单例，线程安全。
 *
 * @property key 显示给用户的中文标签
 * @property prefix 该分区首页的 URL 路径前缀，默认为 `"/"`
 */
enum class DataSourceType(val key: String, val prefix: String = "/") {
    /** 有码影片列表 */
    CENSORED("有碼", "/page/"),
    /** 有码类别列表（无分页路径） */
    GENRE("有碼類別"),
    /** 有码女优列表 */
    ACTRESSES("有碼女優"),

    /** 无码影片列表 */
    UNCENSORED("無碼", "/page/"),
    /** 无码类别列表 */
    UNCENSORED_GENRE("無碼類別"),
    /** 无码女优列表 */
    UNCENSORED_ACTRESSES("無碼女優"),

    /** 欧美影片列表 */
    XYZ("歐美", "/page/"),
    /** 欧美类别列表 */
    XYZ_GENRE("xyz/genre"),
    /** 欧美女优列表 */
    XYZ_ACTRESSES("xyz/actresses"),

    /** 高清分类标签 */
    GENRE_HD("高清"),
    /** 字幕分类标签 */
    Sub("字幕");
}
