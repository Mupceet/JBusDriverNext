package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.MovieDBType
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.CollectionListScreen
import me.jbusdriver.modern.ui.movielist.GenreListScreen
import me.jbusdriver.modern.ui.movielist.MovieListScreen

data class CategoryOption(
    val group: String,
    val name: String,
    val dataSourceType: DataSourceType? = null,
    val collectionDbType: Int = 0
)

val CategoryOptions = listOf(
    CategoryOption("有码", "电影", DataSourceType.CENSORED),
    CategoryOption("有码", "演员", DataSourceType.ACTRESSES),
//    CategoryOption("有码", "类别", DataSourceType.GENRE),
    CategoryOption("无码", "电影", DataSourceType.UNCENSORED),
    CategoryOption("无码", "演员", DataSourceType.UNCENSORED_ACTRESSES),
//    CategoryOption("无码", "类别", DataSourceType.UNCENSORED_GENRE),
    CategoryOption("收藏", "电影", collectionDbType = MovieDBType),
    CategoryOption("收藏", "演员", collectionDbType = ActressDBType),
)

private val genreTypes = setOf(
    DataSourceType.GENRE,
    DataSourceType.UNCENSORED_GENRE
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    CategoryPagerScreen(
        onMovieClick = onMovieClick,
        onActressClick = onActressClick,
        onGenreClick = onGenreClick,
        onSearchClick = onSearchClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPagerScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { CategoryOptions.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                edgePadding = 8.dp,
                indicator = {
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = false)
                    )
                },
                divider = {},
                modifier = Modifier.weight(1f)
            ) {
                CategoryOptions.forEachIndexed { index, option ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                "${option.group}·${option.name}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val option = CategoryOptions[page]
            val dsType = option.dataSourceType
            val active = pagerState.settledPage == page

            when {
                dsType != null && (dsType == DataSourceType.ACTRESSES || dsType == DataSourceType.UNCENSORED_ACTRESSES) ->
                    ActressListScreen(
                        dataSourceType = dsType,
                        active = active,
                        onActressClick = onActressClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
                dsType != null && dsType in genreTypes ->
                    GenreListScreen(
                        dataSourceType = dsType,
                        active = false,
                        onGenreClick = onGenreClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
                dsType != null ->
                    MovieListScreen(
                        dataSourceType = dsType,
                        active = active,
                        onMovieClick = onMovieClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
                else ->
                    CollectionListScreen(
                        dbType = option.collectionDbType,
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
            }
        }
    }
}
