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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.SearchBar
import me.jbusdriver.modern.ui.forum.ForumBoardsScreen
import me.jbusdriver.modern.ui.forum.ForumBoardsViewModel
import me.jbusdriver.modern.ui.movielist.CollectCategoryScreen
import me.jbusdriver.modern.ui.settings.LabSettingsViewModel
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
    onForumThreadClick: (Int) -> Unit = {}
) {
    var selectedCategory by rememberSaveable { mutableStateOf(BottomNavCategory.MOVIE) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val labSettingsViewModel = hiltViewModel<LabSettingsViewModel>()
    val uiPrefsStore = hiltViewModel<UiPrefsViewModel>().store
    val labSettingsUiState by labSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val forumEnabled = labSettingsUiState.forumEnabled
    val isGrid by uiPrefsStore.isGrid.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val toggleGrid: () -> Unit = {
        coroutineScope.launch { uiPrefsStore.setGrid(!isGrid) }
    }

    // Auto-switch away from Forum tab when disabled
    LaunchedEffect(forumEnabled) {
        if (!forumEnabled && selectedCategory == BottomNavCategory.FORUM) {
            selectedCategory = BottomNavCategory.MOVIE
        }
    }

    // Preload forum data when enabled
    if (forumEnabled) hiltViewModel<ForumBoardsViewModel>()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(modifier = Modifier.height(64.dp)) {
                BottomNavItems.forEach { item ->
                    if (item.category == BottomNavCategory.FORUM && !forumEnabled) return@forEach
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
        saveableStateHolder.SaveableStateProvider(selectedCategory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                        onGoHome = { selectedCategory = BottomNavCategory.MOVIE }
                    )

                    BottomNavCategory.FORUM -> {
                        SearchBar(
                            onClick = { onSearchClick("") },
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 4.dp,
                                end = 12.dp,
                                bottom = 8.dp
                            )
                        )
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
