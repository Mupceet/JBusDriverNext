package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.ActressUiModel

/**
 * 可复用的演员网格组件。
 *
 * 职责：以自适应列数的网格展示演员头像和名称，支持加载更多和到底提示。
 *
 * 使用场景：被 ActressListScreen、CollectionListScreen、SearchScreen 的演员结果复用。
 *
 * @param actresses 演员数据列表
 * @param hasMore 是否有更多数据可加载
 * @param isLoadingMore 是否正在加载更多
 * @param onLoadMore 滚动到底部时的加载更多回调
 * @param onActressClick 点击演员的回调
 * @param modifier 应用于网格的 Modifier
 */
@Composable
fun ActressGrid(
    actresses: List<ActressUiModel>,
    modifier: Modifier = Modifier,
    hasMore: Boolean = true,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    onActressLongClick: ((ActressUiModel) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showScrollToTop = rememberScrollToTopVisibility(gridState)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 6
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(95.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { header() }
            }
            itemsIndexed(actresses, key = { _, actress -> actress.link }) { _, actress ->
                ActressGridItem(
                    actress = actress,
                    onClick = { onActressClick(actress) },
                    onLongClick = if (onActressLongClick != null) {{ onActressLongClick(actress) }} else null
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            if (!hasMore && actresses.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("沒有更多了", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ScrollToTopButton(
            visible = showScrollToTop,
            onClick = { scope.launch { gridState.animateScrollToItem(0) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
}

/**
 * 演员网格中的单个演员条目，支持长按操作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActressGridItem(
    actress: ActressUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        ActressAvatar(
            avatarUrl = actress.avatar,
            contentDescription = actress.name,
            size = 90.dp,
            onClick = onClick
        )
        Text(
            text = actress.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
