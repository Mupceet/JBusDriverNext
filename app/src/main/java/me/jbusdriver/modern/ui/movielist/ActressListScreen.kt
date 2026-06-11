package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView

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

    LaunchedEffect(dataSourceType, active) {
        if (active) viewModel.setDataSourceType(dataSourceType)
    }

    // 从后台恢复时触发 revalidate
    LifecycleResumeEffect(Unit) {
        if (active && uiState.actresses.isNotEmpty()) {
            viewModel.revalidate()
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.refreshMessage) {
        uiState.refreshMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
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
                    message = "載入失敗，請重試",
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
                        header = header
                    )

                    if (uiState.isRevalidating && uiState.actresses.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}
