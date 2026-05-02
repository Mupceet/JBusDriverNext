package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.movielist.MovieItem

/**
 * 可复用的影片列表组件。
 *
 * 职责：以 LazyColumn 展示影片条目列表，支持加载更多和到底提示。
 *
 * 使用场景：被 MovieListScreen、CollectionListScreen、SearchScreen 的影片结果复用。
 *
 * @param movies 影片数据列表
 * @param hasMore 是否有更多数据可加载
 * @param isLoadingMore 是否正在加载更多
 * @param onLoadMore 滚动到底部时的加载更多回调
 * @param onMovieClick 点击影片的回调
 * @param modifier 应用于列表的 Modifier
 */
@Composable
fun MovieList(
    movies: List<MovieUiModel>,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onMovieClick: (MovieUiModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
            MovieItem(movie = movie, onClick = { onMovieClick(movie) })
        }
        if (isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!hasMore && movies.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("没有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
