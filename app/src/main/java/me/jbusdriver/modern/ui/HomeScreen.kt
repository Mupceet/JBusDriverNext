package me.jbusdriver.modern.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.components.CategoryBottomSheet
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.GenreListViewModel
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.movielist.MovieListViewModel

private enum class HomeSegment { MOVIE, ACTRESS }

private enum class CensorFilter(val label: String) {
    CENSORED("有碼"), UNCENSORED("無碼")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var segment by remember { mutableStateOf(HomeSegment.MOVIE) }
    var censorFilter by remember { mutableStateOf(CensorFilter.CENSORED) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedGenres by remember { mutableStateOf<Set<GenreUiModel>>(emptySet()) }
    var isGrid by remember { mutableStateOf(false) }

    val genreViewModel: GenreListViewModel = hiltViewModel()
    val genreState by genreViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(censorFilter) {
        selectedGenres = emptySet()
        val genreType = when (censorFilter) {
            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_GENRE
            else -> DataSourceType.GENRE
        }
        genreViewModel.setDataSourceType(genreType)
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false }
        ) {
            CategoryBottomSheet(
                categories = genreState.genreCategories,
                selectedGenres = selectedGenres,
                onSelectionChange = { selectedGenres = it },
                onDismiss = { showCategorySheet = false },
                onApply = { showCategorySheet = false }
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        Surface(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.search_24px),
                    contentDescription = "搜尋",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "搜索影片、演員、類別...",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        // Segment control
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("影片" to HomeSegment.MOVIE, "演員" to HomeSegment.ACTRESS).forEach { (label, seg) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { segment = seg }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontWeight = if (segment == seg) FontWeight.Bold else FontWeight.Normal,
                        color = if (segment == seg) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    if (segment == seg) {
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp)
                        )
                    }
                }
            }
        }

        // Content (chips are passed as list header so they scroll with content)
        Box(modifier = Modifier.fillMaxSize()) {
            when (segment) {
                HomeSegment.MOVIE -> {
                    val genreUrl = if (selectedGenres.isNotEmpty()) {
                        selectedGenres.joinToString("-") { it.link.trimEnd('/').substringAfterLast("/") }
                            .let { ids ->
                                val base = if (censorFilter == CensorFilter.UNCENSORED) "uncensored/" else ""
                                "/${base}genre/$ids"
                            }
                    } else null

                    val filterChipsHeader: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
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
                            FilterChip(
                                selected = selectedGenres.isNotEmpty(),
                                onClick = { showCategorySheet = true },
                                label = {
                                    Text(
                                        if (selectedGenres.isEmpty()) "類別▾"
                                        else "類別(${selectedGenres.size})",
                                        fontSize = 12.sp
                                    )
                                },
                                trailingIcon = if (selectedGenres.isEmpty()) {
                                    { Icon(painterResource(R.drawable.category_24px), null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { isGrid = !isGrid },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isGrid) R.drawable.view_list_24px else R.drawable.grid_view_24px),
                                    contentDescription = if (isGrid) "列表" else "網格",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (genreUrl != null) {
                        val genreVm: MovieListViewModel = hiltViewModel(key = "genre_$genreUrl")
                        LaunchedEffect(genreUrl) { genreVm.setGenreUrl(genreUrl) }
                        MovieListScreen(
                            active = true,
                            onMovieClick = onMovieClick,
                            compact = true,
                            isGrid = isGrid,
                            modifier = Modifier.fillMaxSize(),
                            viewModel = genreVm,
                            header = filterChipsHeader
                        )
                    } else {
                        val dataSourceType = when (censorFilter) {
                            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED
                            else -> DataSourceType.CENSORED
                        }
                        MovieListScreen(
                            dataSourceType = dataSourceType,
                            active = true,
                            onMovieClick = onMovieClick,
                            compact = true,
                            isGrid = isGrid,
                            modifier = Modifier.fillMaxSize(),
                            header = filterChipsHeader
                        )
                    }
                }
                HomeSegment.ACTRESS -> {
                    val actressType = when (censorFilter) {
                        CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_ACTRESSES
                        else -> DataSourceType.ACTRESSES
                    }
                    ActressListScreen(
                        dataSourceType = actressType,
                        active = true,
                        onActressClick = onActressClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
