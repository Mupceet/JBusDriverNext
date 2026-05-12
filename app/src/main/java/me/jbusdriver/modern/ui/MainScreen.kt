package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import me.jbusdriver.R
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.ui.movielist.ActressCategoryScreen
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen
import me.jbusdriver.modern.ui.movielist.GenreCategoryScreen
import me.jbusdriver.modern.ui.movielist.MovieCategoryScreen

enum class BottomNavCategory {
    MOVIE, ACTRESS, GENRE, COLLECT
}

private data class BottomNavItem(
    val category: BottomNavCategory?,
    val label: String,
    val iconRes: Int,
    val defaultSearchType: SearchType
)

private val BottomNavItems = listOf(
    BottomNavItem(BottomNavCategory.MOVIE, "电影", R.drawable.movie_24px, SearchType.CENSORED),
    BottomNavItem(BottomNavCategory.ACTRESS, "演员", R.drawable.person_24px, SearchType.ACTRESS),
    BottomNavItem(null, "搜索", R.drawable.search_24px, SearchType.CENSORED),
    BottomNavItem(BottomNavCategory.GENRE, "类别", R.drawable.category_24px, SearchType.CENSORED),
    BottomNavItem(BottomNavCategory.COLLECT, "收藏", R.drawable.favorite_24px, SearchType.CENSORED)
)

@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel) -> Unit,
    onActressClick: (ActressUiModel) -> Unit = {},
    onGenreClick: (GenreUiModel) -> Unit = {},
    onSearchClick: (String) -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf(BottomNavCategory.MOVIE) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                BottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = item.category != null && selectedCategory == item.category,
                        onClick = {
                            if (item.category != null) {
                                selectedCategory = item.category
                            } else {
                                val currentSearchType = BottomNavItems
                                    .find { it.category == selectedCategory }
                                    ?.defaultSearchType ?: SearchType.CENSORED
                                onSearchClick(currentSearchType.name)
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(selectedCategory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedCategory) {
                    BottomNavCategory.MOVIE -> MovieCategoryScreen(
                        onMovieClick = onMovieClick
                    )
                    BottomNavCategory.ACTRESS -> ActressCategoryScreen(
                        onActressClick = onActressClick
                    )
                    BottomNavCategory.GENRE -> GenreCategoryScreen(
                        onGenreClick = onGenreClick
                    )
                    BottomNavCategory.COLLECT -> CollectCategoryScreen(
                        onMovieClick = onMovieClick,
                        onActressClick = onActressClick
                    )
                }
            }
        }
    }
}
