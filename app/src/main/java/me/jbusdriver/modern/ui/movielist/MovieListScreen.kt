package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import me.jbusdriver.mvp.bean.Movie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    title: String = "电影列表",
    onMovieClick: (Movie) -> Unit = {},
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (uiState.movies.isEmpty() && !uiState.isLoading) {
            viewModel.loadFirstPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.movies.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error ?: "加载失败", color = Color.Red)
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
                        items(uiState.movies, key = { it.link }) { movie ->
                            MovieItem(
                                movie = movie,
                                onClick = { onMovieClick(movie) }
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        if (!uiState.hasMore && uiState.movies.isNotEmpty()) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "没有更多了",
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
