package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.ui.ActressDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.RouteLinkMovies
import me.jbusdriver.modern.ui.components.ActressAvatar
import me.jbusdriver.modern.ui.components.CollectButton
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieFilterBar
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ShareButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

/**
 * 关联链接影片列表页面。
 *
 * 职责：展示某个演员或类别关联的影片列表。当 [type] 为 "actress" 时，顶部会展示演员详情卡片
 * （头像、名称和附加信息），并提供收藏/取消收藏演员的功能。支持下拉刷新和自动加载更多。
 *
 * 使用场景：作为 Navigation 图中的一个目标页面，在用户点击演员头像或类别标签时导航至此。
 * 页面标题根据 [type] 和 [title] 自动生成（如 "演员: XXX" 或 "类别: XXX"）。
 *
 * @param linkUrl 关联链接的 URL，用于加载对应的影片列表
 * @param title 页面标题，用于 TopAppBar 展示
 * @param type 链接类型，"actress" 表示演员，"genre" 表示类别，其他值显示原始标题
 * @param avatarUrl 演员头像 URL，仅在演员类型时使用
 * @param onMovieClick 点击影片条目时的回调
 * @param onBack 返回上一页的回调
 * @param viewModel 关联影片列表的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMovieListScreen(
    linkUrl: String,
    title: String = "",
    type: String = "",
    avatarUrl: String = "",
    censorType: String? = null,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    viewModel: LinkMovieListViewModel = hiltViewModel<LinkMovieListViewModel, LinkMovieListViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(
                RouteLinkMovies(
                    linkUrl,
                    title,
                    type,
                    avatarUrl
                )
            )
        }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiPrefsState by hiltViewModel<UiPrefsViewModel>().uiState.collectAsStateWithLifecycle()
    val isGrid = uiPrefsState.isGrid
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
        viewModel.setAtTopForFreshUpdates(isAtTop)
    }

    LaunchedEffect(linkUrl) {
        viewModel.setLink(linkUrl, type, avatarUrl)
    }

    // 从后台恢复时触发 revalidate
    LifecycleResumeEffect(linkUrl) {
        if (uiState.movies.isNotEmpty()) {
            viewModel.revalidate()
        }
        onPauseOrDispose { }
    }

    val refreshActionLabel = stringResource(R.string.refresh)
    val refreshMessageRes = uiState.refreshMessage
    val refreshMessage = refreshMessageRes?.let { stringResource(it) }
    LaunchedEffect(refreshMessageRes) {
        if (refreshMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = refreshMessage,
                actionLabel = refreshActionLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.applyPendingFreshResult()
                scope.launch {
                    if (isGrid) gridState.animateScrollToItem(0)
                    else listState.animateScrollToItem(0)
                }
            }
            viewModel.consumeRefreshMessage()
        }
    }

    val displayTitle = when (val rt = uiState.resolvedTitle) {
        is ResolvedTitle.Actress -> stringResource(R.string.actress_type, rt.name)
        is ResolvedTitle.Genre -> stringResource(R.string.genre_type, rt.name)
        null -> when {
            title.isNotBlank() && type == "actress" -> stringResource(R.string.actress_type, title)
            title.isNotBlank() && type == "genre" -> stringResource(R.string.genre_type, title)
            else -> stringResource(R.string.loading)
        }
    }

    Scaffold(
        snackbarHost = {
            ThemedSnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (linkUrl.isNotBlank()) {
                        val shareText = buildString {
                            append(displayTitle)
                            append("\n")
                            append(linkUrl)
                        }
                        ShareButton(text = shareText)
                    }
                    if (type == "actress" && uiState.actressHeader.detail != null) {
                        CollectButton(
                            isCollected = uiState.actressHeader.isCollected,
                            onToggle = { viewModel.toggleActressCollect() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing || uiState.isFilterSwitching,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

                        val header: (@Composable () -> Unit)? = when {
                            type == "actress" && filterBar != null -> {
                                {
                                    Column {
                                        val actress = uiState.actressHeader.detail
                                        val actressError = uiState.actressHeader.error
                                        when {
                                            actress != null -> ActressDetailCard(actress)
                                            uiState.actressHeader.isLoading -> ActressDetailLoadingPlaceholder()
                                            actressError != null -> ActressDetailErrorCard(
                                                actressError
                                            )
                                        }
                                        filterBar()
                                    }
                                }
                            }

                            type == "actress" -> {
                                {
                                    val actress = uiState.actressHeader.detail
                                    val actressError = uiState.actressHeader.error
                                    when {
                                        actress != null -> ActressDetailCard(actress)
                                        uiState.actressHeader.isLoading -> ActressDetailLoadingPlaceholder()
                                        actressError != null -> ActressDetailErrorCard(actressError)
                                    }
                                }
                            }

                            filterBar != null -> filterBar
                            else -> null
                        }

                        MovieList(
                            movies = uiState.movies,
                            hasMore = uiState.hasMore,
                            isLoadingMore = uiState.isLoadingMore,
                            onLoadMore = { viewModel.loadMore() },
                            onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                            isGrid = isGrid,
                            modifier = Modifier.fillMaxSize(),
                            header = header,
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
                    }
                }
            }
        }
    }
}
