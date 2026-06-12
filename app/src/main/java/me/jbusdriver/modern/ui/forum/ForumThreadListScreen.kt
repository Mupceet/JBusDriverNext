package me.jbusdriver.modern.ui.forum

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.ui.components.ScrollToTopButton
import me.jbusdriver.modern.ui.components.ThemedSnackbarHost
import me.jbusdriver.modern.ui.components.rememberScrollToTopVisibility
import me.jbusdriver.modern.ui.RouteForumThreadList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumThreadListScreen(
    fid: Int,
    title: String,
    typeId: Int? = null,
    onThreadClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: ForumThreadListViewModel = hiltViewModel<ForumThreadListViewModel, ForumThreadListViewModel.Factory>(
        creationCallback = { factory -> factory.create(RouteForumThreadList(fid, title, typeId)) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showScrollToTop = rememberScrollToTopVisibility(listState)

    LifecycleResumeEffect(Unit) {
        if (state.threads.isNotEmpty()) viewModel.revalidate()
        onPauseOrDispose { }
    }

    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(nearEnd) {
        if (nearEnd && state.hasMore && !state.isLoadingMore) {
            viewModel.loadMore()
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
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                }
            )
        }
    , snackbarHost = {
        ThemedSnackbarHost(hostState = snackbarHostState)
    }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Type filter chips
                    if (state.typeFilters.isNotEmpty()) {
                        val chipListState = rememberLazyListState()
                        val selectedFilterIndex = state.typeFilters.indexOfFirst { it.typeId == state.currentTypeId }
                        LaunchedEffect(state.typeFilters) {
                            if (selectedFilterIndex >= 0) {
                                // +1 because "全部" chip is at index 0
                                chipListState.scrollToItem(selectedFilterIndex + 1)
                            }
                        }
                        LazyRow(
                            state = chipListState,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = state.currentTypeId == null,
                                    onClick = { viewModel.filterByType(null) },
                                    label = { Text("全部", fontSize = 12.sp) }
                                )
                            }
                            itemsIndexed(state.typeFilters, key = { index, _ -> "typeFilter_$index" }) { _, filter ->
                                FilterChip(
                                    selected = state.currentTypeId == filter.typeId,
                                    onClick = { viewModel.filterByType(filter.typeId) },
                                    label = { Text(filter.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    when {
                        state.threads.isNotEmpty() -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.threads, key = { it.tid }) { thread ->
                                    ThreadCard(thread = thread, onClick = { onThreadClick(thread.tid) })
                                }
                                if (state.isLoadingMore) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                }
            }
            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            )
            LaunchedEffect(state.pendingFreshThreads) {
                if (state.pendingFreshThreads != null) {
                    val result = snackbarHostState.showSnackbar(
                        message = state.refreshMessage ?: "有新數據",
                        actionLabel = "刷新",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.applyPendingFreshThreads()
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(thread: ForumThread, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = thread.authorAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                val trailingLabels = buildList {
                    if (thread.isPinned) {
                        add(ForumInlineLabel("\u7f6e\u9802", MaterialTheme.colorScheme.primary))
                    }
                }
                val trailingIcons = buildList {
                    if (thread.isLocked) {
                        add(
                            ForumInlineIcon(
                                id = "locked",
                                iconRes = R.drawable.lock_24px,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = "closed"
                            )
                        )
                    }
                    if (thread.isDigest) {
                        add(
                            ForumInlineIcon(
                                id = "digest",
                                iconRes = R.drawable.thumb_up_24px,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "recommend"
                            )
                        )
                    }
                    if (thread.isHot) {
                        add(
                            ForumInlineIcon(
                                id = "hot",
                                iconRes = R.drawable.local_fire_department_24px,
                                tint = MaterialTheme.colorScheme.error,
                                contentDescription = "hot"
                            )
                        )
                    }
                }
                ForumThreadTitle(
                    title = thread.title,
                    typeName = thread.typeName,
                    typeColor = thread.typeColor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    badgeStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    trailingLabels = trailingLabels,
                    trailingIcons = trailingIcons
                )
                // Thumbnails
                if (thread.images.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        thread.images.take(3).forEach { imgUrl ->
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 80.dp, height = 55.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                // Meta info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${thread.author} · ${thread.dateLine}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${thread.viewCount} 瀏覽 · ${thread.replyCount} 回復",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
