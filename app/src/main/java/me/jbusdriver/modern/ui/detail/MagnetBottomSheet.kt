package me.jbusdriver.modern.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.ui.MagnetUiModel

/**
 * 磁力链接底部弹窗。
 *
 * 以 ModalBottomSheet 展示影片的磁力链接列表，支持分页加载更多。
 * 点击磁力链接项会将链接复制到剪贴板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MagnetBottomSheet(
    uiState: MovieDetailUiState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.618f)
        ) {
            Text(
                stringResource(R.string.magnet_links),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    uiState.isLoadingMagnets && uiState.magnets.isEmpty() -> {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    uiState.magnetsError != null && uiState.magnets.isEmpty() -> {
                        item {
                            Text(
                                stringResource(uiState.magnetsError),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    uiState.magnets.isEmpty() -> {
                        item {
                            Text(
                                stringResource(R.string.no_magnet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        items(uiState.magnets, key = { it.link }) { magnet ->
                            MagnetItem(magnet = magnet, context = context)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单条磁力链接的可组合项。
 *
 * 展示磁力链接的名称、大小和日期信息，提供「复制」和「打开」两个独立操作。
 */
@Composable
private fun MagnetItem(magnet: MagnetUiModel, context: Context) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (magnet.link.isNotBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, magnet.link.toUri())
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.select_download)
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.no_handler),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = magnet.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (magnet.size.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                magnet.size,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (magnet.date.isNotBlank()) {
                        Text(
                            magnet.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    if (magnet.link.isNotBlank()) {
                        context.copy(magnet.link)
                        Toast.makeText(
                            context,
                            context.getString(R.string.copied_magnet),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Icon(
                    painterResource(
                        R.drawable.content_copy_24px
                    ),
                    contentDescription = stringResource(R.string.copy_link),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
