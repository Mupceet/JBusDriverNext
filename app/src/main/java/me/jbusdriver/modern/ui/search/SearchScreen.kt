package me.jbusdriver.modern.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressAvatar
import me.jbusdriver.modern.ui.movielist.MovieItem
import me.jbusdriver.modern.domain.model.SearchType

/**
 * 搜索页面。
 *
 * 职责：提供搜索输入框和搜索类型选择（影片/演员/编号等），根据搜索类型切换展示
 * 影片列表结果或演员网格结果。支持键盘搜索动作、滚动时自动收起键盘、
 * 自动加载更多以及空状态/错误状态提示。
 *
 * 使用场景：作为 Navigation 图中的一个目标页面，用户通过主页搜索入口导航至此。
 *
 * @param onMovieClick 点击影片条目时的回调
 * @param onActressClick 点击演员条目时的回调
 * @param onBack 返回上一页的回调
 * @param modifier 应用于根布局的 Modifier
 * @param viewModel 搜索页面的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var searchInput by rememberSaveable { mutableStateOf(uiState.query) }

    LaunchedEffect(uiState.query) {
        if (uiState.query != searchInput) {
            searchInput = uiState.query
        }
    }

    fun doSearch() {
        focusManager.clearFocus()
        val query = searchInput.trim()
        if (query.isNotBlank()) {
            viewModel.search(query)
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        // Search input with back button
        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            shape = RoundedCornerShape(25),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            trailingIcon = {
                Text(
                    "搜索",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { doSearch() }
                        .padding(end = 8.dp)
                )
            }
        )

        // Search type chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(SearchType.entries) { type ->
                FilterChip(
                    selected = uiState.searchType == type,
                    onClick = {
                        viewModel.setSearchType(type)
                    },
                    label = { Text(type.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Results
        val isActress = uiState.searchType == SearchType.ACTRESS
        val hasResults = if (isActress) uiState.actressResults.isNotEmpty() else uiState.results.isNotEmpty()

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && !hasResults -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "搜索失败", color = Color.Red)
                }
            }
            !hasResults && uiState.query.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = Color.Gray)
                }
            }
            isActress -> ActressResults(
                uiState = uiState,
                onActressClick = onActressClick,
                onLoadMore = { viewModel.loadMore() },
                onScroll = { focusManager.clearFocus() }
            )
            else -> MovieResults(
                uiState = uiState,
                onMovieClick = onMovieClick,
                onLoadMore = { viewModel.loadMore() },
                onScroll = { focusManager.clearFocus() }
            )
        }
    }
}

/**
 * 搜索结果中的演员网格列表。
 *
 * 职责：以三列网格展示演员搜索结果，支持自动加载更多和触摸/滚动时收起键盘。
 *
 * 使用场景：由 [SearchScreen] 在搜索类型为演员时调用，作为搜索结果区域。
 *
 * @param uiState 搜索页面的 UI 状态
 * @param onActressClick 点击演员条目时的回调
 * @param onLoadMore 加载更多结果的回调
 * @param onScroll 滚动或触摸时的回调，用于收起键盘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActressResults(
    uiState: SearchUiState,
    onActressClick: (ActressUiModel) -> Unit,
    onLoadMore: () -> Unit,
    onScroll: () -> Unit = {}
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) onScroll() }
    }

    LaunchedEffect(gridState, uiState.hasMore) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }.collect { nearEnd ->
            if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            onScroll()
                        }
                    }
                }
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(uiState.actressResults, key = { index, actress -> "${index}_${actress.link}" }) { _, actress ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onActressClick(actress) }
            ) {
                ActressAvatar(
                    avatarUrl = actress.avatar,
                    contentDescription = actress.name,
                    size = 96.dp
                )
                Text(
                    text = actress.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (uiState.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!uiState.hasMore && uiState.actressResults.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("没有更多了", color = Color.Gray)
                }
            }
        }
    }
}

/**
 * 搜索结果中的影片列表。
 *
 * 职责：以 LazyColumn 列表展示影片搜索结果，使用 [MovieItem] 作为条目组件，
 * 支持自动加载更多和触摸/滚动时收起键盘。
 *
 * 使用场景：由 [SearchScreen] 在搜索类型为影片/编号等非演员类型时调用，作为搜索结果区域。
 *
 * @param uiState 搜索页面的 UI 状态
 * @param onMovieClick 点击影片条目时的回调
 * @param onLoadMore 加载更多结果的回调
 * @param onScroll 滚动或触摸时的回调，用于收起键盘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieResults(
    uiState: SearchUiState,
    onMovieClick: (MovieUiModel) -> Unit,
    onLoadMore: () -> Unit,
    onScroll: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) onScroll() }
    }

    LaunchedEffect(listState, uiState.hasMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3
        }.collect { nearEnd ->
            if (nearEnd && uiState.hasMore && !uiState.isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            onScroll()
                        }
                    }
                }
            }
    ) {
        itemsIndexed(uiState.results, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
            MovieItem(movie = movie, onClick = { onMovieClick(movie) })
        }
        if (uiState.isLoadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        }
        if (!uiState.hasMore && uiState.results.isNotEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("没有更多了", color = Color.Gray)
                }
            }
        }
    }
}
