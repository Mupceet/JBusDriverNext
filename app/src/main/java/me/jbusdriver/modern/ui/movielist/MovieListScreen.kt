package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.UserMessage
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieFilterBar
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

/**
 * 电影列表页 Route：负责获取 ViewModel、收集状态并把用户操作转成 ViewModel 调用。
 * [MovieListScreen] 只接收状态与回调，不感知 ViewModel，便于预览与测试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListRoute(
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
    val downloadedCodes by viewModel.downloadedCodes.collectAsStateWithLifecycle()
    val uiPrefsState by hiltViewModel<UiPrefsViewModel>().uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dataSourceType, active, uiPrefsState.defaultShowAll) {
        viewModel.setDefaultShowAll(uiPrefsState.defaultShowAll)
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

    MovieListScreen(
        state = uiState,
        downloadedCodes = downloadedCodes,
        isGrid = uiPrefsState.isGrid,
        messages = viewModel.messages,
        modifier = modifier,
        onMovieClick = onMovieClick,
        compact = compact,
        isCollected = isCollected,
        onToggleCollect = onToggleCollect,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onToggleShowAll = viewModel::toggleShowAll,
        onRetry = viewModel::refresh,
        onAtTopChange = viewModel::setAtTopForFreshUpdates,
        onApplyPendingFresh = viewModel::applyPendingFreshResult
    )
}

/**
 * 电影列表页的无状态 Screen：只根据 [state] 渲染，并通过回调表达用户意图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieListScreen(
    state: MovieListUiState,
    downloadedCodes: Set<String>,
    isGrid: Boolean,
    messages: SharedFlow<UserMessage>,
    modifier: Modifier = Modifier,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    compact: Boolean = false,
    isCollected: ((MovieUiModel) -> Boolean)? = null,
    onToggleCollect: ((MovieUiModel) -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onToggleShowAll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAtTopChange: (Boolean) -> Unit = {},
    onApplyPendingFresh: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val refreshLabel = stringResource(R.string.refresh)
    val isAtTop by remember(isGrid) {
        derivedStateOf {
            if (isGrid) {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 20
            } else {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
            }
        }
    }

    LaunchedEffect(isAtTop) {
        onAtTopChange(isAtTop)
    }

    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.format(context),
                actionLabel = if (message.resId == R.string.new_data_available) refreshLabel else null,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                onApplyPendingFresh()
                scope.launch {
                    if (isGrid) gridState.animateScrollToItem(0)
                    else listState.animateScrollToItem(0)
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = (state.isRefreshing || state.isFilterSwitching) && state.movies.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            state.isLoading && state.movies.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null && state.movies.isEmpty() -> {
                ErrorView(
                    message = stringResource(state.error ?: R.string.load_failed),
                    onRetry = onRetry
                )
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
                    val filterBar: (@Composable () -> Unit)? = state.filterInfo?.let { info ->
                        {
                            MovieFilterBar(
                                magnetCount = info.magnetCount,
                                totalCount = info.totalCount,
                                showAll = state.showAll,
                                onToggle = onToggleShowAll
                            )
                        }
                    }
                    MovieList(
                        movies = state.movies,
                        hasMore = state.hasMore,
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = onLoadMore,
                        onMovieClick = onMovieClick,
                        isGrid = isGrid,
                        compact = compact,
                        isCollected = isCollected,
                        onToggleCollect = onToggleCollect,
                        isDownloaded = { it.code.uppercase() in downloadedCodes },
                        header = filterBar,
                        gridState = gridState,
                        listState = listState
                    )

                    if (state.isRevalidating && state.movies.isNotEmpty()) {
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
