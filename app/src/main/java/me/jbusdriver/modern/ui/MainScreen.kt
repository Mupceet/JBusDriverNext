package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.CollapsingSearchBar
import me.jbusdriver.modern.ui.components.rememberSearchBarVisibilityState
import me.jbusdriver.modern.ui.components.SearchBarWithSettings
import me.jbusdriver.modern.ui.forum.ForumBoardsScreen
import me.jbusdriver.modern.ui.forum.ForumBoardsViewModel
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen
import me.jbusdriver.modern.ui.settings.SettingsViewModel
import me.jbusdriver.modern.ui.settings.UiPrefsViewModel

enum class BottomNavCategory { MOVIE, ACTRESS, FORUM, COLLECT }

private data class BottomNavItem(
    val category: BottomNavCategory,
    val labelRes: Int,
    val iconRes: Int
)

private val BottomNavItems = listOf(
    BottomNavItem(BottomNavCategory.MOVIE, R.string.nav_movies, R.drawable.movie_24px),
    BottomNavItem(BottomNavCategory.ACTRESS, R.string.nav_actresses, R.drawable.person_24px),
    BottomNavItem(BottomNavCategory.FORUM, R.string.nav_forum, R.drawable.forum_24px),
    BottomNavItem(BottomNavCategory.COLLECT, R.string.nav_collect, R.drawable.favorite_24px)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onMovieClick: (MovieUiModel, String?) -> Unit,
    onActressClick: (ActressUiModel, String?) -> Unit = { _, _ -> },
    onGenreClick: (GenreUiModel, String?) -> Unit = { _, _ -> },
    onSearchClick: (String) -> Unit = {},
    onForumBoardClick: (me.jbusdriver.modern.domain.model.ForumBoard) -> Unit = {},
    onForumThreadClick: (Int) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf(BottomNavCategory.MOVIE) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val store = settingsViewModel.store
    val showMovieTab by store.showMovieTab.collectAsStateWithLifecycle()
    val showActressTab by store.showActressTab.collectAsStateWithLifecycle()
    val showForumTab by store.showForumTab.collectAsStateWithLifecycle()
    val uiPrefsViewModel = hiltViewModel<UiPrefsViewModel>()
    val uiPrefsUiState by uiPrefsViewModel.uiState.collectAsStateWithLifecycle()
    val isGrid = uiPrefsUiState.isGrid
    val toggleGrid: () -> Unit = uiPrefsViewModel::toggleGrid

    // If the selected tab is hidden (disabled in settings, or cold start before
    // settings load), fall back to the first visible tab.
    LaunchedEffect(showMovieTab, showActressTab, showForumTab, selectedCategory) {
        val isSelectedVisible = when (selectedCategory) {
            BottomNavCategory.MOVIE -> showMovieTab
            BottomNavCategory.ACTRESS -> showActressTab
            BottomNavCategory.FORUM -> showForumTab
            BottomNavCategory.COLLECT -> true
        }
        if (!isSelectedVisible) {
            selectedCategory = when {
                showMovieTab -> BottomNavCategory.MOVIE
                showActressTab -> BottomNavCategory.ACTRESS
                showForumTab -> BottomNavCategory.FORUM
                else -> BottomNavCategory.COLLECT
            }
        }
    }

    // Preload forum data when enabled
    if (showForumTab) hiltViewModel<ForumBoardsViewModel>()

    // Shared search bar follows the active list's scroll (observe-only, never consumes).
    val searchBarState = rememberSearchBarVisibilityState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar() {
                BottomNavItems.forEach { item ->
                    if (item.category == BottomNavCategory.MOVIE && !showMovieTab) return@forEach
                    if (item.category == BottomNavCategory.ACTRESS && !showActressTab) return@forEach
                    if (item.category == BottomNavCategory.FORUM && !showForumTab) return@forEach
                    NavigationBarItem(
                        selected = selectedCategory == item.category,
                        onClick = { selectedCategory = item.category },
                        icon = {
                            Icon(
                                painter = painterResource(item.iconRes),
                                contentDescription = stringResource(item.labelRes)
                            )
                        },
                        label = {
                            Text(
                                stringResource(item.labelRes),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CollapsingSearchBar(
                onSearchClick = { onSearchClick("") },
                onSettingsClick = onSettingsClick,
                state = searchBarState
            )
            saveableStateHolder.SaveableStateProvider(selectedCategory) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(searchBarState.nestedScrollConnection)
                ) {
                    when (selectedCategory) {
                        BottomNavCategory.MOVIE -> MovieTabContent(
                            isGrid = isGrid,
                            toggleGrid = toggleGrid,
                            onSearchClick = onSearchClick,
                            onMovieClick = onMovieClick
                        )

                        BottomNavCategory.ACTRESS -> ActressTabContent(
                            onSearchClick = onSearchClick,
                            onActressClick = onActressClick
                        )

                        BottomNavCategory.COLLECT -> CollectCategoryScreen(
                            onMovieClick = onMovieClick,
                            onActressClick = onActressClick,
                            onSearchClick = onSearchClick
                        )

                        BottomNavCategory.FORUM -> {
                            ForumBoardsScreen(
                                onBoardClick = onForumBoardClick,
                                onThreadClick = onForumThreadClick
                            )
                        }
                    }
                }
            }
        }
    }
}
