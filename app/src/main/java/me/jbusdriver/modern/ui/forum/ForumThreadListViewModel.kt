package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadList

private const val TAG = "ForumVM"

private fun logThreadDiff(oldThreads: List<ForumThread>, newThreads: List<ForumThread>, context: String) {
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeThreads(fid, 1, _uiState.value.currentTypeId, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads,
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters.ifEmpty { it.typeFilters },
                                    isLoading = false,
                                    isRevalidating = event.entry.isExpired,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                threads = event.entry.value.threads.simulateCacheRefreshChange()
                            )
                            val oldThreads = _uiState.value.threads
                            val oldFirstPage = oldThreads.take(fresh.threads.size)
                            val hasChanged = oldFirstPage != fresh.threads
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.loadFirstPage")
                            if (isAtTopForFreshUpdates) {
                                _uiState.update {
                                    it.copy(
                                        threads = fresh.threads,
                                        pageInfo = fresh.pageInfo,
                                        typeFilters = fresh.typeFilters,
                                        hasMore = fresh.pageInfo.hasNext,
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasChanged) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = fresh,
                                        refreshMessage = R.string.new_data_available
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(isLoading = false, isRevalidating = false) }
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRevalidating = false)
                                } else {
                                    it.copy(isLoading = false, isRevalidating = false, error = R.string.load_failed)
                                }
                            }
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
                            val hasChanged = oldFirstPage != fresh.threads
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.revalidate")
                            if (isAtTopForFreshUpdates) {
                                currentPage = 1
                                _uiState.update {
                                    it.copy(
                                        threads = fresh.threads,
                                        pageInfo = fresh.pageInfo,
                                        typeFilters = fresh.typeFilters,
                                        hasMore = fresh.pageInfo.hasNext,
                                        isRevalidating = false,
                                        pendingFreshThreads = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasChanged) {
                                _uiState.update {
                                    it.copy(
                                        isRevalidating = false,
                                        pendingFreshThreads = fresh,
                                        refreshMessage = R.string.new_data_available
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(isRevalidating = false) }
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
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeThreads(fid, 1, _uiState.value.currentTypeId, forceRefresh = true, revalidate = false)
                .collect { event ->
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
        _uiState.update { it.copy(currentTypeId = typeId, threads = emptyList(), pageInfo = PageInfo()) }
        loadFirstPage()
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadList): ForumThreadListViewModel
    }
}
