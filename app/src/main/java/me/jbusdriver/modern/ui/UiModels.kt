package me.jbusdriver.modern.ui

import androidx.compose.runtime.Immutable
import me.jbusdriver.modern.domain.model.Magnet
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDetail

/**
 * 职责：UI 层的 Immutable 数据模型，从 domain model 转换而来
 *
 * 使用场景：ViewModel 将 domain model 转换为 UiModel 后暴露给 Composable，
 * 确保状态不可变，Compose 可正确进行 recomposition 比较
 */

/** 电影列表项的 UI 模型 */
@Immutable
data class MovieUiModel(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    val link: String,
    val tags: List<String> = emptyList()
)

/** 电影详情页的 UI 模型 */
@Immutable
data class MovieDetailUiModel(
    val title: String,
    val content: String,
    val cover: String,
    val headers: List<HeaderUiModel>,
    val genres: List<GenreUiModel>,
    val actresses: List<ActressUiModel>,
    val imageSamples: List<ImageSampleUiModel>,
    val relatedMovies: List<MovieUiModel>
)

/** 详情页信息行的 UI 模型 */
@Immutable
data class HeaderUiModel(val name: String, val value: String, val link: String = "")

/** 类别标签的 UI 模型 */
@Immutable
data class GenreUiModel(val name: String, val link: String)

/** 演员的 UI 模型 */
@Immutable
data class ActressUiModel(val name: String, val avatar: String, val link: String)

/** 截图画册的 UI 模型 */
@Immutable
data class ImageSampleUiModel(val title: String, val thumb: String, val image: String)

/** 磁力链接的 UI 模型 */
@Immutable
data class MagnetUiModel(val name: String, val size: String, val date: String, val link: String)

// region Domain → UI 转换扩展

/** Movie domain model → UI model */
fun Movie.toUiModel() = MovieUiModel(title, imageUrl, code, date, link, tags.orEmpty())

/**
 * MovieDetail domain model → UI model
 *
 * 处理逻辑：
 * - 过滤掉"類別"信息行（改用 genres 列表展示）
 * - "描述"行去掉番号前缀，只保留描述文本
 */
fun MovieDetail.toUiModel(): MovieDetailUiModel {
    val code = headers.firstOrNull { it.name == "識別碼" }?.value.orEmpty()
    return MovieDetailUiModel(
        title = title,
        content = content,
        cover = cover,
        headers = headers
            .filter { it.name != "類別" }
            .map {
                if (it.name == "描述") HeaderUiModel("描述", title.removePrefix(code).trim())
                else HeaderUiModel(it.name, it.value, it.link)
            },
        genres = genres.map { GenreUiModel(it.name, it.link) },
        actresses = actress.map { ActressUiModel(it.name, it.avatar, it.link) },
        imageSamples = imageSamples.map { ImageSampleUiModel(it.title, it.thumb, it.image) },
        relatedMovies = relatedMovies.map { it.toUiModel() }
    )
}

/** Magnet → UI model */
fun Magnet.toUiModel() = MagnetUiModel(name, size, date, link)

/** ActressInfo → ActressUiModel */
fun ActressInfo.toActressUiModel() = ActressUiModel(name, avatar, link)
// endregion

/** 演员详情页的 UI 模型 */
@Immutable
data class ActressDetailUiModel(
    val name: String,
    val avatar: String,
    val info: List<String>
)
