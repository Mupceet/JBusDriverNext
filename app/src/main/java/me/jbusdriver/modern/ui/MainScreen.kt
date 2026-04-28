package me.jbusdriver.modern.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import me.jbusdriver.modern.ui.movielist.ActressListScreen
import me.jbusdriver.modern.ui.movielist.CollectionListScreen
import me.jbusdriver.modern.ui.movielist.GenreListScreen
import me.jbusdriver.modern.ui.movielist.MovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen
import me.jbusdriver.mvp.bean.ActressDBType
import me.jbusdriver.mvp.bean.MovieDBType
import me.jbusdriver.modern.domain.model.DataSourceType

data class CategoryOption(
    val group: String,
    val name: String,
    val dataSourceType: DataSourceType? = null,
    val collectionDbType: Int = 0
)

val CategoryOptions = listOf(
    CategoryOption("有码", "电影", DataSourceType.CENSORED),
    CategoryOption("有码", "演员", DataSourceType.ACTRESSES),
    CategoryOption("有码", "类别", DataSourceType.GENRE),
    CategoryOption("无码", "电影", DataSourceType.UNCENSORED),
    CategoryOption("无码", "演员", DataSourceType.UNCENSORED_ACTRESSES),
    CategoryOption("无码", "类别", DataSourceType.UNCENSORED_GENRE),
    CategoryOption("收藏", "电影", collectionDbType = MovieDBType),
    CategoryOption("收藏", "演员", collectionDbType = ActressDBType),
)

private val genreTypes = setOf(
    DataSourceType.GENRE,
    DataSourceType.UNCENSORED_GENRE
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedOptionIndex by rememberSaveable { mutableIntStateOf(0) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val currentOption = CategoryOptions[selectedOptionIndex]
    val topBarTitle = when (selectedTabIndex) {
        0 -> "${currentOption.group} · ${currentOption.name}"
        1 -> "搜索"
        2 -> "设置"
        else -> ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedTabIndex == 0) {
                        Box {
                            Row(
                                modifier = Modifier.clickable { showCategoryMenu = !showCategoryMenu },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    topBarTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    " ▾",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showCategoryMenu,
                                onDismissRequest = { showCategoryMenu = false }
                            ) {
                                CategoryOptions.forEachIndexed { index, option ->
                                    if (index > 0 && option.group != CategoryOptions[index - 1].group) {
                                        HorizontalDivider()
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            val isSelected = index == selectedOptionIndex
                                            Text(
                                                "${option.group} · ${option.name}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedOptionIndex = index
                                            showCategoryMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(topBarTitle)
                    }
                }
            )
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
                    val dsType = currentOption.dataSourceType
                    when {
                        dsType != null && (dsType == DataSourceType.ACTRESSES || dsType == DataSourceType.UNCENSORED_ACTRESSES) -> ActressListScreen(
                            dataSourceType = dsType,
                            onActressClick = onActressClick,
                            modifier = Modifier.weight(1f)
                        )
                        dsType != null && dsType in genreTypes -> GenreListScreen(
                            dataSourceType = dsType,
                            onGenreClick = onGenreClick,
                            modifier = Modifier.weight(1f)
                        )
                        dsType != null -> MovieListScreen(
                            dataSourceType = dsType,
                            onMovieClick = onMovieClick,
                            modifier = Modifier.weight(1f)
                        )
                        else -> CollectionListScreen(
                            dbType = currentOption.collectionDbType,
                            onMovieClick = onMovieClick,
                            onActressClick = onActressClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
