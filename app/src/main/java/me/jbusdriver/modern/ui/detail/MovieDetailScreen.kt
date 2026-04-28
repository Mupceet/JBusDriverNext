package me.jbusdriver.modern.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.ImageSampleUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.MagnetUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieUrl: String,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMagnetSheet by remember { mutableStateOf(false) }

    LaunchedEffect(movieUrl) {
        viewModel.loadDetail(movieUrl)
    }

    val detail = uiState.movieDetail
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "加载中...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = {
                            viewModel.toggleCollect()
                            val msg = if (!uiState.isCollected) "收藏成功" else "已取消收藏"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (uiState.isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (uiState.isCollected) "取消收藏" else "收藏",
                                tint = if (uiState.isCollected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDetail(movieUrl) }) { Text("重试") }
                    }
                }
            }
            detail != null -> {
                DetailContent(
                    detail = detail,
                    padding = padding,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick,
                    onGenreClick = onGenreClick,
                    onImageClick = onImageClick,
                    onMagnetClick = {
                        showMagnetSheet = true
                        viewModel.loadMagnets()
                    }
                )
            }
        }
    }

    if (showMagnetSheet) {
        MagnetBottomSheet(
            uiState = uiState,
            onLoadMore = { viewModel.loadMoreMagnets() },
            onDismiss = { showMagnetSheet = false }
        )
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetailUiModel,
    padding: PaddingValues,
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onMagnetClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val coverHeight = remember { mutableStateOf(0) }
    val code = detail.headers.firstOrNull()?.value ?: ""

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Cover image
        item(key = "cover") {
            AsyncImage(
                model = detail.cover,
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clickable { onImageClick(listOf(detail.cover), 0) }
                    .onSizeChanged { size -> coverHeight.value = size.height }
            )
        }

        // Headers info (skip description — rendered separately below)
        item(key = "headers") {
            val infoHeaders = detail.headers.filter { it.name != "描述" }
            if (infoHeaders.isNotEmpty()) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        infoHeaders.forEach { header ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
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
        }

        // Description (from headers where name == "描述")
        val descHeader = detail.headers.find { it.name == "描述" }
        if (descHeader != null && descHeader.value.isNotBlank()) {
            item(key = "description") {
                Text(
                    text = descHeader.value,
                    style = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).let {
                        MaterialTheme.typography.bodyMedium.copy(color = it)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Genres
        if (detail.genres.isNotEmpty()) {
            item(key = "genres") {
                GenreSection(genres = detail.genres, onGenreClick = onGenreClick)
            }
        }

        // Image Samples
        if (detail.imageSamples.isNotEmpty()) {
            item(key = "samples") {
                ImageSampleSection(
                    samples = detail.imageSamples,
                    onImageClick = onImageClick
                )
            }
        }

        // Actresses
        if (detail.actresses.isNotEmpty()) {
            item(key = "actresses") {
                ActressSection(actresses = detail.actresses, onActressClick = onActressClick)
            }
        }

        // Related Movies
        if (detail.relatedMovies.isNotEmpty()) {
            item(key = "related") {
                RelatedMovieSection(movies = detail.relatedMovies, onMovieClick = onMovieClick)
            }
        }

        // Magnet button
        item(key = "magnet") {
            Button(
                onClick = onMagnetClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("查看磁力链接")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreSection(genres: List<GenreUiModel>, onGenreClick: (GenreUiModel) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
    samples: List<ImageSampleUiModel>,
    onImageClick: (List<String>, Int) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("截图", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(samples.size) { index ->
                val sample = samples[index]
                AsyncImage(
                    model = sample.thumb,
                    contentDescription = sample.title,
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(4f / 3f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
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
private fun ActressSection(actresses: List<ActressUiModel>, onActressClick: (ActressUiModel) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("演员", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(actresses.size) { index ->
                val actress = actresses[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onActressClick(actress) }
                ) {
                    AsyncImage(
                        model = actress.avatar,
                        contentDescription = actress.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp)),
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
private fun RelatedMovieSection(movies: List<MovieUiModel>, onMovieClick: (MovieUiModel) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("推荐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(movies.size) { index ->
                val movie = movies[index]
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
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MagnetBottomSheet(
    uiState: MovieDetailUiState,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("磁力链接", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoadingMagnets && uiState.magnets.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.magnetsError != null && uiState.magnets.isEmpty() -> {
                    Text(uiState.magnetsError, color = MaterialTheme.colorScheme.error)
                }
                uiState.magnets.isEmpty() -> {
                    Text("暂无磁力链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                else -> {
                    uiState.magnets.forEach { magnet ->
                        MagnetItem(magnet = magnet, context = context)
                        HorizontalDivider()
                    }
                    if (uiState.isLoadingMagnets) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally))
                    } else if (uiState.hasMoreMagnets) {
                        Button(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Text("加载更多")
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MagnetItem(magnet: MagnetUiModel, context: Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (magnet.link.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("magnet", magnet.link))
                    Toast.makeText(context, "已复制磁力链接", Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = magnet.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (magnet.size.isNotBlank()) {
                Text(magnet.size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (magnet.date.isNotBlank()) {
                Text(magnet.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
