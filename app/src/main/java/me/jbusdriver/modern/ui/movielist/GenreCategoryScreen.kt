package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.ui.components.CategorySearchBar

private data class GenreSourceTab(
    val title: String,
    val dataSourceType: DataSourceType
)

private val GenreSourceTabs = listOf(
    GenreSourceTab("有码类别", DataSourceType.GENRE),
    GenreSourceTab("无码类别", DataSourceType.UNCENSORED_GENRE)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreCategoryScreen(
    onGenreClick: (GenreUiModel) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSourceIndex by rememberSaveable { mutableIntStateOf(0) }
    var expandedGroups by rememberSaveable { mutableStateOf(setOf(0)) }

    val viewModelKey = "genre_source_$selectedSourceIndex"
    val viewModel: GenreListViewModel = hiltViewModel(key = viewModelKey)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedSourceIndex) {
        viewModel.setDataSourceType(GenreSourceTabs[selectedSourceIndex].dataSourceType)
        expandedGroups = setOf(0)
    }

    val genreGroups = uiState.genreCategories

    Column(modifier = modifier.fillMaxSize()) {
        CategorySearchBar(onClick = onSearchClick)

        ScrollableTabRow(
            selectedTabIndex = selectedSourceIndex,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedSourceIndex])
                )
            },
            divider = {}
        ) {
            GenreSourceTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedSourceIndex == index,
                    onClick = { selectedSourceIndex = index },
                    text = {
                        Text(
                            tab.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = if (selectedSourceIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedSourceIndex == index)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && genreGroups.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "載入失敗", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(genreGroups, key = { it.title }) { group ->
                            val index = genreGroups.indexOf(group)
                            val isExpanded = index in expandedGroups
                            GenreGroupCard(
                                title = group.title,
                                chipCount = group.genres.size,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedGroups = if (isExpanded)
                                        expandedGroups - index
                                    else
                                        expandedGroups + index
                                },
                                genres = if (isExpanded) group.genres else emptyList(),
                                onGenreClick = onGenreClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreGroupCard(
    title: String,
    chipCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    genres: List<GenreUiModel>,
    onGenreClick: (GenreUiModel) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isExpanded) "▼ $chipCount" else "▶ $chipCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isExpanded && genres.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    genres.forEach { genre ->
                        AssistChip(
                            onClick = { onGenreClick(genre) },
                            label = {
                                Text(genre.name, style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }
            }
        }
    }
}
