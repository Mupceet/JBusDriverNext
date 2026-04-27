package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.MovieUiModel

@Composable
fun LinkMovieListScreen(
    linkUrl: String,
    title: String = "",
    onMovieClick: (MovieUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: LinkMovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(linkUrl) {
        viewModel.setLink(linkUrl)
    }

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null && uiState.movies.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "加载失败", color = Color.Red)
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

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                    MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
