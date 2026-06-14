package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.jbusdriver.R
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.components.MovieFilterBar
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    modifier: Modifier = Modifier,
    dataSourceType: DataSourceType? = DataSourceType.CENSORED,
    active: Boolean = true,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    viewModel: MovieListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isGrid by hiltViewModel<UiPrefsViewModel>().store.isGrid.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val refreshLabel = stringResource(R.string.refresh)
    val isAtTop by remember(isGrid) {
        derivedStateOf {
            if (isGrid == true) {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 20
            } else {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
            }
        }
    }

    LaunchedEffect(isAtTop) {
        viewModel.setAtTopForFreshUpdates(isAtTop)
    }

    LaunchedEffect(dataSourceType, active) {
        if (active && dataSourceType != null) {
            viewModel.setDataSourceType(dataSourceType)
        }
    }

    // 从后台恢复时触发 revalidate
    LifecycleResumeEffect(active) {
        if (active && uiState.movies.isNotEmpty()) {
            viewModel.revalidate()
        }
        onPauseOrDispose { }
    }

    val refreshMessageRes = uiState.refreshMessage
    val refreshMessage = refreshMessageRes?.let { stringResource(it) }
    LaunchedEffect(refreshMessageRes) {
        if (refreshMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = refreshMessage,
                actionLabel = refreshLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.applyPendingFreshResult()
                scope.launch {
                    if (isGrid == true) gridState.animateScrollToItem(0)
                    else listState.animateScrollToItem(0)
                }
            }
            viewModel.consumeRefreshMessage()
        }
    }

    PullToRefreshBox(
        isRefreshing = (uiState.isRefreshing || uiState.isFilterSwitching) && uiState.movies.isNotEmpty(),
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
                    message = stringResource(uiState.error ?: R.string.load_failed),
                    onRetry = { viewModel.refresh() }
                )
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
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
                        header = filterBar,
                        gridState = gridState,
                        listState = listState
                    )

                    if (uiState.isRevalidating && uiState.movies.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    ThemedSnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
