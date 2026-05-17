package me.jbusdriver.modern.domain.model

/**
 * 分页信息数据类，描述当前页、下一页及可用页码列表。
 *
 * 职责：封装从 HTML 分页组件解析出的分页状态，用于 UI 层判断是否显示"加载更多"。
 *
 * 使用场景：[MoviePageResult] 中携带分页信息，ViewModel 根据此决定是否允许翻页。
 *
 * 线程：纯数据类，无线程限制。
 */
data class PageInfo(
    /** 当前页码。 */
    val activePage: Int = 0,
    /** 下一页页码，与 activePage 相等表示无下一页。 */
    val nextPage: Int = 0,
    /** 所有可用页码列表，用于分页控件展示。 */
    val referPages: List<Int> = emptyList()
)

/** 是否还有下一页可加载。 */
val PageInfo.hasNext: Boolean
    inline get() = activePage < nextPage

/**
 * 影片列表分页结果，包含分页信息和影片数据。
 *
 * 职责：将分页元数据 ([PageInfo]) 与业务数据 ([movies]) 组合为一个完整的结果对象。
 *
 * 使用场景：[MovieRepository]、[SearchRepository] 的列表加载方法返回此类型，
 * ViewModel 将其分发到 UI 层展示。
 *
 * 线程：纯数据类，无线程限制。
 */
data class MoviePageResult(
    /** 分页信息。 */
    val pageInfo: PageInfo,
    /** 当前页的影片列表。 */
    val movies: List<Movie>,
    /** 筛选信息（磁力数量与总数），仅在筛选模式下有值。 */
    val filterInfo: MovieFilterInfo? = null
)

/**
 * 影片筛选信息，包含当前筛选条件下的磁力影片数和影片总数。
 *
 * 职责：封装从列表页筛选提示栏解析出的统计信息。
 *
 * 使用场景：[MoviePageResult] 中携带筛选信息，UI 层用于展示筛选状态。
 *
 * 线程：纯数据类，无线程限制。
 */
data class MovieFilterInfo(
    /** 已有磁力链接的影片数量。 */
    val magnetCount: Int,
    /** 全部影片数量。 */
    val totalCount: Int,
    /** 面包屑名称，如 "ダイナナ"。 */
    val breadcrumbName: String? = null,
    /** 面包屑类型名，如 "導演"、"演員"、"類別"。 */
    val breadcrumbType: String? = null
)

/**
 * 演员详情数据类，包含演员的基本信息。
 *
 * 职责：封装从演员详情页解析出的姓名、头像 URL 和简介信息列表。
 *
 * 使用场景：[MovieRepository.loadActressDetail] 返回此类型，
 * UI 层用于展示演员详情页面。
 *
 * 线程：纯数据类，无线程限制。
 */
data class ActressDetail(
    /** 演员姓名。 */
    val name: String,
    /** 演员头像 URL。 */
    val avatar: String,
    /** 演员简介信息列表（如身高、罩杯等）。 */
    val info: List<String>
)
