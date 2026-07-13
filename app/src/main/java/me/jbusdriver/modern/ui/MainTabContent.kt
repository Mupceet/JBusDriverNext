package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.components.CategoryBottomSheet
import me.jbusdriver.modern.ui.components.SearchBar
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.ActressListViewModel
import me.jbusdriver.modern.ui.movielist.GenreListViewModel
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.movielist.MovieListViewModel

internal enum class CensorFilter(val labelRes: Int) {
    CENSORED(R.string.censored), UNCENSORED(R.string.uncensored)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieTabContent(
    isGrid: Boolean,
    toggleGrid: () -> Unit,
    onSearchClick: (String) -> Unit,
    onMovieClick: (MovieUiModel, String?) -> Unit
) {
    var censorFilter by rememberSaveable { mutableStateOf(CensorFilter.CENSORED) }
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var selectedGenreLinks by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var genreLinkMemory by rememberSaveable { mutableStateOf(emptyMap<String, Set<String>>()) }
    val moviePagerState = rememberPagerState { CensorFilter.entries.size }

    val genreViewModel: GenreListViewModel = hiltViewModel()
    val genreState by genreViewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        if (genreState.genreCategories.isNotEmpty()) genreViewModel.revalidate()
        onPauseOrDispose { }
    }

    LaunchedEffect(censorFilter) {
        val genreType = when (censorFilter) {
            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_GENRE
            else -> DataSourceType.GENRE
        }
        genreViewModel.setDataSourceType(genreType)
    }

    LaunchedEffect(moviePagerState.currentPage) {
        val filter = CensorFilter.entries[moviePagerState.currentPage]
        if (censorFilter != filter) {
            genreLinkMemory = genreLinkMemory + (censorFilter.name to selectedGenreLinks)
            censorFilter = filter
            selectedGenreLinks = genreLinkMemory[filter.name] ?: emptySet()
        }
    }
    LaunchedEffect(censorFilter) {
        val target = CensorFilter.entries.indexOf(censorFilter)
        if (moviePagerState.currentPage != target) {
            moviePagerState.animateScrollToPage(target)
        }
    }

    if (showCategorySheet) {
        val allGenres = genreState.genreCategories.flatMap { it.genres.orEmpty() }
        val selectedGenres = allGenres.filter { it.link in selectedGenreLinks }.toSet()

        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showCategorySheet = false }
        ) {
            CategoryBottomSheet(
                categories = genreState.genreCategories,
                selectedGenres = selectedGenres,
                onSelectionChange = { newSelection ->
                    selectedGenreLinks = newSelection.map { it.link }.toSet()
                    genreLinkMemory =
                        genreLinkMemory + (censorFilter.name to newSelection.map { it.link }
                            .toSet())
                },
            )
        }
    }

    SearchBar(
        onClick = { onSearchClick("") },
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CensorFilter.entries.forEach { filter ->
            FilterChip(
                selected = censorFilter == filter,
                onClick = {
                    if (censorFilter != filter) {
                        genreLinkMemory =
                            genreLinkMemory + (censorFilter.name to selectedGenreLinks)
                        censorFilter = filter
                        selectedGenreLinks = genreLinkMemory[filter.name] ?: emptySet()
                    }
                },
                label = { Text(stringResource(filter.labelRes), fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
        }
        Spacer(Modifier.weight(1f))
        val allGenresForChip = genreState.genreCategories.flatMap { it.genres.orEmpty() }
        val selectedGenresForChip =
            allGenresForChip.filter { it.link in selectedGenreLinks }.toSet()
        FilterChip(
            selected = selectedGenreLinks.isNotEmpty(),
            onClick = { showCategorySheet = true },
            label = {
                Text(
                    if (selectedGenreLinks.isEmpty()) stringResource(R.string.genre)
                    else selectedGenresForChip.joinToString("+") { it.name },
                    fontSize = 12.sp,
                    maxLines = 1
                )
            },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.filter_alt_24px),
                    null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { toggleGrid() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(if (isGrid) R.drawable.list_view_24px else R.drawable.grid_view_24px),
                contentDescription = if (isGrid) stringResource(R.string.list_view) else stringResource(
                    R.string.grid
                ),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalPager(
        state = moviePagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val filter = CensorFilter.entries[page]
        val censorType = when (filter) {
            CensorFilter.UNCENSORED -> "UNCENSORED"
            else -> null
        }
        val pageLinks =
            if (filter == censorFilter) selectedGenreLinks else (genreLinkMemory[filter.name]
                ?: emptySet())
        val genreUrl = if (pageLinks.isNotEmpty()) {
            pageLinks.joinToString("-") { it.trimEnd('/').substringAfterLast("/") }
                .let { ids ->
                    val base = if (filter == CensorFilter.UNCENSORED) "uncensored/" else ""
                    "/${base}genre/$ids"
                }
        } else null

        if (genreUrl != null) {
            val genreVm: MovieListViewModel = hiltViewModel(key = "genre_$genreUrl")
            val active = page == moviePagerState.settledPage
            LaunchedEffect(genreUrl, active) {
                if (active) genreVm.setGenreUrl(genreUrl)
            }
            MovieListScreen(
                dataSourceType = null,
                active = active,
                onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
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
                active = page == moviePagerState.settledPage,
                onMovieClick = { movie, _ -> onMovieClick(movie, censorType) },
                modifier = Modifier.fillMaxSize(),
                viewModel = vm
            )
        }
    }
}

@Composable
internal fun ActressTabContent(
    onSearchClick: (String) -> Unit,
    onActressClick: (ActressUiModel, String?) -> Unit
) {
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

    SearchBar(
        onClick = { onSearchClick("") },
        modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 14.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CensorFilter.entries.forEach { filter ->
            FilterChip(
                selected = censorFilter == filter,
                onClick = { censorFilter = filter },
                label = { Text(stringResource(filter.labelRes), fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }

    HorizontalPager(
        state = actressPagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val filter = CensorFilter.entries[page]
        val actressCensorType = when (filter) {
            CensorFilter.UNCENSORED -> "UNCENSORED"
            else -> null
        }
        val actressType = when (filter) {
            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_ACTRESSES
            else -> DataSourceType.ACTRESSES
        }
        val vm: ActressListViewModel = hiltViewModel(key = "actress_$filter")
        ActressListScreen(
            dataSourceType = actressType,
            active = page == actressPagerState.settledPage,
            onActressClick = { actress, _ -> onActressClick(actress, actressCensorType) },
            modifier = Modifier.fillMaxSize(),
            viewModel = vm
        )
    }
}
