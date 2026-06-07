package me.jbusdriver.modern.ui.forum

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.components.ScrollToTopButton
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
                    IconButton(onClick = {
                        val url =
                            "${me.jbusdriver.modern.core.http.NetClient.defaultFastUrl}/forum/forum.php?mod=viewthread&tid=$tid"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享主題"))
                    }) {
                        Icon(painterResource(R.drawable.share_24px), contentDescription = "分享")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
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
                                    ReplyItem(
                                        reply = detail.replies[index],
                                        onImageClick = onImageClick,
                                        loadedGifUrls = loadedGifUrls,
                                        autoLoadGifs = autoLoadGifs,
                                        onLoadGif = { viewModel.onLoadGif(it) },
                                        onLoadAllGifs = { viewModel.onLoadAllGifs() },
                                        onLongClick = { dialogBlocks = detail.replies[index].contentBlocks }
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

                    state.isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) { CircularProgressIndicator() }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error ?: "內容為空",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (state.error != null) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "下拉刷新重試",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
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
private fun ReplyItem(
    reply: ForumReply,
    onImageClick: (List<String>, Int) -> Unit,
    loadedGifUrls: Set<String> = emptySet(),
    autoLoadGifs: Boolean = false,
    onLoadGif: (String) -> Unit = {},
    onLoadAllGifs: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        "${forumFloorLabel(reply.floor, reply.isPinned)} · ${reply.postTime}",
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
                ForumPostContent(
                    blocks = reply.contentBlocks,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(top = 4.dp),
                    loadedGifUrls = loadedGifUrls,
                    autoLoadGifs = autoLoadGifs,
                    onLoadGif = onLoadGif,
                    onLoadAllGifs = onLoadAllGifs,
                    onLongClick = onLongClick
                )
            }
        }
    }
}

@Composable
private fun FloorContentDialog(
    blocks: List<ContentBlock>,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "內容預覽",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                )

                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    ForumPostContent(
                        blocks = blocks,
                        onImageClick = { _, _ -> },
                        showImages = false
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("關閉")
                    }
                    TextButton(onClick = onCopyAll) {
                        Text("複製全部")
                    }
                }
            }
        }
    }
}
