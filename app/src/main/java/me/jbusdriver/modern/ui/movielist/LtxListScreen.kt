package me.jbusdriver.modern.ui.movielist

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ScrollableCenteredState
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * 收藏 tab LTX 页：全库列表 + 状态筛选 + 排序 + 可见窗口驱动的站内补全。
 *
 * 展示契约：卡片信息来自站内补全快照（本地收藏同款逻辑），未补全条目
 * 以番号占位卡展示；下载链接通过长按复制或详情页磁力弹窗 LTX 分组获取。
 */
@Composable
fun LtxListScreen(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    viewModel: LtxListViewModel = hiltViewModel(key = "collect_2"),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ltxEnabled by viewModel.ltxEnabled.collectAsStateWithLifecycle()
    val downloadedCodes by viewModel.downloadedCodes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uiPrefsState by hiltViewModel<UiPrefsViewModel>().uiState.collectAsStateWithLifecycle()
    val isGrid = uiPrefsState.isGrid

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(active) {
        if (active) viewModel.start()
    }

    // 滚动驱动补全：首屏 + 滚动位置变化时把可见窗口内未补全条目尾插入队
    LaunchedEffect(active, isGrid, uiState.items.size) {
        if (!active || uiState.items.isEmpty()) return@LaunchedEffect
        viewModel.requestResolveAround(0, 24)
        snapshotFlow {
            if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
        }.collect { index -> viewModel.requestResolveAround(index, 24) }
    }

    when {
        !ltxEnabled -> {
            ScrollableCenteredState(modifier = modifier) {
                Text(
                    stringResource(R.string.ltx_disabled),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        uiState.isLoading -> {
            ScrollableCenteredState(modifier = modifier) {
                CircularProgressIndicator()
            }
        }

        uiState.error != null && uiState.items.isEmpty() -> {
            ErrorView(
                message = stringResource(uiState.error ?: R.string.load_failed),
                onRetry = viewModel::start,
                modifier = modifier
            )
        }

        uiState.items.isEmpty() -> {
            ScrollableCenteredState(modifier = modifier) {
                Text(
                    stringResource(R.string.ltx_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            val pendingLabel = stringResource(R.string.ltx_pending)
            val resolvingLabel = stringResource(R.string.ltx_resolving)
            val tapToResolveLabel = stringResource(R.string.ltx_tap_to_resolve)
            val notFoundLabel = stringResource(R.string.ltx_not_found)
            val noLinksMessage = stringResource(R.string.ltx_no_links)
            val cards = remember(
                uiState.items,
                uiState.resolvingCodes,
                pendingLabel,
                resolvingLabel,
                tapToResolveLabel,
                notFoundLabel,
            ) {
                uiState.items.map {
                    it.toCardModel(
                        pendingLabel = pendingLabel,
                        resolvingLabel = resolvingLabel,
                        tapToResolveLabel = tapToResolveLabel,
                        notFoundLabel = notFoundLabel,
                        isResolving = it.code in uiState.resolvingCodes,
                    )
                }
            }
            val itemsByCode = remember(uiState.items) {
                uiState.items.associateBy { it.code }
            }

            MovieList(
                movies = cards,
                onMovieClick = { movie, censor ->
                    val item = itemsByCode[movie.code.uppercase()]
                    when {
                        // 已补全且持有站内链接 → 进入详情
                        item?.movie != null && movie.link.isNotBlank() ->
                            onMovieClick(movie, censor)

                        // 未补全 → 点击按需补全并回填，完成后再次点击进入详情
                        item != null && item.resolveState == LtxResolveState.UNRESOLVED ->
                            viewModel.resolveNow(item.code)

                        // NOT_FOUND 终态：无操作（角标已提示站内未收录）
                        else -> Unit
                    }
                },
                isDownloaded = { it.code.uppercase() in downloadedCodes },
                isGrid = isGrid,
                header = {
                    LtxListHeader(
                        uiState = uiState,
                        onRefresh = viewModel::refresh,
                    )
                },
                movieLongPressMenu = { movie, close ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ltx_copy_links)) },
                        onClick = {
                            val links = viewModel.linksFor(movie.code)
                            val message = if (links.isNotEmpty()) {
                                context.resources.getQuantityString(
                                    R.plurals.ltx_links_copied, links.size, links.size
                                )
                            } else {
                                noLinksMessage
                            }
                            if (links.isNotEmpty()) {
                                context.copy(links.joinToString("\n") { it.uri })
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            close()
                        }
                    )
                },
                modifier = modifier,
                listState = listState,
                gridState = gridState,
            )
        }
    }
}

/** 精简头部：更新时间 + 补全进度 + 刷新。筛选/排序入口在 chip 行右侧（与其他收藏页一致）。 */
@Composable
private fun LtxListHeader(
    uiState: LtxListUiState,
    onRefresh: () -> Unit,
) {
    val updatedText = remember(uiState.updatedAtMillis) {
        if (uiState.updatedAtMillis > 0) {
            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(uiState.updatedAtMillis))
        } else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (updatedText.isNotBlank()) {
            Text(
                stringResource(R.string.ltx_updated_at, updatedText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.weight(1f))
        if (uiState.resolvingCodes.isNotEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                pluralStringResource(
                    R.plurals.ltx_resolving_count,
                    uiState.resolvingCodes.size,
                    uiState.resolvingCodes.size
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.refresh), fontSize = 12.sp)
        }
    }
}

/** LTX 条目 → MovieList 卡片模型：已补全展示快照，未补全以番号占位（提示点击补全）。 */
private fun LtxItemUiModel.toCardModel(
    pendingLabel: String,
    resolvingLabel: String,
    tapToResolveLabel: String,
    notFoundLabel: String,
    isResolving: Boolean,
): MovieUiModel {
    val tags = buildList {
        add("LTX")
        if (isPending) add(pendingLabel)
        if (resolveState == LtxResolveState.NOT_FOUND) add(notFoundLabel)
        if (resolveState == LtxResolveState.UNRESOLVED) {
            add(if (isResolving) resolvingLabel else tapToResolveLabel)
        }
    }
    val resolved = movie.takeIf { resolveState == LtxResolveState.RESOLVED }
    return if (resolved != null) {
        resolved.copy(tags = tags)
    } else {
        MovieUiModel(
            title = code,
            imageUrl = "",
            code = code,
            date = "",
            link = "",
            tags = tags
        )
    }
}
