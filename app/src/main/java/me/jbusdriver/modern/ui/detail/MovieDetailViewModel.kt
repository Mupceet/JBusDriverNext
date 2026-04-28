package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.magnet.Magnet
import me.jbusdriver.magnet.MagnetManager
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.toUiModel
import org.json.JSONArray
import javax.inject.Inject

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: String? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: String? = null,
    val hasMoreMagnets: Boolean = true
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private var currentUrl: String = ""
    private var magnetPage: Int = 0

    fun loadDetail(url: String) {
        if (_uiState.value.isLoading) return
        currentUrl = url
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(url)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        if (currentUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.getMovieDetail(currentUrl)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun loadMagnets() {
        if (_uiState.value.isLoadingMagnets) return
        val url = currentUrl.ifBlank { return }
        magnetPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, 1)
                }
                _uiState.update {
                    it.copy(
                        magnets = magnets,
                        isLoadingMagnets = false,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
    }

    fun loadMoreMagnets() {
        if (_uiState.value.isLoadingMagnets || !_uiState.value.hasMoreMagnets) return
        val url = currentUrl.ifBlank { return }
        magnetPage++
        viewModelScope.launch {
            try {
                val more = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, magnetPage)
                }
                _uiState.update {
                    it.copy(
                        magnets = it.magnets + more,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                magnetPage--
            }
        }
    }

    private fun fetchMagnets(keyword: String, page: Int): List<MagnetUiModel> {
        val json = MagnetManager.getMagnets("default", keyword, page)
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            Magnet(
                name = obj.optString("name", ""),
                size = obj.optString("size", ""),
                date = obj.optString("date", ""),
                link = obj.optString("link", "")
            ).toUiModel()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
