package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.repository.MagnetRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import me.jbusdriver.modern.data.translation.TranslationConfigException
import me.jbusdriver.modern.data.translation.TranslationHttpException
import me.jbusdriver.modern.data.translation.TranslationRepository
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.UncensoredMovieCategory
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import me.jbusdriver.modern.ui.LtxLinkUiModel
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.UserMessage
import me.jbusdriver.modern.ui.toLtxLinkUiModels
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

/** 详情页标题翻译显示状态:原文 / 请求中 / 已译(持有译文)。 */
sealed interface TitleTranslationState {
    data object Hidden : TitleTranslationState
    data object Loading : TitleTranslationState
    data class Translated(val text: String) : TitleTranslationState
}

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val titleTranslation: TitleTranslationState = TitleTranslationState.Hidden,
    val error: Int? = null,
    val magnets: List<MagnetUiModel> = emptyList(),
    val isLoadingMagnets: Boolean = false,
    val magnetsError: Int? = null,
    val isCollected: Boolean = false,
    val gid: String? = null,
    val uc: String? = null,
    val localVideos: List<LocalVideo> = emptyList(),
    /** LTX 库提供的下载链接（ed2k/magnet），按番号匹配；未收录为空列表。 */
    val ltxLinks: List<LtxLinkUiModel> = emptyList(),
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository,
    private val magnetRepository: MagnetRepository,
    private val localVideoRepository: LocalVideoRepository,
    private val translationRepository: TranslationRepository,
    private val translationSettings: TranslationSettingsReader,
    private val ltxRepository: LtxRepository,
    private val ltxSettings: LtxSettingsSource,
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
            _uiState.update { it.copy(isLoading = true, error = null, ltxLinks = emptyList()) }
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
                applyDefaultTranslationPolicy()
                loadMagnets()
                val movie = detail.toUiModel().toCollectionMovie(url)
                val collected = collectRepository.isMovieCollected(movie)
                _uiState.update { it.copy(isCollected = collected) }
                val code = detail.headers.firstOrNull()?.value.orEmpty()
                loadLocalVideos(code)
                loadLtxLinks(code)
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
            } catch (e: CancellationException) {
                throw e
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
                applyDefaultTranslationPolicy()
                loadLtxLinks(detail.headers.firstOrNull()?.value.orEmpty())
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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

    /**
     * 异步查询 LTX 库中该番号的下载链接（ed2k/magnet）。
     *
     * 静默降级：功能关闭或库不可用时清空/保持空列表，绝不阻塞详情主流程；
     * 落地前校验当前详情番号未变，防止旧查询覆盖新影片。
     */
    private fun loadLtxLinks(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            val entry = if (ltxSettings.ltxEnabled.first()) {
                ltxRepository.findByCode(code)
            } else {
                null
            }
            _uiState.update { state ->
                val currentCode = state.movieDetail?.headers?.firstOrNull()?.value.orEmpty()
                if (normalizeLtxCode(currentCode) != normalizeLtxCode(code)) {
                    state
                } else {
                    state.copy(ltxLinks = entry.toLtxLinkUiModels())
                }
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

    /** 不改动收藏状态，仅删除指定本地视频（详情页溢出菜单入口）。 */
    fun deleteLocalVideos(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            emitDeleteResult(localVideoRepository.deleteVideos(ids))
        }
    }

    private suspend fun emitDeleteResult(result: DeleteResult) {
        when {
            result.deleted > 0 && result.failed > 0 ->
                _messages.emit(UserMessage(R.plurals.local_video_delete_partial, listOf(result.deleted, result.failed)))
            result.deleted > 0 ->
                _messages.emit(UserMessage(R.plurals.local_video_deleted_count, listOf(result.deleted)))
            result.failed > 0 ->
                _messages.emit(UserMessage(R.string.local_video_delete_all_failed))
        }
    }

    /** 详情页顶栏翻译入口:原文→译文 / 译文→原文;请求中忽略重复点击。 */
    fun toggleTitleTranslation() {
        when (_uiState.value.titleTranslation) {
            TitleTranslationState.Loading -> Unit
            is TitleTranslationState.Translated ->
                _uiState.update { it.copy(titleTranslation = TitleTranslationState.Hidden) }
            TitleTranslationState.Hidden -> startTitleTranslation()
        }
    }

    /** 详情(重新)加载后:重置翻译状态;按"默认翻译"设置决定是否自动翻译新标题。 */
    private fun applyDefaultTranslationPolicy() {
        _uiState.update { it.copy(titleTranslation = TitleTranslationState.Hidden) }
        viewModelScope.launch {
            if (translationSettings.translateByDefault.first()) startTitleTranslation()
        }
    }

    private fun startTitleTranslation() {
        val title = _uiState.value.movieDetail?.title?.takeIf { it.isNotBlank() } ?: return
        _uiState.update { it.copy(titleTranslation = TitleTranslationState.Loading) }
        viewModelScope.launch {
            translationRepository.translate(title).fold(
                onSuccess = { text ->
                    // 仅当仍在翻译当前标题(未被刷新取代)时落地,避免旧请求覆盖新标题的显示
                    _uiState.update { state ->
                        if (state.titleTranslation == TitleTranslationState.Loading && state.movieDetail?.title == title)
                            state.copy(titleTranslation = TitleTranslationState.Translated(text)) else state
                    }
                },
                onFailure = { cause ->
                    if (cause is CancellationException) return@launch
                    val stale = _uiState.value.let {
                        it.titleTranslation != TitleTranslationState.Loading || it.movieDetail?.title != title
                    }
                    if (stale) return@launch
                    _uiState.update { it.copy(titleTranslation = TitleTranslationState.Hidden) }
                    val resId = when {
                        cause is TranslationConfigException -> R.string.translate_config_error
                        cause is TranslationHttpException && cause.code == 429 -> R.string.translate_rate_limited
                        else -> R.string.translate_failed
                    }
                    _messages.emit(UserMessage(resId))
                }
            )
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
