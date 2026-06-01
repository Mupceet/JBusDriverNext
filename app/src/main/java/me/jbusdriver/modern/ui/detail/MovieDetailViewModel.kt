package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.MagnetRepository
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.UncensoredMovieCategory
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: String? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: String? = null,
    val isCollected: Boolean = false,
    val gid: String? = null,
    val uc: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository,
    private val magnetRepository: MagnetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private var currentUrl: String = ""
    private var censorType: String? = null

    fun loadDetail(url: String, censorType: String? = null) {
        if (currentUrl == url && _uiState.value.movieDetail != null) return
        if (_uiState.value.isLoading) return
        currentUrl = url
        this.censorType = censorType
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(url)
                _uiState.update {
                    it.copy(
                        movieDetail = detail.toUiModel(),
                        isLoading = false,
                        gid = detail.gid,
                        uc = detail.uc
                    )
                }
                loadMagnets()
                val movie = detail.toUiModel().toCollectionMovie(url)
                val collected = collectRepository.isMovieCollected(movie)
                _uiState.update { it.copy(isCollected = collected) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun refresh() {
        if (currentUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.getMovieDetail(currentUrl, forceRefresh = true)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun loadMagnets() {
        if (_uiState.value.isLoadingMagnets) return
        val gid = _uiState.value.gid ?: return
        val uc = _uiState.value.uc ?: "0"
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(Dispatchers.IO) {
                    magnetRepository.fetchMagnets(gid, uc).map { it.toUiModel() }
                }
                _uiState.update {
                    it.copy(magnets = magnets, isLoadingMagnets = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun toggleCollect() {
        val detail = _uiState.value.movieDetail ?: return
        val url = currentUrl
        val isUncensored = censorType == "UNCENSORED" ||
            detail.genres.any { "無碼" in it.name }
        viewModelScope.launch {
            val movie = detail.toCollectionMovie(url).apply {
                if (isUncensored) {
                    categoryId = UncensoredMovieCategory.id ?: 3
                }
            }
            val newState = collectRepository.toggleMovieCollect(movie)
            _uiState.update { it.copy(isCollected = newState) }
        }
    }
}

private fun MovieDetailUiModel.toCollectionMovie(link: String): Movie =
    Movie(
        title = title,
        imageUrl = cover,
        code = headers.firstOrNull()?.value ?: "",
        date = headers.firstOrNull { it.name == "發行日期" || it.name == "日期" || it.name == "发行日期" }?.value ?: "",
        link = link
    )
