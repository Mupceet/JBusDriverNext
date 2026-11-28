package me.jbusdriver.modern.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.jbusdriver.R
import me.jbusdriver.modern.ui.components.CollapsingSearchBar
import me.jbusdriver.modern.ui.components.hazeBottomBar
import me.jbusdriver.modern.ui.components.hazeSourceContent
import me.jbusdriver.modern.ui.components.hazeTopBar
import me.jbusdriver.modern.ui.components.rememberAppHazeState
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
    onForumUserClick: (Int) -> Unit = {},
    /** 匿名态点击账号条 → 全局登录弹窗（ForumLoginDialogViewModel，activity 级共享）。 */
    onForumLoginClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenLtxVerification: (String) -> Unit = {}
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
    val forumViewModel = if (showForumTab) hiltViewModel<ForumBoardsViewModel>() else null
    val forumAccountEnabled by store.forumAccountEnabled.collectAsStateWithLifecycle()
    val forumAccountState = forumViewModel?.accountState?.collectAsStateWithLifecycle()?.value
        ?: me.jbusdriver.modern.domain.model.ForumLoginState.Anonymous

    // 从其它界面返回本页时重新校准论坛登录态
    androidx.lifecycle.compose.LifecycleResumeEffect(forumAccountEnabled) {
        if (forumAccountEnabled) forumViewModel?.refreshAccountState()
        onPauseOrDispose { }
    }

    // Shared search bar collapses first (consuming scroll), then the list scrolls.
    val searchBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // 毛玻璃布局：持久玻璃头（状态栏 + 可收起搜索栏 + 常驻 chips）与底部导航悬浮在内容上，
    // 列表从两栏底下穿过
    val hazeState = rememberAppHazeState()
    val density = LocalDensity.current
    // 搜索栏当前可见高（随收起逐帧变小）。列表顶 padding 用它而非静态展开高：
    // 收起多少、内容就同步上移多少，还原占位式布局"头部与列表一起动"的手感
    var searchVisibleHeightPx by remember { mutableIntStateOf(0) }
    var chipsRowHeightPx by remember { mutableIntStateOf(0) }
    val statusTopInset = WindowInsets.statusBars.getTop(density)
    val contentTopPadding =
        with(density) { (statusTopInset + searchVisibleHeightPx + chipsRowHeightPx).toDp() }

    // tab 通过延迟槽位上报自己的 chips 行（闭包读 tab 本地状态，避免把 pager/VM 上提）。
    // 上报发生在组合期内且 tab 分支先于下方玻璃头组合，切换当帧即渲染、无空档闪现。
    // 槽位不清空：非当前 tab 的槽位不会被调用；tab 重进时必然重新组合并先重新上报，
    // 旧闭包无渲染机会。chipsRowHeightPx 同样保持上次实测值，列表顶 padding 不动。
    var movieChipsSlot by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var actressChipsSlot by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var collectChipsSlot by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier.hazeBottomBar(hazeState)
            ) {
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
        val contentBottomPadding = innerPadding.calculateBottomPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            saveableStateHolder.SaveableStateProvider(selectedCategory) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                        // 各 tab 的全部滚动内容作为模糊源；搜索栏浮层在 Box 内、不进源
                        .hazeSourceContent(hazeState)
                ) {
                    when (selectedCategory) {
                        BottomNavCategory.MOVIE -> MovieTabContent(
                            isGrid = isGrid,
                            toggleGrid = toggleGrid,
                            onSearchClick = onSearchClick,
                            onMovieClick = onMovieClick,
                            onOpenLtxVerification = onOpenLtxVerification,
                            contentPaddingTop = contentTopPadding,
                            contentPaddingBottom = contentBottomPadding,
                            onChipsAvailable = { movieChipsSlot = it }
                        )

                        BottomNavCategory.ACTRESS -> ActressTabContent(
                            onSearchClick = onSearchClick,
                            onActressClick = onActressClick,
                            contentPaddingTop = contentTopPadding,
                            contentPaddingBottom = contentBottomPadding,
                            onChipsAvailable = { actressChipsSlot = it }
                        )

                        BottomNavCategory.COLLECT -> CollectCategoryScreen(
                            onMovieClick = onMovieClick,
                            onActressClick = onActressClick,
                            onSearchClick = onSearchClick,
                            contentPaddingTop = contentTopPadding,
                            contentPaddingBottom = contentBottomPadding,
                            onChipsAvailable = { collectChipsSlot = it }
                        )

                        BottomNavCategory.FORUM -> {
                            ForumBoardsScreen(
                                onBoardClick = onForumBoardClick,
                                onThreadClick = onForumThreadClick,
                                contentPaddingTop = contentTopPadding,
                                contentPaddingBottom = contentBottomPadding
                            )
                        }
                    }
                }
            }
            // 持久玻璃头：一个连续玻璃面覆盖 状态栏 + 搜索栏（可收起）+ 常驻 chips。
            // haze 先于 statusBarsPadding，让玻璃面盖住状态栏区域；搜索栏收起后玻璃随之缩短。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .hazeTopBar(hazeState)
                    .statusBarsPadding()
            ) {
                CollapsingSearchBar(
                    onSearchClick = { onSearchClick("") },
                    onSettingsClick = onSettingsClick,
                    scrollState = searchBarScrollBehavior.state,
                    modifier = Modifier.onSizeChanged { searchVisibleHeightPx = it.height },
                    // 账号开关打开时，论坛页右上角设置图标换成账号头像
                    // （点击登录/用户中心，长按打开设置）；其余 tab 保持设置图标
                    trailingIcon = if (selectedCategory == BottomNavCategory.FORUM && forumAccountEnabled) {
                        {
                            me.jbusdriver.modern.ui.forum.account.ForumAccountAvatar(
                                state = forumAccountState,
                                onLoginClick = onForumLoginClick,
                                onUserClick = onForumUserClick,
                                onLongClick = onSettingsClick
                            )
                        }
                    } else {
                        null
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 先量尺寸再加底部间隙：量到的高度须含 6dp 间隙，
                        // 否则 contentPaddingTop 少 6dp、各页首行顶进玻璃
                        .onSizeChanged { chipsRowHeightPx = it.height }
                        .padding(bottom = 6.dp)
                ) {
                    when (selectedCategory) {
                        BottomNavCategory.MOVIE -> movieChipsSlot?.invoke()
                        BottomNavCategory.ACTRESS -> actressChipsSlot?.invoke()
                        BottomNavCategory.COLLECT -> collectChipsSlot?.invoke()
                        // 论坛：账号条已移除（账号入口收敛到搜索栏头像 ForumAccountAvatar），
                        // 玻璃头不含 chips 行，高度随内容自适应
                        BottomNavCategory.FORUM -> Unit
                    }
                }

                // 登录弹窗为全局宿主（Navigation 根层渲染），见 ForumLoginDialogHost
            }
        }
    }
}
