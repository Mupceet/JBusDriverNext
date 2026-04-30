package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import me.jbusdriver.modern.ui.search.SearchScreen

data class CategoryOption(
    val group: String,
    val name: String,
    val dataSourceType: DataSourceType? = null,
    val collectionDbType: Int = 0
)

val CategoryOptions = listOf(
    CategoryOption("有码", "电影", DataSourceType.CENSORED),
    CategoryOption("有码", "演员", DataSourceType.ACTRESSES),
    CategoryOption("有码", "类别", DataSourceType.GENRE),
    CategoryOption("无码", "电影", DataSourceType.UNCENSORED),
    CategoryOption("无码", "演员", DataSourceType.UNCENSORED_ACTRESSES),
    CategoryOption("无码", "类别", DataSourceType.UNCENSORED_GENRE),
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
    onGenreClick: (GenreUiModel) -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = { },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    label = { Text("搜索") },
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 }
                )
            }
        }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> CategoryPagerScreen(
                onMovieClick = onMovieClick,
                onActressClick = onActressClick,
                onGenreClick = onGenreClick,
                modifier = Modifier.padding(padding)
            )
            1 -> SearchScreen(
                onMovieClick = onMovieClick,
                onActressClick = onActressClick,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPagerScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onGenreClick: (GenreUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0) { CategoryOptions.size }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage])
                )
            },
            divider = {}
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val option = CategoryOptions[page]
            val dsType = option.dataSourceType

            when {
                dsType != null && (dsType == DataSourceType.ACTRESSES || dsType == DataSourceType.UNCENSORED_ACTRESSES) ->
                    ActressListScreen(
                        dataSourceType = dsType,
                        onActressClick = onActressClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
                dsType != null && dsType in genreTypes ->
                    GenreListScreen(
                        dataSourceType = dsType,
                        onGenreClick = onGenreClick,
                        viewModel = hiltViewModel(key = "page_$page")
                    )
                dsType != null ->
                    MovieListScreen(
                        dataSourceType = dsType,
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
