package me.jbusdriver.modern.ui.forum.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ForumFavoriteThread
import me.jbusdriver.modern.domain.model.ForumProfileField
import me.jbusdriver.modern.domain.model.ForumUserProfile
import me.jbusdriver.modern.domain.model.ForumUserThread
import me.jbusdriver.modern.ui.RouteUserSpace
import me.jbusdriver.modern.ui.components.AppPullToRefreshBox
import me.jbusdriver.modern.ui.components.BoxBlurTransformation
import me.jbusdriver.modern.ui.components.ScrollToTopButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.components.dimColorFilter
import me.jbusdriver.modern.ui.components.hazeSourceContent
import me.jbusdriver.modern.ui.components.hazeTopBar
import me.jbusdriver.modern.ui.components.rememberAppHazeState
import me.jbusdriver.modern.ui.components.rememberScrollToTopVisibility
import me.jbusdriver.modern.ui.components.rememberSiteImageRequest
import me.jbusdriver.modern.ui.forum.forumViewReplyCountText

/** 本人可见的页签（資料默认）；收藏为登录私有数据，他人视图不出现。 */
private val SELF_TABS = listOf(
    UserSpaceTab.ABOUT, UserSpaceTab.THREADS, UserSpaceTab.REPLIES, UserSpaceTab.FAVORITES
)
private val OTHER_TABS = listOf(UserSpaceTab.ABOUT, UserSpaceTab.THREADS, UserSpaceTab.REPLIES)

/** 标题栏行高（不含状态栏），与 Material 3 小型顶栏及论坛各页的玻璃标题栏一致。 */
private val TITLE_BAR_HEIGHT = 64.dp

/** 头像背景区高度占屏幕高度的比例。 */
private const val BACKDROP_HEIGHT_FRACTION = 0.3f

/** 背景区高度上下限：横屏不过高、平板不过大。 */
private val BACKDROP_MIN_HEIGHT = 160.dp
private val BACKDROP_MAX_HEIGHT = 300.dp

/** 叠在模糊头像上的表面色蒙版：保证名字/等级在任意明暗的头像上都可读。 */
private const val BACKDROP_SCRIM_ALPHA = 0.62f

/** 圆头像直径 = 背景区高度的一半。 */
private const val AVATAR_DIAMETER_FRACTION = 0.5f

/** 页签栏首次测量前的占位高度（48dp 页签 + 6dp 间隙），避免首帧内容跳动。 */
private val TAB_BAR_DEFAULT_HEIGHT = 54.dp

/** 页面左右边距，与论坛各页一致（分割线因此内缩，不顶到屏幕边缘）。 */
private val PAGE_HORIZONTAL_PADDING = 16.dp

/** 回覆视图每帖展示的回帖摘要条数上限，超出显示「還有 N 條回覆」。 */
private const val REPLY_EXCERPT_MAX = 3

/** 列表底部 contentPadding；尾部垫高量计算与布局共用同值。 */
private val LIST_BOTTOM_PADDING = 14.dp

/** 尾部垫高层 key：短内容时补足列表滚动余量，让头像区始终可以上推完全收起。 */
private const val TAIL_FILL_KEY = "tail_fill"

private val UserSpaceTab.labelRes: Int
    get() = when (this) {
        UserSpaceTab.THREADS -> R.string.forum_user_threads
        UserSpaceTab.REPLIES -> R.string.forum_user_replies
        UserSpaceTab.FAVORITES -> R.string.forum_user_favorites
        UserSpaceTab.ABOUT -> R.string.forum_user_about
    }

/** 页签上的数量，取站点统计行；收藏与资料不带数量。 */
private fun UserSpaceTab.countOf(state: UserSpaceUiState): Int? = when (this) {
    UserSpaceTab.THREADS -> state.profile?.topicCount
    UserSpaceTab.REPLIES -> state.profile?.replyCount
    UserSpaceTab.FAVORITES, UserSpaceTab.ABOUT -> null
}

/**
 * 圆头像取大图：空间页给出的是 48px 的 `_avatar_small`，同目录另有更大的 `_avatar_big`
 * （站点实测 141px）。无头像用户两者都是 404，由占位兜底。
 */
private fun String.toLargeAvatarUrl(): String = replace("_avatar_small.", "_avatar_big.")

/**
 * 个人主页（自/他复用）：折叠头部 + 吸顶玻璃页签。
 *
 * - 头像背景区（屏高 30%）铺到屏幕最顶端并延伸到玻璃标题栏后面，所以静止时毛玻璃
 *   就有内容可模糊；圆头像直径为背景区的一半，右侧是名字、等级、UID 与等级下方的里程进度。
 * - 页签栏跟在头像区下方，随内容上移，到标题栏下缘后吸顶；它自身也是玻璃面，
 *   列表内容从它与标题栏两层玻璃下穿过。
 * - 头像区上滑离开时，标题栏淡入「小头像 + 名字 + 等级」（度量同帖子详情标题栏）；
 *   标题栏的玻璃也随同一进度渐显——静止时透明，头像背景清晰地从返回键下穿过。
 * - 内容不满屏（列表空、条目少、资料过短）时，列表末尾的垫高层补足滚动余量，
 *   头像区照样可以上推到完全收起（见 [ProfileList]）。
 * - 資料/主題/回覆/收藏 就地切换（資料为默认页签），整页下拉刷新，触底加载更多，
 *   到底显示「沒有更多了」；滑过首屏出现快速回顶按钮（停手数秒自动隐没）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSpaceScreen(
    uid: Int,
    onThreadClick: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: UserSpaceViewModel = hiltViewModel<UserSpaceViewModel, UserSpaceViewModel.Factory>(
        creationCallback = { factory -> factory.create(RouteUserSpace(uid)) }
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.format(context),
                duration = SnackbarDuration.Long
            )
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.loggedOut.collect { onBack() }
    }

    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    val hazeState = rememberAppHazeState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val tabs = if (state.isSelf) SELF_TABS else OTHER_TABS
    // 快速回顶：滑过首屏后出现、停手数秒自动隐没（同影片/演员列表）
    val scope = rememberCoroutineScope()
    val showScrollToTop = rememberScrollToTopVisibility(listState)

    Scaffold(
        snackbarHost = { ThemedSnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            val titleBarHeight = statusTop + TITLE_BAR_HEIGHT
            val backdropHeight =
                (maxHeight * BACKDROP_HEIGHT_FRACTION).coerceIn(BACKDROP_MIN_HEIGHT, BACKDROP_MAX_HEIGHT)
            val titleBarHeightPx = with(density) { titleBarHeight.roundToPx() }
            // 圆头像在标题栏以下的可见背景区内垂直居中
            val avatarSize = avatarDiameter(backdropHeight, titleBarHeight)
            val avatarTopPx = with(density) {
                (titleBarHeight + (backdropHeight - titleBarHeight - avatarSize) / 2).roundToPx()
            }
            val avatarSizePx = with(density) { avatarSize.roundToPx() }
            // 页签以下内容的最小总高（不含底部 padding）：达到它，列表滚动余量就恰好够把
            // 头像背景区完全推走（页签吸顶到标题栏下缘）。短内容由尾部垫高层补足（见 ProfileList）。
            val minContentBelowHeaderPx = with(density) {
                maxHeight.roundToPx() - titleBarHeightPx - LIST_BOTTOM_PADDING.roundToPx()
            }
            // 完全收起时的滚动量（= 背景区高 − 标题栏高）：收尾吸附的两个边界之一
            val pinnedScrollPx = with(density) { backdropHeight.roundToPx() } - titleBarHeightPx
            var tabBarHeightPx by remember { mutableIntStateOf(0) }
            val tabBarHeight = with(density) { tabBarHeightPx.toDp() }
                .takeIf { it > 0.dp } ?: TAB_BAR_DEFAULT_HEIGHT

            // 以下两项都取列表首项（头像背景区）的实测位置：不靠公式推算，
            // 背景区高度怎么变都不会让页签栏错位。只在放置/绘制阶段读取，滚动不触发重组。
            val tabTop = remember(titleBarHeightPx) {
                derivedStateOf {
                    val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                        ?: return@derivedStateOf titleBarHeightPx
                    maxOf(titleBarHeightPx, header.offset + header.size)
                }
            }
            // 身份淡入进度：圆头像从标题栏下缘滑入到完全没入（0 → 1）
            val identityProgress = remember(titleBarHeightPx, avatarTopPx, avatarSizePx) {
                derivedStateOf {
                    val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                        ?: return@derivedStateOf 1f
                    val scrolledIntoTitleBar = -header.offset - (avatarTopPx - titleBarHeightPx)
                    (scrolledIntoTitleBar.toFloat() / avatarSizePx).coerceIn(0f, 1f)
                }
            }
            // 页签栏独立于列表：把竖向拖动转交给列表，手势表现与页签属于列表时一致
            val tabDragBridge = rememberScrollableState { delta -> listState.dispatchRawDelta(delta) }

            // 内容层：从标题栏与页签栏两层玻璃下穿过
            AppPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                // 指示圈下移到玻璃标题栏底缘，避免画在玻璃里
                indicatorInsetTop = titleBarHeight,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSourceContent(hazeState)
            ) {
                ProfileList(
                    state = state,
                    backdropHeight = backdropHeight,
                    titleBarHeight = titleBarHeight,
                    avatarSize = avatarSize,
                    tabBarHeight = tabBarHeight,
                    minContentBelowHeaderPx = minContentBelowHeaderPx,
                    pinnedScrollPx = pinnedScrollPx,
                    listState = listState,
                    onThreadClick = onThreadClick,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                    onLogout = { showLogoutDialog = true }
                )
            }

            // 页签栏：自身是玻璃面。静止时在头像区正下方，吸顶后列表内容从它下面穿过
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, tabTop.value) }
                    .onSizeChanged { tabBarHeightPx = it.height }
                    .hazeTopBar(hazeState)
                    // 不带越界回弹：页签栏自身是刚性的，到边缘时不该被拉变形
                    .scrollable(
                        state = tabDragBridge,
                        orientation = Orientation.Vertical,
                        overscrollEffect = null
                    )
            ) {
                PrimaryTabRow(
                    selectedTabIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0),
                    modifier = Modifier.padding(bottom = 6.dp),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == state.selectedTab
                        Tab(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            text = {
                                val label = stringResource(tab.labelRes)
                                val count = tab.countOf(state)
                                Text(
                                    text = if (count != null) "$label $count" else label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            // 标题栏：标题位始终留空——名字就在正下方的头像区里，等头像区上滑
            // 离开时淡入身份行（资料加载中不显示占位文本）
            Box(modifier = Modifier.fillMaxWidth()) {
                // 玻璃底单独成层：只有模糊效果，随身份淡入进度同步渐显。静止时完全透明，
                // 头像背景清晰地从返回键下穿过。透明度在绘制层读取，滚动不触发重组。
                // （Haze 2.1 的 hazeBlur 没有进度参数，故用图层透明度实现）
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = identityProgress.value }
                        .hazeTopBar(hazeState)
                )
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TITLE_BAR_HEIGHT)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.arrow_back_24px),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            // 加载中标题位留空：占位文本与身份行的先后出现太干扰，
                            // 资料就绪后随收起进度淡入身份行即可
                            profile?.let {
                                CompactIdentity(
                                    profile = it,
                                    modifier = Modifier.graphicsLayer { alpha = identityProgress.value }
                                )
                            }
                        }
                    }
                }
            }

            // 快速回顶：滑过头像背景区（首项）后出现，点击整列滚回顶部、头像区展开。
            // 底部居中、距底 64dp，与帖子列表/详情一致（footer 的顶部留白也按此计算）
            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            )

            if (state.isLoggingOut) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/** 圆头像直径：背景区的一半；矮屏（横屏）上以「标题栏以下可见区 − 留白」封顶。 */
private fun avatarDiameter(backdropHeight: Dp, titleBarHeight: Dp): Dp {
    val byBackdrop = backdropHeight * AVATAR_DIAMETER_FRACTION
    val visible = backdropHeight - titleBarHeight
    return minOf(byBackdrop, visible - 24.dp).coerceAtLeast(48.dp)
}

/**
 * 列表：头像背景区 → 页签占位 → 当前页签内容 → 尾部垫高层。页签栏是叠在上方的独立玻璃面。
 *
 * 尾部垫高层：头像背景区靠列表自身滚动收起，内容不满屏（列表空、条目少、资料过短）时
 * 滚动余量不够，头像区就推不走。垫高层把「页签以下内容」补足到 [minContentBelowHeaderPx]，
 * 使滚动余量恒 ≥ 背景区高 − 标题栏高——空/短内容也能上推到页签吸顶、身份行完整淡入。
 * 它只在列表末尾延伸、不移动已有项，补高发生在首帧布局之后也无跳动；内容能铺满视口时
 * 高度为 0，长列表无尾部空白。
 *
 * 收尾吸附：滚动区间短（短内容恰好够收起头像区），弱惯性会衰减在半途——手势结束后
 * 若停在收起中途，按最后滚动方向吸附到「展开 / 完全收起」，头部不停在半收半开。
 */
@Composable
private fun ProfileList(
    state: UserSpaceUiState,
    backdropHeight: Dp,
    titleBarHeight: Dp,
    avatarSize: Dp,
    tabBarHeight: Dp,
    minContentBelowHeaderPx: Int,
    pinnedScrollPx: Int,
    listState: LazyListState,
    onThreadClick: (Int) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLogout: () -> Unit
) {
    val profile = state.profile
    val tab = state.selectedTab
    val density = LocalDensity.current
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    // 以页签为 key：切到内容较短的页签时立即补拉下一页
    LaunchedEffect(nearEnd, tab) {
        if (nearEnd) onLoadMore()
    }

    var tailFillPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState, minContentBelowHeaderPx) {
        snapshotFlow {
            val info = listState.layoutInfo
            when {
                info.totalItemsCount == 0 -> 0
                // 有不可见项说明内容够长，滚动余量充足，不垫高
                info.visibleItemsInfo.size < info.totalItemsCount -> 0
                else -> {
                    // 全部可见才能安全累加实测高度；排除首项（背景区）与垫高层自身
                    val belowHeader = info.visibleItemsInfo
                        .filter { it.index > 0 && it.key != TAIL_FILL_KEY }
                        .sumOf { it.size }
                    (minContentBelowHeaderPx - belowHeader).coerceAtLeast(0)
                }
            }
        }.collect { tailFillPx = it }
    }

    // 手势收尾吸附：短内容页签的滚动区间只有「背景区高 − 标题栏高」，较弱的惯性常在
    // 半途衰减殆尽，把头像区留在半收半开的位置。手势结束若停在中途，按最后的滚动方向
    // 吸附到「展开（0）」或「完全收起（吸顶）」——任何一次甩动都干净地落到稳定态，
    // 而不是死在半路（拖动松手同样受益：头部从不停在中间位置）。
    // 吸附动画必须放进子协程：用户在吸附途中按下新手指会以 UserInput 优先级抢占滚动、
    // 取消动画；若在监听本体里直接 await，取消异常会连带杀死整个监听，之后所有松手
    // 都停在半途（他人主页资料加载慢、等待期间触摸更易踩中）。
    LaunchedEffect(listState, pinnedScrollPx) {
        var lastOffset = -1
        var lastMotionUp = false
        var settleJob: Job? = null
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (scrolling, index, offset) ->
            when {
                scrolling -> {
                    if (lastOffset >= 0 && offset != lastOffset) lastMotionUp = offset > lastOffset
                    lastOffset = offset
                }

                index == 0 && offset in 1 until pinnedScrollPx -> {
                    settleJob?.cancel()
                    settleJob = launch {
                        try {
                            listState.animateScrollToItem(
                                0, if (lastMotionUp) pinnedScrollPx else 0
                            )
                        } catch (e: CancellationException) {
                            // 新手势抢占吸附：正常中断，静默结束，监听继续存活
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_PADDING),
        // 关闭越界回弹（到边缘后的拉伸）。它只作用于列表自己绘制的内容、且是纯绘制阶段的
        // 变形——布局坐标不变，而页签栏的位置取自布局坐标，于是内容被拉伸、页签栏却停在
        // 原位，二者脱节（实测观感不佳）。顶上是一整块头像背景图，被拉伸本身也不好看。
        // 共享位移的自绘过冲（列表与页签栏整体随动再弹回）也实现过并按用户要求回退，
        // 勿再引入。
        overscrollEffect = null
    ) {
        // 首项 = 头像背景区。页签栏的纵坐标取本项的实测底边，页签占位紧随其后
        item(key = "header") {
            ProfileBackdrop(
                profile = profile,
                uid = state.uid,
                isLoading = state.profileLoading,
                height = backdropHeight,
                titleBarHeight = titleBarHeight,
                avatarSize = avatarSize
            )
        }
        item(key = "tab_spacer") {
            // 页签栏静止时压在这里（见页签栏的纵坐标计算）
            Spacer(modifier = Modifier.fillMaxWidth().height(tabBarHeight))
        }
        if (profile == null) {
            item(key = "profile_state") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.profileLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
        } else {
            when (tab) {
                UserSpaceTab.THREADS -> threadItems(
                    state.threads, R.string.forum_user_threads_empty, onThreadClick, onRetry
                )

                UserSpaceTab.REPLIES -> threadItems(
                    state.replies, R.string.forum_user_replies_empty, onThreadClick, onRetry
                )

                UserSpaceTab.FAVORITES -> favoriteItems(
                    state.favorites, onThreadClick, onRetry
                )

                UserSpaceTab.ABOUT -> aboutItems(profile, state.isSelf, onLogout)
            }
        }
        // 尾部垫高层：高度见上方 tailFillPx 的计算说明
        item(key = TAIL_FILL_KEY) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { tailFillPx.toDp() })
            )
        }
    }
}

/** 顶栏收起态的身份行：度量与帖子详情标题栏一致（32dp 头像 / 粗体昵称 / 主色等级）。 */
@Composable
private fun CompactIdentity(profile: ForumUserProfile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 取大图（141px）：32dp 在高密度屏上用 48px 小图会发虚，与背景区大头像同源
        Avatar(url = profile.avatarUrl?.toLargeAvatarUrl(), size = 32.dp)
        Column {
            Text(
                text = profile.username,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (profile.groupName.isNotBlank()) {
                Text(
                    text = profile.groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

/**
 * 头像背景区：模糊头像铺满（延伸到标题栏后面），叠表面色蒙版；
 * 圆头像（背景区高度的一半）与名字/等级/UID/里程进度 同行，在标题栏以下的可见区内垂直居中。
 *
 * 背景用 48px 小图：先在解码期做一次真正的盒式模糊（[BoxBlurTransformation]），再放大绘制。
 * 只靠放大插值是不够的——源图每个像素的细节会留成一格一格的拼布感。
 * 加载失败或无头像时露出底下的色调渐变。
 */
@Composable
private fun ProfileBackdrop(
    profile: ForumUserProfile?,
    uid: Int,
    isLoading: Boolean,
    height: Dp,
    titleBarHeight: Dp,
    avatarSize: Dp
) {
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        )
        val backdropUrl = profile?.avatarUrl
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberSiteImageRequest(
                    backdropUrl,
                    transformations = listOf(BoxBlurTransformation),
                    variant = "blurred"
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = dimColorFilter(),
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = BACKDROP_SCRIM_ALPHA))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = titleBarHeight)
                .fillMaxHeight()
                .padding(horizontal = PAGE_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Avatar(
                    url = profile?.avatarUrl?.toLargeAvatarUrl(),
                    size = avatarSize,
                    ring = true
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
            if (profile != null) {
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.username,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (profile.groupName.isNotBlank()) {
                            RankPill(
                                groupName = profile.groupName,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        Text(
                            text = "UID: $uid",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                    // 里程升级进度紧随称号（等级徽标）下方
                    profile.mileage?.let { mileage ->
                        MileageBlock(mileage = mileage, toNextLevel = profile.mileageToNextLevel)
                    }
                }
            }
        }
    }
}

/** 圆头像；加载中/无图只露出色底，不画首字或问号占位（太干扰）。
 *  [ring] 为背景区大头像描的一圈表面色细边，把自己和模糊背景分开。 */
@Composable
private fun Avatar(url: String?, size: Dp, ring: Boolean = false) {
    val shape = CircleShape
    val placeholderColor = if (ring) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(placeholderColor)
            .let { if (ring) it.border(2.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape) else it },
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = rememberSiteImageRequest(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = dimColorFilter(),
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 等级徽标：小圆角色调底。用户等级是论坛身份的核心，故与名字同级展示。 */
@Composable
private fun RankPill(groupName: String, modifier: Modifier = Modifier) {
    Text(
        text = groupName,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * 里程升级进度（在称号下方）：说明文字 + 一条细进度条。
 * 进度 = 已得里程 /（已得 + 距下一级还差）；站点未给出下一级时只展示数值、不画进度条。
 */
@Composable
private fun MileageBlock(mileage: Int, toNextLevel: Int?) {
    val toNext = toNextLevel?.takeIf { it > 0 }
    val caption = if (toNext == null) {
        stringResource(R.string.forum_mileage_value, mileage)
    } else {
        stringResource(R.string.forum_mileage_value, mileage) +
            " · " + stringResource(R.string.forum_mileage_to_next_short, toNext)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (toNext != null) {
            val total = mileage + toNext
            LinearProgressIndicator(
                progress = { if (total <= 0) 0f else (mileage.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * 主题/回覆页签内容：占位（加载/失败/空），或行布局与回帖摘要行。
 *
 * 行布局与帖子列表卡对齐：标题（整行，至多 2 行）→ 版块（primary 小字）→
 * 最后回复人 + 时间（左）与 瀏覽·回覆 计数（右）。回覆视图（[ForumUserThread.replies]
 * 非空）在标题正下方以缩进小列表展示该用户的回帖摘要（每条单行省略，至多
 * [REPLY_EXCERPT_MAX] 条，超出提示余量）；整行仍点击进帖。
 */
private fun LazyListScope.threadItems(
    list: UserListState<ForumUserThread>,
    emptyRes: Int,
    onThreadClick: (Int) -> Unit,
    onRetry: () -> Unit
) {
    if (list.items.isEmpty()) {
        placeholder(list, emptyRes, onRetry)
        return
    }
    itemsIndexed(list.items, key = { _, item -> item.tid }) { index, item ->
        if (index > 0) RowDivider()
        ListRow(onClick = { onThreadClick(item.tid) }) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 回覆视图：该用户的回帖摘要紧跟标题（缩进小列表，每条单行省略）
            val replies = item.replies.orEmpty()
            if (replies.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    replies.take(REPLY_EXCERPT_MAX).forEach { excerpt ->
                        Text(
                            text = "› $excerpt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val rest = replies.size - REPLY_EXCERPT_MAX
                    if (rest > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.forum_user_reply_more, rest, rest),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (item.boardName.isNotBlank()) {
                Text(
                    text = item.boardName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 第三行：同帖子列表卡——左=最后回复人+时间（人名收缩省略、时间完整），右=瀏覽·回覆
            Row(verticalAlignment = Alignment.CenterVertically) {
                val author = item.lastReplyAuthor.orEmpty()
                if (author.isNotEmpty()) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.time.isNotBlank()) {
                            Text(
                                text = item.time,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                } else if (item.time.isNotBlank()) {
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = forumViewReplyCountText(item.viewCount, item.replyCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
    footer(list, onRetry)
}

/** 收藏页签内容：行结构同主题，但站点收藏页不提供版块与回/看计数。 */
private fun LazyListScope.favoriteItems(
    list: UserListState<ForumFavoriteThread>,
    onThreadClick: (Int) -> Unit,
    onRetry: () -> Unit
) {
    if (list.items.isEmpty()) {
        placeholder(list, R.string.forum_user_favorites_empty, onRetry)
        return
    }
    itemsIndexed(list.items, key = { _, item -> item.favId }) { index, item ->
        if (index > 0) RowDivider()
        ListRow(onClick = { onThreadClick(item.tid) }) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.time.isNotBlank()) {
                Text(
                    text = item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
    footer(list, onRetry)
}

/** 列表页签的占位项：加载中 / 失败重试 / 空态。 */
private fun LazyListScope.placeholder(list: UserListState<*>, emptyRes: Int, onRetry: () -> Unit) {
    item(key = "placeholder") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(horizontal = PAGE_HORIZONTAL_PADDING)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                list.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )

                list.error -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }

                else -> Text(
                    text = stringResource(emptyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 触底页脚：追加加载中转圈；追加失败给出行尾重试（已有内容不整页报错）；到底给「沒有更多了」收尾。 */
private fun LazyListScope.footer(list: UserListState<*>, onRetry: () -> Unit) {
    when {
        list.isLoadingMore -> item(key = "footer_loading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        list.error -> item(key = "footer_error") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }

        // 顶部留白让出悬浮回顶按钮（bottom=16 高 40 → 按钮占据距底 16~56dp，同帖子详情做法），
        // 完全滚到底时提示不被按钮遮挡
        list.endReached && list.items.isNotEmpty() -> item(key = "footer_end") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.no_more),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ListRow(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_HORIZONTAL_PADDING)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = PAGE_HORIZONTAL_PADDING),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** 資料页签：分组字段全量展示；退出登录是低频操作，放在页尾且仅本人可见。 */
private fun LazyListScope.aboutItems(profile: ForumUserProfile, isSelf: Boolean, onLogout: () -> Unit) {
    // 同一字段（如「里程」）会在站点的多个分组重复渲染，按标签去重
    profileSection(
        R.string.forum_profile_section_basic, profile.basics.distinctBy { it.label }, first = true
    )
    profileSection(
        R.string.forum_profile_section_activity, profile.activity.distinctBy { it.label }, first = false
    )
    if (profile.basics.isEmpty() && profile.activity.isEmpty() && !isSelf) {
        item(key = "about_empty") {
            Text(
                text = stringResource(R.string.empty_content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = PAGE_HORIZONTAL_PADDING)
                    .padding(vertical = 24.dp)
            )
        }
    }
    if (isSelf) {
        item(key = "logout") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = onLogout) {
                    Text(
                        text = stringResource(R.string.forum_logout),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun LazyListScope.profileSection(titleRes: Int, fields: List<ForumProfileField>, first: Boolean) {
    if (fields.isEmpty()) return
    item(key = "section_$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = PAGE_HORIZONTAL_PADDING)
                .padding(top = if (first) 12.dp else 20.dp, bottom = 4.dp)
        )
    }
    itemsIndexed(fields, key = { index, _ -> "field_${titleRes}_$index" }) { index, field ->
        if (index > 0) RowDivider()
        FieldRow(field)
    }
}

@Composable
private fun FieldRow(field: ForumProfileField) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_HORIZONTAL_PADDING)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forum_logout)) },
        text = { Text(stringResource(R.string.forum_logout_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
