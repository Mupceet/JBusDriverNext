package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

@Composable
fun CollectionListScreen(
    dbType: Int,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onMovieClick: (MovieUiModel) -> Unit = {},
    onActressClick: (ActressUiModel) -> Unit = {},
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isGrid by hiltViewModel<UiPrefsViewModel>().store.isGrid.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(dbType, active) {
        if (active) {
            viewModel.loadCollection(dbType)
        }
    }

    // Show filter button when there are items (use total count, not filtered)
    val hasItems = if (dbType == MovieDBType) uiState.movieCount > 0 else uiState.actressCount > 0

    Column(modifier = modifier.fillMaxSize()) {
        // Filter bar
        if (hasItems) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filterState.hasActiveFilters,
                    onClick = { showFilterSheet = true },
                    label = {
                        if (uiState.filterState.hasActiveFilters) {
                            Text("筛选 (${uiState.filterState.activeFilterCount})", fontSize = 12.sp)
                        } else {
                            Text("筛选", fontSize = 12.sp)
                        }
                    }
                )
            }
        }

        // Content
        when {
            uiState.isLoading && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
                ErrorView(
                    message = "載入失敗，請重試",
                    onRetry = { viewModel.loadCollection(dbType) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            dbType == MovieDBType -> {
                if (uiState.movies.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (hasItems) "没有匹配的筛选结果" else "還沒有收藏",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MovieList(
                        movies = uiState.movies,
                        onMovieClick = onMovieClick,
                        isCollected = { true },
                        onToggleCollect = { viewModel.removeMovie(it) },
                        isGrid = isGrid,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            dbType == ActressDBType -> {
                if (uiState.actresses.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (hasItems) "没有匹配的筛选结果" else "還沒有收藏",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    ActressGrid(
                        actresses = uiState.actresses,
                        onActressClick = onActressClick,
                        onActressLongClick = { viewModel.removeActress(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        CollectionFilterSheet(
            dbType = dbType,
            filterState = uiState.filterState,
            availableYears = uiState.availableYears,
            onFilterChange = { viewModel.updateFilter(it) },
            onDismiss = { showFilterSheet = false }
        )
    }
}
