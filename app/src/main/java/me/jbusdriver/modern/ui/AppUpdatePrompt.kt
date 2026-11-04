package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.repository.AppUpdateInfo
import me.jbusdriver.modern.data.repository.AppUpdateRepository
import me.jbusdriver.modern.data.settings.AppUpdateCheckStore
import me.jbusdriver.modern.ui.settings.AppUpdateDialog
import me.jbusdriver.modern.ui.settings.AppUpdateDownloader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** 自动检查间隔：距上次成功检查超过 6 小时的前台回返才再次检查。 */
internal const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

/** 距上次成功检查超过 [AUTO_CHECK_INTERVAL_MS]（含从未检查过）时返回 true。 */
internal fun isAutoCheckDue(lastSuccessfulCheckAtMs: Long, nowMs: Long): Boolean =
    nowMs - lastSuccessfulCheckAtMs >= AUTO_CHECK_INTERVAL_MS

/**
 * 应用回到前台时自动检查更新（注册到 [androidx.lifecycle.ProcessLifecycleOwner]，
 * 冷启动与后台回返各触发一次 ON_START）。
 *
 * 距上次成功检查不足 [AUTO_CHECK_INTERVAL_MS] 时直接跳过；检查成功（含已是最新）
 * 后记录时间；检查失败不记录，下次回前台自动重试。发现新版本时经 [pendingUpdate]
 * 通知全局弹框宿主 [AppUpdatePromptHost] 提示。
 */
@Singleton
class AppUpdateAutoChecker @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val checkStore: AppUpdateCheckStore,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pendingUpdate = MutableStateFlow<AppUpdateInfo?>(null)

    /** 当前待提示的新版本；非空时弹框展示，关闭或开始下载后清空。 */
    val pendingUpdate: StateFlow<AppUpdateInfo?> = _pendingUpdate.asStateFlow()

    private val inFlight = AtomicBoolean()

    // 测试注入点：时钟与检查协程的作用域（生产代码使用默认值）
    internal var nowMs: () -> Long = System::currentTimeMillis
    internal var checkScope: CoroutineScope = scope

    override fun onStart(owner: LifecycleOwner) {
        if (!isAutoCheckDue(checkStore.lastSuccessfulCheckAt(), nowMs())) return
        // 回前台高频事件，检查进行中时忽略重复触发
        if (!inFlight.compareAndSet(false, true)) return
        checkScope.launch {
            try {
                val update = appUpdateRepository.checkForUpdate()
                checkStore.recordSuccessfulCheck(nowMs())
                if (update != null) _pendingUpdate.value = update
                KLog.t(TAG).d("Auto update check done, update=${update?.versionName ?: "none"}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 自动检查静默失败，不打扰用户；未记录成功时间，下次回前台重试
                KLog.t(TAG).w("Auto update check failed: ${e.message}")
            } finally {
                inFlight.set(false)
            }
        }
    }

    /** 用户关闭自动提示弹框。 */
    fun dismiss() {
        _pendingUpdate.value = null
    }

    companion object {
        private const val TAG = "AppUpdateAutoChecker"
    }
}

/**
 * 全局更新提示弹框的状态入口：状态来自进程级 [AppUpdateAutoChecker]，
 * 下载走进程级 [AppUpdateDownloader]。
 */
@HiltViewModel
class AppUpdatePromptViewModel @Inject constructor(
    private val autoChecker: AppUpdateAutoChecker,
    private val appUpdateDownloader: AppUpdateDownloader,
) : ViewModel() {

    val pendingUpdate: StateFlow<AppUpdateInfo?> = autoChecker.pendingUpdate

    fun dismiss() {
        autoChecker.dismiss()
    }

    /** 关闭提示并开始下载；下载/安装引导由 [AppUpdateDownloader] 驱动。 */
    fun startDownload(update: AppUpdateInfo) {
        dismiss()
        appUpdateDownloader.start(update)
    }
}

/**
 * 挂在 Activity 根组合层的更新提示弹框宿主：用户停留在任意界面时，
 * 自动检查发现新版本都会弹出与设置页一致的 [AppUpdateDialog]。
 */
@Composable
fun AppUpdatePromptHost() {
    val viewModel: AppUpdatePromptViewModel = hiltViewModel()
    val pendingUpdate by viewModel.pendingUpdate.collectAsStateWithLifecycle()
    pendingUpdate?.let { update ->
        AppUpdateDialog(
            update = update,
            onDismiss = viewModel::dismiss,
            onConfirmDownload = { viewModel.startDownload(update) }
        )
    }
}
