package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.domain.model.MovieCategory
import me.jbusdriver.modern.domain.model.UncensoredMovieCategory
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ScrollableCenteredState
import me.jbusdriver.modern.ui.components.SelectableDropdownItem
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

@Composable
fun CollectionListScreen(
    dbType: Int,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadedCodes by viewModel.downloadedCodes.collectAsStateWithLifecycle()
    val uiPrefsState by hiltViewModel<UiPrefsViewModel>().uiState.collectAsStateWithLifecycle()
    val isGrid = uiPrefsState.isGrid

    LaunchedEffect(dbType, active) {
        if (active) {
            viewModel.loadCollection(dbType)
        }
    }

    // Total count (unfiltered) for empty state message
    val hasItems = if (dbType == MovieDBType) uiState.movieCount > 0 else uiState.actressCount > 0

    when {
        uiState.isLoading && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
            ScrollableCenteredState(modifier = modifier) {
                CircularProgressIndicator()
            }
        }

        uiState.error != null && uiState.movies.isEmpty() && uiState.actresses.isEmpty() -> {
            ErrorView(
                message = stringResource(uiState.error ?: R.string.load_failed),
                onRetry = { viewModel.loadCollection(dbType) },
                modifier = modifier
            )
        }

        dbType == MovieDBType -> {
            // 即便没有已收藏影片，只要开启了「显示未收藏本地视频」且有数据，也要渲染列表（含未收藏分区），
            // 否则 0 收藏时会进入空状态分支，未收藏本地视频永远不显示。
            val uncollected = if (uiState.showUncollectedLocal) uiState.uncollectedVideos else emptyList()
            if (uiState.movies.isEmpty() && uncollected.isEmpty()) {
                ScrollableCenteredState(modifier = modifier) {
                    Text(
                        if (hasItems) stringResource(R.string.no_filter_match) else stringResource(R.string.no_collection_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                MovieList(
                    movies = uiState.movies,
                    onMovieClick = onMovieClick,
                    isCollected = { true },
                    onToggleCollect = { viewModel.removeMovie(it) },
                    isDownloaded = { it.code.uppercase() in downloadedCodes },
                    isGrid = isGrid,
                    footerHeader = {
                        if (uncollected.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.local_video_uncollected_section_count,
                                    uncollected.size,
                                    uncollected.size,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp),
                            )
                        }
                    },
                    footerMovies = uncollected,
                    movieLongPressMenu = { movie, close ->
                        val isUncensored = movie.categoryId == UncensoredMovieCategory.id
                        SelectableDropdownItem(
                            label = stringResource(R.string.mark_as_censored),
                            selected = !isUncensored,
                            onClick = {
                                viewModel.setMovieCategory(movie, MovieCategory.id ?: 1)
                                close()
                            }
                        )
                        SelectableDropdownItem(
                            label = stringResource(R.string.mark_as_uncensored),
                            selected = isUncensored,
                            onClick = {
                                viewModel.setMovieCategory(movie, UncensoredMovieCategory.id ?: 3)
                                close()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uncollect_action)) },
                            onClick = {
                                viewModel.removeMovie(movie)
                                close()
                            }
                        )
                    },
                    modifier = modifier
                )
            }
        }

        dbType == ActressDBType -> {
            if (uiState.actresses.isEmpty()) {
                ScrollableCenteredState(modifier = modifier) {
                    Text(
                        if (hasItems) stringResource(R.string.no_filter_match) else stringResource(R.string.no_collection_yet),
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
