package me.jbusdriver.modern.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.ImageSample
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieUrl: String,
    onMovieClick: (Movie) -> Unit = {},
    onActressClick: (ActressInfo) -> Unit = {},
    onGenreClick: (Genre) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(movieUrl) {
        viewModel.loadDetail(movieUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(uiState.movieDetail?.title ?: "加载中...") })
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDetail(movieUrl) }) {
                            Text("重试")
                        }
                    }
                }
            }
            uiState.movieDetail != null -> {
                DetailContent(
                    detail = uiState.movieDetail!!,
                    padding = padding,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick,
                    onGenreClick = onGenreClick,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetail,
    padding: PaddingValues,
    onMovieClick: (Movie) -> Unit,
    onActressClick: (ActressInfo) -> Unit,
    onGenreClick: (Genre) -> Unit,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cover image
        AsyncImage(
            model = detail.cover,
            contentDescription = detail.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        // Title
        Text(
            text = detail.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Description
        if (detail.content.isNotBlank()) {
            Text(
                text = detail.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        // Headers (info rows)
        if (detail.headers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    detail.headers.forEach { header ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = header.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                text = header.value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Genres
        if (detail.genres.isNotEmpty()) {
            GenreSection(genres = detail.genres, onGenreClick = onGenreClick)
        }

        // Image Samples
        if (detail.imageSamples.isNotEmpty()) {
            ImageSampleSection(
                samples = detail.imageSamples,
                onImageClick = onImageClick
            )
        }

        // Actresses
        if (detail.actress.isNotEmpty()) {
            ActressSection(actresses = detail.actress, onActressClick = onActressClick)
        }

        // Related Movies
        if (detail.relatedMovies.isNotEmpty()) {
            RelatedMovieSection(movies = detail.relatedMovies, onMovieClick = onMovieClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreSection(genres: List<Genre>, onGenreClick: (Genre) -> Unit) {
    Column {
        Text("类别", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun ImageSampleSection(
    samples: List<ImageSample>,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column {
        Text("截图", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(samples.size) { index ->
                val sample = samples[index]
                AsyncImage(
                    model = sample.thumb,
                    contentDescription = sample.title,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val images = samples.map { it.image }
                            onImageClick(images, index)
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ActressSection(actresses: List<ActressInfo>, onActressClick: (ActressInfo) -> Unit) {
    Column {
        Text("演员", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(actresses) { actress ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    AsyncImage(
                        model = actress.avatar,
                        contentDescription = actress.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = actress.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedMovieSection(movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    Column {
        Text("推荐", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(movies) { movie ->
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onMovieClick(movie) }
                ) {
                    AsyncImage(
                        model = movie.imageUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = movie.code,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
