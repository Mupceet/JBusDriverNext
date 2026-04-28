package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.modern.ui.ActressDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMovieListScreen(
    linkUrl: String,
    title: String = "",
    type: String = "",
    avatarUrl: String = "",
    onMovieClick: (MovieUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: LinkMovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(linkUrl) {
        viewModel.setLink(linkUrl, type, avatarUrl)
    }

    val displayTitle = when {
        title.isNotBlank() && type == "actress" -> "演员: $title"
        title.isNotBlank() && type == "genre" -> "类别: $title"
        title.isNotBlank() -> title
        else -> "影片列表"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (type == "actress" && uiState.actressDetail != null) {
                        IconButton(onClick = {
                            viewModel.toggleActressCollect()
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
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.movies.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val listState = rememberLazyListState()

                LaunchedEffect(listState, uiState.hasMore) {
                    snapshotFlow {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        lastVisible >= totalItems - 3
                    }.collect { nearEnd ->
                        if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Actress detail header
                    if (type == "actress") {
                        val actress = uiState.actressDetail
                        if (actress != null) {
                            item(key = "actress_header") {
                                ActressDetailCard(actress)
                            }
                        }
                    }

                    itemsIndexed(
                        uiState.movies,
                        key = { index, movie -> "${index}_${movie.link}" }
                    ) { _, movie ->
                        MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActressDetailCard(actress: ActressDetailUiModel) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = actress.avatar,
                contentDescription = actress.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.CenterVertically)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actress.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                actress.info.forEach { infoLine ->
                    Text(
                        text = infoLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
