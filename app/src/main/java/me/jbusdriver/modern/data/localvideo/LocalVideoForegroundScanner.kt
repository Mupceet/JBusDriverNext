package me.jbusdriver.modern.data.localvideo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.KLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听应用进入前台，触发本地视频索引重扫。
 *
 * 注册到 [androidx.lifecycle.ProcessLifecycleOwner]，ON_START（冷启动 + 从后台返回）各触发一次；
 * 视频不多、扫描快，故不做节流。重扫本身在仓库内串行化。
 */
@Singleton
class LocalVideoForegroundScanner @Inject constructor(
    private val repository: LocalVideoRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            runCatching { repository.rescan() }
                .onFailure { KLog.w("Local video rescan failed: ${it.message}") }
        }
    }
}
