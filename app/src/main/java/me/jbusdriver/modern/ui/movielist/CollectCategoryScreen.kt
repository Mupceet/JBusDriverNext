package me.jbusdriver.modern.ui.movielist

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollectCategoryScreen(
    onMovieClick: (MovieUiModel, String?) -> Unit,
    onActressClick: (ActressUiModel, String?) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { 3 }

    // Sync pager → tab：必须用 settledPage（动画结束后才更新）。currentPage 在
    // 0→2 动画途经 1 时会瞬时变为 1，若据此回写 selectedTab 会把动画拉回第二页。
    LaunchedEffect(pagerState.settledPage) {
        if (selectedTab != pagerState.settledPage) selectedTab = pagerState.settledPage
    }
    // Sync tab → pager
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab)
    }

    val movieVm: CollectionListViewModel = hiltViewModel(key = "collect_0")
    val actressVm: CollectionListViewModel = hiltViewModel(key = "collect_1")
    val ltxVm: LtxListViewModel = hiltViewModel(key = "collect_2")
    val actionVm: CollectCategoryViewModel = hiltViewModel()
    val movieState by movieVm.uiState.collectAsStateWithLifecycle()
    val actressState by actressVm.uiState.collectAsStateWithLifecycle()
    val ltxState by ltxVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val exportSuccessMessage = stringResource(R.string.export_success)
    val cannotReadFileMessage = stringResource(R.string.cannot_read_file)
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showLtxFilterSheet by remember { mutableStateOf(false) }

    // Active tab's ViewModel and state for filter sheet (LTX 页有自己的内联筛选，不走收藏筛选弹窗)
    val activeVm = if (selectedTab == 0) movieVm else actressVm
    val activeFilterState by activeVm.uiState.collectAsStateWithLifecycle()
    val activeDbType = if (selectedTab == 0) MovieDBType else ActressDBType

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        actionVm.exportCollectionsToDocument(uri.toString())
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        actionVm.importCollectionsFromDocument(uri.toString())
    }

    // VM 只发事件，UI 在主线程 collect 后处理（Toast / 刷新列表），避免回调时序与线程问题
    LaunchedEffect(Unit) {
        actionVm.events.collect { event ->
            when (event) {
                CollectActionEvent.ExportSuccess ->
                    Toast.makeText(context, exportSuccessMessage, Toast.LENGTH_SHORT).show()

                is CollectActionEvent.ImportSuccess -> {
                    movieVm.loadCollection(MovieDBType)
                    actressVm.loadCollection(ActressDBType)
                }

                is CollectActionEvent.ActionFailed -> {
                    val message = when {
                        event.isImport && event.throwable is IllegalStateException -> cannotReadFileMessage
                        event.isImport -> resources.getString(
                            R.string.import_failed_detail,
                            event.throwable.message.orEmpty()
                        )

                        else -> resources.getString(
                            R.string.export_failed_detail,
                            event.throwable.message.orEmpty()
                        )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 数字仅在选中时显示，避免三个 chip + 筛选按钮一行放不下
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = {
                    Text(
                        if (selectedTab == 0) {
                            stringResource(R.string.tab_movies_count, movieState.movies.size)
                        } else {
                            stringResource(R.string.nav_movies)
                        },
                        fontSize = 12.sp
                    )
                }
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = {
                    Text(
                        if (selectedTab == 1) {
                            stringResource(
                                R.string.tab_actresses_count,
                                actressState.actresses.size
                            )
                        } else {
                            stringResource(R.string.nav_actresses)
                        },
                        fontSize = 12.sp
                    )
                }
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = {
                    Text(
                        if (selectedTab == 2) {
                            stringResource(R.string.tab_ltx_count, ltxState.totalCount)
                        } else {
                            "LTX"
                        },
                        fontSize = 12.sp
                    )
                }
            )
            Spacer(Modifier.weight(1f))
            // 筛选入口三个 tab 都有：影片/演员打开收藏筛选，LTX 打开 LTX 筛选
            FilterChip(
                selected = if (selectedTab < 2) {
                    activeFilterState.filterState.hasActiveFilters
                } else {
                    ltxState.hasActiveFilters
                },
                onClick = { if (selectedTab < 2) showFilterSheet = true else showLtxFilterSheet = true },
                label = {
                    val activeCount = if (selectedTab < 2) {
                        activeFilterState.filterState.activeFilterCount
                    } else {
                        ltxState.activeFilterCount
                    }
                    if (activeCount > 0) {
                        Text(
                            stringResource(R.string.filter_count, activeCount),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(stringResource(R.string.filter), fontSize = 12.sp)
                    }
                },
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.filter_alt_24px),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            // 导入/导出仅作用于影片与演员收藏
            if (selectedTab < 2) {
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            painterResource(R.drawable.import_export_24px),
                            contentDescription = stringResource(R.string.import_export),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_collect)) },
                            onClick = {
                                showMenu = false
                                val filename = "jbus_backup_${
                                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                }.json"
                                exportLauncher.launch(filename)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_collect)) },
                            onClick = {
                                showMenu = false
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 2) {
                LtxListScreen(
                    active = pagerState.currentPage == 2,
                    onMovieClick = onMovieClick,
                )
            } else {
                val dbType = if (page == 0) MovieDBType else ActressDBType
                val vm: CollectionListViewModel = hiltViewModel(key = "collect_$page")
                CollectionListScreen(
                    dbType = dbType,
                    active = true,
                    onMovieClick = onMovieClick,
                    onActressClick = onActressClick,
                    viewModel = vm
                )
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        CollectionFilterSheet(
            dbType = activeDbType,
            filterState = activeFilterState.filterState,
            availableYears = activeFilterState.availableYears,
            onFilterChange = { activeVm.updateFilter(it) },
            availablePublishMonths = activeFilterState.availablePublishMonths,
            availableCollectMonths = activeFilterState.availableCollectMonths,
            hasLocalVideoFolder = activeFilterState.hasLocalVideoFolder,
            onDismiss = { showFilterSheet = false }
        )
    }

    // LTX Filter Bottom Sheet
    if (showLtxFilterSheet) {
        LtxFilterSheet(
            sortOption = ltxState.sortOption,
            statusFilter = ltxState.statusFilter,
            resolveFilter = ltxState.resolveFilter,
            hasActiveFilters = ltxState.hasActiveFilters,
            onSortChange = ltxVm::updateSort,
            onStatusFilterChange = ltxVm::updateStatusFilter,
            onResolveFilterChange = ltxVm::updateResolveFilter,
            onReset = ltxVm::resetFilters,
            onDismiss = { showLtxFilterSheet = false }
        )
    }
}
