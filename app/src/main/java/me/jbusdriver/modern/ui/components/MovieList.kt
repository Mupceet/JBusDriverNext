package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 可复用的影片列表组件。
 *
 * @param isGrid false = LazyColumn 列表模式, true = LazyVerticalGrid 网格模式
 */
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    modifier: Modifier = Modifier,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    isGrid: Boolean? = null,
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    isDownloaded: ((MovieUiModel) -> Boolean)? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState()
) {
    val useGrid = isGrid ?: false
    if (useGrid) {
        val scope = rememberCoroutineScope()
        val showScrollToTop = rememberScrollToTopVisibility(gridState)

        LaunchedEffect(gridState) {
            snapshotFlow {
                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = gridState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 95.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (header != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { header() }
                }
                itemsIndexed(
                    movies,
                    key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                    MovieGridItem(
                        movie = movie,
                        onClick = { onMovieClick(movie, null) },
                        isDownloaded = isDownloaded?.invoke(movie) == true
                    )
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                if (!hasMore && movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_more),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (footer != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { footer() }
                }
            }

            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            )
        }
    } else {
        val scope = rememberCoroutineScope()
        val showScrollToTop = rememberScrollToTopVisibility(listState)

        LaunchedEffect(listState) {
            snapshotFlow {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = listState.layoutInfo.totalItemsCount
                lastVisible >= totalItems - 3
            }.collect { nearEnd ->
                if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                if (header != null) {
                    item { header() }
                }
                itemsIndexed(
                    movies,
                    key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                    if (compact) {
                        CompactMovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, null) },
                            isCollected = isCollected?.invoke(movie) == true,
                            onToggleCollect = if (onToggleCollect != null) {
                                { onToggleCollect(movie) }
                            } else null
                        )
                    } else {
                        MovieItem(
                            movie = movie,
                            onClick = { onMovieClick(movie, null) },
                            isDownloaded = isDownloaded?.invoke(movie) == true
                        )
                    }
                }
                if (isLoadingMore) {
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
                if (!hasMore && movies.isNotEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_more),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (footer != null) {
                    item { footer() }
                }
            }

            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            )
        }
    }
}

/**
 * 影片列表中的单个影片条目卡片（横排，左图右文）。
 */
