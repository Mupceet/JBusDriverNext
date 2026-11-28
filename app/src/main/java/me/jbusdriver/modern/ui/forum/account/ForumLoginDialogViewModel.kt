package me.jbusdriver.modern.ui.forum.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.domain.model.ForumLoginChallenge
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.ui.UserMessage
import javax.inject.Inject

private const val TAG = "ForumLogin"

/** 登录对话框状态；null = 关闭。错误为站点原文（parseDiscuzMessage），在对话框内展示。 */
data class ForumLoginUiState(
    val isLoading: Boolean = false,
    val challenge: ForumLoginChallenge? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

/**
 * 全局原生登录弹窗宿主（activity 级共享，Navigation 根层渲染）。
 *
 * 任何论坛界面的匿名写操作（账号条 / 帖子收藏 / 点赞 …）都经 [onLoginRequested]
 * 唤起同一弹窗；登录挑战获取、提交与服务端校验、登录态翻转自动关弹窗的逻辑
 * 集中在此，不依赖论坛首页 ViewModel 存活。
 */
@HiltViewModel
class ForumLoginDialogViewModel @Inject constructor(
    private val repository: ForumRepository
) : ViewModel() {

    private val _loginUiState = MutableStateFlow<ForumLoginUiState?>(null)
    val loginUiState: StateFlow<ForumLoginUiState?> = _loginUiState.asStateFlow()

    /** 一次性用户消息（登录成功 Toast 等），宿主收集展示。 */
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    init {
        // 登录态翻转为已登录时自动关闭弹窗（覆盖网页登录返回后的收敛）
        viewModelScope.launch {
            repository.loginState.collect { state ->
                if (state is ForumLoginState.LoggedIn && _loginUiState.value != null) {
                    _loginUiState.value = null
                    _messages.emit(UserMessage(R.string.forum_login_success))
                }
            }
        }
    }

    /**
     * 匿名写操作入口：先在后台校准登录态——网页登录返回后此处即收敛（弹窗自动关闭）；
     * 确认仍匿名才加载验证码打开原生登录框。不依赖生命周期回调
     * （内置浏览器为应用内叠加层，返回不触发 ON_RESUME）。
     */
    fun onLoginRequested() {
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
}
