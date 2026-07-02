package me.jbusdriver.modern.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.components.AppAsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.ImageSampleUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressAvatar

/**
 * 影片类别标签区域。
 *
 * 以 FlowRow 形式展示影片所属的所有类别标签，每个标签可点击跳转。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenreSection(genres: List<GenreUiModel>, onGenreClick: (GenreUiModel) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.genre), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            genres.forEach { genre ->
                AssistChip(
                    onClick = { onGenreClick(genre) },
                    label = { Text(genre.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

/**
 * 影片截图预览区域。
 *
 * 以水平滚动列表展示影片的截图缩略图，点击任意截图可进入全屏图片查看器。
 */
@Composable
internal fun ImageSampleSection(
    samples: List<ImageSampleUiModel>,
    allImages: List<String>,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.screenshot),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(samples.size) { index ->
                val sample = samples[index]
                AppAsyncImage(
                    model = sample.thumb,
                    contentDescription = sample.title,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onImageClick(allImages, index + 1)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/**
 * 演员头像列表区域。
 *
 * 以水平滚动列表展示影片关联的演员头像和名称，点击可跳转到演员详情。
 */
@Composable
internal fun ActressSection(
    actresses: List<ActressUiModel>,
    onActressClick: (ActressUiModel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.actresses),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(actresses.size) { index ->
                val actress = actresses[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    ActressAvatar(
                        avatarUrl = actress.avatar,
                        contentDescription = actress.name,
                        size = 64.dp,
                        onClick = { onActressClick(actress) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }
    }
}

/**
 * 推荐影片区域。
 *
 * 以水平滚动列表展示与当前影片相关的推荐影片，包含封面和标题。
 */
@Composable
internal fun RelatedMovieSection(movies: List<MovieUiModel>, onMovieClick: (MovieUiModel) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.related),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(movies.size) { index ->
                val movie = movies[index]
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onMovieClick(movie) }
                ) {
                    AppAsyncImage(
                        model = movie.imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = movie.code + " " + movie.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
