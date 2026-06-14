package me.jbusdriver.modern.ui.forum

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumSummaryThread
import me.jbusdriver.modern.ui.components.EmptyStateView
import me.jbusdriver.modern.ui.components.LoadingViewCentered
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost

private val TabLabels =
    listOf(R.string.forum_tab_latest, R.string.forum_tab_latest_reply, R.string.forum_tab_hot)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumBoardsScreen(
    onBoardClick: (ForumBoard) -> Unit,
    onThreadClick: (Int) -> Unit
) {
    val viewModel: ForumBoardsViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        if (state.groups.isNotEmpty()) viewModel.revalidate()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        val refreshMessageRes = state.refreshMessage
        val refreshMessage = refreshMessageRes?.let { stringResource(it) }
        LaunchedEffect(refreshMessageRes) {
            if (refreshMessage != null) {
                snackbarHostState.showSnackbar(
                    message = refreshMessage,
                    duration = androidx.compose.material3.SnackbarDuration.Long
                )
                viewModel.consumeRefreshMessage()
            }
        }
        if (state.groups.isNotEmpty()) {
            ForumHomeContent(
                state = state,
                onBoardClick = onBoardClick,
                onThreadClick = onThreadClick
            )
        } else {
            if (state.isLoading) {
                LoadingViewCentered()
            } else {
                EmptyStateView(message = state.error?.let { stringResource(it) })
            }
        }
        if (state.isRevalidating && state.groups.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }
        ThemedSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ForumHomeContent(
    state: ForumBoardsUiState,
    onBoardClick: (ForumBoard) -> Unit,
    onThreadClick: (Int) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Carousel
        if (state.banners.isNotEmpty()) {
            item(key = "carousel") {
                BannerCarousel(
                    banners = state.banners,
                    onClick = onThreadClick
                )
            }
        }
        // Insert tabbed summary
        item(key = "summary_tabs") {
            SummarySection(
                summary = state.summary,
                onThreadClick = onThreadClick
            )
        }

        // Board groups with tabbed summary inserted between groups
        state.groups.forEachIndexed { groupIndex, group ->
            item(key = "group_header_$groupIndex") {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                )
            }
            items(group.boards, key = { "board_${groupIndex}_${it.id}_${it.typeId}" }) { board ->
                BoardCard(board = board, onClick = { onBoardClick(board) })
            }
        }
    }
}

@Composable
private fun BannerCarousel(
    banners: List<ForumBanner>,
    onClick: (Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { banners.size })
    val userInteracting = remember { mutableStateOf(false) }

    LaunchedEffect(banners.size) {
        while (true) {
            delay(4000)
            if (!userInteracting.value) {
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) { page ->
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick(banner.tid) }
            ) {
                AsyncImage(
                    model = banner.imageUrl,
                    contentDescription = banner.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(56.dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
        )
        // Dots row above text
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(banners.size) { i ->
                Box(
                    modifier = Modifier
                        .width(if (i == pagerState.currentPage) 12.dp else 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i == pagerState.currentPage) Color.White else Color.White.copy(
                                alpha = 0.4f
                            )
                        )
                )
            }
        }
        // Title text at very bottom
        Text(
            banners[pagerState.currentPage].title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        )
        // Detect user drag via snapshotFlow
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPageOffsetFraction }
                .collect { offset ->
                    val dragging = kotlin.math.abs(offset) > 0.01f
                    if (dragging && !userInteracting.value) {
                        userInteracting.value = true
                    }
                }
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { userInteracting.value = false }
        }
    }
}

@Composable
private fun SummarySection(
    summary: me.jbusdriver.modern.domain.model.ForumHomeSummary,
    onThreadClick: (Int) -> Unit
) {
    val threadsLists = listOf(summary.latestThreads, summary.latestReplies, summary.hotTopics)
    val pagerState = rememberPagerState(pageCount = { TabLabels.size })
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                TabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        text = {
                            Text(
                                stringResource(label),
                                fontSize = 13.sp,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { page ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    threadsLists[page].forEachIndexed { index, thread ->
                        SummaryThreadItem(
                            thread = thread,
                            index = index,
                            isLast = index == threadsLists[page].lastIndex,
                            onClick = { onThreadClick(thread.tid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryThreadItem(
    thread: ForumSummaryThread,
    index: Int,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val rank = index + 1
    val bgColor = when (rank) {
        1 -> Color(0xFFFF6B6B)
        2 -> Color(0xFFFF9F43)
        3 -> Color(0xFFFED330)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                thread.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BoardCard(board: ForumBoard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    board.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (board.todayPosts > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(${board.todayPosts})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (board.description.isNotEmpty()) {
                Text(
                    board.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (board.lastPost.title.isNotEmpty()) {
                Text(
                    "${board.lastPost.title} · ${board.lastPost.author} · ${board.lastPost.time}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
