package me.jbusdriver.modern.ui.movielist

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.copy
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.components.ErrorView
import me.jbusdriver.modern.data.repository.LTX_CACHE_TTL_MILLIS
import me.jbusdriver.modern.ui.components.MovieList
import me.jbusdriver.modern.ui.components.ScrollableCenteredState
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import java.text.DateFormat
import java.util.Date

/**
 * 影片 tab LTX 页：全库列表 + 状态筛选 + 排序 + 可见窗口驱动的站内补全。
 *
 * 展示契约：卡片信息来自站内补全快照（本地收藏同款逻辑），未补全条目
 * 以番号占位卡展示；下载链接通过长按复制或详情页磁力弹窗 LTX 分组获取。
 */
@Composable
fun LtxListScreen(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onMovieClick: (MovieUiModel, String?) -> Unit = { _, _ -> },
    viewModel: LtxListViewModel = hiltViewModel(key = "movie_ltx"),
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

    // 人机验证人工介入：仓库发射请求后弹可见验证窗口（弹窗开合是 UI 局部状态）
    var showVerification by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.verificationRequests.collect { showVerification = true }
    }
    if (showVerification) {
        LtxVerificationDialog(viewModel = viewModel, onDismiss = { showVerification = false })
    }

    // 下拉刷新：缓存新鲜时强刷全库流量大，先弹窗确认（确认框同为 UI 局部状态）
    var showRefreshConfirm by rememberSaveable { mutableStateOf(false) }
    var cachedBytes by remember { mutableStateOf(0L) }
    if (showRefreshConfirm) {
        LaunchedEffect(Unit) { cachedBytes = viewModel.cachedPayloadBytes() }
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
            title = { Text(stringResource(R.string.ltx_refresh_confirm_title)) },
            text = {
                Text(
                    if (cachedBytes > 0L) {
                        stringResource(
                            R.string.ltx_refresh_confirm_hint_sized,
                            Formatter.formatShortFileSize(context, cachedBytes)
                        )
                    } else {
                        stringResource(R.string.ltx_refresh_confirm_hint)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRefreshConfirm = false
                    viewModel.refresh()
                }) {
                    Text(stringResource(R.string.refresh))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 滚动驱动的补全分两层，兼顾「边滑边补全」与「停留优先」：
    // 1) 途经发现：滑动过程中节流触发（150ms），把当前窗口内的未补全条目按屏幕
    //    顺序排队补全——快速滑动途经的条目也会持续入队，接近其它页面边滑边加载的体验；
    // 2) 停留插队：滑动停止（索引 400ms 无变化）后把停留窗口内的未补全条目整体
    //    插到队首，保证停留界面始终优先于途经与批量任务完成。
    // key 用 items 列表本身（按内容判等）：筛选/排序切换会整体替换条目而尺寸未必变化，
    // 仅以 size 为 key 时新窗口的未补全条目不会触发补全，需要滑动一下才恢复。
    // snapshotFlow 订阅即发射当前值，页面激活/列表变化重启后都从当前位置出发。
    LaunchedEffect(active, isGrid, uiState.items) {
        if (!active || uiState.items.isEmpty()) return@LaunchedEffect
        val scrollIndexFlow = if (isGrid) {
            snapshotFlow { gridState.firstVisibleItemIndex }
        } else {
            snapshotFlow { listState.firstVisibleItemIndex }
        }
        // MovieList 在 item 0 放页头，items 列表索引相对 lazy 索引整体偏移 1
        fun toItemsIndex(lazyIndex: Int): Int = (lazyIndex - 1).coerceAtLeast(0)
        launch {
            var lastDiscoveryMark: TimeMark? = null
            scrollIndexFlow.collect { lazyIndex ->
                val mark = lastDiscoveryMark
                if (mark == null ||
                    mark.elapsedNow().inWholeMilliseconds >= LTX_RESOLVE_DISCOVERY_THROTTLE_MS
                ) {
                    lastDiscoveryMark = TimeSource.Monotonic.markNow()
                    viewModel.requestResolveVisible(toItemsIndex(lazyIndex), LTX_RESOLVE_VIEWPORT)
                }
            }
        }
        launch {
            scrollIndexFlow.collectLatest { lazyIndex ->
                delay(LTX_RESOLVE_SETTLE_DEBOUNCE_MS)
                viewModel.requestResolveSettled(toItemsIndex(lazyIndex), LTX_RESOLVE_VIEWPORT)
            }
        }
    }

    PullToRefreshBox(
        // 首次加载（状态未明）用全屏转圈，避免与下拉指示圈叠显；
        // 状态明确后的加载一律由下拉指示圈表达
        isRefreshing = uiState.isRefreshing && !uiState.isLoading,
        onRefresh = {
            if (!ltxEnabled) return@PullToRefreshBox
            // 缓存新鲜时强刷全库流量大（单包快照），先弹窗确认再强刷
            val stale = uiState.cacheStoredAtMillis <= 0L ||
                System.currentTimeMillis() - uiState.cacheStoredAtMillis > LTX_CACHE_TTL_MILLIS
            if (stale) viewModel.refresh() else showRefreshConfirm = true
        },
        modifier = modifier.fillMaxSize()
    ) {
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
                    onRetry = viewModel::retry,
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
                        LtxListHeader(uiState = uiState)
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
}

/** 精简头部：条目数 + 更新时间 + 补全进度。筛选/排序入口在影片 tab chip 行右侧，刷新走下拉。 */
@Composable
private fun LtxListHeader(
    uiState: LtxListUiState,
) {
    val updatedText = remember(uiState.updatedAtMillis) {
        if (uiState.updatedAtMillis > 0) {
            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(uiState.updatedAtMillis))
        } else ""
    }

    // 列表容器自带 14dp contentPadding，此处不再叠加水平边距，保持与卡片对齐
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (uiState.totalCount > 0 || updatedText.isNotBlank()) {
            Text(
                if (updatedText.isBlank()) {
                    stringResource(R.string.ltx_count_only, uiState.totalCount)
                } else {
                    stringResource(R.string.ltx_count_updated, uiState.totalCount, updatedText)
                },
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
    }
}

/** 途经发现节流窗口：滑动过程中至多按该间隔入队一次当前窗口，避免逐帧触发。 */
private const val LTX_RESOLVE_DISCOVERY_THROTTLE_MS = 150L

/** 滑动去抖窗口：滚动索引停止变化该时长后视为停留，触发停留补全插队。 */
private const val LTX_RESOLVE_SETTLE_DEBOUNCE_MS = 400L

/** 停留补全窗口大小（条目数，以首个可见项为中心 ±viewport/2）。 */
private const val LTX_RESOLVE_VIEWPORT = 24

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
        // 卡片日期取 LTX 库原始 date（item.date 已含快照回退），补全前后展示一致
        resolved.copy(date = date, tags = tags)
    } else {
        MovieUiModel(
            title = code,
            imageUrl = "",
            code = code,
            date = date,
            link = "",
            tags = tags
        )
    }
}
