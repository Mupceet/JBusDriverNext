package me.jbusdriver.modern.ui

import androidx.compose.runtime.Immutable
import me.jbusdriver.modern.data.magnet.Magnet
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.ImageSample
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDetail

@Immutable
data class MovieUiModel(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    val link: String
)

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

@Immutable
data class HeaderUiModel(val name: String, val value: String)

@Immutable
data class GenreUiModel(val name: String, val link: String)

@Immutable
data class ActressUiModel(val name: String, val avatar: String, val link: String)

@Immutable
data class ImageSampleUiModel(val title: String, val thumb: String, val image: String)

@Immutable
data class MagnetUiModel(val name: String, val size: String, val date: String, val link: String)

fun Movie.toUiModel() = MovieUiModel(title, imageUrl, code, date, link)

fun MovieDetail.toUiModel() = MovieDetailUiModel(
    title = title,
    content = content,
    cover = cover,
    headers = headers.map { HeaderUiModel(it.name, it.value) },
    genres = genres.map { GenreUiModel(it.name, it.link) },
    actresses = actress.map { ActressUiModel(it.name, it.avatar, it.link) },
    imageSamples = imageSamples.map { ImageSampleUiModel(it.title, it.thumb, it.image) },
    relatedMovies = relatedMovies.map { it.toUiModel() }
)

fun Magnet.toUiModel() = MagnetUiModel(name, size, date, link)

fun ActressInfo.toActressUiModel() = ActressUiModel(name, avatar, link)

@Immutable
data class ActressDetailUiModel(
    val name: String,
    val avatar: String,
    val info: List<String>
)
