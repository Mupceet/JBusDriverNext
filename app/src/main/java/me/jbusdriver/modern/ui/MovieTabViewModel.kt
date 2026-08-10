package me.jbusdriver.modern.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 电影 Tab 的页面级业务筛选状态。
 *
 * 筛选条件（有码/无码 + 已选分类）会驱动电影列表的加载与分类弹窗的展示，
 * 属于跨组件共享的业务状态，因此由 ViewModel 持有并用 [SavedStateHandle] 恢复；
 * 组合层只保留弹窗显隐等纯 UI 元素状态。
 */
internal data class MovieTabUiState(
    /** 当前有码/无码筛选 */
    val censorFilter: CensorFilter = CensorFilter.CENSORED,
    /** 当前筛选下已选中的分类链接集合 */
    val selectedGenreLinks: Set<String> = emptySet(),
    /** 各筛选标签下上次选中的分类记忆，切换标签时恢复 */
    val genreLinkMemory: Map<String, Set<String>> = emptyMap()
)

@HiltViewModel
internal class MovieTabViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieTabUiState())
    val uiState: StateFlow<MovieTabUiState> = _uiState.asStateFlow()

    init {
        val restoredFilter = savedStateHandle.get<String>(KEY_CENSOR_FILTER)
            ?.let { name -> runCatching { CensorFilter.valueOf(name) }.getOrNull() }
            ?: CensorFilter.CENSORED
        val restoredLinks =
            savedStateHandle.get<List<String>>(KEY_SELECTED_GENRE_LINKS)?.toSet() ?: emptySet()
        _uiState.value = MovieTabUiState(
            censorFilter = restoredFilter,
            selectedGenreLinks = restoredLinks
        )
    }

    /** 切换有码/无码标签：先记住当前标签的选择，再恢复目标标签的记忆。 */
    fun onCensorFilterChanged(filter: CensorFilter) {
        if (_uiState.value.censorFilter == filter) return
        _uiState.update {
            it.copy(
                genreLinkMemory = it.genreLinkMemory + (it.censorFilter.name to it.selectedGenreLinks),
                censorFilter = filter,
                selectedGenreLinks = it.genreLinkMemory[filter.name] ?: emptySet()
            )
        }
        persist()
    }

    /** 分类弹窗确认选择：更新当前标签的选中分类并持久化。 */
    fun onGenreSelectionChanged(links: Set<String>) {
        _uiState.update {
            it.copy(
                selectedGenreLinks = links,
                genreLinkMemory = it.genreLinkMemory + (it.censorFilter.name to links)
            )
        }
        persist()
    }

    private fun persist() {
        savedStateHandle[KEY_CENSOR_FILTER] = _uiState.value.censorFilter.name
        savedStateHandle[KEY_SELECTED_GENRE_LINKS] = _uiState.value.selectedGenreLinks.toList()
    }

    private companion object {
        const val KEY_CENSOR_FILTER = "movie_tab_censor_filter"
        const val KEY_SELECTED_GENRE_LINKS = "movie_tab_selected_genre_links"
    }
}
