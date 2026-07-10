package me.jbusdriver.modern.ui.movielist

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            val text = if (msg.args.isEmpty()) context.getString(msg.resId)
            else {
                val q = (msg.args.firstOrNull() as? Number)?.toInt() ?: 1
                context.resources.getQuantityString(msg.resId, q, *msg.args.toTypedArray())
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

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
                                inSelectionMode = uiState.uncollectedInSelectionMode,
                                selection = uiState.uncollectedSelection,
                                confirmMessage = context.resources.getQuantityString(
                                    R.plurals.local_video_delete_confirm_message,
                                    uiState.uncollectedSelection.size,
                                    uiState.uncollectedSelection.size,
                                ),
                                onEnterSelection = viewModel::enterUncollectedSelection,
                                onExitSelection = viewModel::exitUncollectedSelection,
                                onToggleSelected = viewModel::toggleUncollectedSelected,
                                onSelectAll = viewModel::selectAllUncollected,
                                onConfirmDelete = viewModel::deleteSelectedUncollected,
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
 * 每个 [MovieUiModel] 为虚拟卡片（[MovieUiModel.isVirtual] = true）；
 * 默认只读，点「清理」进入多选模式后可勾选番号并删除其全部本地视频文件。
 */
@Composable
private fun UncollectedLocalVideoSection(
    videos: List<MovieUiModel>,
    inSelectionMode: Boolean,
    selection: Set<String>,
    confirmMessage: String,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onConfirmDelete: () -> Unit,
    onMovieClick: (MovieUiModel, String?) -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.local_video_delete_confirm_title)) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onConfirmDelete() }) {
                    Text(stringResource(R.string.local_video_delete_selected))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.local_video_show_uncollected),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!inSelectionMode) {
                TextButton(onClick = onEnterSelection) { Text(stringResource(R.string.local_video_cleanup)) }
            } else {
                Row {
                    TextButton(onClick = onSelectAll) { Text(stringResource(R.string.local_video_select_all)) }
                    TextButton(onClick = onExitSelection) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = { if (selection.isNotEmpty()) showConfirm = true },
                        enabled = selection.isNotEmpty(),
                    ) { Text(stringResource(R.string.local_video_delete_selected)) }
                }
            }
        }
        videos.forEach { v ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inSelectionMode) {
                    Checkbox(checked = v.code in selection, onCheckedChange = { onToggleSelected(v.code) })
                }
                Box(Modifier.weight(1f)) {
                    MovieItem(
                        movie = v,
                        onClick = {
                            if (inSelectionMode) onToggleSelected(v.code) else onMovieClick(v, null)
                        },
                        isVirtual = true,
                    )
                }
            }
        }
    }
}
