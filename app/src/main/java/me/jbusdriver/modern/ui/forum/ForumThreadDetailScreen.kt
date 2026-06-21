package me.jbusdriver.modern.ui.forum

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.components.EmptyStateView
import me.jbusdriver.modern.ui.components.LoadingViewCentered
import me.jbusdriver.modern.ui.components.ScrollToTopButton
import me.jbusdriver.modern.ui.components.ShareButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.components.rememberScrollToTopVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ForumThreadDetailViewModel =
        hiltViewModel<ForumThreadDetailViewModel, ForumThreadDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(RouteForumThreadDetail(tid)) }
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadedGifUrls by viewModel.loadedGifUrlsFlow.collectAsStateWithLifecycle()
    val autoLoadGifs by viewModel.autoLoadGifs.collectAsStateWithLifecycle()
    val detail = state.detail
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshLabel = stringResource(R.string.refresh)

    LifecycleResumeEffect(Unit) {
        if (state.detail != null) viewModel.revalidate()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val showScrollToTop = rememberScrollToTopVisibility(listState)

    val nearEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(nearEnd, detail?.pageInfo?.nextPage) {
        if (nearEnd && detail != null && detail.pageInfo.hasNext && !state.isLoadingMore) {
            viewModel.loadMoreReplies()
        }
    }
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
        }
    }

    LaunchedEffect(isAtTop) {
        viewModel.setAtTopForFreshUpdates(isAtTop)
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    ShareButton(
                        text = viewModel.shareThreadUrl,
                        chooserTitle = stringResource(R.string.share_thread)
                    )
                }
            )
        },
        snackbarHost = {
            ThemedSnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    detail != null -> {
                        var dialogBlocks by remember { mutableStateOf<List<ContentBlock>?>(null) }

                        dialogBlocks?.let { blocks ->
                            FloorContentDialog(
                                blocks = blocks,
                                onDismiss = { dialogBlocks = null },
                                onCopyAll = {
                                    context.copy(buildForumPlainText(blocks))
                                    Toast.makeText(context, "已複製", Toast.LENGTH_SHORT).show()
                                    dialogBlocks = null
                                }
                            )
                        }

                        state.commentSheet?.let { sheet ->
                            FloorCommentsBottomSheet(
                                sheet = sheet,
                                onLoadMore = { viewModel.loadMoreFloorComments() },
                                onRetry = { viewModel.loadMoreFloorComments() },
                                onDismiss = { viewModel.dismissCommentsSheet() }
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            item(key = "header") {
                                ThreadHeader(detail)
                            }

                            item(key = "content") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = CardDefaults.outlinedCardBorder(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    ForumPostContent(
                                        blocks = detail.contentBlocks,
                                        onImageClick = onImageClick,
                                        modifier = Modifier.padding(10.dp),
                                        loadedGifUrls = loadedGifUrls,
                                        autoLoadGifs = autoLoadGifs,
                                        onLoadGif = { viewModel.onLoadGif(it) },
                                        onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                        onLongClick = { dialogBlocks = detail.contentBlocks }
                                    )
                                    PostCommentsPreview(
                                        comments = detail.comments,
                                        pageInfo = detail.commentPageInfo,
                                        onViewMore = viewModel::openFirstPostCommentsSheet,
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            end = 10.dp,
                                            bottom = 10.dp
                                        )
                                    )
                                }
                            }

                            if (detail.replies.isNotEmpty()) {
                                item(key = "replies_header") {
                                    RepliesHeader(
                                        replyCount = detail.replyCount,
                                        floorOrder = state.floorOrder,
                                        onFloorOrderSelected = viewModel::setFloorOrder
                                    )
                                }
                                items(count = detail.replies.size, key = { "reply_$it" }) { index ->
                                    ReplyItem(
                                        reply = detail.replies[index],
                                        onImageClick = onImageClick,
                                        loadedGifUrls = loadedGifUrls,
                                        autoLoadGifs = autoLoadGifs,
                                        onLoadGif = { viewModel.onLoadGif(it) },
                                        onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                        onLongClick = {
                                            dialogBlocks = detail.replies[index].contentBlocks
                                        },
                                        onViewComments = { reply ->
                                            viewModel.openReplyCommentsSheet(reply.floor)
                                        }
                                    )
                                }
                            }

                            if (state.isLoadingMore) {
                                item(key = "loading_more") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            } else if (detail.replies.isNotEmpty() && !detail.pageInfo.hasNext) {
                                item(key = "no_more") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "沒有更多了",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    state.isLoading -> LoadingViewCentered()
                    else -> EmptyStateView(message = state.error?.let { stringResource(it) })
                }
                val pendingFreshMessage =
                    stringResource(state.refreshMessage ?: R.string.new_data_available)
                LaunchedEffect(state.pendingFreshDetail) {
                    if (state.pendingFreshDetail != null) {
                        val result = snackbarHostState.showSnackbar(
                            message = pendingFreshMessage,
                            actionLabel = refreshLabel,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.applyPendingFreshDetail()
                            scope.launch { listState.animateScrollToItem(0) }
                        }
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
}
