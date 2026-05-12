package me.jbusdriver.modern.ui.movielist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var selectedThemeIndex by rememberSaveable { mutableIntStateOf(0) }

    val viewModelKey = "genre_source_$selectedSourceIndex"
    val viewModel: GenreListViewModel = hiltViewModel(key = viewModelKey)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedSourceIndex) {
        viewModel.setDataSourceType(GenreSourceTabs[selectedSourceIndex].dataSourceType)
    }

    val themeGroups = uiState.genreCategories
    LaunchedEffect(themeGroups) {
        if (selectedThemeIndex >= themeGroups.size && themeGroups.isNotEmpty()) {
            selectedThemeIndex = 0
        }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        CategorySearchBar(onClick = onSearchClick)

        // Outer tabs: 有码类别 / 无码类别
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
                    onClick = {
                        selectedSourceIndex = index
                        selectedThemeIndex = 0
                    },
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

        // Inner tabs: dynamic theme groups
        if (themeGroups.isNotEmpty()) {
            val clampedIndex = selectedThemeIndex.coerceIn(0, themeGroups.size - 1)
            ScrollableTabRow(
                selectedTabIndex = clampedIndex,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[clampedIndex])
                    )
                },
                divider = {}
            ) {
                themeGroups.forEachIndexed { index, category ->
                    Tab(
                        selected = clampedIndex == index,
                        onClick = { selectedThemeIndex = index },
                        text = {
                            Text(
                                category.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = if (clampedIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (clampedIndex == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }

        // Content: genre chips
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
                uiState.error != null && themeGroups.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(uiState.error ?: "載入失敗", color = MaterialTheme.colorScheme.error)
                    }
                }
                themeGroups.isNotEmpty() -> {
                    val clampedIndex = selectedThemeIndex.coerceIn(0, themeGroups.size - 1)
                    val genres = themeGroups[clampedIndex].genres
                    FlowRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
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
}
