package me.jbusdriver.modern.ui.forum

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import me.jbusdriver.modern.ui.components.GifPlaceholder
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
                            contentDescription = stringResource(R.string.back)
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
                        var dialogContent by remember { mutableStateOf<ForumDialogContent?>(null) }
                        val (firstPostSections, firstPostViewableImages) = remember(
                            detail.contentBlocks, autoLoadGifs, loadedGifUrls
                        ) {
                            groupFirstPostBlocks(detail.contentBlocks, autoLoadGifs, loadedGifUrls)
                        }

                        // 点击引用楼层的 "xxx 发表于 xxx"：跳转到被引楼层。
                        // 列表顺序：header + 首帖分块 + content_comments + replies_header + replies。
                        val jumpToFloor: (Int) -> Unit = jump@{ quotedPid ->
                            if (quotedPid <= 0) return@jump
                            if (quotedPid == detail.pid) {
                                scope.launch { listState.animateScrollToItem(0) }
                            } else {
                                val replyIndex = detail.replies.indexOfFirst { it.pid == quotedPid }
                                if (replyIndex >= 0) {
                                    val replyStartIndex = firstPostSections.size + 3
                                    scope.launch {
                                        listState.animateScrollToItem(replyStartIndex + replyIndex)
                                    }
                                }
                            }
                        }

                        dialogContent?.let { content ->
                            SelectableContentDialog(
                                blocks = content.blocks,
                                onDismiss = { dialogContent = null },
                                onCopyAll = {
                                    context.copy(content.copyText)
                                    Toast.makeText(
                                        context,
                                        R.string.copied,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    dialogContent = null
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
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            item(key = "header") {
                                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                    ThreadHeader(
                                        detail = detail,
                                        onTitleLongClick = {
                                            context.copy(detail.title)
                                            Toast.makeText(
                                                context,
                                                R.string.copied,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            }

                            firstPostSections.forEach { section ->
                                when (section) {
                                    is FirstPostSection.Text -> item(
                                        key = "content_${section.startBlockIndex}"
                                    ) {
                                        ForumPostContent(
                                            blocks = section.blocks,
                                            onImageClick = onImageClick,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            loadedGifUrls = loadedGifUrls,
                                            autoLoadGifs = autoLoadGifs,
                                            onLoadGif = { viewModel.onLoadGif(it) },
                                            onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                            onQuoteClick = jumpToFloor,
                                            onLongClick = {
                                                dialogContent =
                                                    ForumDialogContent.Blocks(detail.contentBlocks)
                                            }
                                        )
                                    }

                                    is FirstPostSection.ImageBlock -> item(
                                        key = "content_${section.startBlockIndex}"
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 2.dp)) {
                                            if (section.viewableIndex >= 0) {
                                                ForumImage(
                                                    block = section.block,
                                                    onClick = {
                                                        onImageClick(
                                                            firstPostViewableImages,
                                                            section.viewableIndex
                                                        )
                                                    }
                                                )
                                            } else {
                                                GifPlaceholder(
                                                    onClick = { viewModel.onLoadGif(section.block.url) },
                                                    onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                                    modifier = if (section.block.isFullSize) {
                                                        Modifier.fillMaxWidth().height(180.dp)
                                                    } else {
                                                        Modifier.size(48.dp)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "content_comments") {
                                PostCommentsPreview(
                                    comments = detail.comments,
                                    pageInfo = detail.commentPageInfo,
                                    onViewMore = viewModel::openFirstPostCommentsSheet,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            }

                            if (detail.replies.isNotEmpty()) {
                                item(key = "replies_header") {
                                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                        RepliesHeader(
                                            replyCount = detail.replyCount,
                                            floorOrder = state.floorOrder,
                                            onFloorOrderSelected = viewModel::setFloorOrder
                                        )
                                    }
                                }
                                items(items = detail.replies, key = { reply -> "reply_${reply.floor}" }) { reply ->
                                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                        ReplyItem(
                                            reply = reply,
                                            onImageClick = onImageClick,
                                            loadedGifUrls = loadedGifUrls,
                                            autoLoadGifs = autoLoadGifs,
                                            onLoadGif = { viewModel.onLoadGif(it) },
                                            onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                            onQuoteClick = jumpToFloor,
                                            onLongClick = {
                                                dialogContent =
                                                    ForumDialogContent.Blocks(reply.contentBlocks)
                                            },
                                            onViewComments = { r ->
                                                viewModel.openReplyCommentsSheet(r.floor)
                                            }
                                        )
                                    }
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
                            scope.launch { listState.scrollToItem(0) }
                        }
                    }
                }
                ScrollToTopButton(
                    visible = showScrollToTop,
                    onClick = { scope.launch { listState.scrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                )
            }
        }
    }
}

private sealed interface ForumDialogContent {
    val blocks: List<ContentBlock>
    val copyText: String

    data class Blocks(val originalBlocks: List<ContentBlock>) : ForumDialogContent {
        override val blocks: List<ContentBlock> = expandForumLinksForPreview(originalBlocks)
        override val copyText: String = buildForumPlainText(originalBlocks)
    }
}
