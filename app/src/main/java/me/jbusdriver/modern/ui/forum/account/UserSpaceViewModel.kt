package me.jbusdriver.modern.ui.forum.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.domain.model.ForumFavoritePageResult
import me.jbusdriver.modern.domain.model.ForumFavoriteThread
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumUserProfile
import me.jbusdriver.modern.domain.model.ForumUserThread
import me.jbusdriver.modern.domain.model.ForumUserThreadPageResult
import me.jbusdriver.modern.domain.model.ForumUserThreadType
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteUserSpace
import me.jbusdriver.modern.ui.UserMessage

private const val TAG = "UserSpaceVM"

/** 个人主页页签；收藏为登录私有数据，仅本人可见。 */
enum class UserSpaceTab {
    THREADS, REPLIES, FAVORITES, ABOUT;

    /** 对应的站内列表类型；收藏与资料页签无。 */
    val threadType: ForumUserThreadType?
        get() = when (this) {
            THREADS -> ForumUserThreadType.THREAD
            REPLIES -> ForumUserThreadType.REPLY
            FAVORITES, ABOUT -> null
        }
}

/** 单个列表页签的内容：首页走 SWR（缓存先出、Fresh 替换），追加页直接网络抓取。 */
data class UserListState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** 下一个待加载的追加页页码，由首页的分页信息给出（1 = 首页尚未加载成功）。 */
    val nextPage: Int = 1,
    /** 最近一次请求失败：无内容时整页重试，有内容时列表尾重试。 */
    val error: Boolean = false,
)

data class UserSpaceUiState(
    val uid: Int = 0,
    val isSelf: Boolean = false,
    val profile: ForumUserProfile? = null,
    val profileLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoggingOut: Boolean = false,
    val selectedTab: UserSpaceTab = UserSpaceTab.THREADS,
    val threads: UserListState<ForumUserThread> = UserListState(),
    val replies: UserListState<ForumUserThread> = UserListState(),
    val favorites: UserListState<ForumFavoriteThread> = UserListState(),
)

/**
 * 个人主页：资料 + 主題/回覆/收藏/資料 四个页签的内容都在本页就地切换。
 * 各页签首次进入时才加载，加载过的直接复用；整页下拉刷新会强制重拉资料与当前页签首页。
 */
@HiltViewModel(assistedFactory = UserSpaceViewModel.Factory::class)
class UserSpaceViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    @Assisted navKey: RouteUserSpace
) : ViewModel() {

    private val uid: Int = navKey.uid

    private val _uiState = MutableStateFlow(
        UserSpaceUiState(
            uid = uid,
            isSelf = (repository.loginState.value as? ForumLoginState.LoggedIn)?.uid == uid
        )
    )
    val uiState: StateFlow<UserSpaceUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    /** 退出登录成功：页面应关闭（回到论坛首页，账号条已回到匿名态）。 */
    private val _loggedOut = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loggedOut: SharedFlow<Unit> = _loggedOut.asSharedFlow()

    private val loadedTabs = mutableSetOf<UserSpaceTab>()
    private var pendingRefreshes = 0
    private var refreshFailed = false

    init {
        KLog.d("[UserSpace] init uid=$uid isSelf=${_uiState.value.isSelf}", TAG)
        loadProfile(forceRefresh = false)
        loadTab(UserSpaceTab.THREADS)
    }

    fun selectTab(tab: UserSpaceTab) {
        if (tab == UserSpaceTab.FAVORITES && !_uiState.value.isSelf) return
        _uiState.update { it.copy(selectedTab = tab) }
        loadTab(tab)
    }

    /** 整页下拉刷新：资料 + 当前页签首页跳过缓存强制重拉；全部结束后收起指示圈。 */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val tab = _uiState.value.selectedTab
        // 先把本轮要等的路数记满再启动请求：请求可能同步完成，边启动边计数会提前归零
        pendingRefreshes = if (tab == UserSpaceTab.ABOUT) 1 else 2
        refreshFailed = false
        _uiState.update { it.copy(isRefreshing = true) }
        loadProfile(forceRefresh = true)
        tab.threadType?.let { type -> observeThreads(type, forceRefresh = true) }
        if (tab == UserSpaceTab.FAVORITES) observeFavorites(forceRefresh = true)
    }

    /** 触底加载当前列表页签的下一页。 */
    fun loadMore() {
        when (val tab = _uiState.value.selectedTab) {
            UserSpaceTab.FAVORITES -> loadMoreFavorites()
            UserSpaceTab.ABOUT -> Unit
            else -> tab.threadType?.let(::loadMoreThreads)
        }
    }

    /** 失败重试：资料缺失时重拉资料；列表无内容时重拉首页，有内容时续接失败的追加页。 */
    fun retry() {
        val state = _uiState.value
        if (state.profile == null && !state.profileLoading) {
            _uiState.update { it.copy(profileLoading = true) }
            loadProfile(forceRefresh = false)
        }
        when (val tab = state.selectedTab) {
            UserSpaceTab.FAVORITES ->
                if (state.favorites.items.isEmpty()) {
                    updateFavorites { it.copy(isLoading = true) }
                    observeFavorites(forceRefresh = false)
                } else {
                    loadMoreFavorites()
                }

            UserSpaceTab.ABOUT -> Unit
            else -> {
                val type = tab.threadType ?: return
                if (threadList(type).items.isEmpty()) {
                    updateThreads(type) { it.copy(isLoading = true) }
                    observeThreads(type, forceRefresh = false)
                } else {
                    loadMoreThreads(type)
                }
            }
        }
    }

    /** 退出登录：成功后收起仅本人可见的内容，并通知页面关闭。 */
    fun logout() {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            try {
                repository.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e("[UserSpace] logout failed: ${e.message}", TAG)
                _uiState.update { it.copy(isLoggingOut = false) }
                _messages.emit(UserMessage(R.string.refresh_failed))
                return@launch
            }
            loadedTabs.remove(UserSpaceTab.FAVORITES)
            _uiState.update {
                it.copy(
                    isLoggingOut = false,
                    isSelf = false,
                    favorites = UserListState(),
                    selectedTab = if (it.selectedTab == UserSpaceTab.FAVORITES) {
                        UserSpaceTab.THREADS
                    } else {
                        it.selectedTab
                    }
                )
            }
            _loggedOut.emit(Unit)
        }
    }

    /** 首次进入某列表页签时拉首页；加载成功过的页签直接复用，不重复请求。 */
    private fun loadTab(tab: UserSpaceTab) {
        if (tab in loadedTabs) return
        val type = tab.threadType
        when {
            type != null -> {
                if (threadList(type).isLoading) return
                updateThreads(type) { it.copy(isLoading = true) }
                observeThreads(type, forceRefresh = false)
            }

            tab == UserSpaceTab.FAVORITES -> {
                if (_uiState.value.favorites.isLoading) return
                updateFavorites { it.copy(isLoading = true) }
                observeFavorites(forceRefresh = false)
            }
        }
    }

    private fun loadProfile(forceRefresh: Boolean) {
        viewModelScope.launch {
            repository.observeUserProfile(uid, forceRefresh, revalidate = false).collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> _uiState.update {
                        it.copy(profile = event.entry.value, profileLoading = false)
                    }

                    is CachedLoadEvent.Fresh -> {
                        _uiState.update { it.copy(profile = event.entry.value, profileLoading = false) }
                        if (forceRefresh) finishRefresh(succeeded = true)
                    }

                    is CachedLoadEvent.Failure -> {
                        KLog.e("[UserSpace] load profile failed (force=$forceRefresh)", TAG)
                        _uiState.update { it.copy(profileLoading = false) }
                        if (forceRefresh) finishRefresh(succeeded = false)
                    }
                }
            }
        }
    }

    private suspend fun finishRefresh(succeeded: Boolean) {
        if (!succeeded) refreshFailed = true
        pendingRefreshes--
        if (pendingRefreshes > 0) return
        pendingRefreshes = 0
        _uiState.update { it.copy(isRefreshing = false) }
        // 资料与列表可能同时失败：一次刷新最多提示一次
        if (refreshFailed) {
            refreshFailed = false
            _messages.emit(UserMessage(R.string.refresh_failed))
        }
    }

    private fun threadList(type: ForumUserThreadType): UserListState<ForumUserThread> =
        if (type == ForumUserThreadType.THREAD) _uiState.value.threads else _uiState.value.replies

    private fun updateThreads(
        type: ForumUserThreadType,
        transform: (UserListState<ForumUserThread>) -> UserListState<ForumUserThread>
    ) {
        _uiState.update { state ->
            if (type == ForumUserThreadType.THREAD) {
                state.copy(threads = transform(state.threads))
            } else {
                state.copy(replies = transform(state.replies))
            }
        }
    }

    private fun updateFavorites(
        transform: (UserListState<ForumFavoriteThread>) -> UserListState<ForumFavoriteThread>
    ) {
        _uiState.update { it.copy(favorites = transform(it.favorites)) }
    }

    private fun observeThreads(type: ForumUserThreadType, forceRefresh: Boolean) {
        viewModelScope.launch {
            repository.observeUserThreads(uid, type, 1, forceRefresh).collect { event ->
                when (event) {
                    // 强制刷新时缓存不再回放，只认新数据
                    is CachedLoadEvent.Cached ->
                        if (!forceRefresh) applyThreads(type, event.entry.value, append = false)

                    is CachedLoadEvent.Fresh -> {
                        applyThreads(type, event.entry.value, append = false)
                        if (forceRefresh) finishRefresh(succeeded = true)
                    }

                    is CachedLoadEvent.Failure -> {
                        KLog.e("[UserSpace] load threads failed (type=$type force=$forceRefresh)", TAG)
                        if (forceRefresh) {
                            finishRefresh(succeeded = false)
                        } else {
                            updateThreads(type) { it.copy(isLoading = false, error = it.items.isEmpty()) }
                        }
                    }
                }
            }
        }
    }

    private fun applyThreads(
        type: ForumUserThreadType,
        result: ForumUserThreadPageResult,
        append: Boolean
    ) {
        loadedTabs.add(
            if (type == ForumUserThreadType.THREAD) UserSpaceTab.THREADS else UserSpaceTab.REPLIES
        )
        updateThreads(type) { state ->
            state.copy(
                items = (if (append) state.items else emptyList())
                    .plus(result.threads)
                    .distinctBy { it.tid },
                isLoading = false,
                isLoadingMore = false,
                error = false,
                endReached = result.threads.isEmpty() || !result.pageInfo.hasNext,
                nextPage = result.pageInfo.nextPage
            )
        }
    }

    private fun loadMoreThreads(type: ForumUserThreadType) {
        val current = threadList(type)
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        val page = current.nextPage
        if (page <= 1) return
        updateThreads(type) { it.copy(isLoadingMore = true, error = false) }
        viewModelScope.launch {
            try {
                applyThreads(type, repository.loadUserThreads(uid, type, page), append = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e("[UserSpace] load more threads failed: ${e.message}", TAG)
                updateThreads(type) { it.copy(isLoadingMore = false, error = true) }
            }
        }
    }

    private fun observeFavorites(forceRefresh: Boolean) {
        viewModelScope.launch {
            repository.observeUserFavorites(1, forceRefresh).collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached ->
                        if (!forceRefresh) applyFavorites(event.entry.value, append = false)

                    is CachedLoadEvent.Fresh -> {
                        applyFavorites(event.entry.value, append = false)
                        if (forceRefresh) finishRefresh(succeeded = true)
                    }

                    is CachedLoadEvent.Failure -> {
                        KLog.e("[UserSpace] load favorites failed (force=$forceRefresh)", TAG)
                        if (forceRefresh) {
                            finishRefresh(succeeded = false)
                        } else {
                            updateFavorites { it.copy(isLoading = false, error = it.items.isEmpty()) }
                        }
                    }
                }
            }
        }
    }

    private fun applyFavorites(result: ForumFavoritePageResult, append: Boolean) {
        loadedTabs.add(UserSpaceTab.FAVORITES)
        updateFavorites { state ->
            state.copy(
                items = (if (append) state.items else emptyList())
                    .plus(result.favorites)
                    .distinctBy { it.tid },
                isLoading = false,
                isLoadingMore = false,
                error = false,
                endReached = result.favorites.isEmpty() || !result.pageInfo.hasNext,
                nextPage = result.pageInfo.nextPage
            )
        }
    }

    private fun loadMoreFavorites() {
        val current = _uiState.value.favorites
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        val page = current.nextPage
        if (page <= 1) return
        updateFavorites { it.copy(isLoadingMore = true, error = false) }
        viewModelScope.launch {
            try {
                applyFavorites(repository.loadUserFavorites(page), append = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e("[UserSpace] load more favorites failed: ${e.message}", TAG)
                updateFavorites { it.copy(isLoadingMore = false, error = true) }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteUserSpace): UserSpaceViewModel
    }
}
