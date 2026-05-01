package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.MovieDBType

/**
 * 收藏列表页面。
 *
 * 职责：根据 [dbType] 展示用户收藏的影片或演员列表。影片收藏以 LazyColumn 列表形式展示，
 * 演员收藏以三列网格形式展示。数据来源于本地 Room 数据库。
 *
 * 使用场景：作为主页收藏 Tab 的内容区域，根据子 Tab 选择（影片收藏/演员收藏）切换展示内容。
 * 通过 [active] 参数控制是否激活数据加载。
 *
 * @param dbType 数据库类型标识，区分影片收藏和演员收藏
 * @param active 当前页面是否处于激活（可见）状态，仅在激活时加载数据
 * @param onMovieClick 点击影片条目时的回调（影片收藏模式下使用）
 * @param onActressClick 点击演员条目时的回调（演员收藏模式下使用）
 * @param modifier 应用于根布局的 Modifier
 * @param viewModel 收藏列表的 ViewModel，由 Hilt 自动注入
 */
@Composable
fun CollectionListScreen(
    dbType: Int,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(dbType, active) {
        if (active) {
            viewModel.loadCollection(dbType)
        }
    }

    when {
        uiState.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
            }
        }
        dbType == MovieDBType -> {
            if (uiState.movies.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = modifier.fillMaxSize()) {
                    itemsIndexed(uiState.movies, key = { index, movie -> "${index}_${movie.link}" }) { _, movie ->
                        MovieItem(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            }
        }
        dbType == ActressDBType -> {
            if (uiState.actresses.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(uiState.actresses, key = { index, actress -> "${index}_${actress.link}" }) { _, actress ->
                        ActressGridItem(actress = actress, onClick = { onActressClick(actress) })
                    }
                }
            }
        }
    }
}

/**
 * 收藏列表中的演员网格条目。
 *
 * 职责：以圆形头像加名称的形式展示单个演员信息，点击触发跳转。
 *
 * 使用场景：作为 [CollectionListScreen] 中演员收藏模式的 LazyVerticalGrid 单个条目。
 *
 * @param actress 演员的 UI 数据模型
 * @param onClick 点击条目时的回调
 */
@Composable
private fun ActressGridItem(
    actress: ActressUiModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = actress.avatar,
            contentDescription = actress.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = actress.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}