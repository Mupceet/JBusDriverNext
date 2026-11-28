package me.jbusdriver.modern.ui

import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.StateFlow
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.LtxPageHandshake
import me.jbusdriver.modern.data.ltx.LTX_GATE_PASSED_STATUSES
import me.jbusdriver.modern.data.ltx.LTX_ITEMS_PATH
import me.jbusdriver.modern.data.parser.listPageTypeFromPath
import me.jbusdriver.modern.ui.browser.BrowserProbeSpec
import me.jbusdriver.modern.ui.browser.BrowserScreen
import me.jbusdriver.modern.ui.browser.LocalWebNavigator
import me.jbusdriver.modern.ui.browser.WebNavigator
import me.jbusdriver.modern.ui.detail.MovieDetailScreen
import me.jbusdriver.modern.ui.forum.ForumThreadDetailScreen
import me.jbusdriver.modern.ui.forum.ForumThreadListScreen
import me.jbusdriver.modern.ui.image.ImageViewScreen
import me.jbusdriver.modern.ui.movielist.LinkMovieListRoute
import me.jbusdriver.modern.ui.movielist.LtxVerifyViewModel
import me.jbusdriver.modern.ui.search.SearchScreen
import me.jbusdriver.modern.ui.settings.SettingsScreen
import me.jbusdriver.modern.ui.sitemanager.SiteManagerScreen

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

    // 就绪窗口测量：首次组合（含 NavDisplay 返回回调注册）完成后触发，与 ModernMainActivity
    // 的 onCreate 日志对比即为窗口时长。KLog.d 仅 debug 构建输出。
    LaunchedEffect(Unit) {
        KLog.t("BackReady").d(
            "NavDisplay ready backStack=${backStack.size} at=${SystemClock.elapsedRealtime()}ms"
        )
    }

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

    // 网页统一入口：优先 push 内置浏览器（RouteBrowser），各界面经 rememberWebOpener() 使用
    val webNavigator = remember {
        WebNavigator { webUrl -> backStack.add(RouteBrowser(webUrl)) }
    }

    CompositionLocalProvider(LocalWebNavigator provides webNavigator) {
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
                        onForumUserClick = { uid ->
                            backStack.add(RouteUserSpace(uid))
                        },
                        onSettingsClick = { backStack.add(RouteSettings) },
                        onOpenLtxVerification = { url ->
                            backStack.add(RouteLtxVerify(url))
                        }
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
                                        type = listPageTypeFromPath(header.link) ?: "header",
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
                    LinkMovieListRoute(
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
                        onOpenMovieDetail = { url ->
                            backStack.add(RouteMovieDetail(movieUrl = url, censorType = null))
                        },
                        onOpenThread = { tid ->
                            backStack.add(RouteForumThreadDetail(tid))
                        },
                        onOpenBoard = { fid ->
                            backStack.add(RouteForumThreadList(fid = fid))
                        },
                        onOpenLinkMovies = { key ->
                            backStack.add(key)
                        },
                        onOpenUserSpace = { uid ->
                            backStack.add(RouteUserSpace(uid))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<RouteUserSpace> { key ->
                    me.jbusdriver.modern.ui.forum.account.UserSpaceScreen(
                        uid = key.uid,
                        onThreadClick = { tid ->
                            backStack.add(RouteForumThreadDetail(tid))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<RouteSettings>(
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
                ) {
                    SettingsScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenSiteManager = { backStack.add(RouteSiteManager) }
                    )
                }
                entry<RouteSiteManager>(
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
                ) {
                    SiteManagerScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<RouteBrowser>(
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
                    // 浏览器关闭（返回/系统手势均会退出组合）即通知论坛侧自动校准登录态：
                    // 叠加层返回不触发 ON_RESUME，须靠此信号（ForumAccountSignals）
                    val signalsVm: me.jbusdriver.modern.ui.forum.account.ForumAccountSignalsViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        onDispose { signalsVm.signals.notifyWebLoginClosed() }
                    }
                    BrowserScreen(
                        url = key.url,
                        title = key.title,
                        onImageClick = { images, startIndex ->
                            backStack.add(RouteImageViewer(images, startIndex))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<RouteLtxVerify>(
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
                    // LTX 人机验证浏览器：SPA 过 Turnstile 后页内探测 items 状态；
                    // 新协议（428）须在页内完成加密握手（完全验证）才导出会话自动关闭，
                    // 探测通过只证明边缘门禁，过早导出会让仓库层二次握手失败
                    val verifyVm: LtxVerifyViewModel = hiltViewModel()
                    val verifyTitle = stringResource(R.string.ltx_verify_title)
                    val verifyHint = stringResource(R.string.ltx_verify_hint)
                    val verifyPassed = stringResource(R.string.ltx_verify_passed)
                    val context = LocalContext.current
                    val itemsProbeUrl = remember(key.url) { key.url.trimEnd('/') + LTX_ITEMS_PATH }
                    BrowserScreen(
                        url = key.url,
                        title = key.title.ifBlank { verifyTitle },
                        probe = remember(key.url, verifyHint) {
                            BrowserProbeSpec(
                                probeUrl = itemsProbeUrl,
                                acceptStatuses = LTX_GATE_PASSED_STATUSES,
                                hint = verifyHint,
                                postGate = { status, webView ->
                                    when (status) {
                                        // 旧协议镜像（200）：无加密会话，Cookie 导出即用
                                        200 -> true
                                        // 新协议：页内握手完成才算完全验证
                                        else -> {
                                            val full = LtxPageHandshake.establish(webView, itemsProbeUrl)
                                            if (full != null) verifyVm.onCryptoSession(full)
                                            full != null
                                        }
                                    }
                                },
                            )
                        },
                        onVerified = { session ->
                            // 会话注入后浏览器自动关闭、列表静默刷新：Toast 补一句成功反馈
                            verifyVm.onVerified(session)
                            Toast.makeText(context, verifyPassed, Toast.LENGTH_SHORT).show()
                        },
                        onImageClick = { images, startIndex ->
                            backStack.add(RouteImageViewer(images, startIndex))
                        },
                        onBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
