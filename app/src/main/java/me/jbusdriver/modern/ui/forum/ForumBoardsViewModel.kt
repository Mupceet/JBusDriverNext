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
import me.jbusdriver.modern.domain.model.ForumLoginChallenge
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

/** 登录对话框状态；null = 关闭。错误为站点原文（parseDiscuzMessage），在对话框内展示。 */
data class ForumLoginUiState(
    val isLoading: Boolean = false,
    val challenge: ForumLoginChallenge? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

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

    private val _loginUiState = MutableStateFlow<ForumLoginUiState?>(null)
    val loginUiState: StateFlow<ForumLoginUiState?> = _loginUiState.asStateFlow()

    /** 登录成功一次性事件（UI 关闭对话框）。 */
    private val _loginSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSucceeded: SharedFlow<Unit> = _loginSucceeded.asSharedFlow()

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
        // 登录态翻转为已登录时自动关闭登录弹窗（覆盖网页登录返回后的收敛）
        viewModelScope.launch {
            repository.loginState.collect { state ->
                if (state is ForumLoginState.LoggedIn && _loginUiState.value != null) {
                    _loginUiState.value = null
                    _loginSucceeded.tryEmit(Unit)
                    _messages.emit(UserMessage(R.string.forum_login_success))
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

    /**
     * 匿名态点击账号条：先在后台校准登录态——网页登录返回后此处即收敛（条变已登录、
     * 弹窗自动关闭）；确认仍匿名才加载验证码打开原生登录框。不依赖生命周期回调
     * （内置浏览器为应用内叠加层，返回不触发 ON_RESUME）。
     */
    fun onAccountBarTapped() {
        if (_loginUiState.value != null) return
        _loginUiState.value = ForumLoginUiState(isLoading = true)
        viewModelScope.launch {
            repository.refreshLoginState()
            if (repository.loginState.value is ForumLoginState.Anonymous && _loginUiState.value != null) {
                runCatching { repository.requestLoginChallenge() }
                    .onSuccess { challenge ->
                        _loginUiState.update { it?.copy(challenge = challenge, isLoading = false) }
                    }
                    .onFailure { e ->
                        _loginUiState.update {
                            it?.copy(error = e.message ?: "驗證碼獲取失敗，請重試", isLoading = false)
                        }
                    }
            } else {
                _loginUiState.value = null
            }
        }
    }

    /** 打开登录对话框并获取验证码挑战；也用于"刷新验证码"。 */
    fun requestLoginChallenge() {
        viewModelScope.launch {
            _loginUiState.value = ForumLoginUiState(isLoading = true)
            runCatching { repository.requestLoginChallenge() }
                .onSuccess { challenge ->
                    _loginUiState.value = ForumLoginUiState(challenge = challenge)
                }
                .onFailure { e ->
                    KLog.e("[Forum] requestLoginChallenge failed: ${e.message}", TAG)
                    _loginUiState.value = ForumLoginUiState(error = e.message ?: "驗證碼獲取失敗，請重試")
                }
        }
    }

    fun submitLogin(username: String, password: String, seccode: String) {
        val challenge = _loginUiState.value?.challenge ?: return
        viewModelScope.launch {
            _loginUiState.update { it?.copy(isSubmitting = true, error = null) }
            val result = runCatching {
                repository.login(challenge, username, password, seccode)
            }.getOrElse { Result.failure(it) }
            result.onSuccess {
                _loginUiState.value = null
                _loginSucceeded.tryEmit(Unit)
                _messages.emit(UserMessage(R.string.forum_login_success))
            }.onFailure { e ->
                // 失败后旧验证码已失效：保留错误文案（标题位展示），槽位转刷新中并自动换新图
                _loginUiState.update {
                    (it ?: ForumLoginUiState()).copy(
                        isSubmitting = false,
                        isLoading = true,
                        challenge = null,
                        error = e.message ?: "登錄失敗"
                    )
                }
                runCatching { repository.requestLoginChallenge() }
                    .onSuccess { newChallenge ->
                        _loginUiState.update { it?.copy(challenge = newChallenge, isLoading = false) }
                    }
                    .onFailure {
                        _loginUiState.update { it?.copy(isLoading = false) }
                    }
            }
        }
    }

    fun dismissLoginDialog() {
        _loginUiState.value = null
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
