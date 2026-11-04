package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.components.CategoryBottomSheet
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.ActressListViewModel
import me.jbusdriver.modern.ui.movielist.GenreListViewModel
import me.jbusdriver.modern.ui.movielist.LtxFilterSheet
import me.jbusdriver.modern.ui.movielist.LtxListScreen
import me.jbusdriver.modern.ui.movielist.LtxListViewModel
import me.jbusdriver.modern.ui.movielist.MovieListRoute
import me.jbusdriver.modern.ui.movielist.MovieListViewModel

internal enum class CensorFilter(val labelRes: Int) {
    CENSORED(R.string.censored), UNCENSORED(R.string.uncensored)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieTabContent(
    isGrid: Boolean,
    toggleGrid: () -> Unit,
    onSearchClick: (String) -> Unit,
    onMovieClick: (MovieUiModel, String?) -> Unit
) {
    val movieTabViewModel: MovieTabViewModel = hiltViewModel()
    val tabState by movieTabViewModel.uiState.collectAsStateWithLifecycle()
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var showLtxFilterSheet by remember { mutableStateOf(false) }
    val ltxVm: LtxListViewModel = hiltViewModel(key = "movie_ltx")
    val ltxEnabled by ltxVm.ltxEnabled.collectAsStateWithLifecycle()
    // 稳定回调：父级重组时 onMovieClick 会拿到新实例，包装后各页点击回调保持
    // 同一实例，避免三个重列表页跟着无关状态变化整页重组。
    val onMovieClickState = rememberUpdatedState(onMovieClick)

    // 页切换采用「命令 + 派生」而非「共享目标状态」：chip 点击直接命令 pager 动画
    // （animateScrollToPage 互斥：新调用取消旧动画，从当前位置向最新目标收敛），
    // 显示状态全部从 pager 派生——chip 高亮/影片筛选跟随 currentPage，selectedTab
    // 仅落定时记录（供进程恢复）。不存在任何既是动画目标、又被途经页回写的状态，
    // 因此三页 pager 动画途经中间页时不可能被截停（两页的演员页无此问题的推广）。
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(tabState.censorFilter.ordinal) }
    // LTX 关闭时退回两页，chip 与页面一并隐藏
    val pageCount = if (ltxEnabled) 3 else 2
    val moviePagerState = rememberPagerState(
        initialPage = selectedTab.coerceAtMost(pageCount - 1)
    ) { pageCount }

    val genreViewModel: GenreListViewModel = hiltViewModel()
    val genreState by genreViewModel.uiState.collectAsStateWithLifecycle()
    // 分类数据延迟加载：首屏不请求，仅在有真实消费方时拉取——
    // 打开分类弹窗（见分类 chip onClick）或恢复的分类选择需要回填 chip 名称。
    val desiredGenreType = when (tabState.censorFilter) {
        CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_GENRE
        else -> DataSourceType.GENRE
    }
    LifecycleResumeEffect(Unit) {
        if (genreState.genreCategories.isNotEmpty()) genreViewModel.revalidate()
        onPauseOrDispose { }
    }
    val genreNeedsLoad = tabState.selectedGenreLinks.isNotEmpty() && genreState.genreCategories.isEmpty()
    LaunchedEffect(genreNeedsLoad, desiredGenreType) {
        if (genreNeedsLoad) genreViewModel.setDataSourceType(desiredGenreType)
    }

    // 派生①：影片筛选跟随当前页实时翻转（动画途经页会瞬时写入，落定页为最终值）
    LaunchedEffect(moviePagerState.currentPage) {
        val page = moviePagerState.currentPage
        if (page < 2) movieTabViewModel.onCensorFilterChanged(CensorFilter.entries[page])
    }
    // 派生②：落定时记录选中页，仅用于 rememberSaveable 进程恢复
    LaunchedEffect(moviePagerState.settledPage) {
        selectedTab = moviePagerState.settledPage
    }

    if (showCategorySheet) {
        val allGenres = genreState.genreCategories.flatMap { it.genres.orEmpty() }
        val selectedGenres = allGenres.filter { it.link in tabState.selectedGenreLinks }.toSet()

        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showCategorySheet = false }
        ) {
            if (genreState.isLoading) {
                // 延迟加载中：弹窗先给加载圈，数据到位后渲染分组
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                CategoryBottomSheet(
                    categories = genreState.genreCategories,
                    selectedGenres = selectedGenres,
                    onSelectionChange = { newSelection ->
                        movieTabViewModel.onGenreSelectionChanged(newSelection.map { it.link }.toSet())
                    },
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CensorFilter.entries.forEach { filter ->
            FilterChip(
                selected = moviePagerState.currentPage == filter.ordinal,
                onClick = { scope.launch { moviePagerState.animateScrollToPage(filter.ordinal) } },
                label = { Text(stringResource(filter.labelRes), fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
        }
        // LTX 页作为可选第三页（条目数在列表头部展示，chip 保持简短）
        if (ltxEnabled) {
            FilterChip(
                selected = moviePagerState.currentPage == 2,
                onClick = { scope.launch { moviePagerState.animateScrollToPage(2) } },
                label = { Text("LTX", fontSize = 12.sp) }
            )
        }
        Spacer(Modifier.weight(1f))
        if (moviePagerState.currentPage < 2) {
            val allGenresForChip = genreState.genreCategories.flatMap { it.genres.orEmpty() }
            val selectedGenresForChip =
                allGenresForChip.filter { it.link in tabState.selectedGenreLinks }.toSet()
            FilterChip(
                selected = tabState.selectedGenreLinks.isNotEmpty(),
                onClick = {
                    // 分类数据延迟加载：打开弹窗时才拉取/切换数据源
                    genreViewModel.setDataSourceType(desiredGenreType)
                    showCategorySheet = true
                },
                label = {
                    Text(
                        if (tabState.selectedGenreLinks.isEmpty()) stringResource(R.string.genre)
                        else selectedGenresForChip.joinToString("+") { it.name },
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.filter_alt_24px),
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        } else {
            // LTX 页的分类筛选不适用，替换为 LTX 自身的筛选入口
            LtxFilterChipButton(ltxVm = ltxVm, onClick = { showLtxFilterSheet = true })
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { toggleGrid() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(if (isGrid) R.drawable.list_view_24px else R.drawable.grid_view_24px),
                contentDescription = if (isGrid) stringResource(R.string.list_view) else stringResource(
                    R.string.grid
                ),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    HorizontalPager(
        state = moviePagerState,
        // 三页常驻（全部预加载）：影片两页 + LTX 页进入影片 Tab 即拉取首屏，
        // 滑动到位直接展示；仅首屏数据，流量可控
        beyondViewportPageCount = 2,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        if (page == 2) {
            LtxListScreen(
                onMovieClick = remember {
                    { movie: MovieUiModel, censor: String? -> onMovieClickState.value(movie, censor) }
                },
                viewModel = ltxVm,
            )
        } else {
            val filter = CensorFilter.entries[page]
            val censorType = when (filter) {
                CensorFilter.UNCENSORED -> "UNCENSORED"
                else -> null
            }
            // pageLinks 与全局筛选解耦：优先读各页自己的分类记忆，仅当前筛选页在
            // 恢复初期回退到 selectedGenreLinks——翻页/切换筛选时各页内容保持稳定，
            // 不会因 censorFilter 翻转而整页重建（换 VM 重新加载）。
            val pageLinks =
                tabState.genreLinkMemory[filter.name]
                    ?: (if (filter == tabState.censorFilter) tabState.selectedGenreLinks else emptySet())
            val genreUrl = if (pageLinks.isNotEmpty()) {
                pageLinks.joinToString("-") { it.trimEnd('/').substringAfterLast("/") }
                    .let { ids ->
                        val base = if (filter == CensorFilter.UNCENSORED) "uncensored/" else ""
                        "/${base}genre/$ids"
                    }
            } else null
            // 影片页预加载：常驻加载（合成即拉取首屏 + 后台恢复时 revalidate），
            // 滑动到位直接展示，未就绪时列表自身显示加载圈；不随滑页翻转 active，
            // 消除滑动过程中的加载抖动。
            val pageOnMovieClick = remember(censorType) {
                { movie: MovieUiModel, _: String? -> onMovieClickState.value(movie, censorType) }
            }

            if (genreUrl != null) {
                val genreVm: MovieListViewModel = hiltViewModel(key = "genre_$genreUrl")
                LaunchedEffect(genreUrl) {
                    genreVm.setGenreUrl(genreUrl)
                }
                MovieListRoute(
                    dataSourceType = null,
                    onMovieClick = pageOnMovieClick,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = genreVm
                )
            } else {
                val vm: MovieListViewModel = hiltViewModel(key = "pager_$filter")
                MovieListRoute(
                    dataSourceType = when (filter) {
                        CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED
                        else -> DataSourceType.CENSORED
                    },
                    onMovieClick = pageOnMovieClick,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = vm
                )
            }
        }
    }

    // LTX Filter Bottom Sheet
    if (showLtxFilterSheet) {
        LtxFilterSheetContent(ltxVm = ltxVm, onDismiss = { showLtxFilterSheet = false })
    }
}

/** LTX 筛选入口 chip：状态就近收集，补全进度变化只重组本 chip，不上溯整个影片 Tab。 */
@Composable
private fun LtxFilterChipButton(ltxVm: LtxListViewModel, onClick: () -> Unit) {
    val ltxState by ltxVm.uiState.collectAsStateWithLifecycle()
    FilterChip(
        selected = ltxState.hasActiveFilters,
        onClick = onClick,
        label = {
            if (ltxState.activeFilterCount > 0) {
                Text(
                    stringResource(R.string.filter_count, ltxState.activeFilterCount),
                    fontSize = 12.sp
                )
            } else {
                Text(stringResource(R.string.filter), fontSize = 12.sp)
            }
        },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.filter_alt_24px),
                null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

/** LTX 筛选弹窗：列表状态同样就近收集。 */
@Composable
private fun LtxFilterSheetContent(ltxVm: LtxListViewModel, onDismiss: () -> Unit) {
    val ltxState by ltxVm.uiState.collectAsStateWithLifecycle()
    LtxFilterSheet(
        sortField = ltxState.sortField,
        sortDirection = ltxState.sortDirection,
        statusFilter = ltxState.statusFilter,
        resolveFilter = ltxState.resolveFilter,
        hasActiveFilters = ltxState.hasActiveFilters,
        onSortFieldChange = ltxVm::updateSortField,
        onSortDirectionChange = ltxVm::updateSortDirection,
        onStatusFilterChange = ltxVm::updateStatusFilter,
        onToggleResolveFilter = ltxVm::toggleResolveFilter,
        onReset = ltxVm::resetFilters,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ActressTabContent(
    onSearchClick: (String) -> Unit,
    onActressClick: (ActressUiModel, String?) -> Unit
) {
    var censorFilter by rememberSaveable { mutableStateOf(CensorFilter.CENSORED) }
    val actressPagerState = rememberPagerState { CensorFilter.entries.size }

    LaunchedEffect(actressPagerState.currentPage) {
        censorFilter = CensorFilter.entries[actressPagerState.currentPage]
    }
    LaunchedEffect(censorFilter) {
        val target = CensorFilter.entries.indexOf(censorFilter)
        if (actressPagerState.currentPage != target) {
            actressPagerState.animateScrollToPage(target)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CensorFilter.entries.forEach { filter ->
            FilterChip(
                selected = censorFilter == filter,
                onClick = { censorFilter = filter },
                label = { Text(stringResource(filter.labelRes), fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }

    HorizontalPager(
        state = actressPagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        val filter = CensorFilter.entries[page]
        val actressCensorType = when (filter) {
            CensorFilter.UNCENSORED -> "UNCENSORED"
            else -> null
        }
        val actressType = when (filter) {
            CensorFilter.UNCENSORED -> DataSourceType.UNCENSORED_ACTRESSES
            else -> DataSourceType.ACTRESSES
        }
        val vm: ActressListViewModel = hiltViewModel(key = "actress_$filter")
        ActressListScreen(
            dataSourceType = actressType,
            active = page == actressPagerState.settledPage,
            onActressClick = { actress, _ -> onActressClick(actress, actressCensorType) },
            modifier = Modifier.fillMaxSize(),
            viewModel = vm
        )
    }
}
