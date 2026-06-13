package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
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
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost

/**
 * 演员列表页面。
 *
 * 职责：以网格布局展示演员头像和名称，支持下拉刷新和自动加载更多。
 *
 * 使用场景：作为主页 Tab 内容区域使用，通过 [active] 参数控制是否激活数据加载。
 */
@Composable
fun ActressListScreen(
    dataSourceType: DataSourceType,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    header: (@Composable () -> Unit)? = null,
    viewModel: ActressListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val isAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 20
        }
    }

    LaunchedEffect(isAtTop) {
        viewModel.setAtTopForFreshUpdates(isAtTop)
    }

    LaunchedEffect(dataSourceType, active) {
        if (active) viewModel.setDataSourceType(dataSourceType)
    }

    // 从后台恢复时触发 revalidate
    LifecycleResumeEffect(active) {
        if (active && uiState.actresses.isNotEmpty()) {
            viewModel.revalidate()
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.refreshMessage) {
        uiState.refreshMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "刷新",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.applyPendingFreshActresses()
                scope.launch { gridState.animateScrollToItem(0) }
            }
            viewModel.consumeRefreshMessage()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing && uiState.actresses.isNotEmpty(),
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.actresses.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.actresses.isEmpty() -> {
                ErrorView(
                    message = stringResource(R.string.load_failed),
                    onRetry = { viewModel.refresh() }
                )
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    ActressGrid(
                        actresses = uiState.actresses,
                        hasMore = uiState.hasMore,
                        isLoadingMore = uiState.isLoadingMore,
                        onLoadMore = { viewModel.loadMore() },
                        onActressClick = onActressClick,
                        header = header,
                        gridState = gridState
                    )

                    if (uiState.isRevalidating && uiState.actresses.isNotEmpty()) {
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
