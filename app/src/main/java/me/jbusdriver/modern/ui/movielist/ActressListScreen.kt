package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.components.ActressAvatar
import me.jbusdriver.modern.domain.model.DataSourceType

/**
 * 演员列表页面。
 *
 * 职责：以三列网格布局展示演员头像和名称，支持下拉刷新和自动加载更多（滚动到底部触发）。
 * 根据 [DataSourceType] 加载不同分类的演员数据。
 *
 * 使用场景：作为主页 Tab 内容区域使用，根据用户选择的数据源类型展示对应演员列表。
 * 通常嵌入到 [MainScreen] 的 Tab 页中，通过 [active] 参数控制是否激活数据加载。
 *
 * @param dataSourceType 数据源类型，决定加载哪个分类的演员
 * @param active 当前页面是否处于激活（可见）状态，仅在激活时加载数据
 * @param onActressClick 点击演员条目时的回调
 * @param modifier 应用于根布局的 Modifier
 * @param viewModel 演员列表的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActressListScreen(
    dataSourceType: DataSourceType,
    active: Boolean = true,
    onActressClick: (ActressUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ActressListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dataSourceType, active) {
        if (active) {
            viewModel.setDataSourceType(dataSourceType)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.actresses.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                val gridState = rememberLazyGridState()

                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = gridState.layoutInfo.totalItemsCount
                        lastVisible >= totalItems - 6
                    }.collect { nearEnd ->
                        if (nearEnd) {
                            viewModel.loadMore()
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.actresses, key = { _, actress -> actress.link }) { _, actress ->
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
                    if (!uiState.hasMore && uiState.actresses.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("没有更多了", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
