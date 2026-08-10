package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.SavedStateHandle
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
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.PagedSwrStateHolder
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.settings.ForumSettingsReader
import me.jbusdriver.modern.data.settings.ForumThreadOrder
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadList
import me.jbusdriver.modern.ui.UserMessage

private const val TAG = "ForumVM"

private fun logThreadDiff(
    oldThreads: List<ForumThread>,
    newThreads: List<ForumThread>,
    context: String
) {
    me.jbusdriver.modern.core.logListDiff(
        oldItems = oldThreads,
        newItems = newThreads,
        context = context,
        tag = TAG,
        keySelector = { it.tid },
        describe = { "${it.tid}:${it.title.take(20)}" },
        diffFields = { old, new ->
            buildList {
                if (old.replyCount != new.replyCount) add("replyCount:${old.replyCount}→${new.replyCount}")
                if (old.viewCount != new.viewCount) add("viewCount:${old.viewCount}→${new.viewCount}")
                if (old.title != new.title) add("title改變")
                if (old.isPinned != new.isPinned) add("isPinned:${old.isPinned}→${new.isPinned}")
                if (old.isDigest != new.isDigest) add("isDigest:${old.isDigest}→${new.isDigest}")
                if (old.lastReplyAuthor != new.lastReplyAuthor) add("lastReply:${old.lastReplyAuthor}→${new.lastReplyAuthor}")
                if (old.lastReplyTime != new.lastReplyTime) add("lastTime:${old.lastReplyTime}→${new.lastReplyTime}")
            }
        }
    )
}

data class ForumThreadListUiState(
    val threads: List<ForumThread> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val currentTypeId: Int? = null,
    val currentThreadOrder: ForumThreadOrder = ForumThreadOrder.LASTPOST,
    val typeFilters: List<ForumTypeFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Int? = null,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshThreads: ForumThreadPageResult? = null
) {
    /** 是否还有更多数据可加载（由 [pageInfo] 派生，避免与 hasNext 重复保存） */
    val hasMore: Boolean
        get() = pageInfo.hasNext
}

@HiltViewModel(assistedFactory = ForumThreadListViewModel.Factory::class)
class ForumThreadListViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    private val forumSettingsReader: ForumSettingsReader,
    @Assisted private val navKey: RouteForumThreadList,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val fid: Int = navKey.fid
    private val initialTypeId: Int? = navKey.typeId
    /** 分页/顶部开关/请求代际的状态机 */
    private val swr = PagedSwrStateHolder<ThreadListIdentity>()

    private data class ThreadListIdentity(
        val fid: Int,
        val typeId: Int?,
        val threadOrder: ForumThreadOrder
    )

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        swr.atTop.isAtTop = isAtTop
    }

    fun applyPendingFreshThreads() {
        val pending = _uiState.value.pendingFreshThreads ?: return
        logThreadDiff(_uiState.value.threads, pending.threads, "ThreadList.applyPending")
        swr.pages.startFirstPage()
        _uiState.update {
            it.copy(
                threads = pending.threads,
                pageInfo = pending.pageInfo,
                typeFilters = pending.typeFilters,
                pendingFreshThreads = null
            )
        }
    }

    private val _uiState = MutableStateFlow(ForumThreadListUiState())
    val uiState: StateFlow<ForumThreadListUiState> = _uiState.asStateFlow()

    /** 一次性用户消息（Snackbar/Toast），UI 展示后即视为消费 */
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    init {
        KLog.d("[Forum] ForumThreadListViewModel init: fid=$fid, typeId=$initialTypeId", TAG)
        if (initialTypeId != null) {
            _uiState.update { it.copy(currentTypeId = initialTypeId) }
        }
        viewModelScope.launch {
            val defaultOrder = forumSettingsReader.currentThreadSortOrder()
            val restoredOrder = savedStateHandle.get<String>(KEY_THREAD_ORDER)
                ?.let { name -> runCatching { ForumThreadOrder.valueOf(name) }.getOrNull() }
            val restoredTypeId = savedStateHandle.get<String>(KEY_TYPE_ID)?.toIntOrNull()
            _uiState.update {
                it.copy(
                    currentThreadOrder = restoredOrder ?: defaultOrder,
                    currentTypeId = restoredTypeId ?: it.currentTypeId
                )
            }
            loadFirstPage()
        }
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        swr.pages.startFirstPage()
        KLog.d("[Forum] loadFirstPage: fid=$fid, typeId=${_uiState.value.currentTypeId}", TAG)
        val identity = currentListIdentity()
        val generation = swr.begin(identity)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null
                )
            }
            var hasContent = false
            repository.observeThreads(identity.fid, 1, identity.typeId, identity.threadOrder, revalidate = false)
                .collect { event ->
                    if (!swr.isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update { it.applyFirstPageCached(event.entry) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                threads = event.entry.value.threads.simulateCacheRefreshChange()
                            )
                            val oldThreads = _uiState.value.threads
                            val oldFirstPage = oldThreads.take(fresh.threads.size)
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.loadFirstPage")
                            val reduction = _uiState.value.applyFirstPageFresh(
                                event.entry.copy(value = fresh),
                                isAtTop = swr.atTop.isAtTop
                            )
                            _uiState.value = reduction.state
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.applyFirstPageFailure(event, hasContent) }
                        }
                    }
                }
        }
    }

    fun revalidate() {
        val state = _uiState.value
        if (state.threads.isEmpty() || state.isLoading || state.isRevalidating || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeThreads(fid, 1, state.currentTypeId, state.currentThreadOrder, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                threads = event.entry.value.threads.simulateCacheRefreshChange()
                            )
                            val oldThreads = _uiState.value.threads
                            val oldFirstPage = oldThreads.take(fresh.threads.size)
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.revalidate")
                            val reduction = _uiState.value.applyFreshRevalidate(
                                event.entry.copy(value = fresh),
                                isAtTop = swr.atTop.isAtTop
                            )
                            if (reduction.outcome == FreshRevalidateOutcome.ApplyImmediately) {
                                swr.pages.startFirstPage()
                            }
                            _uiState.value = reduction.state
                            if (reduction.outcome == FreshRevalidateOutcome.StorePending) {
                                _messages.emit(UserMessage(R.string.new_data_available))
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.copy(isRevalidating = false) }
                        }
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (!swr.pages.shouldLoadMore(state.pageInfo)) return

        swr.pages.advanceTo(nextPage)
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val result = repository.loadThreads(fid, nextPage, state.currentTypeId, state.currentThreadOrder)
                _uiState.update {
                    it.copy(
                        threads = (it.threads + result.threads).distinctBy { it.tid },
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                swr.pages.rollbackTo(_uiState.value.pageInfo.activePage)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val identity = currentListIdentity()
        val generation = swr.begin(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            repository.observeThreads(
                identity.fid,
                1,
                identity.typeId,
                identity.threadOrder,
                forceRefresh = true,
                revalidate = false
            )
                .collect { event ->
                    if (!swr.isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            swr.pages.startFirstPage()
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads,
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters,
                                    isRefreshing = false,
                                    pendingFreshThreads = null
                                )
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.threads.isEmpty()) R.string.load_failed else it.error
                                )
                            }
                        }
                    }
                }
        }
    }

    fun filterByType(typeId: Int?) {
        if (_uiState.value.currentTypeId == typeId) return
        savedStateHandle[KEY_TYPE_ID] = typeId?.toString()
        swr.resetForNewSource()
        swr.atTop.isAtTop = true
        _uiState.update {
            it.copy(
                currentTypeId = typeId,
                threads = emptyList(),
                pageInfo = PageInfo()
            )
        }
        loadFirstPage()
    }

    fun setThreadOrder(order: ForumThreadOrder) {
        if (_uiState.value.currentThreadOrder == order) return
        savedStateHandle[KEY_THREAD_ORDER] = order.name
        swr.resetForNewSource()
        swr.atTop.isAtTop = true
        _uiState.update {
            it.copy(
                currentThreadOrder = order,
                threads = emptyList(),
                pageInfo = PageInfo()
            )
        }
        loadFirstPage()
    }

    private fun currentListIdentity(): ThreadListIdentity =
        ThreadListIdentity(fid, _uiState.value.currentTypeId, _uiState.value.currentThreadOrder)

    private companion object {
        const val KEY_TYPE_ID = "forum_thread_list_type_id"
        const val KEY_THREAD_ORDER = "forum_thread_list_thread_order"
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadList): ForumThreadListViewModel
    }
}
