package me.jbusdriver.modern.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.core.coroutine.IoDispatcher
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.mirror.ScanState
import me.jbusdriver.modern.data.repository.AppUpdateInfo
import me.jbusdriver.modern.data.repository.AppUpdateRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.settings.AppSettingsContract
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.ui.UserMessage
import javax.inject.Inject

/** 检查更新状态：checking 时入口转圈；availableUpdate 非空时展示更新弹框。 */
data class UpdateCheckState(
    val checking: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val store: AppSettingsContract,
    private val siteConfig: SiteConfig,
    private val localVideoRepository: LocalVideoRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateDownloader: AppUpdateDownloader,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _updateCheckState = MutableStateFlow(UpdateCheckState())
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    val localVideoSummary: StateFlow<LocalVideoSummary> =
        localVideoRepository.observeSummary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalVideoSummary())

    val showUncollectedLocal: StateFlow<Boolean> =
        localVideoRepository.observeShowUncollectedLocal()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLocalVideoFolder(uri: Uri) {
        viewModelScope.launch { localVideoRepository.setFolder(uri) }
    }

    fun clearLocalVideoFolder() {
        viewModelScope.launch { localVideoRepository.clearFolder() }
    }

    fun setShowUncollectedLocal(value: Boolean) {
        viewModelScope.launch { localVideoRepository.setShowUncollectedLocal(value) }
    }

    // region App update check
    fun checkForUpdate() {
        if (_updateCheckState.value.checking) return
        viewModelScope.launch {
            _updateCheckState.update { it.copy(checking = true) }
            try {
                val update = appUpdateRepository.checkForUpdate()
                if (update == null) {
                    _messages.emit(UserMessage(R.string.update_up_to_date))
                } else {
                    _updateCheckState.update { it.copy(availableUpdate = update) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.t(TAG).e("update check failed", e)
                _messages.emit(UserMessage(R.string.update_check_failed))
            } finally {
                _updateCheckState.update { it.copy(checking = false) }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateCheckState.update { it.copy(availableUpdate = null) }
    }

    /**
     * 开始下载更新 APK。下载/安装引导由进程级 [AppUpdateDownloader] 驱动，
     * 不随本 ViewModel 销毁而中断；结果 Toast 也由其直接发出。
     */
    fun startUpdateDownload(update: AppUpdateInfo) {
        dismissUpdateDialog()
        appUpdateDownloader.start(update)
    }
    // endregion

    // region Network scan (delegates to store -> MirrorScanner)
    fun startScan() = launchScan {
        store.scanMirrorUrls(_scanState, store.selectedBaseUrl.first())
    }
    fun startVerify() = launchScan { store.verifyMirrorUrls(_scanState) }
    fun cancelScan() { _scanState.value = ScanState() }
    fun selectUrl(url: String) {
        viewModelScope.launch { store.selectUrl(url); siteConfig.baseUrl = url }
    }
    private fun launchScan(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                _scanState.value = ScanState()
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.t(TAG).e("mirror scan failed", e)
                _scanState.value = ScanState()
            }
        }
    }
    // endregion

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
