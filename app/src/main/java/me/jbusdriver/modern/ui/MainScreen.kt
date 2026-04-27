package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen
import me.jbusdriver.ui.data.enums.DataSourceType

data class CategoryGroup(
    val name: String,
    val subCategories: List<SubCategory>
)

data class SubCategory(
    val name: String,
    val dataSourceType: DataSourceType
)

val CategoryGroups = listOf(
    CategoryGroup("有碼", listOf(
        SubCategory("电影", DataSourceType.CENSORED),
        SubCategory("女优", DataSourceType.ACTRESSES),
        SubCategory("类别", DataSourceType.GENRE),
    )),
    CategoryGroup("無碼", listOf(
        SubCategory("电影", DataSourceType.UNCENSORED),
        SubCategory("女优", DataSourceType.UNCENSORED_ACTRESSES),
        SubCategory("类别", DataSourceType.UNCENSORED_GENRE),
    )),
    CategoryGroup("欧美", listOf(
        SubCategory("电影", DataSourceType.XYZ),
        SubCategory("演员", DataSourceType.XYZ_ACTRESSES),
        SubCategory("类别", DataSourceType.XYZ_GENRE),
    )),
    CategoryGroup("高清", listOf(
        SubCategory("电影", DataSourceType.GENRE_HD),
    )),
    CategoryGroup("字幕", listOf(
        SubCategory("电影", DataSourceType.Sub),
    )),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedSubCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    val currentGroup = CategoryGroups[selectedCategoryIndex]
    val currentSubCategory = currentGroup.subCategories[selectedSubCategoryIndex]

    val topBarTitle = when (selectedTabIndex) {
        0 -> currentGroup.name
        1 -> "搜索"
        2 -> "设置"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(topBarTitle) })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "电影") },
                    label = { Text("电影") },
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    label = { Text("搜索") },
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 }
                )
            }
        }
    ) { padding ->
        when (selectedTabIndex) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Main category tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedCategoryIndex,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        CategoryGroups.forEachIndexed { index, group ->
                            Tab(
                                selected = selectedCategoryIndex == index,
                                onClick = {
                                    selectedCategoryIndex = index
                                    selectedSubCategoryIndex = 0
                                },
                                text = { Text(group.name) }
                            )
                        }
                    }

                    // Sub-category chips (only show if group has >1 sub-category)
                    val subCategories = currentGroup.subCategories
                    if (subCategories.size > 1) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            subCategories.forEachIndexed { index, sub ->
                                FilterChip(
                                    selected = selectedSubCategoryIndex == index,
                                    onClick = { selectedSubCategoryIndex = index },
                                    label = {
                                        Text(
                                            sub.name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }

                    // Movie list
                    MovieListScreen(
                        dataSourceType = currentSubCategory.dataSourceType,
                        onMovieClick = onMovieClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            1 -> SearchScreen(
                onMovieClick = onMovieClick,
                modifier = Modifier.padding(padding)
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(padding)
            )
        }
    }
}
