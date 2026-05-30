package me.jbusdriver.modern.data.db

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.data.db.entity.History
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.parser.stripToPath
import me.jbusdriver.modern.data.parser.wrapImage
import me.jbusdriver.modern.domain.model.*

/**
 * 获取 [ILink] 实例对应的数据库类型标识。
 *
 * 职责：将运行时类型映射为整型数据库类型常量，用于 Room 数据库的 type 字段。
 *
 * 使用场景：收藏入库时（[convertDBItem]）确定 [LinkItem.dbType] 值。
 *
 * @return 对应的数据库类型常量（[MovieDBType] 等）
 * @throws IllegalStateException 如果遇到未知的 [ILink] 实现类
 */
val ILink.DBtype: Int
    inline get() = when (this) {
        is Movie -> MovieDBType
        is ActressInfo -> ActressDBType
        is Header -> HeaderDBType
        is Genre -> GenreDBType
        is SearchLink -> SearchLinkDBType
        is PageLink -> PageLinkDBType
        else -> error(" $this has no matched class for des")
    }

/**
 * 获取 [ILink] 实例的唯一标识 key。
 *
 * 职责：为每个数据项生成用于去重的唯一 key。
 *       [SearchLink] 使用查询关键词作为 key，其他类型使用链接路径。
 *
 * 使用场景：收藏数据库中 [LinkItem.key] 字段的值，用于去重判断。
 *
 * @return 唯一标识字符串
 */
val ILink.uniqueKey: String
    inline get() = when (this) {
        is SearchLink -> query
        else -> link.urlPath
    }

/**
 * 将 [ILink] 实例转换为数据库实体 [LinkItem]。
 *
 * 职责：将领域模型转换为 Room 数据库实体，包含序列化的 JSON 数据和分类信息。
 *
 * 使用场景：用户点击"收藏"按钮时，将当前数据项转换后插入收藏数据库。
 *
 * @return 可直接插入数据库的 [LinkItem] 实体
 */
fun ILink.convertDBItem(): LinkItem {
    val stripped = stripUrlFields(this)
    return LinkItem(
        dbType = this.DBtype,
        createTime = System.currentTimeMillis(),
        key = stripped.uniqueKey,
        jsonStr = stripped.toJsonString(),
        categoryId = when {
            this.categoryId > 0 -> categoryId
            else -> AllFirstParentDBCategoryGroup[this.DBtype]?.id ?: LinkCategory.id ?: -1
        }
    )
}

/**
 * 将 [History] 实体反序列化为对应的 [ILink] 领域对象。
 *
 * @return 反序列化后的领域对象
 * @throws IllegalStateException 当 [History.dbType] 不匹配任何已知类型时
 */
fun History.toILink(): ILink = when (dbType) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$dbType : $jsonStr has no matched class")
}

/**
 * 将 [LinkItem] 实体反序列化为对应的 [ILink] 领域对象。
 * 反序列化失败时返回 null 并记录警告日志，避免因单条数据损坏导致列表崩溃。
 *
 * @return 反序列化后的领域对象，失败时返回 null
 */
fun LinkItem.toILink(): ILink? {
    return kotlin.runCatching {
        val raw = deserializeLink(dbType, jsonStr)
        val link = restoreUrlFields(raw)
        link.categoryId = this.categoryId
        link
    }.onFailure {
        KLog.w("error toILink : $this")
    }.getOrNull()
}

/**
 * 根据 [type] 将 JSON 字符串反序列化为对应的 [ILink] 实现。
 *
 * @param type 数据类型标识
 * @param jsonStr JSON 序列化字符串
 * @return 反序列化后的领域对象
 * @throws IllegalStateException 当类型不匹配任何已知类型时
 */
private fun deserializeLink(type: Int, jsonStr: String): ILink = when (type) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$type : $jsonStr has no matched class")
}

private fun stripUrlFields(link: ILink): ILink {
    return when (link) {
        is Movie -> link.copy(
            link = link.link.stripToPath(),
            imageUrl = link.imageUrl.stripToPath()
        )
        is ActressInfo -> link.copy(
            link = link.link.stripToPath(),
            avatar = link.avatar.stripToPath()
        )
        is Header -> link.copy(link = link.link.stripToPath())
        is Genre -> link.copy(link = link.link.stripToPath())
        else -> link
    }
}

private fun restoreUrlFields(link: ILink): ILink {
    val baseUrl = NetClient.siteConfig.baseUrl
    return when (link) {
        is Movie -> link.copy(
            link = link.link.wrapImage(baseUrl),
            imageUrl = link.imageUrl.wrapImage(baseUrl)
        )
        is ActressInfo -> link.copy(
            link = link.link.wrapImage(baseUrl),
            avatar = link.avatar.wrapImage(baseUrl)
        )
        is Header -> link.copy(link = link.link.wrapImage(baseUrl))
        is Genre -> link.copy(link = link.link.wrapImage(baseUrl))
        else -> link
    }
}
