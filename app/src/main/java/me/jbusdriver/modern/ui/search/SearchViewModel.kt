package me.jbusdriver.modern.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.SearchHistoryStore
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

/**
 * 搜索页的 UI 状态。
 *
 * 包含搜索关键词、搜索类型、搜索结果列表、分页信息及各加载/错误标志。
 * 搜索结果分为电影结果和女优结果，根据搜索类型互斥展示。
 */
data class SearchUiState(
    /** 当前搜索关键词 */
    val query: String = "",
    /** 当前搜索类型（有码/无码/女优等） */
    val searchType: SearchType = SearchType.CENSORED,
    /** 电影搜索结果列表 */
    val results: List<MovieUiModel> = emptyList(),
    /** 女优搜索结果列表 */
    val actressResults: List<ActressUiModel> = emptyList(),
    /** 是否正在搜索 */
    val isLoading: Boolean = false,
    /** 是否正在下拉刷新 */
    val isRefreshing: Boolean = false,
    /** 是否正在加载更多（翻页） */
    val isLoadingMore: Boolean = false,
    /** 是否还有更多搜索结果可加载 */
    val hasMore: Boolean = true,
    /** 错误信息，正常时为 null */
    val error: Int? = null,
    /** 当前搜索结果的页码 */
    val currentPage: Int = 0
)

/**
 * 搜索页 ViewModel。
 *
 * 职责：管理搜索功能，支持按不同类型（有码/无码/女优）搜索，
 * 支持分页加载更多搜索结果和下拉刷新。
 *
 * 使用场景：在搜索页面中使用，通过 Hilt 注入。
 * 用户输入关键词后发起搜索，可切换搜索类型重新搜索，
 * 支持下拉刷新和滚动到底部加载更多。
 *
 * 线程：网络请求通过 Repository 内部调度器执行，UI 状态通过 [MutableStateFlow] 更新。
 *
 * @param repository 搜索仓库，负责执行搜索请求并解析结果
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val historyStore: SearchHistoryStore
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(SearchUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 当前搜索请求的 Job，用于取消旧请求 */
    private var searchJob: Job? = null
    private var requestGeneration = 0L
    private var activeIdentity: RequestIdentity? = null

    private data class RequestIdentity(val query: String, val type: SearchType)

    /** 搜索历史记录 */
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        viewModelScope.launch { _searchHistory.value = historyStore.getHistory() }
    }

    /** 清除搜索历史 */
    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clearHistory()
            _searchHistory.value = emptyList()
        }
    }

    /** 删除单条搜索历史 */
    fun removeHistoryItem(query: String) {
        viewModelScope.launch {
            historyStore.removeQuery(query)
            _searchHistory.value = historyStore.getHistory()
        }
    }

    /** 清空搜索内容，恢复空状态 */
    fun clearSearch() {
        searchJob?.cancel()
        requestGeneration += 1
        activeIdentity = null
        _uiState.update {
            it.copy(
                query = "",
                results = emptyList(),
                actressResults = emptyList(),
                error = null,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false
            )
        }
    }

    /** 当前是否为女优搜索模式 */
    private val isActressSearch get() = _uiState.value.searchType == SearchType.ACTRESS

    /**
     * 使用指定关键词和类型执行搜索。
     *
     * 会重置结果列表并从第一页开始加载。如果未指定类型则使用当前已选类型。
     * 空关键词搜索会被忽略。
     *
     * @param query 搜索关键词
     * @param type 搜索类型，为 null 时使用当前 [SearchUiState.searchType]
     */
    fun search(query: String, type: SearchType? = null) {
        if (query.isBlank()) return
        val searchType = type ?: _uiState.value.searchType
        val identity = RequestIdentity(query, searchType)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            historyStore.addQuery(query)
            _searchHistory.value = historyStore.getHistory()
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val activeQuery = query
            val activeType = searchType
            _uiState.update {
                it.copy(
                    query = activeQuery,
                    searchType = activeType,
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null,
                    currentPage = 1
                )
            }
            try {
                if (activeType == SearchType.ACTRESS) {
                    val result = repository.searchActresses(activeQuery, 1)
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            actressResults = result.second.map { a -> a.toActressUiModel() },
                            results = emptyList(),
                            isLoading = false,
                            hasMore = result.first.hasNext,
                            currentPage = result.first.activePage
                        )
                    }
                } else {
                    val result = repository.searchMovies(activeType, activeQuery, 1)
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            results = result.movies.map { m -> m.toUiModel() },
                            actressResults = emptyList(),
                            isLoading = false,
                            hasMore = result.pageInfo.hasNext,
                            currentPage = result.pageInfo.activePage
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(generation, identity)) return@launch
                _uiState.update { it.copy(isLoading = false, error = R.string.search_failed) }
            }
        }
    }

    /**
     * 下拉刷新搜索结果。
     *
     * 重新从第一页加载搜索结果。如果正在刷新或关键词为空则跳过。
     */
    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.query.isBlank()) return
        val identity = RequestIdentity(state.query, state.searchType)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                if (state.searchType == SearchType.ACTRESS) {
                    val result = repository.searchActresses(state.query, 1)
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            actressResults = result.second.map { a -> a.toActressUiModel() },
                            isRefreshing = false,
                            hasMore = result.first.hasNext,
                            currentPage = result.first.activePage
                        )
                    }
                } else {
                    val result = repository.searchMovies(
                        state.searchType,
                        state.query,
                        1,
                        forceRefresh = true
                    )
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            results = result.movies.map { m -> m.toUiModel() },
                            isRefreshing = false,
                            hasMore = result.pageInfo.hasNext,
                            currentPage = result.pageInfo.activePage
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(generation, identity)) return@launch
                _uiState.update { it.copy(isRefreshing = false, error = R.string.search_failed) }
            }
        }
    }

    /**
     * 加载下一页搜索结果，追加到现有列表末尾。
     *
     * 如果正在加载、没有更多数据则跳过。根据当前搜索类型决定追加电影或女优结果。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1
        val identity = RequestIdentity(state.query, state.searchType)
        val generation = beginRequest(identity)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                if (state.searchType == SearchType.ACTRESS) {
                    val result = repository.searchActresses(state.query, nextPage)
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            actressResults = it.actressResults + result.second.map { a -> a.toActressUiModel() },
                            isLoadingMore = false,
                            hasMore = result.first.hasNext,
                            currentPage = result.first.activePage
                        )
                    }
                } else {
                    val result = repository.searchMovies(state.searchType, state.query, nextPage)
                    if (!isCurrent(generation, identity)) return@launch
                    _uiState.update {
                        it.copy(
                            results = it.results + result.movies.map { m -> m.toUiModel() },
                            isLoadingMore = false,
                            hasMore = result.pageInfo.hasNext,
                            currentPage = result.pageInfo.activePage
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(generation, identity)) return@launch
                _uiState.update { it.copy(isLoadingMore = false, error = R.string.search_failed) }
            }
        }
    }

    /**
     * 切换搜索类型。
     *
     * 如果当前有关键词，则立即使用新类型重新搜索；
     * 否则仅更新类型，等待用户输入后搜索。
     *
     * @param type 新的搜索类型
     */
    fun setSearchType(type: SearchType) {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            search(query, type)
        } else {
            _uiState.update { it.copy(searchType = type) }
        }
    }

    /** 清除当前的错误信息 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 更新搜索关键词的输入文本。
     *
     * 仅更新状态中的关键词，不触发搜索。
     *
     * @param query 用户输入的搜索关键词
     */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    private fun beginRequest(identity: RequestIdentity): Long {
        requestGeneration += 1
        activeIdentity = identity
        return requestGeneration
    }

    private fun isCurrent(generation: Long, identity: RequestIdentity): Boolean {
        return generation == requestGeneration &&
            activeIdentity == identity
    }
}
