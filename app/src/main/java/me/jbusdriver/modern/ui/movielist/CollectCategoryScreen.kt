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
import androidx.compose.ui.text.font.FontWeight
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
    onGoHome: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState { 2 }

    // Sync pager → tab
    LaunchedEffect(pagerState.currentPage) {
        if (selectedTab != pagerState.currentPage) selectedTab = pagerState.currentPage
    }
    // Sync tab → pager
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab)
    }

    val movieVm: CollectionListViewModel = hiltViewModel(key = "collect_0")
    val actressVm: CollectionListViewModel = hiltViewModel(key = "collect_1")
    val actionVm: CollectCategoryViewModel = hiltViewModel()
    val movieState by movieVm.uiState.collectAsStateWithLifecycle()
    val actressState by actressVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val exportSuccessMessage = stringResource(R.string.export_success)
    val cannotReadFileMessage = stringResource(R.string.cannot_read_file)
    var showMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Active tab's ViewModel and state for filter sheet
    val activeVm = if (selectedTab == 0) movieVm else actressVm
    val activeFilterState by activeVm.uiState.collectAsStateWithLifecycle()
    val activeDbType = if (selectedTab == 0) MovieDBType else ActressDBType

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        actionVm.exportCollectionsToDocument(
            documentUri = uri.toString(),
            onDone = {
                Toast.makeText(context, exportSuccessMessage, Toast.LENGTH_SHORT).show()
            },
            onError = { e ->
                Toast.makeText(
                    context,
                    resources.getString(R.string.export_failed_detail, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        actionVm.importCollectionsFromDocument(
            documentUri = uri.toString(),
            onDone = {
                movieVm.loadCollection(MovieDBType)
                actressVm.loadCollection(ActressDBType)
            },
            onError = { e ->
                val message = if (e is IllegalStateException) {
                    cannotReadFileMessage
                } else {
                    resources.getString(R.string.import_failed_detail, e.message.orEmpty())
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.my_collect),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painterResource(R.drawable.more_vert_24px),
                        contentDescription = stringResource(R.string.more)
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
                                SimpleDateFormat(
                                    "yyyyMMdd",
                                    Locale.US
                                ).format(Date())
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = {
                    Text(
                        stringResource(R.string.tab_movies_count, movieState.movies.size),
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
                        stringResource(
                            R.string.tab_actresses_count,
                            actressState.actresses.size
                        ), fontSize = 12.sp
                    )
                }
            )
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = activeFilterState.filterState.hasActiveFilters,
                onClick = { showFilterSheet = true },
                label = {
                    if (activeFilterState.filterState.hasActiveFilters) {
                        Text(
                            stringResource(
                                R.string.filter_count,
                                activeFilterState.filterState.activeFilterCount
                            ), fontSize = 12.sp
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
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
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

    // Filter Bottom Sheet
    if (showFilterSheet) {
        CollectionFilterSheet(
            dbType = activeDbType,
            filterState = activeFilterState.filterState,
            availableYears = activeFilterState.availableYears,
            onFilterChange = { activeVm.updateFilter(it) },
            availablePublishMonths = activeFilterState.availablePublishMonths,
            onDismiss = { showFilterSheet = false }
        )
    }
}
