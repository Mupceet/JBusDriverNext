package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import javax.inject.Inject

private const val TAG = "ForumVM"

data class ForumBoardsUiState(
    val banners: List<ForumBanner> = emptyList(),
    val summary: ForumHomeSummary = ForumHomeSummary(),
    val groups: List<ForumBoardGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Int? = null,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val refreshMessage: Int? = null
)

@HiltViewModel
class ForumBoardsViewModel @Inject constructor(
    private val repository: ForumRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForumBoardsUiState())
    val uiState: StateFlow<ForumBoardsUiState> = _uiState.asStateFlow()
    private var requestGeneration = 0L

    private fun beginRequest(): Long {
        requestGeneration += 1
        return requestGeneration
    }

    private fun isCurrent(generation: Long): Boolean = generation == requestGeneration

    init {
        KLog.d("[Forum] ForumBoardsViewModel init", TAG)
        loadBoards()
    }

    fun loadBoards() {
        if (_uiState.value.isLoading) return
        val generation = beginRequest()
        KLog.d("[Forum] loadBoards started", TAG)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    error = null,
                    refreshMessage = null
                )
            }
            var hasContent = false
            repository.observeForumBoards(revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update { it.applyBoardsCached(event.entry) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            _uiState.update { it.applyBoardsFresh(event.entry) }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.applyBoardsFailure(event, hasContent) }
                        }
                    }
                }
        }
    }

    fun refresh() {
        val generation = beginRequest()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeForumBoards(forceRefresh = true, revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update { it.applyBoardsRefreshFresh(event.entry) }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.applyBoardsRefreshFailure() }
                        }
                    }
                }
        }
    }

    fun revalidate() {
        if (_uiState.value.groups.isNotEmpty()) loadBoards()
    }

    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }
}
