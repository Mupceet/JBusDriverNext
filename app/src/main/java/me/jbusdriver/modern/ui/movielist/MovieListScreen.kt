package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieFilterBar
import me.jbusdriver.modern.ui.components.MovieList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    dataSourceType: DataSourceType = DataSourceType.CENSORED,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    isGrid: Boolean = false,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dataSourceType, active) {
        if (active) {
            viewModel.setDataSourceType(dataSourceType)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing || uiState.isFilterSwitching,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.movies.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.movies.isEmpty() -> {
                ErrorView(
                    message = "載入失敗，請重試",
                    onRetry = { viewModel.refresh() }
                )
            }

            else -> {
                val filterBar: (@Composable () -> Unit)? = uiState.filterInfo?.let { info ->
                    {
                        MovieFilterBar(
                            magnetCount = info.magnetCount,
                            totalCount = info.totalCount,
                            showAll = uiState.showAll,
                            onToggle = { viewModel.toggleShowAll() }
                        )
                    }
                }
                val combinedHeader: (@Composable () -> Unit)? = when {
                    header != null && filterBar != null -> {
                        { header(); filterBar() }
                    }
                    header != null -> header
                    filterBar != null -> filterBar
                    else -> null
                }
                MovieList(
                    movies = uiState.movies,
                    hasMore = uiState.hasMore,
                    isLoadingMore = uiState.isLoadingMore,
                    onLoadMore = { viewModel.loadMore() },
                    onMovieClick = onMovieClick,
                    isGrid = isGrid,
                    compact = compact,
                    isCollected = isCollected,
                    onToggleCollect = onToggleCollect,
                    header = combinedHeader
                )
            }
        }
    }
}
