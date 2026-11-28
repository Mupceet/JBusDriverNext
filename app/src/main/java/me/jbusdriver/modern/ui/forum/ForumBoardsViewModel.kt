package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.settings.ForumSettingsReader
import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.ui.UserMessage
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
    val lastUpdatedAtMillis: Long? = null
)

/** 登录对话框状态与全局宿主见 ui/forum/account/ForumLoginDialogViewModel.kt（activity 级共享）。 */

@HiltViewModel
class ForumBoardsViewModel @Inject constructor(
    private val repository: ForumRepository,
    private val forumSettingsReader: ForumSettingsReader,
    private val accountSignals: me.jbusdriver.modern.data.repository.ForumAccountSignals
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForumBoardsUiState())
    val uiState: StateFlow<ForumBoardsUiState> = _uiState.asStateFlow()

    /** 论坛登录态（服务端校准，账号开关打开时维护）。 */
    val accountState: StateFlow<ForumLoginState> = repository.loginState

    /** 一次性用户消息（Snackbar/Toast），UI 展示后即视为消费 */
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    private var requestGeneration = 0L

    private fun beginRequest(): Long {
        requestGeneration += 1
        return requestGeneration
    }

    private fun isCurrent(generation: Long): Boolean = generation == requestGeneration

    init {
        KLog.d("[Forum] ForumBoardsViewModel init", TAG)
        loadBoards()
        // 设置页切换敏感内容显示后按新可见性重载（缓存优先，即时生效）
        viewModelScope.launch {
            forumSettingsReader.showSensitiveForumContent.drop(1).collect { loadBoards() }
        }
        // 账号开关：打开时恢复缓存登录态并实测校准；开关关闭的登出由 SettingsViewModel 处理
        viewModelScope.launch {
            forumSettingsReader.forumAccountEnabled.collect { enabled ->
                if (enabled) {
                    repository.tryRestoreCachedLoginState()
                    repository.refreshLoginState()
                }
            }
        }
        // 浏览器关闭即静默校准登录态（覆盖从帖子链接进浏览器手动登录的场景；
        // 见 ForumAccountSignals——叠加层返回不触发 ON_RESUME，须靠此信号）
        viewModelScope.launch {
            accountSignals.webLoginClosed.collect {
                if (!forumSettingsReader.forumAccountEnabled.value) return@collect
                if (repository.loginState.value is ForumLoginState.LoggedIn) return@collect
                repository.refreshLoginState()
                if (repository.loginState.value is ForumLoginState.LoggedIn) {
                    _messages.emit(UserMessage(R.string.forum_login_success))
                }
            }
        }
    }

    /** 重新校准登录态（网页登录返回 / 恢复到前台时调用）。 */
    fun refreshAccountState() {
        if (!forumSettingsReader.forumAccountEnabled.value) return
        viewModelScope.launch { repository.refreshLoginState() }
    }

    /** 敏感内容（性息等）默认隐藏：在数据进入 UiState 的边界过滤，仓库缓存保持原始数据。 */
    private fun sanitizeEntry(entry: CacheEntry<ForumHomeData>): CacheEntry<ForumHomeData> =
        if (forumSettingsReader.showSensitiveForumContent.value) entry
        else entry.copy(value = entry.value.withoutSensitiveBoards())

    fun loadBoards() {
        if (_uiState.value.isLoading) return
        val generation = beginRequest()
        KLog.d("[Forum] loadBoards started", TAG)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    error = null
                )
            }
            var hasContent = false
            repository.observeForumBoards(revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update { it.applyBoardsCached(sanitizeEntry(event.entry)) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            _uiState.update { it.applyBoardsFresh(sanitizeEntry(event.entry)) }
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
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            repository.observeForumBoards(forceRefresh = true, revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update { it.applyBoardsRefreshFresh(sanitizeEntry(event.entry)) }
                        }

                        is CachedLoadEvent.Failure -> {
                            val hadContent = _uiState.value.groups.isNotEmpty()
                            _uiState.update { it.applyBoardsRefreshFailure() }
                            if (hadContent) {
                                _messages.emit(UserMessage(R.string.refresh_failed))
                            }
                        }
                    }
                }
        }
    }

    fun revalidate() {
        if (_uiState.value.groups.isNotEmpty()) loadBoards()
    }

}
