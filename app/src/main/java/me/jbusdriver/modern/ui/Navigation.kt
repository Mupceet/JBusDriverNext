package me.jbusdriver.modern.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.StateFlow
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import me.jbusdriver.modern.ui.forum.ForumThreadDetailScreen
import me.jbusdriver.modern.ui.forum.ForumThreadListScreen
import me.jbusdriver.modern.ui.image.ImageViewScreen
import me.jbusdriver.modern.ui.movielist.LinkMovieListScreen
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen

private const val ANIM_DURATION = 350
private const val ANIM_DURATION_SEARCH = 400

@Composable
fun JBusNavigation(
    deepLinkFlow: StateFlow<NavKey?>? = null,
    deepLinkKey: NavKey? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(RouteMain)
    val deepLinkValue = deepLinkFlow?.collectAsState()?.value ?: deepLinkKey
    var suppressAnim by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkValue) {
        deepLinkValue?.let { key ->
            suppressAnim = true
            when {
                key is RouteMain -> {
                    while (backStack.size > 1) backStack.removeLastOrNull()
                }

                backStack.last() is RouteSearch || backStack.last() is RouteImageViewer -> {
                    backStack.removeLastOrNull()
                    backStack.add(key)
                }

                else -> backStack.add(key)
            }
            onDeepLinkConsumed()
        }
    }

    val forwardSpec = if (suppressAnim) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(ANIM_DURATION)
        ) togetherWith (scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(ANIM_DURATION)
        ) + fadeOut(tween(ANIM_DURATION)))
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            suppressAnim = false
            forwardSpec
        },
        popTransitionSpec = {
            (scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(ANIM_DURATION)
            )
        },
        predictivePopTransitionSpec = {
            (scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(ANIM_DURATION)
            ) + fadeIn(tween(ANIM_DURATION))) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(ANIM_DURATION)
            )
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<RouteMain> {
                MainScreen(
                    onMovieClick = { movie, censorType ->
                        backStack.add(RouteMovieDetail(movie.link, censorType))
                    },
                    onActressClick = { actress, censorType ->
                        backStack.add(
                            RouteLinkMovies(
                                actress.link,
                                actress.name,
                                type = "actress",
                                avatar = actress.avatar,
                                censorType = censorType
                            )
                        )
                    },
                    onGenreClick = { genre, censorType ->
                        backStack.add(
                            RouteLinkMovies(
                                genre.link,
                                genre.name,
                                type = "genre",
                                censorType = censorType
                            )
                        )
                    },
                    onSearchClick = { searchType ->
                        backStack.add(RouteSearch(searchType))
                    },
                    onForumBoardClick = { board ->
                        backStack.add(RouteForumThreadList(board.id, board.name, board.typeId))
                    },
                    onForumThreadClick = { tid ->
                        backStack.add(RouteForumThreadDetail(tid))
                    },
                    onSettingsClick = { backStack.add(RouteSettings) }
                )
            }
            entry<RouteSearch>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        (slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(ANIM_DURATION_SEARCH)
                        ) + fadeIn(tween(ANIM_DURATION_SEARCH))) togetherWith
                                (scaleOut(
                                    targetScale = 0.9f,
                                    animationSpec = tween(ANIM_DURATION)
                                ) + fadeOut(tween(ANIM_DURATION)))
                    }
                    put(NavDisplay.PopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(ANIM_DURATION)
                        ) + fadeIn(tween(ANIM_DURATION))) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(ANIM_DURATION_SEARCH)
                                ) + fadeOut(tween(ANIM_DURATION_SEARCH)))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.9f,
                            animationSpec = tween(ANIM_DURATION)
                        ) + fadeIn(tween(ANIM_DURATION))) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(ANIM_DURATION_SEARCH)
                                ) + fadeOut(tween(ANIM_DURATION_SEARCH)))
                    }
                }
            ) { key ->
                SearchScreen(
                    defaultSearchType = key.defaultSearchType,
                    onMovieClick = { movie, censorType ->
                        backStack.add(RouteMovieDetail(movie.link, censorType))
                    },
                    onActressClick = { actress, censorType ->
                        backStack.add(
                            RouteLinkMovies(
                                actress.link,
                                actress.name,
                                type = "actress",
                                avatar = actress.avatar,
                                censorType = censorType
                            )
                        )
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteMovieDetail> { key ->
                MovieDetailScreen(
                    movieUrl = key.movieUrl,
                    censorType = key.censorType,
                    onMovieClick = { movie, censorType ->
                        backStack.add(RouteMovieDetail(movie.link, censorType))
                    },
                    onImageClick = { images, startIndex ->
                        backStack.add(RouteImageViewer(images, startIndex))
                    },
                    onActressClick = { actress, censorType ->
                        backStack.add(
                            RouteLinkMovies(
                                actress.link,
                                actress.name,
                                type = "actress",
                                avatar = actress.avatar,
                                censorType = censorType
                            )
                        )
                    },
                    onGenreClick = { genre, censorType ->
                        backStack.add(
                            RouteLinkMovies(
                                genre.link,
                                genre.name,
                                type = "genre",
                                censorType = censorType
                            )
                        )
                    },
                    onHeaderClick = { header, censorType ->
                        if (header.link.isNotBlank()) {
                            backStack.add(
                                RouteLinkMovies(
                                    header.link,
                                    header.name + ": " + header.value,
                                    type = "header",
                                    censorType = censorType
                                )
                            )
                        }
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteImageViewer> { key ->
                ImageViewScreen(
                    images = key.images,
                    startIndex = key.startIndex,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteLinkMovies> { key ->
                LinkMovieListScreen(
                    linkUrl = key.linkUrl,
                    title = key.title,
                    type = key.type,
                    avatarUrl = key.avatar,
                    censorType = key.censorType,
                    onMovieClick = { movie, censorType ->
                        backStack.add(RouteMovieDetail(movie.link, censorType))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteForumThreadList> { key ->
                ForumThreadListScreen(
                    fid = key.fid,
                    title = key.title,
                    typeId = key.typeId,
                    onThreadClick = { tid ->
                        backStack.add(RouteForumThreadDetail(tid))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteForumThreadDetail> { key ->
                ForumThreadDetailScreen(
                    tid = key.tid,
                    onImageClick = { images, startIndex ->
                        backStack.add(RouteImageViewer(images, startIndex))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<RouteSettings> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        }
    )
}
