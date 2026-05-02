package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 影片列表页面。
 *
 * 职责：以垂直滚动列表展示影片数据，支持下拉刷新和自动加载更多（滚动到底部触发）。
 * 根据 [DataSourceType] 加载不同分类的影片数据。
 *
 * 使用场景：作为主页 Tab 内容区域使用，根据用户选择的数据源类型（有码/无码/欧美等）展示对应影片列表。
 * 通常嵌入到 [MainScreen] 的 Tab 页中，通过 [active] 参数控制是否激活数据加载。
 *
 * @param dataSourceType 数据源类型，决定加载哪个分类的影片
 * @param active 当前页面是否处于激活（可见）状态，仅在激活时加载数据
 * @param onMovieClick 点击影片条目时的回调
 * @param modifier 应用于根布局的 Modifier
 * @param viewModel 影片列表的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    dataSourceType: DataSourceType = DataSourceType.CENSORED,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dataSourceType, active) {
        if (active) {
            viewModel.setDataSourceType(dataSourceType)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.movies.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                val listState = rememberLazyListState()

                LaunchedEffect(listState, uiState.hasMore) {
                    snapshotFlow {
                        val lastVisible =
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        lastVisible >= totalItems - 3
                    }.collect { nearEnd ->
                        if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        uiState.movies,
                        key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
