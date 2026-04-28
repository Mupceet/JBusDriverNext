package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.DataSourceType
import javax.inject.Inject

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreListUiState())
    val uiState: StateFlow<GenreListUiState> = _uiState.asStateFlow()

    private var dataSourceType: DataSourceType = DataSourceType.GENRE

    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.genreCategories.isNotEmpty()) return
        dataSourceType = type
        _uiState.value = GenreListUiState()
        loadGenres()
    }

    private fun loadGenres() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val categories = repository.loadGenreCategories(dataSourceType)
                _uiState.update {
                    it.copy(
                        genreCategories = categories,
                        isLoading = false,
                        error = if (categories.isEmpty()) "没有数据" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }
}
