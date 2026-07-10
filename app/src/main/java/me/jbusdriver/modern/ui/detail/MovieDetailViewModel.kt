package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.MagnetRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.UncensoredMovieCategory
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.UserMessage
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: Int? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: Int? = null,
    val isCollected: Boolean = false,
    val gid: String? = null,
    val uc: String? = null,
    val localVideos: List<LocalVideo> = emptyList(),
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository,
    private val magnetRepository: MagnetRepository,
    private val localVideoRepository: LocalVideoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    private var currentUrl: String = ""
    private var censorType: String? = null
    private var localVideoJob: Job? = null

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
                val code = detail.headers.firstOrNull()?.value.orEmpty()
                loadLocalVideos(code)
                // 看过即补全：把标题/封面/日期回填到本地视频表
                viewModelScope.launch {
                    runCatching {
                        localVideoRepository.snapshotMetadata(
                            code = movie.code,
                            title = movie.title,
                            imageUrl = movie.imageUrl,
                            date = movie.date,
                            censorType = censorType,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = R.string.load_failed) }
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
                _uiState.update { it.copy(isRefreshing = false, error = R.string.load_failed) }
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
                val magnets = magnetRepository.fetchMagnets(gid, uc).map { it.toUiModel() }
                _uiState.update {
                    it.copy(magnets = magnets, isLoadingMagnets = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMagnets = false,
                        magnetsError = R.string.load_failed
                    )
                }
            }
        }
    }

    private fun loadLocalVideos(code: String) {
        localVideoJob?.cancel()
        if (code.isBlank()) {
            _uiState.update { it.copy(localVideos = emptyList()) }
            return
        }
        localVideoJob = viewModelScope.launch {
            localVideoRepository.observeForCode(code).collect { videos ->
                _uiState.update { it.copy(localVideos = videos) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun toggleCollect() {
        val detail = _uiState.value.movieDetail ?: return
        val url = currentUrl
        val isUncensored = censorType == "UNCENSORED"
        viewModelScope.launch {
            val movie = detail.toCollectionMovie(url)
            val categoryId = if (isUncensored) UncensoredMovieCategory.id ?: 3 else null
            val newState = collectRepository.toggleMovieCollect(movie, categoryId)
            _uiState.update { it.copy(isCollected = newState) }
        }
    }

    /** 取消收藏并保留本地视频。 */
    fun uncollectKeepVideos() = doUncollect(deleteIds = null)

    /** 取消收藏并删除该番号全部本地视频。 */
    fun uncollectDeleteAll() {
        val ids = _uiState.value.localVideos.map { it.id }
        doUncollect(deleteIds = ids)
    }

    /** 取消收藏并删除选中的本地视频。 */
    fun uncollectDeleteSelected(ids: List<Int>) = doUncollect(deleteIds = ids)

    /** 不改动收藏状态，仅删除指定本地视频（详情页溢出菜单入口）。 */
    fun deleteLocalVideos(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            emitDeleteResult(localVideoRepository.deleteVideos(ids))
        }
    }

    private fun doUncollect(deleteIds: List<Int>?) {
        val detail = _uiState.value.movieDetail ?: return
        viewModelScope.launch {
            val movie = detail.toCollectionMovie(currentUrl)
            val categoryId = if (censorType == "UNCENSORED") UncensoredMovieCategory.id ?: 3 else null
            val newState = collectRepository.toggleMovieCollect(movie, categoryId)
            _uiState.update { it.copy(isCollected = newState) }
            if (deleteIds != null && !newState) {
                emitDeleteResult(localVideoRepository.deleteVideos(deleteIds))
            }
        }
    }

    private suspend fun emitDeleteResult(result: DeleteResult) {
        when {
            result.deleted > 0 && result.failed > 0 ->
                _messages.emit(UserMessage(R.string.local_video_delete_partial, listOf(result.deleted, result.failed)))
            result.deleted > 0 ->
                _messages.emit(UserMessage(R.plurals.local_video_deleted_count, listOf(result.deleted)))
            result.failed > 0 ->
                _messages.emit(UserMessage(R.string.local_video_delete_all_failed))
        }
    }
}

private fun MovieDetailUiModel.toCollectionMovie(link: String): Movie =
    Movie(
        title = title,
        imageUrl = cover,
        code = headers.firstOrNull()?.value ?: "",
        date = headers.firstOrNull { it.name == "發行日期" || it.name == "日期" || it.name == "发行日期" }?.value
            ?: "",
        link = link
    )
