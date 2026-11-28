package me.jbusdriver.modern.ui.forum

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.data.parser.ForumLinkTarget
import me.jbusdriver.modern.data.parser.resolveForumLinkTarget
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.RouteLinkMovies
import me.jbusdriver.modern.ui.browser.rememberWebOpener
import me.jbusdriver.modern.ui.components.AppPullToRefreshBox
import me.jbusdriver.modern.ui.components.EmptyStateView
import me.jbusdriver.modern.ui.components.GifPlaceholder
import me.jbusdriver.modern.ui.components.LoadingViewCentered
import me.jbusdriver.modern.ui.components.ScrollToTopButton
import me.jbusdriver.modern.ui.components.ShareButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.components.hazeSourceContent
import me.jbusdriver.modern.ui.components.hazeTopBar
import me.jbusdriver.modern.ui.components.rememberAppHazeState
import me.jbusdriver.modern.ui.components.rememberScrollToTopVisibility

/** 楼层跳转定位后背景高亮的持续时间。 */
private const val FLOOR_HIGHLIGHT_DURATION_MS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int) -> Unit,
    /** 点击帖子中识别出的番号后打开影片详情（参数为详情页绝对 URL）。 */
    onOpenMovieDetail: (String) -> Unit = {},
    /** 点击他帖链接后打开帖子详情（tid，用当前站点加载，镜像链接归一化）。 */
    onOpenThread: (Int) -> Unit = {},
    /** 点击板块链接后打开板块帖子列表（fid）。 */
    onOpenBoard: (Int) -> Unit = {},
    /** 点击站内列表页链接（演员/导演/制作/发行/系列/分类）后打开关联影片列表。 */
    onOpenLinkMovies: (RouteLinkMovies) -> Unit = {},
    /** 点击作者（楼主栏/楼层）后打开用户空间（uid）。 */
    onOpenUserSpace: (Int) -> Unit = {},
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

    val openMovieByCode: (String) -> Unit = { code ->
        onOpenMovieDetail(viewModel.movieUrlForCode(code))
    }

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


    val hazeState = rememberAppHazeState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.hazeTopBar(hazeState),
                title = {
                    // 常显楼主信息行（头像+昵称+等级），帖子标题只在内容区展示
                    detail?.let { ThreadAuthorRow(detail = it, onAuthorClick = onOpenUserSpace) }
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
        // 内容穿透毛玻璃顶栏：不吃顶部 padding，顶高转交 LazyColumn contentPadding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            AppPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                // 指示圈下移到玻璃顶栏底缘，避免画在玻璃里
                indicatorInsetTop = innerPadding.calculateTopPadding(),
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    detail != null -> {
                        val (firstPostSections, firstPostViewableImages) = remember(
                            detail.contentBlocks, autoLoadGifs, loadedGifUrls
                        ) {
                            groupFirstPostBlocks(detail.contentBlocks, autoLoadGifs, loadedGifUrls)
                        }
                        // 回复区渲染用组合列表（楼层 + 断层分隔行）；跳转索引另在 jumpToFloor
                        // 内按最新 state 重算，避免合并后闭包持有旧列表
                        val replyItems = remember(detail.replies, state.floorOrder, detail.pageInfo.hasNext) {
                            buildReplyListItems(detail.replies, state.floorOrder, detail.pageInfo.hasNext)
                        }
                        // sticky 操作条高度，跳转引用楼层时用它让目标楼层落在操作条下方
                        var operationBarHeightPx by remember { mutableIntStateOf(0) }

                        // 楼层跳转定位后的高亮楼层 pid（纯 UI 瞬时状态，超时自动清除）
                        var highlightedPid by remember { mutableStateOf(0) }
                        LaunchedEffect(highlightedPid) {
                            if (highlightedPid != 0) {
                                delay(FLOOR_HIGHLIGHT_DURATION_MS)
                                highlightedPid = 0
                            }
                        }

                        // 点击引用楼层的 "xxx 发表于 xxx"：跳转到被引楼层。
                        // 列表顺序：header + 首帖分块 + content_comments + replies_header +
                        // 回复区（楼层 + 断层分隔行，见 buildReplyListItems）。
                        // 读取 uiState 最新值：引用定位合并后组合闭包可能仍持有旧 detail。
                        // 负偏移让目标楼层的名称落在 sticky 操作条下方，避免被遮挡。
                        // 返回是否命中：目标为本帖首帖，或楼层已在已加载的回复中；
                        // 未命中由调用方定位加载或回退浏览器打开。
                        val jumpToFloor: (Int) -> Boolean = jump@{ quotedPid ->
                            if (quotedPid <= 0) return@jump false
                            val freshState = viewModel.uiState.value
                            val freshDetail = freshState.detail ?: return@jump false
                            if (quotedPid == freshDetail.pid) {
                                scope.launch { listState.animateScrollToItem(0) }
                                return@jump true
                            }
                            if (freshDetail.replies.none { it.pid == quotedPid }) return@jump false
                            // 回复区可能插入断层分隔行，目标索引须在组合列表中计算
                            val combinedIndex = buildReplyListItems(
                                freshDetail.replies,
                                freshState.floorOrder,
                                freshDetail.pageInfo.hasNext
                            ).indexOfFirst { it is ReplyListItem.Reply && it.reply.pid == quotedPid }
                            if (combinedIndex < 0) return@jump false
                            val replyStartIndex = firstPostSections.size + 3
                            scope.launch {
                                // animateScrollToItem 挂起至完全落位(长距离滚动含预估高度修正段)
                                // 后才返回,高亮计时必须在落位后启动,否则 800ms 在滚动途中耗尽
                                listState.animateScrollToItem(
                                    replyStartIndex + combinedIndex,
                                    -operationBarHeightPx
                                )
                                highlightedPid = quotedPid
                            }
                            true
                        }

                        // 帖内行内链接：同帖楼层指路链接（goto=findpost / #pid 锚点）跳转定位，
                        // 其余（外链/跨站）用内置浏览器打开。
                        val openWeb = rememberWebOpener()

                        // 引用楼层/同帖 findpost 链接点击：先在已加载楼层中定位；未命中时经
                        // findpost 重定向拉取目标页合并后重新定位，仍失败回退浏览器打开链接。
                        val onFloorJumpClick: (Int, String) -> Unit = onFloorJumpClick@{ quotedPid, fallbackUrl ->
                            if (jumpToFloor(quotedPid)) return@onFloorJumpClick
                            scope.launch {
                                when (viewModel.locateQuotedFloor(quotedPid)) {
                                    FloorLocateResult.Located -> jumpToFloor(quotedPid)
                                    FloorLocateResult.NotFound -> openWeb(fallbackUrl)

                                    FloorLocateResult.Superseded -> Unit
                                }
                            }
                        }

                        // 帖内链接路由:按 Discuz 结构特征判定(不查域名,镜像链接归一化到当前站点加载)
                        // ① 楼层指路 → 本帖跳转;② 图片直链 → 查看器;③ 站内列表页(演员/导演等) →
                        // 关联影片列表;④ 影片番号链接 → 影片详情;⑤ 他帖 → 详情;⑥ 板块 → 列表;
                        // ⑦ 其余 → 内置浏览器
                        val onPostLinkClick: (String) -> Unit = onPostLink@{ url ->
                            when (val target = resolveForumLinkTarget(url)) {
                                is ForumLinkTarget.Thread -> {
                                    val sameThread = target.threadId == 0 || target.threadId == detail.tid
                                    when {
                                        sameThread && target.postId != null ->
                                            onFloorJumpClick(target.postId, url)

                                        !sameThread -> onOpenThread(target.threadId)
                                        // 本帖无楼层锚点:停留当前页
                                    }
                                }

                                is ForumLinkTarget.Board -> onOpenBoard(target.forumId)
                                is ForumLinkTarget.Image -> onImageClick(listOf(target.url), 0)
                                // 列表页标题不预设(锚文本/链接 id 观感差),先「載入中」,页面
                                // 面包屑解析后由 resolvedTitle 更新
                                is ForumLinkTarget.ListPage -> onOpenLinkMovies(
                                    RouteLinkMovies(
                                        linkUrl = target.url,
                                        type = target.type,
                                        censorType = target.censorType
                                    )
                                )

                                is ForumLinkTarget.Movie ->
                                    onOpenMovieDetail(viewModel.movieUrlForCode(target.code))
                                is ForumLinkTarget.Web -> openWeb(url)
                            }
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
                            modifier = Modifier
                                .fillMaxSize()
                                .hazeSourceContent(hazeState),
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = innerPadding.calculateTopPadding() + 14.dp,
                                bottom = 14.dp
                            )
                        ) {
                            item(key = "header") {
                                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                    ThreadHeader(
                                        detail = detail,
                                        onMovieCodeClick = openMovieByCode,
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
                                        SelectableForumPostContent(
                                            blocks = section.blocks,
                                            onImageClick = onImageClick,
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            loadedGifUrls = loadedGifUrls,
                                            autoLoadGifs = autoLoadGifs,
                                            onLoadGif = { viewModel.onLoadGif(it) },
                                            onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                            onQuoteClick = { pid ->
                                                onFloorJumpClick(pid, viewModel.findPostUrl(pid))
                                            },
                                            onMovieCodeClick = openMovieByCode,
                                            onLinkClick = onPostLinkClick
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

                            if (detail.replies.isNotEmpty() || state.showAuthorOnly) {
                                stickyHeader(key = "replies_header") {
                                    ReplyOperationBar(
                                        allReplyCount = state.allReplyCount,
                                        authorReplyCount = state.authorReplyCount,
                                        showAuthorOnly = state.showAuthorOnly,
                                        floorOrder = state.floorOrder,
                                        isSwitching = state.isChangingFloorOrder ||
                                            state.isChangingAuthorFilter,
                                        onAuthorFilterSelected = viewModel::setAuthorFilter,
                                        onFloorOrderSelected = viewModel::setFloorOrder,
                                        modifier = Modifier.onSizeChanged {
                                            operationBarHeightPx = it.height
                                        }
                                    )
                                }
                                items(items = replyItems, key = { it.key }) { item ->
                                    when (item) {
                                        is ReplyListItem.Gap -> FloorGapDivider(
                                            gap = item.gap,
                                            isLoading = state.gapLoadingBelowFloor == item.gap.belowFloor,
                                            onClick = { viewModel.loadGapFloors(item.gap.belowFloor) }
                                        )

                                        is ReplyListItem.Reply -> {
                                            val reply = item.reply
                                            // 楼层跳转定位后短暂高亮目标楼层:在内容外围外扩绘制圆角背景,
                                            // 内容位置不变,与背景边缘留出呼吸间距
                                            val highlightColor by animateColorAsState(
                                                targetValue = if (highlightedPid == reply.pid) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    Color.Transparent
                                                },
                                                label = "floorHighlight"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(bottom = 16.dp)
                                                    .drawBehind {
                                                        if (highlightColor.alpha > 0f) {
                                                            val outsetX = 12.dp.toPx()
                                                            val outsetY = 8.dp.toPx()
                                                            drawRoundRect(
                                                                color = highlightColor,
                                                                topLeft = Offset(-outsetX, -outsetY),
                                                                size = Size(size.width + outsetX * 2, size.height + outsetY * 2),
                                                                cornerRadius = CornerRadius(8.dp.toPx())
                                                            )
                                                        }
                                                    }
                                            ) {
                                                ReplyItem(
                                                    reply = reply,
                                                    onImageClick = onImageClick,
                                                    loadedGifUrls = loadedGifUrls,
                                                    autoLoadGifs = autoLoadGifs,
                                                    onLoadGif = { viewModel.onLoadGif(it) },
                                                    onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                                    onQuoteClick = { pid ->
                                                        onFloorJumpClick(pid, viewModel.findPostUrl(pid))
                                                    },
                                                    onMovieCodeClick = openMovieByCode,
                                                    onLinkClick = onPostLinkClick,
                                                    onAuthorClick = onOpenUserSpace,
                                                    onViewComments = { r ->
                                                        viewModel.openReplyCommentsSheet(r.floor)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.isLoadingMore || state.isLocatingFloor) {
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
                                    // 顶部留白让出底部居中回顶按钮（bottom=64 高 40 → 占据距底 64~104dp），
                                    // 完全滚到底时末楼不被按钮遮挡，提示语保持在按钮下方收尾。
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 64.dp, bottom = 16.dp),
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

                    // 居中区排除顶部玻璃，转圈落在可见区域中心
                    state.isLoading -> LoadingViewCentered(
                        Modifier.padding(top = innerPadding.calculateTopPadding())
                    )
                    else -> EmptyStateView(
                        message = state.error?.let { stringResource(it) },
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                    )
                }
                LaunchedEffect(viewModel) {
                    viewModel.messages.collect { message ->
                        val result = snackbarHostState.showSnackbar(
                            message = message.format(context),
                            actionLabel = if (message.resId == R.string.new_data_available) refreshLabel else null,
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

/** 楼层断层分隔行：提示两侧楼层之间未加载的区间，点击后循环翻页补齐。 */
@Composable
private fun FloorGapDivider(
    gap: FloorGap,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
        Text(
            text = stringResource(
                R.string.floor_gap_load,
                gap.belowFloor + 1,
                gap.aboveFloor - 1
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = if (isLoading) Modifier.padding(start = 8.dp) else Modifier
        )
    }
}
