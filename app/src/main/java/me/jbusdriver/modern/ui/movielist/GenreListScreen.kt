package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.GenreUiModel

/**
 * 类别分组数据类，用于将类别按分组标题归类展示。
 *
 * @property title 分组标题
 * @property genres 该分组下的类别列表
 */
data class GenreCategory(val title: String, val genres: List<GenreUiModel>)

/**
 * 类别列表页面。
 *
 * 职责：以可滚动的分组标签列表展示所有影片类别，每个类别以 AssistChip 形式呈现，
 * 按 [GenreCategory] 分组归类显示。支持下拉刷新。
 *
 * 使用场景：作为主页 Tab 内容区域使用，用户可点击某个类别跳转到该类别下的影片列表。
 * 通常嵌入到 [MainScreen] 的 Tab 页中，通过 [active] 参数控制是否激活数据加载。
 *
 * @param dataSourceType 数据源类型，决定加载哪个分类的类别数据
 * @param active 当前页面是否处于激活（可见）状态，仅在激活时加载数据
 * @param onGenreClick 点击类别标签时的回调
 * @param modifier 应用于根布局的 Modifier
 * @param viewModel 类别列表的 ViewModel，由 Hilt 自动注入
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreListScreen(
    dataSourceType: DataSourceType,
    active: Boolean = true,
    onGenreClick: (GenreUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GenreListViewModel = hiltViewModel()
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

            uiState.error != null && uiState.genreCategories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.genreCategories.forEach { category ->
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            category.genres.forEach { genre ->
                                AssistChip(
                                    onClick = { onGenreClick(genre) },
                                    label = {
                                        Text(
                                            genre.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
