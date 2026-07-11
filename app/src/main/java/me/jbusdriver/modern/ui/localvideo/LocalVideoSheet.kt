package me.jbusdriver.modern.ui.localvideo

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.LocalVideo

enum class LocalVideoSheetMode { Pick, DeleteMulti }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalVideoSheet(
    videos: List<LocalVideo>,
    mode: LocalVideoSheetMode = LocalVideoSheetMode.Pick,
    onPicked: (LocalVideo) -> Unit = {},
    onSelected: (List<LocalVideo>) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<LocalVideo>() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(if (mode == LocalVideoSheetMode.Pick) R.string.play_local_video else R.string.local_video_delete_menu),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (mode == LocalVideoSheetMode.DeleteMulti) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { selected.clear(); selected.addAll(videos) }) {
                        Text(stringResource(R.string.local_video_select_all))
                    }
                    TextButton(onClick = {
                        val current = selected.toSet()
                        selected.clear(); selected.addAll(videos.filter { it !in current })
                    }) { Text(stringResource(R.string.local_video_invert)) }
                }
            }

            LazyColumn(Modifier.fillMaxWidth()) {
                items(videos, key = { it.uri }) { video ->
                    val isChecked = video in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (mode) {
                                    LocalVideoSheetMode.Pick -> onPicked(video)
                                    LocalVideoSheetMode.DeleteMulti -> {
                                        if (isChecked) selected.remove(video) else selected.add(video)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mode == LocalVideoSheetMode.DeleteMulti) {
                            Checkbox(checked = isChecked, onCheckedChange = {
                                if (it) selected.add(video) else selected.remove(video)
                            })
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            video.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Formatter.formatShortFileSize(context, video.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (mode == LocalVideoSheetMode.DeleteMulti) {
                val totalSize = selected.sumOf { it.size }
                Button(
                    onClick = { onSelected(selected.toList()); selected.clear() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.local_video_delete_selected)
                        else stringResource(R.string.local_video_delete_selected_count, selected.size, totalSize)
                    )
                }
            }
        }
    }
}
