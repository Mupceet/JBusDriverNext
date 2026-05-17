package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.components.CategoryBottomSheet
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.ActressListViewModel
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen
import me.jbusdriver.modern.ui.movielist.GenreListViewModel
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.movielist.MovieListViewModel
import androidx.core.content.edit

enum class BottomNavCategory { MOVIE, ACTRESS, COLLECT }

private enum class CensorFilter(val label: String) {
    CENSORED("有碼"), UNCENSORED("無碼")
}

private data class BottomNavItem(
    val category: BottomNavCategory,
    val label: String,
    val iconRes: Int
)

private val BottomNavItems = listOf(
    BottomNavItem(BottomNavCategory.MOVIE, "影片", R.drawable.movie_24px),
    BottomNavItem(BottomNavCategory.ACTRESS, "演員", R.drawable.person_24px),
    BottomNavItem(BottomNavCategory.COLLECT, "收藏", R.drawable.favorite_24px)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onSearchClick: (String) -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf(BottomNavCategory.MOVIE) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val uiPrefs = remember { JBus.getSharedPreferences("ui_prefs", 0) }
    var isGrid by rememberSaveable {
        mutableStateOf(uiPrefs.getBoolean("is_grid", false))
    }
    val toggleGrid = {
        isGrid = !isGrid
        uiPrefs.edit { putBoolean("is_grid", isGrid) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(modifier = Modifier.height(64.dp)) {
                BottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = selectedCategory == item.category,
                        onClick = { selectedCategory = item.category },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(selectedCategory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedCategory) {
                    BottomNavCategory.MOVIE -> {
                        var censorFilter by rememberSaveable { mutableStateOf(CensorFilter.CENSORED) }
                        var showCategorySheet by rememberSaveable { mutableStateOf(false) }

                        val genreSaver = listSaver<Set<GenreUiModel>, String>(
                            save = { it.map { g -> "${g.name}||${g.link}" } },
                            restore = { it.map { s -> s.split("||", limit = 2).let { p -> GenreUiModel(p[0], p.getOrElse(1) { "" }) } }.toSet() }
                        )
                        val genreMapSaver = listSaver<Map<CensorFilter, Set<GenreUiModel>>, Pair<String, String>>(
                            save = { map -> map.entries.flatMap { (k, v) -> v.map { k.name to "${it.name}||${it.link}" } } },
                            restore = { pairs -> pairs.groupBy({ it.first }, { it.second })
                                .mapKeys { (k, _) -> CensorFilter.valueOf(k) }
                                .mapValues { (_, vals) -> vals.map { s -> s.split("||", limit = 2).let { p -> GenreUiModel(p[0], p.getOrElse(1) { "" }) } }.toSet() } }
                        )
                        var genreMemory by rememberSaveable(stateSaver = genreMapSaver) { mutableStateOf(emptyMap<CensorFilter, Set<GenreUiModel>>()) }
                        var selectedGenres by rememberSaveable(stateSaver = genreSaver) { mutableStateOf(emptySet()) }
                        val moviePagerState = rememberPagerState { CensorFilter.entries.size }

                        val genreViewModel: GenreListViewModel = hiltViewModel()
                        val genreState by genreViewModel.uiState.collectAsStateWithLifecycle()

                        LaunchedEffect(censorFilter) {
                            val genreType = when (censorFilter) {
                                CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_GENRE
                                else -> DataSourceType.GENRE
                            }
                            genreViewModel.setDataSourceType(genreType)
                        }

                        // Sync pager → censorFilter
                        LaunchedEffect(moviePagerState.currentPage) {
                            val filter = CensorFilter.entries[moviePagerState.currentPage]
                            if (censorFilter != filter) {
                                genreMemory = genreMemory + (censorFilter to selectedGenres)
                                censorFilter = filter
                                selectedGenres = genreMemory[filter] ?: emptySet()
                            }
                        }
                        // Sync chip → pager
                        LaunchedEffect(censorFilter) {
                            val target = CensorFilter.entries.indexOf(censorFilter)
                            if (moviePagerState.currentPage != target) {
                                moviePagerState.animateScrollToPage(target)
                            }
                        }

                        if (showCategorySheet) {
                            ModalBottomSheet(
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                onDismissRequest = { showCategorySheet = false }
                            ) {
                                CategoryBottomSheet(
                                    categories = genreState.genreCategories,
                                    selectedGenres = selectedGenres,
                                    onSelectionChange = {
                                        selectedGenres = it
                                        genreMemory = genreMemory + (censorFilter to it)
                                    },
                                )
                            }
                        }

                        // Search bar
                        Surface(
                            onClick = { onSearchClick("") },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search_24px),
                                    contentDescription = "搜尋",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "搜索影片、演員、類別...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Filter chips
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CensorFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = censorFilter == filter,
                                    onClick = {
                                        if (censorFilter != filter) {
                                            genreMemory = genreMemory + (censorFilter to selectedGenres)
                                            censorFilter = filter
                                            selectedGenres = genreMemory[filter] ?: emptySet()
                                        }
                                    },
                                    label = { Text(filter.label, fontSize = 12.sp) }
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            FilterChip(
                                selected = selectedGenres.isNotEmpty(),
                                onClick = { showCategorySheet = true },
                                label = {
                                    Text(
                                        if (selectedGenres.isEmpty()) "類別"
                                        else selectedGenres.joinToString("+") { it.name },
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                trailingIcon = { Icon(painterResource(R.drawable.filter_alt_24px), null, modifier = Modifier.size(16.dp)) }
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { toggleGrid() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isGrid) R.drawable.list_view_24px else R.drawable.grid_view_24px),
                                    contentDescription = if (isGrid) "列表" else "網格",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Movie list pager
                        HorizontalPager(
                            state = moviePagerState,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val filter = CensorFilter.entries[page]
                            val pageGenres = if (filter == censorFilter) selectedGenres else (genreMemory[filter] ?: emptySet())
                            val genreUrl = if (pageGenres.isNotEmpty()) {
                                pageGenres.joinToString("-") { it.link.trimEnd('/').substringAfterLast("/") }
                                    .let { ids ->
                                        val base = if (filter == CensorFilter.UNCENSORED) "uncensored/" else ""
                                        "/${base}genre/$ids"
                                    }
                            } else null

                            if (genreUrl != null) {
                                val genreVm: MovieListViewModel = hiltViewModel(key = "genre_$genreUrl")
                                LaunchedEffect(genreUrl) { genreVm.setGenreUrl(genreUrl) }
                                MovieListScreen(
                                    active = true,
                                    onMovieClick = onMovieClick,
                                    isGrid = isGrid,
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = genreVm
                                )
                            } else {
                                val dataSourceType = when (filter) {
                                    CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED
                                    else -> DataSourceType.CENSORED
                                }
                                val vm: MovieListViewModel = hiltViewModel(key = "pager_$filter")
                                MovieListScreen(
                                    dataSourceType = dataSourceType,
                                    active = true,
                                    onMovieClick = onMovieClick,
                                    isGrid = isGrid,
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = vm
                                )
                            }
                        }
                    }

                    BottomNavCategory.ACTRESS -> {
                        var censorFilter by rememberSaveable { mutableStateOf(CensorFilter.CENSORED) }
                        val actressPagerState = rememberPagerState { CensorFilter.entries.size }

                        LaunchedEffect(actressPagerState.currentPage) {
                            censorFilter = CensorFilter.entries[actressPagerState.currentPage]
                        }
                        LaunchedEffect(censorFilter) {
                            val target = CensorFilter.entries.indexOf(censorFilter)
                            if (actressPagerState.currentPage != target) {
                                actressPagerState.animateScrollToPage(target)
                            }
                        }

                        // Search bar
                        Surface(
                            onClick = { onSearchClick("") },
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search_24px),
                                    contentDescription = "搜尋",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "搜索影片、演員、類別...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Filter chips
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CensorFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = censorFilter == filter,
                                    onClick = { censorFilter = filter },
                                    label = { Text(filter.label, fontSize = 12.sp) }
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                        }

                        // Actress list pager
                        HorizontalPager(
                            state = actressPagerState,
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val filter = CensorFilter.entries[page]
                            val actressType = when (filter) {
                                CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_ACTRESSES
                                else -> DataSourceType.ACTRESSES
                            }
                            val vm: ActressListViewModel = hiltViewModel(key = "actress_$filter")
                            ActressListScreen(
                                dataSourceType = actressType,
                                active = true,
                                onActressClick = onActressClick,
                                modifier = Modifier.fillMaxSize(),
                                viewModel = vm
                            )
                        }
                    }

                    BottomNavCategory.COLLECT -> CollectCategoryScreen(
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick,
                        onGoHome = { selectedCategory = BottomNavCategory.MOVIE }
                    )
                }
            }
        }
    }
}
