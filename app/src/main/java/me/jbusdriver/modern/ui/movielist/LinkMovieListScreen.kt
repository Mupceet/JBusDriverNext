package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.RouteLinkMovies
import me.jbusdriver.modern.ui.UserMessage
import me.jbusdriver.modern.ui.components.CollectButton
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieFilterBar
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ShareButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

/**
 * 关联链接影片列表页 Route：负责获取 ViewModel、收集状态并把用户操作转成 ViewModel 调用。
 * [LinkMovieListScreen] 只接收状态与回调，不感知 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMovieListRoute(
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
    val downloadedCodes by viewModel.downloadedCodes.collectAsStateWithLifecycle()
    val uiPrefsState by hiltViewModel<UiPrefsViewModel>().uiState.collectAsStateWithLifecycle()

    LaunchedEffect(linkUrl, uiPrefsState.defaultShowAll) {
        // 先以默认筛选加载链接：链接变化时由 setLink 承担唯一一次加载，随后的
        // setDefaultShowAll 因 showAll 已一致而直接返回；仅默认值变化（同一链接）时，
        // setLink 提前返回，由 setDefaultShowAll 原地重载。
        viewModel.setLink(linkUrl, type, avatarUrl, uiPrefsState.defaultShowAll)
        viewModel.setDefaultShowAll(uiPrefsState.defaultShowAll)
    }

    // 从后台恢复时触发 revalidate
    LifecycleResumeEffect(linkUrl) {
        if (uiState.movies.isNotEmpty()) {
            viewModel.revalidate()
        }
        onPauseOrDispose { }
    }

    LinkMovieListScreen(
        state = uiState,
        downloadedCodes = downloadedCodes,
        isGrid = uiPrefsState.isGrid,
        messages = viewModel.messages,
        title = title,
        type = type,
        censorType = censorType,
        shareUrl = linkUrl,
        onMovieClick = onMovieClick,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onToggleShowAll = viewModel::toggleShowAll,
        onRetry = viewModel::refresh,
        onAtTopChange = viewModel::setAtTopForFreshUpdates,
        onToggleActressCollect = viewModel::toggleActressCollect,
        onApplyPendingFresh = viewModel::applyPendingFreshResult
    )
}

/**
 * 关联链接影片列表页的无状态 Screen：只根据 [state] 渲染，并通过回调表达用户意图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMovieListScreen(
    state: LinkMovieListUiState,
    downloadedCodes: Set<String>,
    isGrid: Boolean,
    messages: SharedFlow<UserMessage>,
    title: String,
    type: String,
    censorType: String?,
    shareUrl: String,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onToggleShowAll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAtTopChange: (Boolean) -> Unit = {},
    onToggleActressCollect: () -> Unit = {},
    onApplyPendingFresh: () -> Unit = {}
) {
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
        onAtTopChange(isAtTop)
    }

    val refreshActionLabel = stringResource(R.string.refresh)
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.format(context),
                actionLabel = if (message.resId == R.string.new_data_available) refreshActionLabel else null,
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

    val displayTitle = when (val rt = state.resolvedTitle) {
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
                    if (shareUrl.isNotBlank()) {
                        val shareText = buildString {
                            append(displayTitle)
                            append("\n")
                            append(shareUrl)
                        }
                        ShareButton(text = shareText)
                    }
                    if (type == "actress" && state.actressHeader.detail != null) {
                        CollectButton(
                            isCollected = state.actressHeader.isCollected,
                            onToggle = onToggleActressCollect
                        )
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing || state.isFilterSwitching,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

                        val header: (@Composable () -> Unit)? = when {
                            type == "actress" && filterBar != null -> {
                                {
                                    Column {
                                        val actress = state.actressHeader.detail
                                        val actressError = state.actressHeader.error
                                        when {
                                            actress != null -> ActressDetailCard(actress)
                                            state.actressHeader.isLoading -> ActressDetailLoadingPlaceholder()
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
                                    val actress = state.actressHeader.detail
                                    val actressError = state.actressHeader.error
                                    when {
                                        actress != null -> ActressDetailCard(actress)
                                        state.actressHeader.isLoading -> ActressDetailLoadingPlaceholder()
                                        actressError != null -> ActressDetailErrorCard(actressError)
                                    }
                                }
                            }

                            filterBar != null -> filterBar
                            else -> null
                        }

                        MovieList(
                            movies = state.movies,
                            hasMore = state.hasMore,
                            isLoadingMore = state.isLoadingMore,
                            onLoadMore = onLoadMore,
                            onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                            isGrid = isGrid,
                            isDownloaded = { it.code.uppercase() in downloadedCodes },
                            modifier = Modifier.fillMaxSize(),
                            header = header,
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
                    }
                }
            }
        }
    }
}
