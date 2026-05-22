package me.jbusdriver.modern.ui.forum

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.TextPart
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.components.ScrollToTopButton
import me.jbusdriver.modern.ui.components.rememberScrollToTopVisibility
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadDetailScreen(
    tid: Int,
    onImageClick: (List<String>, Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ForumThreadDetailViewModel = hiltViewModel<ForumThreadDetailViewModel, ForumThreadDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(RouteForumThreadDetail(tid)) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = state.detail
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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

    val handleLinkClick = rememberLinkClickHandler()

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
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.error != null && detail == null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadDetail() }) { Text("重試") }
                    }
                }
                detail != null -> {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        item(key = "header") {
                            ThreadHeader(detail)
                        }

                        item(key = "content") {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = CardDefaults.outlinedCardBorder(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                PostContent(
                                    blocks = detail.contentBlocks,
                                    onImageClick = onImageClick,
                                    onLinkClick = handleLinkClick,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (detail.comments.isNotEmpty()) {
                            item(key = "comments") {
                                CommentsSection(comments = detail.comments)
                            }
                        }

                        if (detail.replies.isNotEmpty()) {
                            item(key = "replies_header") {
                                Text(
                                    "精彩評論 (${detail.replyCount})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            items(count = detail.replies.size, key = { "reply_$it" }) { index ->
                                ReplyItem(reply = detail.replies[index], onImageClick = onImageClick, onLinkClick = handleLinkClick)
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
                                    Text("沒有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { CircularProgressIndicator() }
                }
            }
            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}
}

@Composable
private fun RichTextContent(
    parts: List<TextPart>,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkStyle = androidx.compose.ui.text.SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
    )
    val tag = "URL"

    val annotatedString = buildAnnotatedString(parts, linkStyle, tag)

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag, offset, offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

@Composable
private fun SelectableRichTextContent(
    parts: List<TextPart>,
    modifier: Modifier = Modifier
) {
    val fullText = parts.joinToString("") { part ->
        when (part) {
            is TextPart.Plain -> part.text
            is TextPart.Link -> part.text
        }
    }
    SelectionContainer {
        Text(
            fullText,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = modifier
        )
    }
}

private fun buildAnnotatedString(
    parts: List<TextPart>,
    linkStyle: androidx.compose.ui.text.SpanStyle,
    tag: String
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        for (part in parts) {
            when (part) {
                is TextPart.Plain -> append(part.text)
                is TextPart.Link -> {
                    pushStringAnnotation(tag, part.url)
                    pushStyle(linkStyle)
                    append(part.text)
                    pop()
                    pop()
                }
            }
        }
    }
}

@Composable
private fun rememberLinkClickHandler(
    onForumThreadClick: (Int) -> Unit = {},
    onForumBoardClick: (Int) -> Unit = {}
): (String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current

    return remember(context) {
        { url: String ->
            val threadMatch = Regex("""tid=(\d+)""").find(url)
            val fidMatch = Regex("""fid=(\d+)""").find(url)

            when {
                threadMatch != null -> {
                    val tid = threadMatch.groupValues[1].toIntOrNull()
                    if (tid != null) onForumThreadClick(tid)
                }
                fidMatch != null -> {
                    val fid = fidMatch.groupValues[1].toIntOrNull()
                    if (fid != null) onForumBoardClick(fid)
                }
                else -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, "選擇瀏覽器"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "無法打開鏈接", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun PostContent(
    blocks: List<ContentBlock>,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {}
) {
    val imageUrls = remember(blocks) {
        blocks.filterIsInstance<ContentBlock.Image>().map { it.url }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var imageIndex = 0
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.RichText -> {
                    RichTextContent(
                        parts = block.parts,
                        onLinkClick = onLinkClick
                    )
                }
                is ContentBlock.Image -> {
                    if (block.isFullSize) {
                        val currentIdx = imageIndex++
                        AsyncImage(
                            model = block.url,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(imageUrls, currentIdx) },
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        val currentIdx = imageIndex++
                        AsyncImage(
                            model = block.url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onImageClick(imageUrls, currentIdx) },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                is ContentBlock.Quote -> {
                    val accentColor = MaterialTheme.colorScheme.primary
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .drawBehind {
                                    drawLine(
                                        color = accentColor,
                                        start = Offset(0f, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                }
                                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                        ) {
                            Column {
                                if (block.author.isNotEmpty()) {
                                    Text(
                                        "${block.author}：",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    block.content,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadHeader(detail: ForumThreadDetail) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.typeName.isNotEmpty()) {
                Text(
                    detail.typeName,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .background(
                            runCatching { Color(detail.typeColor.toColorInt()) }
                                .getOrDefault(Color(0xFF666666)),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            detail.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${detail.author} · ${detail.postTime}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${detail.viewCount} 瀏覽 · ${detail.replyCount} 回復",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentsSection(comments: List<Comment>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "點評",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            comments.forEach { comment ->
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    AsyncImage(
                        model = comment.authorAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
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
            }
        }
    }
}

@Composable
private fun ReplyItem(reply: ForumReply, onImageClick: (List<String>, Int) -> Unit, onLinkClick: (String) -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = reply.authorAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        "${reply.floor}# · ${reply.postTime}",
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
                PostContent(
                    blocks = reply.contentBlocks,
                    onImageClick = onImageClick,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
