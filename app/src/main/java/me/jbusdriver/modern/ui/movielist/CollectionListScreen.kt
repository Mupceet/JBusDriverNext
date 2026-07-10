package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ActressGrid
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieItem
import me.jbusdriver.modern.ui.components.MovieList
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
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            if (uiState.movies.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    footer = {
                        if (uiState.filterState.showUncollectedLocal && uiState.uncollectedVideos.isNotEmpty()) {
                            UncollectedLocalVideoSection(
                                videos = uiState.uncollectedVideos,
                                onMovieClick = onMovieClick,
                            )
                        }
                    },
                    modifier = modifier
                )
            }
        }

        dbType == ActressDBType -> {
            if (uiState.actresses.isEmpty()) {
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

/**
 * 收藏页底部的「未收藏本地视频」分区。
 *
 * 每个 [MovieUiModel] 为虚拟卡片（[MovieUiModel.isVirtual] = true），以虚线边框区分；
 * 点击后 [onMovieClick] 以番号作为 movie URL 路径进入详情页。
 */
@Composable
private fun UncollectedLocalVideoSection(
    videos: List<MovieUiModel>,
    onMovieClick: (MovieUiModel, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(R.string.local_video_show_uncollected),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        videos.forEach { v ->
            MovieItem(
                movie = v,
                onClick = { onMovieClick(v, null) },
                isVirtual = true,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
