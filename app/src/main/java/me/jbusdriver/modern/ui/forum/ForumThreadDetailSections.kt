package me.jbusdriver.modern.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.components.AppAsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext

@Composable
internal fun ThreadHeader(
    detail: ForumThreadDetail,
    onTitleLongClick: () -> Unit = {},
    onMovieCodeClick: ((String) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Column {
        ForumThreadTitle(
            title = detail.title,
            typeName = detail.typeName,
            typeColor = detail.typeColor,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            badgeStyle = MaterialTheme.typography.titleMedium,
            onMovieCodeClick = onMovieCodeClick,
            modifier = Modifier
                .pointerInput(onTitleLongClick) {
                    detectTapGestures(onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTitleLongClick()
                    })
                }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                detail.postTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                forumViewReplyCountText(detail.viewCount, detail.replyCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 顶栏常显的楼主信息行：左 32dp 头像，右昵称在上、等级在下（昵称单行省略）；
 *  [onAuthorClick] 非空且 uid 有效时可点进用户空间（账号功能关闭时传 null，与已发布版本一致）。 */
@Composable
internal fun ThreadAuthorRow(
    detail: ForumThreadDetail,
    modifier: Modifier = Modifier,
    onAuthorClick: ((Int) -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppAsyncImage(
            model = detail.authorAvatar,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .let { m ->
                    if (onAuthorClick != null && detail.authorUid > 0) {
                        m.clickable { onAuthorClick(detail.authorUid) }
                    } else m
                },
            contentScale = ContentScale.Crop
        )
        Column(modifier = if (onAuthorClick != null && detail.authorUid > 0) {
            Modifier.clickable { onAuthorClick(detail.authorUid) }
        } else Modifier) {
            Text(
                detail.author,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!detail.authorGroup.isNullOrBlank()) {
                Text(
                    detail.authorGroup,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@Composable
internal fun ReplyOperationBar(
    allReplyCount: Int?,
    authorReplyCount: Int?,
    showAuthorOnly: Boolean,
    floorOrder: ForumFloorOrder,
    isSwitching: Boolean,
    onAuthorFilterSelected: (Boolean) -> Unit,
    onFloorOrderSelected: (ForumFloorOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplyFilterButton(
                label = stringResource(R.string.reply_filter_all),
                count = allReplyCount,
                selected = !showAuthorOnly,
                onClick = { onAuthorFilterSelected(false) }
            )
            ReplyFilterButton(
                label = stringResource(R.string.reply_filter_author),
                count = authorReplyCount,
                selected = showAuthorOnly,
                onClick = { onAuthorFilterSelected(true) }
            )
        }

        Spacer(Modifier.size(8.dp))

        if (isSwitching) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.weight(1f))

        FloorOrderPill(
            label = stringResource(R.string.floor_order_regular),
            selected = floorOrder == ForumFloorOrder.REGULAR,
            onClick = { onFloorOrderSelected(ForumFloorOrder.REGULAR) }
        )
        FloorOrderPill(
            label = stringResource(R.string.floor_order_reverse),
            selected = floorOrder == ForumFloorOrder.REVERSE,
            onClick = { onFloorOrderSelected(ForumFloorOrder.REVERSE) }
        )
    }
}

/** 左侧“全部评论 / 只看楼主”过滤按钮：TextButton，选中态用主色文字。 */
@Composable
private fun ReplyFilterButton(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val text = if (selected && count != null) "$label ($count)" else label
    Text(
        modifier = Modifier.clickable(
            onClick = onClick,
        ),
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1
    )
}

/** 右侧“升序 / 降序”胶囊：比左侧弱化（描边、小号字）。 */
@Composable
private fun FloorOrderPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border =
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant
    val content =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = content,
        border = BorderStroke(1.dp, border),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

/**
 * 帖子底部固定操作条（未来回帖入口也在此）：左侧圆角回复提示框常驻（无回帖权限时
 * 换"无权限"hint 且点击无反应，避免底栏空洞），右侧收藏带计数（点赞已移除——站点
 * 点赞不可撤销，交互反直觉）。计数来自帖子页 #p_btn，游客态也渲染；匿名点击由调用方
 * 引导全局登录弹窗。容器自身为透明玻璃（调用方挂 hazeBottomBar），内容从条下穿过
 * （条高由调用方量出后并入列表 contentPadding）。
 */
@Composable
internal fun ThreadActionBar(
    canReply: Boolean,
    isFavorited: Boolean,
    favoriteCount: Int,
    onReplyClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 回复提示框：视觉上的"伪输入框"，F2-1 回帖上线前点击仅提示；
        // 无权限（含匿名/等级不足）换 hint 文案、点击无反应
        Surface(
            onClick = { if (canReply) onReplyClick() },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(
                    if (canReply) R.string.forum_action_reply_hint
                    else R.string.forum_action_reply_no_permission
                ),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }

        ActionCountPill(
            iconRes = if (isFavorited) R.drawable.favorite_fill_24px else R.drawable.favorite_24px,
            label = stringResource(R.string.forum_action_favorite),
            count = favoriteCount,
            active = isFavorited,
            onClick = onFavoriteClick
        )
    }
}

/**
 * 收藏计数胶囊：图标 + 数字。点击即乐观生效（状态由 VM 立即置位），点亮瞬间播放
 * 反馈动画——容器/内容色淡变 + 图标弹跳。点亮态用 error 红实底（与影片收藏按钮同惯例，
 * 淡紫 primary 在玻璃条上不够醒目）；并发重复点击由 VM 提交锁静默拦截。
 */
@Composable
private fun ActionCountPill(
    iconRes: Int,
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.error else Color.Transparent,
        label = "actionPillContainer"
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onError
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "actionPillColor"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        label = "actionPillBorder"
    )
    val iconScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            iconScale.snapTo(0.6f)
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier
                    .size(16.dp)
                    .scale(iconScale.value)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun CommentsSection(comments: List<Comment>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.comment),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            comments.forEach { comment ->
                ForumCommentRow(comment = comment)
            }
        }
    }
}

@Composable
private fun ForumCommentRow(
    comment: Comment,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        AppAsyncImage(
            model = comment.authorAvatar,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer {
            ForumCommentText(comment = comment)
        }
    }
}

@Composable
private fun ForumCommentText(comment: Comment) {
    Column {
        Text(
            "${comment.author}  ${comment.time}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            comment.content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun PostCommentsPreview(
    comments: List<Comment>,
    pageInfo: PageInfo,
    onViewMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (comments.isEmpty() && !pageInfo.hasNext) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text(
            stringResource(R.string.floor_comments),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        comments.take(3).forEach { comment ->
            ForumCommentRow(comment = comment)
        }
        if (comments.size > 3 || pageInfo.hasNext) {
            TextButton(onClick = onViewMore) {
                Text(stringResource(R.string.view_more_comments))
            }
        }
    }
}

@Composable
internal fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onQuoteClick: (Int) -> Unit = {},
    onMovieCodeClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onAuthorClick: ((Int) -> Unit)? = null,
    onViewComments: (ForumReply) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppAsyncImage(
            model = reply.authorAvatar,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .let { m ->
                    if (onAuthorClick != null && reply.authorUid > 0) {
                        m.clickable { onAuthorClick(reply.authorUid) }
                    } else m
                },
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    reply.author,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${
                        forumFloorLabel(
                            reply.floor,
                            reply.isPinned,
                            stringResource(R.string.pinned)
                        )
                    } · ${reply.postTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (reply.authorGroup.isNotEmpty()) {
                Text(
                    reply.authorGroup,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            SelectableForumPostContent(
                blocks = reply.contentBlocks,
                onImageClick = onImageClick,
                modifier = Modifier.padding(top = 4.dp),
                loadedGifUrls = loadedGifUrls,
                autoLoadGifs = autoLoadGifs,
                onLoadGif = onLoadGif,
                onLoadAllGifs = onLoadAllGifs,
                onQuoteClick = onQuoteClick,
                onMovieCodeClick = onMovieCodeClick,
                onLinkClick = onLinkClick
            )
            PostCommentsPreview(
                comments = reply.comments,
                pageInfo = reply.commentPageInfo,
                onViewMore = { onViewComments(reply) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloorCommentsBottomSheet(
    sheet: FloorCommentSheetState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var lastAutoLoadAnchor by remember(sheet.pid) { mutableStateOf<String?>(null) }
    val nearEnd by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    LaunchedEffect(
        nearEnd,
        sheet.pageInfo.nextPage,
        sheet.isLoadingMore,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        val anchor = "${listState.firstVisibleItemIndex}:${listState.firstVisibleItemScrollOffset}"
        if (
            nearEnd &&
            sheet.pageInfo.hasNext &&
            !sheet.isLoadingMore &&
            lastAutoLoadAnchor != anchor
        ) {
            lastAutoLoadAnchor = anchor
            onLoadMore()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FloorCommentsSheetContent(
            sheet = sheet,
            listState = listState,
            onRetry = onRetry
        )
    }
}

@Composable
private fun FloorCommentsSheetContent(
    sheet: FloorCommentSheetState,
    listState: LazyListState,
    onRetry: () -> Unit
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxSheetHeight = with(LocalDensity.current) { (windowHeight * 0.7f).toDp() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {
        item(key = "comments_title") {
            Text(
                stringResource(R.string.floor_comments),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        itemsIndexed(sheet.comments, key = { index, comment ->
            "comment_${index}_${comment.author}_${comment.time}_${comment.content.hashCode()}"
        }) { _, comment ->
            ForumCommentRow(comment = comment)
        }
        item(key = "footer") {
            when {
                sheet.isLoadingMore -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }

                sheet.error != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry_load_comments))
                    }
                }

                !sheet.pageInfo.hasNext -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.no_more),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
