package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadList

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
    val typeFilters: List<ForumTypeFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Int? = null,
    val hasMore: Boolean = true,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshThreads: ForumThreadPageResult? = null,
    val refreshMessage: Int? = null
)

@HiltViewModel(assistedFactory = ForumThreadListViewModel.Factory::class)
class ForumThreadListViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    @Assisted private val navKey: RouteForumThreadList
) : ViewModel() {
    private val fid: Int = navKey.fid
    private val initialTypeId: Int? = navKey.typeId
    private var currentPage = 0

    private var isAtTopForFreshUpdates: Boolean = true
    private var requestGeneration = 0L
    private var activeListIdentity: ThreadListIdentity? = null

    private data class ThreadListIdentity(
        val fid: Int,
        val typeId: Int?
    )

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        isAtTopForFreshUpdates = isAtTop
    }

    fun applyPendingFreshThreads() {
        val pending = _uiState.value.pendingFreshThreads ?: return
        logThreadDiff(_uiState.value.threads, pending.threads, "ThreadList.applyPending")
        currentPage = 1
        _uiState.update {
            it.copy(
                threads = pending.threads,
                pageInfo = pending.pageInfo,
                typeFilters = pending.typeFilters,
                pendingFreshThreads = null,
                refreshMessage = null,
                hasMore = pending.pageInfo.hasNext
            )
        }
    }

    private val _uiState = MutableStateFlow(ForumThreadListUiState())
    val uiState: StateFlow<ForumThreadListUiState> = _uiState.asStateFlow()

    init {
        KLog.d("[Forum] ForumThreadListViewModel init: fid=$fid, typeId=$initialTypeId", TAG)
        if (initialTypeId != null) {
            _uiState.update { it.copy(currentTypeId = initialTypeId) }
        }
        loadFirstPage()
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        KLog.d("[Forum] loadFirstPage: fid=$fid, typeId=${_uiState.value.currentTypeId}", TAG)
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null,
                    refreshMessage = null
                )
            }
            var hasContent = false
            repository.observeThreads(identity.fid, 1, identity.typeId, revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
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
                                isAtTop = isAtTopForFreshUpdates
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
            repository.observeThreads(fid, 1, state.currentTypeId, revalidate = false)
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
                                isAtTop = isAtTopForFreshUpdates
                            )
                            if (reduction.outcome == FreshRevalidateOutcome.ApplyImmediately) {
                                currentPage = 1
                            }
                            _uiState.value = reduction.state
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
        if (nextPage <= currentPage) return

        currentPage = nextPage
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val result = repository.loadThreads(fid, nextPage, state.currentTypeId)
                _uiState.update {
                    it.copy(
                        threads = (it.threads + result.threads).distinctBy { it.tid },
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                currentPage = _uiState.value.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeThreads(
                identity.fid,
                1,
                identity.typeId,
                forceRefresh = true,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            currentPage = 1
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads,
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters,
                                    isRefreshing = false,
                                    hasMore = event.entry.value.pageInfo.hasNext,
                                    pendingFreshThreads = null,
                                    refreshMessage = null
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
        currentPage = 0
        isAtTopForFreshUpdates = true
        _uiState.update {
            it.copy(
                currentTypeId = typeId,
                threads = emptyList(),
                pageInfo = PageInfo()
            )
        }
        loadFirstPage()
    }

    private fun currentListIdentity(): ThreadListIdentity =
        ThreadListIdentity(fid, _uiState.value.currentTypeId)

    private fun beginListRequest(identity: ThreadListIdentity): Long {
        requestGeneration += 1
        activeListIdentity = identity
        return requestGeneration
    }

    private fun isCurrent(generation: Long, identity: ThreadListIdentity): Boolean =
        generation == requestGeneration && activeListIdentity == identity

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadList): ForumThreadListViewModel
    }
}
