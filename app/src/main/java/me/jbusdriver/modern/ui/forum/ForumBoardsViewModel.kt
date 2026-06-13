package me.jbusdriver.modern.ui.forum

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
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

    init {
        KLog.d("[Forum] ForumBoardsViewModel init", TAG)
        loadBoards()
    }

    fun loadBoards() {
        if (_uiState.value.isLoading) return
        KLog.d("[Forum] loadBoards started", TAG)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeForumBoards(revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isLoading = false,
                                    isRevalidating = event.entry.isExpired,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isLoading = false,
                                    isRevalidating = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
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

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeForumBoards(forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isRefreshing = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.groups.isEmpty()) R.string.load_failed else it.error,
                                    refreshMessage = if (it.groups.isNotEmpty()) R.string.refresh_failed else null
                                )
                            }
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

    override fun onCleared() {
        super.onCleared()
        repository.destroySession()
    }
}
