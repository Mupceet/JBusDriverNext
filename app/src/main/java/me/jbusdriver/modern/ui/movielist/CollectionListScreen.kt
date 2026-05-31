package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    LaunchedEffect(dbType, active) {
        if (active) {
            viewModel.loadCollection(dbType)
        }
    }

    // Total count (unfiltered) for empty state message
    val hasItems = if (dbType == MovieDBType) uiState.movieCount > 0 else uiState.actressCount > 0

    when {
        uiState.isLoading && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        uiState.error != null && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
            ErrorView(
                message = "載入失敗，請重試",
                onRetry = { viewModel.loadCollection(dbType) },
                modifier = modifier
            )
        }

        dbType == MovieDBType -> {
            if (uiState.movies.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (hasItems) "沒有匹配的篩選結果" else "還沒有收藏",
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
                    modifier = modifier
                )
            }
        }

        dbType == ActressDBType -> {
            if (uiState.actresses.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (hasItems) "沒有匹配的篩選結果" else "還沒有收藏",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ActressGrid(
                    actresses = uiState.actresses,
                    onActressClick = onActressClick,
                    onActressLongClick = { viewModel.removeActress(it) },
                    modifier = modifier
                )
            }
        }
    }
}
